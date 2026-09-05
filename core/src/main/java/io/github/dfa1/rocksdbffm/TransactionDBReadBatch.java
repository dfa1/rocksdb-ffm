package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.List;

/// Reusable, preallocated batch for repeated multi-get calls against the same [TransactionDB]
/// (and, optionally, the same column family), all three tiers. Same reuse shape as [ReadBatch],
/// but for a genuinely different native call: `rocksdb_transactiondb_multi_get[_cf]` is a
/// separate native symbol from the plain `rocksdb_t*` `rocksdb_batched_multi_get_cf` [ReadBatch]
/// wraps, and returns values as malloc'd `char*` + `size_t` pairs (freed via
/// [RocksDB#free(MemorySegment)]) rather than `rocksdb_pinnableslice_t*` handles — see
/// [RawMultiGet] for the shared collection logic that difference requires.
///
/// ```
/// try (var batch = TransactionDBReadBatch.create(txnDb, 16)) {
///     List<byte[]> values = batch.get(keys);
/// }
/// ```
public final class TransactionDBReadBatch implements AutoCloseable {

	/// `void rocksdb_transactiondb_multi_get(rocksdb_transactiondb_t* txn_db, const rocksdb_readoptions_t* options, size_t num_keys, const char* const* keys_list, const size_t* keys_list_sizes, char** values_list, size_t* values_list_sizes, char** errs);`
	private static final MethodHandle MH_MULTI_GET;
	/// `void rocksdb_transactiondb_multi_get_cf(rocksdb_transactiondb_t* txn_db, const rocksdb_readoptions_t* options, const rocksdb_column_family_handle_t* const* column_families, size_t num_keys, const char* const* keys_list, const size_t* keys_list_sizes, char** values_list, size_t* values_list_sizes, char** errs);`
	private static final MethodHandle MH_MULTI_GET_CF;

	static {
		MH_MULTI_GET = NativeLibrary.lookup("rocksdb_transactiondb_multi_get",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_MULTI_GET_CF = NativeLibrary.lookup("rocksdb_transactiondb_multi_get_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private final Arena arena;
	private final TransactionDB db;
	private final ColumnFamilyHandle cf;
	private final int capacity;
	private final MemorySegment keysArr;
	private final MemorySegment keySizesArr;
	private final MemorySegment valuesListArr;
	private final MemorySegment valuesListSizesArr;
	private final MemorySegment errsArr;
	private final MemorySegment cfArr;
	private boolean closed;

	private TransactionDBReadBatch(TransactionDB db, ColumnFamilyHandle cf, int capacity) {
		this.arena = Arena.ofConfined();
		this.db = db;
		this.cf = cf;
		this.capacity = capacity;
		this.keysArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.keySizesArr = arena.allocate(ValueLayout.JAVA_LONG, capacity);
		this.valuesListArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.valuesListSizesArr = arena.allocate(ValueLayout.JAVA_LONG, capacity);
		this.errsArr = arena.allocate(ValueLayout.ADDRESS, capacity);
		this.cfArr = cf != null ? arena.allocate(ValueLayout.ADDRESS, capacity) : null;
	}

	/// Creates a batch reading from `db`'s default column family, with room for up to `capacity`
	/// keys per [#get] call.
	///
	/// @param db       transaction database to read from
	/// @param capacity maximum number of keys any single [#get] call may pass; must be positive
	/// @return a new [TransactionDBReadBatch]; caller must close it
	public static TransactionDBReadBatch create(TransactionDB db, int capacity) {
		return create(db, null, capacity);
	}

	/// Creates a batch reading from `cf`, with room for up to `capacity` keys per [#get] call.
	///
	/// @param db       transaction database to read from
	/// @param cf       column family every key in every [#get] call belongs to
	/// @param capacity maximum number of keys any single [#get] call may pass; must be positive
	/// @return a new [TransactionDBReadBatch]; caller must close it
	public static TransactionDBReadBatch create(TransactionDB db, ColumnFamilyHandle cf, int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive: " + capacity);
		}
		return new TransactionDBReadBatch(db, cf, capacity);
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
	/// arrays. `null` at index `i` means `keys.get(i)` was not found; a genuine per-key error
	/// surfaces as a thrown [RocksDBException] only after every key has been processed and every
	/// native resource released.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param keys        keys to look up; `keys.size()` must be at most [#capacity()]
	/// @throws IllegalArgumentException if `keys.size()` exceeds [#capacity()]
	/// @return one entry per key, in the same order, `null` where not found
	public List<byte[]> get(ReadOptions readOptions, List<byte[]> keys) {
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
			RocksDB.requireNoNullEntries(keysArr, n, "TransactionDBReadBatch keys array");
			invoke(readOptions, n);
			return RawMultiGet.collectBytes(valuesListArr, valuesListSizesArr, errsArr, n);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("TransactionDBReadBatch.get failed", t);
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

	/// Reads `keys` into the corresponding pre-sized buffer in `values` (same index, same order),
	/// advancing each buffer's position on a successful copy. `keys` and `values` must be the
	/// same size.
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
		RawMultiGet.checkCapacity(n, capacity);
		if (n == 0) {
			return List.of();
		}
		try {
			for (int i = 0; i < n; i++) {
				ByteBuffer key = keys.get(i);
				RawMultiGet.writeKeySlot(keysArr, keySizesArr, i, MemorySegment.ofBuffer(key), key.remaining());
			}
			RocksDB.requireNoNullEntries(keysArr, n, "TransactionDBReadBatch keys array");
			invoke(readOptions, n);
			return RawMultiGet.collectBuffers(valuesListArr, valuesListSizesArr, errsArr, n, values);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("TransactionDBReadBatch.get failed", t);
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

	/// Reads `keys` in one batched native call, reusing this batch's preallocated arrays instead
	/// of allocating fresh ones — the only per-call allocation is the returned `List` itself.
	/// `null` at index `i` means `keys.get(i)` was not found.
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
		RawMultiGet.checkCapacity(n, capacity);
		if (n == 0) {
			return List.of();
		}
		try {
			for (int i = 0; i < n; i++) {
				MemorySegment key = keys.get(i);
				RawMultiGet.writeKeySlot(keysArr, keySizesArr, i, key, key.byteSize());
			}
			RocksDB.requireNoNullEntries(keysArr, n, "TransactionDBReadBatch keys array");
			invoke(readOptions, n);
			return RawMultiGet.collect(valuesListArr, valuesListSizesArr, errsArr, n, fn);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("TransactionDBReadBatch.get failed", t);
		}
	}

	/// Invokes the plain or `_cf` native call depending on whether this batch was created with a
	/// column family, filling the per-key column-family array the `_cf` variant requires with
	/// `cf`'s handle repeated `n` times — `rocksdb_transactiondb_multi_get_cf` takes one column
	/// family per key, unlike `rocksdb_batched_multi_get_cf`'s single shared handle, but this
	/// batch's UX still fixes one column family at [#create] time.
	private void invoke(ReadOptions readOptions, int n) throws Throwable {
		if (cf == null) {
			MH_MULTI_GET.invokeExact(db.ptr(), readOptions.ptr(), (long) n,
					keysArr, keySizesArr, valuesListArr, valuesListSizesArr, errsArr);
		} else {
			MemorySegment cfPtr = cf.ptr();
			for (int i = 0; i < n; i++) {
				cfArr.setAtIndex(ValueLayout.ADDRESS, i, cfPtr);
			}
			MH_MULTI_GET_CF.invokeExact(db.ptr(), readOptions.ptr(), cfArr, (long) n,
					keysArr, keySizesArr, valuesListArr, valuesListSizesArr, errsArr);
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
