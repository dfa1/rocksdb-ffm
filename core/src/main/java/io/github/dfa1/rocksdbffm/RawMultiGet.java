package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Shared bookkeeping for the `char** values_list` + `size_t* values_list_sizes` + `char** errs`
/// multi-get shape used by `rocksdb_transactiondb_multi_get[_cf]` and
/// `rocksdb_transaction_multi_get[_for_update][_cf]` (see [TransactionDBReadBatch],
/// [TransactionReadBatch]) — malloc'd raw buffers freed via [RocksDB#free(MemorySegment)], unlike
/// [ReadBatch]'s `rocksdb_pinnableslice_t**` values, which are wrapped and released through
/// [PinnableSlice]. Not-found uses the same convention either way: a NULL entry, with no
/// corresponding `errs` entry set.
///
/// Takes no [java.lang.invoke.MethodHandle] — every native call site still invokes its own
/// `private static final MH_...` field directly; this only processes the parallel output arrays a
/// call already populated, the same kind of consolidation [NativeFields] does for cold-path
/// getter/setter boilerplate.
final class RawMultiGet {

	private RawMultiGet() {
	}

	/// Preallocated bookkeeping shared verbatim by [TransactionDBReadBatch] and
	/// [TransactionReadBatch]: keys/key-sizes for the call, values-list/values-list-sizes/errs for
	/// the results, and — only when a column family was fixed at batch-create time — a per-key
	/// column-family array. Both native call families take one column family per key, unlike
	/// `rocksdb_batched_multi_get_cf`'s single shared handle, but a batch's own UX still fixes one
	/// column family for its whole lifetime, so [#cfArr] just gets refilled with the same handle
	/// repeated `n` times before each call.
	static final class Buffers implements AutoCloseable {

		private final Arena arena;
		private final int capacity;
		final MemorySegment keysArr;
		final MemorySegment keySizesArr;
		final MemorySegment valuesListArr;
		final MemorySegment valuesListSizesArr;
		final MemorySegment errsArr;
		/// `null` unless this batch was created with a column family.
		final MemorySegment cfArr;
		private boolean closed;

		private Buffers(int capacity, boolean withCf) {
			this.arena = Arena.ofConfined();
			this.capacity = capacity;
			this.keysArr = arena.allocate(ValueLayout.ADDRESS, capacity);
			this.keySizesArr = arena.allocate(ValueLayout.JAVA_LONG, capacity);
			this.valuesListArr = arena.allocate(ValueLayout.ADDRESS, capacity);
			this.valuesListSizesArr = arena.allocate(ValueLayout.JAVA_LONG, capacity);
			this.errsArr = arena.allocate(ValueLayout.ADDRESS, capacity);
			this.cfArr = withCf ? arena.allocate(ValueLayout.ADDRESS, capacity) : null;
		}

		/// Allocates a fresh set of buffers for up to `capacity` keys per call.
		///
		/// @param capacity maximum number of keys any single call may pass; must be positive
		/// @param withCf   whether to also allocate the per-key column-family array
		/// @return a new [Buffers]; caller must close it
		static Buffers allocate(int capacity, boolean withCf) {
			if (capacity <= 0) {
				throw new IllegalArgumentException("capacity must be positive: " + capacity);
			}
			return new Buffers(capacity, withCf);
		}

		int capacity() {
			return capacity;
		}

		@Override
		public void close() {
			if (!closed) {
				closed = true;
				arena.close();
			}
		}
	}

	/// Writes both halves of key slot `i` together, mirroring `ReadBatch.writeKeySlot`.
	///
	/// @param keysArr     preallocated keys array to write into
	/// @param keySizesArr preallocated key-sizes array to write into
	/// @param i           slot index
	/// @param keyPtr      native pointer to this key's bytes
	/// @param keyLen      this key's length in bytes
	static void writeKeySlot(MemorySegment keysArr, MemorySegment keySizesArr, int i, MemorySegment keyPtr, long keyLen) {
		keysArr.setAtIndex(ValueLayout.ADDRESS, i, keyPtr);
		keySizesArr.setAtIndex(ValueLayout.JAVA_LONG, i, keyLen);
	}

	static void checkCapacity(int n, int capacity) {
		if (n > capacity) {
			throw new IllegalArgumentException("keys.size() " + n + " exceeds capacity " + capacity);
		}
	}

