package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.List;

/// Reusable, preallocated batch for repeated multi-get calls against the same [Transaction]
/// (and, optionally, the same column family), all three tiers, in two modes: plain [#get] reads
/// committed-or-in-this-transaction data, [#getForUpdate] additionally takes a lock on each key
/// like [Transaction#get(MemorySegment, Mapper)]'s `_for_update` single-key counterpart. Same
/// reuse shape and malloc'd-buffer value representation as [TransactionDBReadBatch] — see
/// [RawMultiGet] for the shared collection logic.
///
/// ```
/// try (var batch = TransactionReadBatch.create(txn, 16)) {
///     List<byte[]> values = batch.getForUpdate(keys);
/// }
/// ```
public final class TransactionReadBatch implements AutoCloseable {

	/// `void rocksdb_transaction_multi_get(rocksdb_transaction_t* txn, const rocksdb_readoptions_t* options, size_t num_keys, const char* const* keys_list, const size_t* keys_list_sizes, char** values_list, size_t* values_list_sizes, char** errs);`
	private static final MethodHandle MH_MULTI_GET;
	/// `void rocksdb_transaction_multi_get_for_update(rocksdb_transaction_t* txn, const rocksdb_readoptions_t* options, size_t num_keys, const char* const* keys_list, const size_t* keys_list_sizes, char** values_list, size_t* values_list_sizes, char** errs);`
	private static final MethodHandle MH_MULTI_GET_FOR_UPDATE;
	/// `void rocksdb_transaction_multi_get_cf(rocksdb_transaction_t* txn, const rocksdb_readoptions_t* options, const rocksdb_column_family_handle_t* const* column_families, size_t num_keys, const char* const* keys_list, const size_t* keys_list_sizes, char** values_list, size_t* values_list_sizes, char** errs);`
	private static final MethodHandle MH_MULTI_GET_CF;
	/// `void rocksdb_transaction_multi_get_for_update_cf(rocksdb_transaction_t* txn, const rocksdb_readoptions_t* options, const rocksdb_column_family_handle_t* const* column_families, size_t num_keys, const char* const* keys_list, const size_t* keys_list_sizes, char** values_list, size_t* values_list_sizes, char** errs);`
	private static final MethodHandle MH_MULTI_GET_FOR_UPDATE_CF;

