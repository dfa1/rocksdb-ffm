package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/// Reusable, preallocated batch for repeated multiGet calls against the same database (and,
/// optionally, the same column family), all three tiers. The keys/sizes/values/errors
/// bookkeeping arrays `rocksdb_batched_multi_get_cf` needs are allocated once, for up to
/// [#capacity()] keys, and reused across every `get` call, instead of allocating a fresh
/// [Arena] and fresh arrays every time — the sole entry point for batched reads in this library,
/// there is no separate one-shot `multiGet()` anywhere else.
///
/// The reuse benefit is real for all three tiers' bookkeeping (how many keys, where the results
/// land), but only the MemorySegment tier avoids per-call allocation entirely: `byte[]` keys
/// still get copied into a fresh, call-scoped [Arena] every time (their *content* changes call
/// to call even when their *shape* doesn't), and `ByteBuffer` keys/values are supplied
/// off-heap by the caller already. A one-off, single-batch read still just means
/// `try (var batch = ReadBatch.create(db, keys.size())) { return batch.get(keys, fn); }`.
///
/// `db`'s (and `cf`'s, if given) native pointer is re-resolved on every `get` call rather
/// than cached at [#create] time — the same safety convention every other wrapper in this
/// codebase follows. Closing `db` (or `cf`) between calls surfaces as [IllegalStateException]
/// from the next `get` call, not a stale-pointer crash.
///
/// ```
/// try (var batch = ReadBatch.create(db, 16)) {
///     for (List<MemorySegment> keys : manyBatchesOfAtMost16Keys) {
///         List<Long> sizes = batch.get(keys, MemorySegment::byteSize);
///     }
/// }
/// ```
public final class ReadBatch implements AutoCloseable {

	/// `rocksdb_column_family_handle_t* rocksdb_get_default_column_family_handle(rocksdb_t* db);`
	private static final MethodHandle MH_GET_DEFAULT_CF;
	/// `void rocksdb_batched_multi_get_cf(rocksdb_t* db, const rocksdb_readoptions_t* options, rocksdb_column_family_handle_t* column_family, size_t num_keys, const char* const* keys_list, const size_t* keys_list_sizes, rocksdb_pinnableslice_t** values, char** errs, const bool sorted_input);`
	private static final MethodHandle MH_BATCHED_MULTI_GET_CF;