	/// Scans `errs` for every genuine per-key error (as opposed to not-found, which never sets an
	/// `errs` entry). Every error after the first is attached to it via
	/// [Throwable#addSuppressed(Throwable)], mirroring `ReadBatch.collectErrors`.
	///
	/// @param errsArr native array of `n` `char*` error slots
	/// @param n       number of keys in this call
	/// @return the first per-key error, with every subsequent one suppressed on it, or `null` if
	/// every key succeeded (found or not found)
	static RocksDBException collectErrors(MemorySegment errsArr, int n) {
		RocksDBException firstError = null;
		for (int i = 0; i < n; i++) {
			MemorySegment errSlot = errsArr.asSlice((long) i * ValueLayout.ADDRESS.byteSize(), ValueLayout.ADDRESS);
			try {
				RocksDB.checkError(errSlot);
			} catch (RocksDBException e) {
				if (firstError == null) {
					firstError = e;
				} else {
					firstError.addSuppressed(e);
				}
			}
		}
		return firstError;
	}

	/// Frees every found value's raw malloc'd buffer without copying it — used once
	/// [#collectErrors] has already determined the whole call is going to fail, so nothing but
	/// cleanup is left to do.
	///
	/// @param valuesListArr native array of `n` `char*` value slots
	/// @param n             number of keys in this call
	static void drainValues(MemorySegment valuesListArr, int n) {
		for (int i = 0; i < n; i++) {
			MemorySegment valuePtr = valuesListArr.getAtIndex(ValueLayout.ADDRESS, i);
			if (!MemorySegment.NULL.equals(valuePtr)) {
				RocksDB.free(valuePtr);
			}
		}
	}

	/// Maps every found value through `fn` with no intermediate copy, freeing each raw buffer
	/// exactly once. If any key reported a genuine error, every value is drained instead (so
	/// nothing leaks) and that error is thrown — nothing here is mapped, since the result would be
	/// discarded anyway.
	static <R> List<R> collect(MemorySegment valuesListArr, MemorySegment valuesListSizesArr, MemorySegment errsArr, int n, Mapper<R> fn) {
		RocksDBException firstError = collectErrors(errsArr, n);
		if (firstError != null) {
			drainValues(valuesListArr, n);
			throw firstError;
		}
		List<R> result = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			MemorySegment valuePtr = valuesListArr.getAtIndex(ValueLayout.ADDRESS, i);
			if (MemorySegment.NULL.equals(valuePtr)) {
				result.add(null);
			} else {
				long len = valuesListSizesArr.getAtIndex(ValueLayout.JAVA_LONG, i);
				MemorySegment view = valuePtr.reinterpret(len).asReadOnly();
				R mapped = fn.map(view);
				Objects.requireNonNull(mapped, "Mapper.map(MemorySegment) must not return null");
				result.add(mapped);
				RocksDB.free(valuePtr);
			}
		}
		return result;
	}

	/// Same walk-and-drain contract as [#collect], but copies each found value to a `byte[]`
	/// instead of mapping it through a callback.
	static List<byte[]> collectBytes(MemorySegment valuesListArr, MemorySegment valuesListSizesArr, MemorySegment errsArr, int n) {
		RocksDBException firstError = collectErrors(errsArr, n);
		if (firstError != null) {
			drainValues(valuesListArr, n);
			throw firstError;
		}
		List<byte[]> result = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			MemorySegment valuePtr = valuesListArr.getAtIndex(ValueLayout.ADDRESS, i);
			if (MemorySegment.NULL.equals(valuePtr)) {
				result.add(null);
			} else {
				long len = valuesListSizesArr.getAtIndex(ValueLayout.JAVA_LONG, i);
				result.add(RocksDB.toByteArray(valuePtr, len));
				RocksDB.free(valuePtr);
			}
		}
		return result;
	}

	/// Same walk-and-drain contract as [#collect], but copies each found value into the
	/// caller-supplied `values.get(i)` buffer instead of mapping it through a callback.
	static List<CopyResult> collectBuffers(MemorySegment valuesListArr, MemorySegment valuesListSizesArr, MemorySegment errsArr, int n, List<ByteBuffer> values) {
		RocksDBException firstError = collectErrors(errsArr, n);
		if (firstError != null) {
			drainValues(valuesListArr, n);
			throw firstError;
		}
		List<CopyResult> result = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			MemorySegment valuePtr = valuesListArr.getAtIndex(ValueLayout.ADDRESS, i);
			if (MemorySegment.NULL.equals(valuePtr)) {
				result.add(CopyResult.NotFound.INSTANCE);
			} else {
				long len = valuesListSizesArr.getAtIndex(ValueLayout.JAVA_LONG, i);
				ByteBuffer dest = values.get(i);
				if (len > dest.remaining()) {
					result.add(new CopyResult.NotEnoughCapacity(len));
				} else {
					MemorySegment.ofBuffer(dest).copyFrom(valuePtr.reinterpret(len));
					dest.position(dest.position() + (int) len);
					result.add(CopyResult.Copied.INSTANCE);
				}
				RocksDB.free(valuePtr);
			}
		}
		return result;
	}
}