	static {
		MH_MULTI_GET = NativeLibrary.lookup("rocksdb_transaction_multi_get",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_MULTI_GET_FOR_UPDATE = NativeLibrary.lookup("rocksdb_transaction_multi_get_for_update",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_MULTI_GET_CF = NativeLibrary.lookup("rocksdb_transaction_multi_get_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_MULTI_GET_FOR_UPDATE_CF = NativeLibrary.lookup("rocksdb_transaction_multi_get_for_update_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private final Arena arena;
	private final Transaction txn;
	private final ColumnFamilyHandle cf;
	private final int capacity;
	private final MemorySegment keysArr;
	private final MemorySegment keySizesArr;
	private final MemorySegment valuesListArr;
	private final MemorySegment valuesListSizesArr;
	private final MemorySegment errsArr;
	private final MemorySegment cfArr;
	private boolean closed;

	private TransactionReadBatch(Transaction txn, ColumnFamilyHandle cf, int capacity) {
		this.arena = Arena.ofConfined();
		this.txn = txn;
		this.cf = cf;
		this.capacity = capacity;
		this.keysArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.keySizesArr = arena.allocate(ValueLayout.JAVA_LONG, capacity);
		this.valuesListArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.valuesListSizesArr = arena.allocate(ValueLayout.JAVA_LONG, capacity);
		this.errsArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.cfArr = cf != null ? arena.allocate(ValueLayout.ADDRESS, capacity) : null;
	}

	/// Creates a batch reading from `txn`'s default column family, with room for up to `capacity`
	/// keys per [#get]/[#getForUpdate] call.
	///
	/// @param txn      transaction to read from
	/// @param capacity maximum number of keys any single call may pass; must be positive
	/// @return a new [TransactionReadBatch]; caller must close it
	public static TransactionReadBatch create(Transaction txn, int capacity) {
		return create(txn, null, capacity);
	}

	/// Creates a batch reading from `cf`, with room for up to `capacity` keys per
	/// [#get]/[#getForUpdate] call.
	///
	/// @param txn      transaction to read from
	/// @param cf       column family every key in every call belongs to
	/// @param capacity maximum number of keys any single call may pass; must be positive
	/// @return a new [TransactionReadBatch]; caller must close it
	public static TransactionReadBatch create(Transaction txn, ColumnFamilyHandle cf, int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive: " + capacity);
		}
		return new TransactionReadBatch(txn, cf, capacity);
	}

	/// The maximum number of keys a single call on this batch may pass.
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

	/// Reads `keys` without taking a lock on them, reusing this batch's preallocated bookkeeping
	/// arrays. `null` at index `i` means `keys.get(i)` was not found.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up; `keys.size()` must be at most [#capacity()]
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @return one entry per key, in the same order, `null` where not found
	public List<byte[]> get(ReadOptions readOptions, List<byte[]> keys) {
		return getBytes(readOptions, keys, false);
	}

	/// [#getForUpdate(ReadOptions, List)] with default [ReadOptions].
	///
	/// @param keys keys to look up and lock; `keys.size()` must be at most [#capacity()]
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @return one entry per key, in the same order, `null` where not found
	public List<byte[]> getForUpdate(List<byte[]> keys) {
		return getForUpdate(RocksDB.DEFAULT_READ_OPTIONS, keys);
	}

	/// Reads `keys` and takes a lock on each one (as `rocksdb_transaction_get_pinned_for_update`
	/// does for a single key), reusing this batch's preallocated bookkeeping arrays. `null` at
	/// index `i` means `keys.get(i)` was not found.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up and lock; `keys.size()` must be at most [#capacity()]
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @return one entry per key, in the same order, `null` where not found
	public List<byte[]> getForUpdate(ReadOptions readOptions, List<byte[]> keys) {
		return getBytes(readOptions, keys, true);
	}

	private List<byte[]> getBytes(ReadOptions readOptions, List<byte[]> keys, boolean forUpdate) {
		int n = keys.size();
		RawMultiGet.checkCapacity(n, capacity);
		if (n == 0) {
			return List.of();
		}
		try (Arena callArena = Arena.ofConfined()) {
			for (int i = 0; i < n; i++) {
				byte[] key = keys.get(i);
				RawMultiGet.writeKeySlot(keysArr, keySizesArr, i, RocksDB.toNative(callArena, key), key.length);
			}
			RocksDB.requireNoNullEntries(keysArr, n, "TransactionReadBatch keys array");
			invoke(readOptions, n, forUpdate);
			return RawMultiGet.collectBytes(valuesListArr, valuesListSizesArr, errsArr, n);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("TransactionReadBatch.get failed", t);
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

	/// Reads `keys` into the corresponding pre-sized buffer in `values` without taking a lock on
	/// them (same index, same order), advancing each buffer's position on a successful copy.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up; `keys.size()` must be at most [#capacity()]
	/// @param values      one destination buffer per key, in the same order
	/// @throws IllegalArgumentException if `keys` and `values` differ in size, or if
	///                                  `keys.size()` exceeds [#capacity()]
	/// @return one [CopyResult] per key, in the same order
	public List<CopyResult> get(ReadOptions readOptions, List<ByteBuffer> keys, List<ByteBuffer> values) {
		return getBuffers(readOptions, keys, values, false);
	}

	/// [#getForUpdate(ReadOptions, List, List)] with default [ReadOptions].
	///
	/// @param keys   keys to look up and lock; `keys.size()` must be at most [#capacity()]
	/// @param values one destination buffer per key, in the same order
	/// @throws IllegalArgumentException if `keys` and `values` differ in size, or if
	///                                  `keys.size()` exceeds [#capacity()]
	/// @return one [CopyResult] per key, in the same order
	public List<CopyResult> getForUpdate(List<ByteBuffer> keys, List<ByteBuffer> values) {
		return getForUpdate(RocksDB.DEFAULT_READ_OPTIONS, keys, values);
	}

	/// Reads `keys` into the corresponding pre-sized buffer in `values` and takes a lock on each
	/// key (same index, same order), advancing each buffer's position on a successful copy.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up and lock; `keys.size()` must be at most [#capacity()]
	/// @param values      one destination buffer per key, in the same order
	/// @throws IllegalArgumentException if `keys` and `values` differ in size, or if
	///                                  `keys.size()` exceeds [#capacity()]
	/// @return one [CopyResult] per key, in the same order
	public List<CopyResult> getForUpdate(ReadOptions readOptions, List<ByteBuffer> keys, List<ByteBuffer> values) {
		return getBuffers(readOptions, keys, values, true);
	}

	private List<CopyResult> getBuffers(ReadOptions readOptions, List<ByteBuffer> keys, List<ByteBuffer> values, boolean forUpdate) {
		int n = keys.size();
		if (values.size() != n) {
			throw new IllegalArgumentException("keys and values must be the same size: " + n + " vs " + values.size());
		}
		RawMultiGet.checkCapacity(n, capacity);
		if (n == 0) {
			return List.of();
		}
		try {
			for (int i = 0; i < n; i++) {
				ByteBuffer key = keys.get(i);
				RawMultiGet.writeKeySlot(keysArr, keySizesArr, i, MemorySegment.ofBuffer(key), key.remaining());
			}
			RocksDB.requireNoNullEntries(keysArr, n, "TransactionReadBatch keys array");
			invoke(readOptions, n, forUpdate);
			return RawMultiGet.collectBuffers(valuesListArr, valuesListSizesArr, errsArr, n, values);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("TransactionReadBatch.get failed", t);
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

	/// Reads `keys` without taking a lock on them, reusing this batch's preallocated arrays
	/// instead of allocating fresh ones — the only per-call allocation is the returned `List`
	/// itself.
	///
	/// @param <R>         the type produced by `fn`
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up; `keys.size()` must be at most [#capacity()]
	/// @param fn          callback invoked once per found key with a zero-copy view of its value
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @throws NullPointerException if `fn` returns `null` for any found key
	/// @return one entry per key, in the same order, `null` where not found
	public <R> List<R> get(ReadOptions readOptions, List<MemorySegment> keys, Mapper<R> fn) {
		return getMapped(readOptions, keys, fn, false);
	}

	/// [#getForUpdate(ReadOptions, List, Mapper)] with default [ReadOptions].
	///
	/// @param <R>  the type produced by `fn`
	/// @param keys keys to look up and lock; `keys.size()` must be at most [#capacity()]
	/// @param fn   callback invoked once per found key with a zero-copy view of its value
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @throws NullPointerException if `fn` returns `null` for any found key
	/// @return one entry per key, in the same order, `null` where not found
	public <R> List<R> getForUpdate(List<MemorySegment> keys, Mapper<R> fn) {
		return getForUpdate(RocksDB.DEFAULT_READ_OPTIONS, keys, fn);
	}

	/// Reads `keys` and takes a lock on each one, reusing this batch's preallocated arrays
	/// instead of allocating fresh ones — the only per-call allocation is the returned `List`
	/// itself.
	///
	/// @param <R>         the type produced by `fn`
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up and lock; `keys.size()` must be at most [#capacity()]
	/// @param fn          callback invoked once per found key with a zero-copy view of its value
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @throws NullPointerException if `fn` returns `null` for any found key
	/// @return one entry per key, in the same order, `null` where not found
	public <R> List<R> getForUpdate(ReadOptions readOptions, List<MemorySegment> keys, Mapper<R> fn) {
		return getMapped(readOptions, keys, fn, true);
	}

	private <R> List<R> getMapped(ReadOptions readOptions, List<MemorySegment> keys, Mapper<R> fn, boolean forUpdate) {
		int n = keys.size();
		RawMultiGet.checkCapacity(n, capacity);
		if (n == 0) {
			return List.of();
		}
		try {
			for (int i = 0; i < n; i++) {
				MemorySegment key = keys.get(i);
				RawMultiGet.writeKeySlot(keysArr, keySizesArr, i, key, key.byteSize());
			}
			RocksDB.requireNoNullEntries(keysArr, n, "TransactionReadBatch keys array");
			invoke(readOptions, n, forUpdate);
			return RawMultiGet.collect(valuesListArr, valuesListSizesArr, errsArr, n, fn);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("TransactionReadBatch.get failed", t);
		}
	}

	/// Invokes the plain or `_cf` native call, in either mode, depending on whether this batch
	/// was created with a column family — filling the per-key column-family array the `_cf`
	/// variants require with `cf`'s handle repeated `n` times, same as
	/// [TransactionDBReadBatch#invoke(ReadOptions, int)]. Each of the four native symbols gets
	/// its own direct `invokeExact` call site on its own `static final` field rather than a
	/// shared local variable, so the JIT can still treat every call target as a compile-time
	/// constant.
	private void invoke(ReadOptions readOptions, int n, boolean forUpdate) throws Throwable {
		if (cf == null) {
			if (forUpdate) {
				MH_MULTI_GET_FOR_UPDATE.invokeExact(txn.ptr(), readOptions.ptr(), (long) n,
						keysArr, keySizesArr, valuesListArr, valuesListSizesArr, errsArr);
			} else {
				MH_MULTI_GET.invokeExact(txn.ptr(), readOptions.ptr(), (long) n,
						keysArr, keySizesArr, valuesListArr, valuesListSizesArr, errsArr);
			}
		} else {
			MemorySegment cfPtr = cf.ptr();
			for (int i = 0; i < n; i++) {
				cfArr.setAtIndex(ValueLayout.ADDRESS, i, cfPtr);
			}
			if (forUpdate) {
				MH_MULTI_GET_FOR_UPDATE_CF.invokeExact(txn.ptr(), readOptions.ptr(), cfArr, (long) n,
						keysArr, keySizesArr, valuesListArr, valuesListSizesArr, errsArr);
			} else {
				MH_MULTI_GET_CF.invokeExact(txn.ptr(), readOptions.ptr(), cfArr, (long) n,
						keysArr, keySizesArr, valuesListArr, valuesListSizesArr, errsArr);
			}
		}
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			arena.close();
		}
	}
}