	static {
		MH_GET_DEFAULT_CF = NativeLibrary.lookup("rocksdb_get_default_column_family_handle",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_BATCHED_MULTI_GET_CF = NativeLibrary.lookup("rocksdb_batched_multi_get_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_BOOLEAN));
	}

	private final Arena arena;
	private final RocksDBReadOperations db;
	private final ColumnFamilyHandle cf;
	private final int capacity;
	private final MemorySegment keysArr;
	private final MemorySegment keySizesArr;
	private final MemorySegment valuesArr;
	private final MemorySegment errsArr;
	private final MemorySegment vallenOut;
	// Unlike NativeObject's AtomicReference-guarded close(), a confined Arena's own close()
	// throws (rather than no-oping) on a second call, so this class needs its own idempotency
	// guard. A plain boolean is enough: Arena.ofConfined() already restricts this object to a
	// single thread, so there is no concurrent-close race to protect against beyond that.
	private boolean closed;

	private ReadBatch(RocksDBReadOperations db, ColumnFamilyHandle cf, int capacity) {
		this.arena = Arena.ofConfined();
		this.db = db;
		this.cf = cf;
		this.capacity = capacity;
		this.keysArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.keySizesArr = arena.allocate(ValueLayout.JAVA_LONG, capacity);
		this.valuesArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.errsArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.vallenOut = arena.allocate(ValueLayout.JAVA_LONG);
	}

	/// Creates a batch reading from `db`'s default column family, with room for up to
	/// `capacity` keys per [#get] call.
	///
	/// @param db       database to read from
	/// @param capacity maximum number of keys any single [#get] call may pass; must be positive
	/// @return a new [ReadBatch]; caller must close it
	public static ReadBatch create(RocksDBReadOperations db, int capacity) {
		return create(db, null, capacity);
	}

	/// Creates a batch reading from `cf`, with room for up to `capacity` keys per [#get] call.
	///
	/// @param db       database to read from
	/// @param cf       column family every key in every [#get] call belongs to
	/// @param capacity maximum number of keys any single [#get] call may pass; must be positive
	/// @return a new [ReadBatch]; caller must close it
	public static ReadBatch create(RocksDBReadOperations db, ColumnFamilyHandle cf, int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive: " + capacity);
		}
		return new ReadBatch(db, cf, capacity);
	}

	/// The maximum number of keys a single [#get] call on this batch may pass.
	///
	/// @return this batch's capacity
	public int capacity() {
		return capacity;
	}

	/// [#get(ReadOptions, List)] with default [ReadOptions].
	///
	/// @param keys keys to look up; `keys.size()` must be at most [#capacity()]
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @return one entry per key, in the same order, `null` where not found
	public List<byte[]> get(List<byte[]> keys) {
		return get(RocksDB.DEFAULT_READ_OPTIONS, keys);
	}

	/// Reads `keys` in one batched native call, reusing this batch's preallocated bookkeeping
	/// arrays. Unlike the MemorySegment tier, each key's bytes are still copied into a fresh,
	/// call-scoped native buffer — there is nothing to preallocate for content that changes
	/// every call. `null` at index `i` means `keys.get(i)` was not found; a genuine per-key
	/// error surfaces as a thrown [RocksDBException] only after every key has been processed
	/// and every native resource released.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up; `keys.size()` must be at most [#capacity()]
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @return one entry per key, in the same order, `null` where not found
	public List<byte[]> get(ReadOptions readOptions, List<byte[]> keys) {
		int n = keys.size();
		checkCapacity(n);
		if (n == 0) {
			return List.of();
		}
		try (Arena callArena = Arena.ofConfined()) {
			MemorySegment cfPtr = cf != null ? cf.ptr() : (MemorySegment) MH_GET_DEFAULT_CF.invokeExact(db.dbPtr());
			for (int i = 0; i < n; i++) {
				byte[] key = keys.get(i);
				keysArr.setAtIndex(ValueLayout.ADDRESS, i, RocksDB.toNative(callArena, key));
				keySizesArr.setAtIndex(ValueLayout.JAVA_LONG, i, key.length);
			}
			MH_BATCHED_MULTI_GET_CF.invokeExact(db.dbPtr(), readOptions.ptr(), cfPtr, (long) n,
					keysArr, keySizesArr, valuesArr, errsArr, false);
			return collectBytes(n);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("ReadBatch.get failed", t);
		}
	}

	/// [#get(ReadOptions, List, List)] with default [ReadOptions].
	///
	/// @param keys   keys to look up; `keys.size()` must be at most [#capacity()]
	/// @param values one destination buffer per key, in the same order
	/// @throws IllegalArgumentException if `keys` and `values` differ in size, or if
	///                                  `keys.size()` exceeds [#capacity()]
	/// @return one [CopyResult] per key, in the same order
	public List<CopyResult> get(List<ByteBuffer> keys, List<ByteBuffer> values) {
		return get(RocksDB.DEFAULT_READ_OPTIONS, keys, values);
	}

	/// Reads `keys` into the corresponding pre-sized buffer in `values` (same index, same
	/// order), advancing each buffer's position on a successful copy, reusing this batch's
	/// preallocated bookkeeping arrays. `keys` and `values` must be the same size.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up; `keys.size()` must be at most [#capacity()]
	/// @param values      one destination buffer per key, in the same order
	/// @throws IllegalArgumentException if `keys` and `values` differ in size, or if
	///                                  `keys.size()` exceeds [#capacity()]
	/// @return one [CopyResult] per key, in the same order
	public List<CopyResult> get(ReadOptions readOptions, List<ByteBuffer> keys, List<ByteBuffer> values) {
		int n = keys.size();
		if (values.size() != n) {
			throw new IllegalArgumentException("keys and values must be the same size: " + n + " vs " + values.size());
		}
		checkCapacity(n);
		if (n == 0) {
			return List.of();
		}
		try {
			MemorySegment cfPtr = cf != null ? cf.ptr() : (MemorySegment) MH_GET_DEFAULT_CF.invokeExact(db.dbPtr());
			for (int i = 0; i < n; i++) {
				ByteBuffer key = keys.get(i);
				keysArr.setAtIndex(ValueLayout.ADDRESS, i, MemorySegment.ofBuffer(key));
				keySizesArr.setAtIndex(ValueLayout.JAVA_LONG, i, key.remaining());
			}
			MH_BATCHED_MULTI_GET_CF.invokeExact(db.dbPtr(), readOptions.ptr(), cfPtr, (long) n,
					keysArr, keySizesArr, valuesArr, errsArr, false);
			return collectBuffers(n, values);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("ReadBatch.get failed", t);
		}
	}

	private void checkCapacity(int n) {
		if (n > capacity) {
			throw new IllegalArgumentException("keys.size() " + n + " exceeds capacity " + capacity);
		}
	}

	/// [#get(ReadOptions, List, Mapper)] with default [ReadOptions].
	///
	/// @param <R>  the type produced by `fn`
	/// @param keys keys to look up; `keys.size()` must be at most [#capacity()]
	/// @param fn   callback invoked once per found key with a zero-copy view of its value
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @throws NullPointerException if `fn` returns `null` for any found key
	/// @return one entry per key, in the same order, `null` where not found
	public <R> List<R> get(List<MemorySegment> keys, Mapper<R> fn) {
		return get(RocksDB.DEFAULT_READ_OPTIONS, keys, fn);
	}

	/// Reads `keys` in one batched native call, reusing this batch's preallocated arrays
	/// instead of allocating fresh ones — the only per-call allocation is the returned `List`
	/// itself. `null` at index `i` means `keys.get(i)` was not found; a genuine per-key error
	/// surfaces as a thrown [RocksDBException] only after every key has been processed and
	/// every native resource released.
	///
	/// @param <R>         the type produced by `fn`
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up; `keys.size()` must be at most [#capacity()]
	/// @param fn          callback invoked once per found key with a zero-copy view of its value
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @throws NullPointerException if `fn` returns `null` for any found key
	/// @return one entry per key, in the same order, `null` where not found
	public <R> List<R> get(ReadOptions readOptions, List<MemorySegment> keys, Mapper<R> fn) {
		int n = keys.size();
		checkCapacity(n);
		if (n == 0) {
			return List.of();
		}
		try {
			MemorySegment cfPtr = cf != null ? cf.ptr() : (MemorySegment) MH_GET_DEFAULT_CF.invokeExact(db.dbPtr());
			for (int i = 0; i < n; i++) {
				MemorySegment key = keys.get(i);
				keysArr.setAtIndex(ValueLayout.ADDRESS, i, key);
				keySizesArr.setAtIndex(ValueLayout.JAVA_LONG, i, key.byteSize());
			}
			MH_BATCHED_MULTI_GET_CF.invokeExact(db.dbPtr(), readOptions.ptr(), cfPtr, (long) n,
					keysArr, keySizesArr, valuesArr, errsArr, false);
			return collect(n, fn);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("ReadBatch.get failed", t);
		}
	}

	/// Walks the `values`/`errs` arrays the native call above just populated, mapping every
	/// found value through `fn` with no intermediate copy and releasing every `PinnableSlice`
	/// exactly once. If any key reported a genuine error, every value/error is still drained
	/// (so nothing leaks) and the first error is thrown once the walk completes.
	private <R> List<R> collect(int n, Mapper<R> fn) {
		RocksDBException firstError = null;
		List<R> result = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			MemorySegment errSlot = errsArr.asSlice((long) i * ValueLayout.ADDRESS.byteSize(), ValueLayout.ADDRESS);
			MemorySegment valuePtr = valuesArr.getAtIndex(ValueLayout.ADDRESS, i);
			try {
				RocksDB.checkError(errSlot);
				if (MemorySegment.NULL.equals(valuePtr)) {
					result.add(null);
				} else if (firstError == null) {
					try (PinnableSlice ps = PinnableSlice.wrap(valuePtr)) {
						result.add(ps.map(arena, fn, vallenOut));
					}
				} else {
					PinnableSlice.wrap(valuePtr).close();
				}
			} catch (RocksDBException e) {
				if (firstError == null) {
					firstError = e;
				}
			}
		}
		if (firstError != null) {
			throw firstError;
		}
		return result;
	}

	/// Same walk-and-drain contract as [#collect], but copies each found value to a `byte[]`
	/// instead of mapping it through a callback.
	private List<byte[]> collectBytes(int n) {
		RocksDBException firstError = null;
		List<byte[]> result = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			MemorySegment errSlot = errsArr.asSlice((long) i * ValueLayout.ADDRESS.byteSize(), ValueLayout.ADDRESS);
			MemorySegment valuePtr = valuesArr.getAtIndex(ValueLayout.ADDRESS, i);
			try {
				RocksDB.checkError(errSlot);
				if (MemorySegment.NULL.equals(valuePtr)) {
					result.add(null);
				} else if (firstError == null) {
					try (PinnableSlice ps = PinnableSlice.wrap(valuePtr)) {
						result.add(ps.toByteArray(vallenOut));
					}
				} else {
					PinnableSlice.wrap(valuePtr).close();
				}
			} catch (RocksDBException e) {
				if (firstError == null) {
					firstError = e;
				}
			}
		}
		if (firstError != null) {
			throw firstError;
		}
		return result;
	}

	/// Same walk-and-drain contract as [#collect], but copies each found value into the
	/// caller-supplied `values.get(i)` buffer instead of mapping it through a callback.
	private List<CopyResult> collectBuffers(int n, List<ByteBuffer> values) {
		RocksDBException firstError = null;
		List<CopyResult> result = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			MemorySegment errSlot = errsArr.asSlice((long) i * ValueLayout.ADDRESS.byteSize(), ValueLayout.ADDRESS);
			MemorySegment valuePtr = valuesArr.getAtIndex(ValueLayout.ADDRESS, i);
			try {
				RocksDB.checkError(errSlot);
				if (MemorySegment.NULL.equals(valuePtr)) {
					result.add(CopyResult.NotFound.INSTANCE);
				} else if (firstError == null) {
					try (PinnableSlice ps = PinnableSlice.wrap(valuePtr)) {
						result.add(ps.copyInto(values.get(i), vallenOut));
					}
				} else {
					PinnableSlice.wrap(valuePtr).close();
				}
			} catch (RocksDBException e) {
				if (firstError == null) {
					firstError = e;
				}
			}
		}
		if (firstError != null) {
			throw firstError;
		}
		return result;
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			arena.close();
		}
	}
}
