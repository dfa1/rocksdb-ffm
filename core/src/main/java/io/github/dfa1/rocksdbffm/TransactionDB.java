package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/// FFM wrapper for `rocksdb_transactiondb_t` — a RocksDB database with pessimistic
/// (locking) transaction support.
/// ```
/// try (TransactionDBOptions txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
///      Options opts = Options.newOptions().setCreateIfMissing(true);
///      TransactionDB db = TransactionDB.open(opts, txnDbOpts, path)) {
///     try (WriteOptions wo = WriteOptions.newWriteOptions();
///          Transaction txn = db.beginTransaction(wo)) {
///         txn.put("key".getBytes(), "value".getBytes());
///         txn.commit();
///     }
/// }
/// ```
public final class TransactionDB extends NativeObjectWithBaseDb implements MonitoringOperations {

	// -----------------------------------------------------------------------
	// Method handles
	// -----------------------------------------------------------------------

	/// `void rocksdb_transactiondb_close(rocksdb_transactiondb_t* txn_db);`
	private static final MethodHandle MH_CLOSE;
	/// `void rocksdb_transactiondb_close_base_db(rocksdb_t* base_db);`
	private static final MethodHandle MH_CLOSE_BASE_DB;
	/// `rocksdb_transaction_t* rocksdb_transaction_begin(rocksdb_transactiondb_t* txn_db, const rocksdb_writeoptions_t* write_options, const rocksdb_transaction_options_t* txn_options, rocksdb_transaction_t* old_txn);`
	private static final MethodHandle MH_BEGIN;
	/// `const rocksdb_snapshot_t* rocksdb_transactiondb_create_snapshot(rocksdb_transactiondb_t* txn_db);`
	private static final MethodHandle MH_CREATE_SNAPSHOT;
	/// `void rocksdb_transactiondb_flush(rocksdb_transactiondb_t* txn_db, const rocksdb_flushoptions_t* options, char** errptr);`
	private static final MethodHandle MH_FLUSH;
	/// `void rocksdb_transactiondb_flush_wal(rocksdb_transactiondb_t* txn_db, unsigned char sync, char** errptr);`
	private static final MethodHandle MH_FLUSH_WAL;
	/// `char* rocksdb_transactiondb_property_value(rocksdb_transactiondb_t* db, const char* propname);`
	private static final MethodHandle MH_PROPERTY_VALUE;
	/// `int rocksdb_transactiondb_property_int(rocksdb_transactiondb_t* db, const char* propname, uint64_t* out_val);`
	private static final MethodHandle MH_PROPERTY_INT;
	/// `rocksdb_column_family_handle_t* rocksdb_transactiondb_create_column_family(rocksdb_transactiondb_t* txn_db, const rocksdb_options_t* column_family_options, const char* column_family_name, char** errptr);`
	private static final MethodHandle MH_CREATE_CF;

	// Direct (non-transactional) operations on the TransactionDB
	/// `void rocksdb_transactiondb_put(rocksdb_transactiondb_t* txn_db, const rocksdb_writeoptions_t* options, const char* key, size_t klen, const char* val, size_t vlen, char** errptr);`
	private static final MethodHandle MH_PUT;
	/// `void rocksdb_transactiondb_delete(rocksdb_transactiondb_t* txn_db, const rocksdb_writeoptions_t* options, const char* key, size_t klen, char** errptr);`
	private static final MethodHandle MH_DELETE;
	/// `void rocksdb_transactiondb_merge(rocksdb_transactiondb_t* txn_db, const rocksdb_writeoptions_t* options, const char* key, size_t klen, const char* val, size_t vlen, char** errptr);`
	private static final MethodHandle MH_MERGE;
	/// `rocksdb_pinnableslice_t* rocksdb_transactiondb_get_pinned(rocksdb_transactiondb_t* txn_db, const rocksdb_readoptions_t* options, const char* key, size_t klen, char** errptr);`
	private static final MethodHandle MH_GET_PINNED;

	// Column-family variants
	/// `void rocksdb_transactiondb_put_cf(rocksdb_transactiondb_t* txn_db, const rocksdb_writeoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t keylen, const char* val, size_t vallen, char** errptr);`
	private static final MethodHandle MH_PUT_CF;
	/// `void rocksdb_transactiondb_delete_cf(rocksdb_transactiondb_t* txn_db, const rocksdb_writeoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t keylen, char** errptr);`
	private static final MethodHandle MH_DELETE_CF;
	/// `void rocksdb_transactiondb_merge_cf(rocksdb_transactiondb_t* txn_db, const rocksdb_writeoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t klen, const char* val, size_t vlen, char** errptr);`
	private static final MethodHandle MH_MERGE_CF;
	/// `rocksdb_pinnableslice_t* rocksdb_transactiondb_get_pinned_cf(rocksdb_transactiondb_t* txn_db, const rocksdb_readoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t keylen, char** errptr);`
	private static final MethodHandle MH_GET_PINNED_CF;
	/// `rocksdb_iterator_t* rocksdb_transactiondb_create_iterator_cf(rocksdb_transactiondb_t* txn_db, const rocksdb_readoptions_t* options, rocksdb_column_family_handle_t* column_family);`
	private static final MethodHandle MH_CREATE_ITERATOR_CF;
	/// `void rocksdb_transactiondb_flush_cf(rocksdb_transactiondb_t* txn_db, const rocksdb_flushoptions_t* options, rocksdb_column_family_handle_t* column_family, char** errptr);`
	private static final MethodHandle MH_FLUSH_CF;


	static {
		MH_CLOSE = NativeLibrary.lookup("rocksdb_transactiondb_close",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_CLOSE_BASE_DB = NativeLibrary.lookup("rocksdb_transactiondb_close_base_db",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_BEGIN = NativeLibrary.lookup("rocksdb_transaction_begin",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_PUT = NativeLibrary.lookup("rocksdb_transactiondb_put",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_DELETE = NativeLibrary.lookup("rocksdb_transactiondb_delete",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_MERGE = NativeLibrary.lookup("rocksdb_transactiondb_merge",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_GET_PINNED = NativeLibrary.lookup("rocksdb_transactiondb_get_pinned",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_PUT_CF = NativeLibrary.lookup("rocksdb_transactiondb_put_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_DELETE_CF = NativeLibrary.lookup("rocksdb_transactiondb_delete_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_MERGE_CF = NativeLibrary.lookup("rocksdb_transactiondb_merge_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_GET_PINNED_CF = NativeLibrary.lookup("rocksdb_transactiondb_get_pinned_cf",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_CREATE_ITERATOR_CF = NativeLibrary.lookup("rocksdb_transactiondb_create_iterator_cf",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_FLUSH_CF = NativeLibrary.lookup("rocksdb_transactiondb_flush_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_CREATE_SNAPSHOT = NativeLibrary.lookup("rocksdb_transactiondb_create_snapshot",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_CREATE_CF = NativeLibrary.lookup("rocksdb_transactiondb_create_column_family",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_FLUSH = NativeLibrary.lookup("rocksdb_transactiondb_flush",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_FLUSH_WAL = NativeLibrary.lookup("rocksdb_transactiondb_flush_wal",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_PROPERTY_VALUE = NativeLibrary.lookup("rocksdb_transactiondb_property_value",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_PROPERTY_INT = NativeLibrary.lookup("rocksdb_transactiondb_property_int",
				FunctionDescriptor.of(ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	// -----------------------------------------------------------------------
	// Instance state
	// -----------------------------------------------------------------------

	private final WriteOptions writeOpts; // default write options for direct ops
	private final ReadOptions readOpts;  // default read options for direct ops

	TransactionDB(MemorySegment ptr, MemorySegment baseDb) {
		super(ptr, baseDb);
		this.writeOpts = RocksDB.DEFAULT_WRITE_OPTIONS;
		this.readOpts = RocksDB.DEFAULT_READ_OPTIONS;
	}

	/// Returns the base `rocksdb_t*` pointer. Overridden only to widen visibility to `public`,
	/// satisfying [MonitoringOperations#dbPtr()] — the guard itself lives in
	/// [NativeObjectWithBaseDb#dbPtr()].
	///
	/// @return the base DB pointer
	/// @throws IllegalStateException if this transaction DB has been closed
	@Override
	public MemorySegment dbPtr() {
		return super.dbPtr();
	}

	// -----------------------------------------------------------------------
	// Flush
	// -----------------------------------------------------------------------

	/// Flushes all memtable data to SST files on disk.
	/// Blocks until the flush completes when [FlushOptions#isWait()] is `true`.
	///
	/// @param flushOptions options controlling flush behavior
	public void flush(FlushOptions flushOptions) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_FLUSH.invokeExact(ptr(), flushOptions.ptr(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("flush failed", t);
		}
	}

	/// Flushes the WAL (write-ahead log) to disk.
	///
	/// @param sync if `true`, performs an `fsync` after writing
	public void flushWal(boolean sync) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_FLUSH_WAL.invokeExact(ptr(), RocksDB.toByte(sync), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("flushWal failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Snapshot
	// -----------------------------------------------------------------------

	/// Creates a snapshot of the current TransactionDB state.
	/// The returned snapshot must be closed after use.
	///
	/// @return a new [Snapshot]; caller must close it
	public Snapshot getSnapshot() {
		try {
			MemorySegment snapPtr = (MemorySegment) MH_CREATE_SNAPSHOT.invokeExact(ptr());
			return new Snapshot(this, ptr(), snapPtr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getSnapshot failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Transaction API
	// -----------------------------------------------------------------------

	/// Begins a new transaction using the supplied write options and default
	/// transaction options.
	///
	/// @param writeOptions write options for the transaction
	/// @return a new [Transaction]; caller must commit or rollback and close it
	public Transaction beginTransaction(WriteOptions writeOptions) {
		try (TransactionOptions txnOpts = TransactionOptions.newTransactionOptions()) {
			return beginTransaction(writeOptions, txnOpts);
		}
	}

	/// Begins a new transaction using the supplied write options and transaction options.
	///
	/// @param writeOptions write options for the transaction
	/// @param txnOptions   transaction-specific options
	/// @return a new [Transaction]; caller must commit or rollback and close it
	public Transaction beginTransaction(WriteOptions writeOptions, TransactionOptions txnOptions) {
		try {
			MemorySegment txnPtr = (MemorySegment) MH_BEGIN.invokeExact(
					ptr(), writeOptions.ptr(), txnOptions.ptr(), MemorySegment.NULL);
			return new Transaction(txnPtr, txnOptions.getSetSnapshot());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("beginTransaction failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Direct (non-transactional) operations
	// -----------------------------------------------------------------------

	/// Direct put, bypassing any active transaction. Slow path: allocates native memory.
	///
	/// @param key   the key to store
	/// @param value the value to associate with the key
	public void put(byte[] key, byte[] value) {
		put(writeOpts, key, value);
	}

	/// [#put(byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to store
	/// @param value        the value to associate with the key
	public void put(WriteOptions writeOptions, byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment k = RocksDB.toNative(arena, key);
			MemorySegment v = RocksDB.toNative(arena, value);
			MH_PUT.invokeExact(ptr(), writeOptions.ptr(), k, (long) key.length, v, (long) value.length, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("put failed", t);
		}
	}

	/// Zero-copy put: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param key   direct [java.nio.ByteBuffer] containing the key
	/// @param value direct [java.nio.ByteBuffer] containing the value
	public void put(java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		put(writeOpts, key, value);
	}

	/// [#put(ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [java.nio.ByteBuffer] containing the key
	/// @param value        direct [java.nio.ByteBuffer] containing the value
	public void put(WriteOptions writeOptions, java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_PUT.invokeExact(ptr(), writeOptions.ptr(),
					MemorySegment.ofBuffer(key), (long) key.remaining(),
					MemorySegment.ofBuffer(value), (long) value.remaining(),
					err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("put failed", t);
		}
	}

	/// Zero-copy put: caller supplies pre-allocated native segments.
	///
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	public void put(MemorySegment key, MemorySegment value) {
		put(writeOpts, key, value);
	}

	/// [#put(MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the value
	public void put(WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_PUT.invokeExact(ptr(), writeOptions.ptr(), key, key.byteSize(), value, value.byteSize(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("put failed", t);
		}
	}

	/// Direct merge, bypassing any active transaction. Slow path: allocates native memory.
	///
	/// @param key   the key to merge into
	/// @param value the merge operand
	public void merge(byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, writeOpts, RocksDB.toNative(arena, key), RocksDB.toNative(arena, value));
		}
	}

	/// [#merge(byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to merge into
	/// @param value        the merge operand
	public void merge(WriteOptions writeOptions, byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, writeOptions, RocksDB.toNative(arena, key), RocksDB.toNative(arena, value));
		}
	}

	/// Zero-copy merge: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param key   direct [java.nio.ByteBuffer] containing the key
	/// @param value direct [java.nio.ByteBuffer] containing the merge operand
	public void merge(java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, writeOpts, MemorySegment.ofBuffer(key), MemorySegment.ofBuffer(value));
		}
	}

	/// [#merge(ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [java.nio.ByteBuffer] containing the key
	/// @param value        direct [java.nio.ByteBuffer] containing the merge operand
	public void merge(WriteOptions writeOptions, java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, writeOptions, MemorySegment.ofBuffer(key), MemorySegment.ofBuffer(value));
		}
	}

	/// Zero-copy merge: caller supplies pre-allocated native segments.
	///
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	public void merge(MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, writeOpts, key, value);
		}
	}

	/// [#merge(MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the merge operand
	public void merge(WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, writeOptions, key, value);
		}
	}

	/// Merge core using the caller's arena — every tier above builds its segments then delegates here.
	private void merge(Arena arena, WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		try {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_MERGE.invokeExact(ptr(), writeOptions.ptr(), key, key.byteSize(), value, value.byteSize(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("merge failed", t);
		}
	}

	/// Direct get with explicit ReadOptions (e.g. for snapshot-pinned reads), via PinnableSlice.
	/// Returns `null` if not found.
	///
	/// @param readOptions read options (e.g. snapshot)
	/// @param key         the key to look up
	/// @return value bytes, or `null` if not found
	public byte[] get(ReadOptions readOptions, byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin = (MemorySegment) MH_GET_PINNED.invokeExact(
					ptr(), readOptions.ptr(),
					RocksDB.toNative(arena, key), (long) key.length, err);
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return null;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.toByteArray(err);
			}
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Native call failed", t);
		}
	}

	/// Direct get, reading committed data only, via PinnableSlice. Returns `null` if not found.
	/// Slow path.
	///
	/// @param key the key to look up
	/// @return value bytes, or `null` if not found
	public byte[] get(byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin = (MemorySegment) MH_GET_PINNED.invokeExact(
					ptr(), readOpts.ptr(),
					RocksDB.toNative(arena, key), (long) key.length, err);
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return null;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.toByteArray(err);
			}
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("get failed", t);
		}
	}

	/// Single-copy get + direct output [java.nio.ByteBuffer], via PinnableSlice. Copies nothing
	/// into `value` when its remaining capacity is too small.
	///
	/// @param key   direct [java.nio.ByteBuffer] containing the key
	/// @param value direct [java.nio.ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	public CopyResult get(java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin = (MemorySegment) MH_GET_PINNED.invokeExact(
					ptr(), readOpts.ptr(),
					MemorySegment.ofBuffer(key), (long) key.remaining(), err);
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return CopyResult.NotFound.INSTANCE;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.copyInto(value, err);
			}
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("get failed", t);
		}
	}

	/// Single-copy get into a caller-supplied native segment, via PinnableSlice. Copies
	/// nothing into `value` when its capacity is too small.
	///
	/// @param key   native segment containing the key
	/// @param value native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	public CopyResult get(MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin = (MemorySegment) MH_GET_PINNED.invokeExact(
					ptr(), readOpts.ptr(), key, key.byteSize(), err);
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return CopyResult.NotFound.INSTANCE;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.copyInto(value, value.byteSize(), err);
			}
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("get failed", t);
		}
	}

	/// Scoped zero-copy get: reads `key` via `rocksdb_transactiondb_get_pinned` and passes
	/// a read-only view of the value directly to `fn`, with no intermediate copy.
	///
	/// The view passed to `fn` is bound to an arena that is closed the moment `fn`
	/// returns, so it must not be retained beyond the call — doing so throws
	/// `IllegalStateException` (used after this call returns) or `WrongThreadException`
	/// (used from another thread) rather than reading freed memory.
	///
	/// @param <R> the type produced by `fn`
	/// @param key native segment containing the key
	/// @param fn  callback invoked with a zero-copy view of the pinned value
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`, or `null` if `key` is absent
	public <R> R get(MemorySegment key, Mapper<R> fn) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin;
			try {
				pin = (MemorySegment) MH_GET_PINNED.invokeExact(ptr(), readOpts.ptr(), key, key.byteSize(), err);
			} catch (Throwable t) {
				throw RocksDB.wrapInvokeFailure("get_pinned failed", t);
			}
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return null;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.map(arena, fn, err);
			}
		}
	}

	// -----------------------------------------------------------------------
	// DB Properties
	// -----------------------------------------------------------------------
	//
	/// Returns the value of a DB property as a string, or [Optional#empty()] if not supported.
	///
	/// @param property the property to query
	/// @return the property value, or empty if not supported
	public Optional<String> getProperty(Property property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment propSeg = arena.allocateFrom(property.propertyName());
			MemorySegment result = (MemorySegment) MH_PROPERTY_VALUE.invokeExact(ptr(), propSeg);
			return RocksDB.toOptionalString(result);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getProperty failed", t);
		}
	}

	/// Returns the value of a numeric DB property, or [OptionalLong#empty()] if not supported.
	///
	/// @param property the property to query
	/// @return the numeric property value, or empty if not supported
	public OptionalLong getLongProperty(Property property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment propSeg = arena.allocateFrom(property.propertyName());
			MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG);
			int rc = (int) MH_PROPERTY_INT.invokeExact(ptr(), propSeg, out);
			if (rc != 0) {
				return OptionalLong.empty();
			}
			return OptionalLong.of(out.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getLongProperty failed", t);
		}
	}

	/// Direct delete, bypassing any active transaction. Slow path.
	///
	/// @param key the key to remove
	public void delete(byte[] key) {
		delete(writeOpts, key);
	}

	/// [#delete(byte\[\])] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to remove
	public void delete(WriteOptions writeOptions, byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment k = RocksDB.toNative(arena, key);
			MH_DELETE.invokeExact(ptr(), writeOptions.ptr(), k, (long) key.length, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("delete failed", t);
		}
	}

	/// Zero-copy for direct [java.nio.ByteBuffer]s.
	///
	/// @param key direct [java.nio.ByteBuffer] containing the key to remove
	public void delete(java.nio.ByteBuffer key) {
		delete(writeOpts, key);
	}

	/// [#delete(ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [java.nio.ByteBuffer] containing the key to remove
	public void delete(WriteOptions writeOptions, java.nio.ByteBuffer key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_DELETE.invokeExact(ptr(), writeOptions.ptr(),
					MemorySegment.ofBuffer(key), (long) key.remaining(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("delete failed", t);
		}
	}

	/// Zero-copy native-first path.
	///
	/// @param key native segment containing the key to remove
	public void delete(MemorySegment key) {
		delete(writeOpts, key);
	}

	/// [#delete(MemorySegment)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key to remove
	public void delete(WriteOptions writeOptions, MemorySegment key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_DELETE.invokeExact(ptr(), writeOptions.ptr(), key, key.byteSize(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("delete failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Column family management
	// -----------------------------------------------------------------------

	/// Creates a new column family described by `descriptor` and returns its handle.
	/// Uses `rocksdb_transactiondb_create_column_family` so the handle is registered
	/// with this `txn_db`'s own column-family lookup — creating it via the base `rocksdb_t*`
	/// instead would leave every `*_cf` transaction operation unable to find it.
	///
	/// @param descriptor name and options for the new column family
	/// @return handle to the newly created column family; caller must close it
	public ColumnFamilyHandle createColumnFamily(ColumnFamilyDescriptor descriptor) {
		List<Options> tempOptions = new ArrayList<>(1);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			Options cfOpts = descriptor.options();
			if (cfOpts == null) {
				cfOpts = Options.newOptions();
				tempOptions.add(cfOpts);
			}
			MemorySegment nameSeg = arena.allocateFrom(
					new String(descriptor.name(), StandardCharsets.UTF_8));
			MemorySegment handle = (MemorySegment) MH_CREATE_CF.invokeExact(
					ptr(), cfOpts.ptr(), nameSeg, err);
			RocksDB.checkError(err);
			return ColumnFamilyHandle.wrap(handle);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("createColumnFamily failed", t);
		} finally {
			for (Options o : tempOptions) {
				o.close();
			}
		}
	}

	/// Drops the column family identified by `handle`.
	///
	/// @param handle handle of the column family to drop
	public void dropColumnFamily(ColumnFamilyHandle handle) {
		RocksDB.dropCf(dbPtr(), handle);
	}

	// -----------------------------------------------------------------------
	// Put — column family overloads
	// -----------------------------------------------------------------------

	/// Stores `value` under `key` in `cf`, bypassing any active transaction. Slow path.
	///
	/// @param cf    target column family
	/// @param key   the key to store
	/// @param value the value to associate with the key
	public void put(ColumnFamilyHandle cf, byte[] key, byte[] value) {
		put(cf, writeOpts, key, value);
	}

	/// [#put(ColumnFamilyHandle, byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to store
	/// @param value        the value to associate with the key
	public void put(ColumnFamilyHandle cf, WriteOptions writeOptions, byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_PUT_CF.invokeExact(ptr(), writeOptions.ptr(), cf.ptr(),
					RocksDB.toNative(arena, key), (long) key.length,
					RocksDB.toNative(arena, value), (long) value.length, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("put failed", t);
		}
	}

	/// Zero-copy put into `cf` for direct [java.nio.ByteBuffer]s.
	///
	/// @param cf    target column family
	/// @param key   direct [java.nio.ByteBuffer] containing the key
	/// @param value direct [java.nio.ByteBuffer] containing the value
	public void put(ColumnFamilyHandle cf, java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		put(cf, writeOpts, key, value);
	}

	/// [#put(ColumnFamilyHandle, ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [java.nio.ByteBuffer] containing the key
	/// @param value        direct [java.nio.ByteBuffer] containing the value
	public void put(ColumnFamilyHandle cf, WriteOptions writeOptions, java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_PUT_CF.invokeExact(ptr(), writeOptions.ptr(), cf.ptr(),
					MemorySegment.ofBuffer(key), (long) key.remaining(),
					MemorySegment.ofBuffer(value), (long) value.remaining(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("put failed", t);
		}
	}

	/// Zero-copy put into `cf` for [MemorySegment]s.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	public void put(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		put(cf, writeOpts, key, value);
	}

	/// [#put(ColumnFamilyHandle, MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the value
	public void put(ColumnFamilyHandle cf, WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_PUT_CF.invokeExact(ptr(), writeOptions.ptr(), cf.ptr(),
					key, key.byteSize(), value, value.byteSize(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("put failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Merge — column family overloads
	// -----------------------------------------------------------------------

	/// Merges `value` into `key` in `cf`, bypassing any active transaction. Slow path.
	///
	/// @param cf    target column family
	/// @param key   the key to merge into
	/// @param value the merge operand
	public void merge(ColumnFamilyHandle cf, byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, cf, writeOpts, RocksDB.toNative(arena, key), RocksDB.toNative(arena, value));
		}
	}

	/// [#merge(ColumnFamilyHandle, byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to merge into
	/// @param value        the merge operand
	public void merge(ColumnFamilyHandle cf, WriteOptions writeOptions, byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, cf, writeOptions, RocksDB.toNative(arena, key), RocksDB.toNative(arena, value));
		}
	}

	/// Zero-copy merge into `cf` for direct [java.nio.ByteBuffer]s.
	///
	/// @param cf    target column family
	/// @param key   direct [java.nio.ByteBuffer] containing the key
	/// @param value direct [java.nio.ByteBuffer] containing the merge operand
	public void merge(ColumnFamilyHandle cf, java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, cf, writeOpts, MemorySegment.ofBuffer(key), MemorySegment.ofBuffer(value));
		}
	}

	/// [#merge(ColumnFamilyHandle, ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [java.nio.ByteBuffer] containing the key
	/// @param value        direct [java.nio.ByteBuffer] containing the merge operand
	public void merge(ColumnFamilyHandle cf, WriteOptions writeOptions, java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, cf, writeOptions, MemorySegment.ofBuffer(key), MemorySegment.ofBuffer(value));
		}
	}

	/// Zero-copy merge into `cf` for [MemorySegment]s.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	public void merge(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, cf, writeOpts, key, value);
		}
	}

	/// [#merge(ColumnFamilyHandle, MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the merge operand
	public void merge(ColumnFamilyHandle cf, WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			merge(arena, cf, writeOptions, key, value);
		}
	}

	/// Merge-into-cf core using the caller's arena — every tier above delegates here.
	private void merge(Arena arena, ColumnFamilyHandle cf, WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		try {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_MERGE_CF.invokeExact(ptr(), writeOptions.ptr(), cf.ptr(),
					key, key.byteSize(), value, value.byteSize(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("merge failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Get — column family overloads
	// -----------------------------------------------------------------------

	/// Get from `cf` via PinnableSlice. Returns `null` if not found.
	///
	/// @param cf  target column family
	/// @param key the key to look up
	/// @return value bytes, or `null` if not found
	public byte[] get(ColumnFamilyHandle cf, byte[] key) {
		return get(cf, readOpts, key);
	}

	/// Get from `cf` with explicit [ReadOptions]. Returns `null` if not found.
	///
	/// @param cf          target column family
	/// @param readOptions read options (e.g. snapshot)
	/// @param key         the key to look up
	/// @return value bytes, or `null` if not found
	public byte[] get(ColumnFamilyHandle cf, ReadOptions readOptions, byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin = (MemorySegment) MH_GET_PINNED_CF.invokeExact(
					ptr(), readOptions.ptr(), cf.ptr(),
					RocksDB.toNative(arena, key), (long) key.length, err);
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return null;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.toByteArray(err);
			}
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("get failed", t);
		}
	}

	/// Single-copy get from `cf` into a direct [java.nio.ByteBuffer], via PinnableSlice.
	/// Copies nothing into `value` when its remaining capacity is too small.
	///
	/// @param cf    target column family
	/// @param key   direct [java.nio.ByteBuffer] containing the key
	/// @param value direct [java.nio.ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	public CopyResult get(ColumnFamilyHandle cf, java.nio.ByteBuffer key, java.nio.ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin = (MemorySegment) MH_GET_PINNED_CF.invokeExact(
					ptr(), readOpts.ptr(), cf.ptr(),
					MemorySegment.ofBuffer(key), (long) key.remaining(), err);
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return CopyResult.NotFound.INSTANCE;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.copyInto(value, err);
			}
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("get failed", t);
		}
	}

	/// Single-copy get from `cf` into a caller-supplied native segment, via PinnableSlice.
	/// Copies nothing into `value` when its capacity is too small.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	public CopyResult get(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin = (MemorySegment) MH_GET_PINNED_CF.invokeExact(
					ptr(), readOpts.ptr(), cf.ptr(), key, key.byteSize(), err);
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return CopyResult.NotFound.INSTANCE;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.copyInto(value, value.byteSize(), err);
			}
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("get failed", t);
		}
	}

	/// Scoped zero-copy get from `cf`. See [#get(MemorySegment, Mapper)] for
	/// the lifetime contract on the view passed to `fn`.
	///
	/// @param <R> the type produced by `fn`
	/// @param cf  target column family
	/// @param key native segment containing the key
	/// @param fn  callback invoked with a zero-copy view of the pinned value
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`, or `null` if `key` is absent
	public <R> R get(ColumnFamilyHandle cf, MemorySegment key, Mapper<R> fn) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pin;
			try {
				pin = (MemorySegment) MH_GET_PINNED_CF.invokeExact(ptr(), readOpts.ptr(), cf.ptr(), key, key.byteSize(), err);
			} catch (Throwable t) {
				throw RocksDB.wrapInvokeFailure("get_pinned failed", t);
			}
			RocksDB.checkError(err);
			if (MemorySegment.NULL.equals(pin)) {
				return null;
			}
			try (PinnableSlice slice = PinnableSlice.wrap(pin)) {
				return slice.map(arena, fn, err);
			}
		}
	}

	// -----------------------------------------------------------------------
	// Delete — column family overloads
	// -----------------------------------------------------------------------

	/// Removes `key` from `cf`, bypassing any active transaction. Slow path.
	///
	/// @param cf  target column family
	/// @param key the key to remove
	public void delete(ColumnFamilyHandle cf, byte[] key) {
		delete(cf, writeOpts, key);
	}

	/// [#delete(ColumnFamilyHandle, byte\[\])] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to remove
	public void delete(ColumnFamilyHandle cf, WriteOptions writeOptions, byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_DELETE_CF.invokeExact(ptr(), writeOptions.ptr(), cf.ptr(),
					RocksDB.toNative(arena, key), (long) key.length, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("delete failed", t);
		}
	}

	/// Zero-copy delete from `cf` for direct [java.nio.ByteBuffer]s.
	///
	/// @param cf  target column family
	/// @param key direct [java.nio.ByteBuffer] containing the key to remove
	public void delete(ColumnFamilyHandle cf, java.nio.ByteBuffer key) {
		delete(cf, writeOpts, key);
	}

	/// [#delete(ColumnFamilyHandle, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [java.nio.ByteBuffer] containing the key to remove
	public void delete(ColumnFamilyHandle cf, WriteOptions writeOptions, java.nio.ByteBuffer key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_DELETE_CF.invokeExact(ptr(), writeOptions.ptr(), cf.ptr(),
					MemorySegment.ofBuffer(key), (long) key.remaining(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("delete failed", t);
		}
	}

	/// Zero-copy delete from `cf` for [MemorySegment]s.
	///
	/// @param cf  target column family
	/// @param key native segment containing the key to remove
	public void delete(ColumnFamilyHandle cf, MemorySegment key) {
		delete(cf, writeOpts, key);
	}

	/// [#delete(ColumnFamilyHandle, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key to remove
	public void delete(ColumnFamilyHandle cf, WriteOptions writeOptions, MemorySegment key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_DELETE_CF.invokeExact(ptr(), writeOptions.ptr(), cf.ptr(), key, key.byteSize(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("delete failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Iterator — column family overloads
	// -----------------------------------------------------------------------

	/// Returns a new iterator scoped to `cf` using the database's default read options.
	///
	/// @param cf target column family
	/// @return a new [RocksIterator]; caller must close it
	public RocksIterator newIterator(ColumnFamilyHandle cf) {
		try {
			MemorySegment iterPtr = (MemorySegment) MH_CREATE_ITERATOR_CF.invokeExact(
					ptr(), readOpts.ptr(), cf.ptr());
			return RocksIterator.create(iterPtr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("newIterator failed", t);
		}
	}

	/// Returns a new iterator scoped to `cf` with explicit [ReadOptions].
	///
	/// @param cf          target column family
	/// @param readOptions read options (e.g. snapshot)
	/// @return a new [RocksIterator]; caller must close it
	public RocksIterator newIterator(ColumnFamilyHandle cf, ReadOptions readOptions) {
		try {
			MemorySegment iterPtr = (MemorySegment) MH_CREATE_ITERATOR_CF.invokeExact(
					ptr(), readOptions.ptr(), cf.ptr());
			return RocksIterator.create(iterPtr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("newIterator failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Flush — column family overloads
	// -----------------------------------------------------------------------

	/// Flushes the memtable for `cf` to SST files.
	///
	/// @param cf           target column family
	/// @param flushOptions options controlling flush behavior
	public void flush(ColumnFamilyHandle cf, FlushOptions flushOptions) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_FLUSH_CF.invokeExact(ptr(), flushOptions.ptr(), cf.ptr(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("flush failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// DB Properties — column family overloads
	// -----------------------------------------------------------------------

	/// Returns the value of a property for `cf`, or [Optional#empty()] if not supported.
	///
	/// @param cf       target column family
	/// @param property the property to query
	/// @return the property value, or empty if not supported
	public Optional<String> getProperty(ColumnFamilyHandle cf, Property property) {
		return RocksDB.getPropertyCf(dbPtr(), cf, property);
	}

	/// Returns the value of a numeric property for `cf`, or [OptionalLong#empty()] if not supported.
	///
	/// @param cf       target column family
	/// @param property the property to query
	/// @return the numeric property value, or empty if not supported
	public OptionalLong getLongProperty(ColumnFamilyHandle cf, Property property) {
		return RocksDB.getLongPropertyCf(dbPtr(), cf, property);
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryCloseBaseDb(MemorySegment baseDb) throws Throwable {
		MH_CLOSE_BASE_DB.invokeExact(baseDb);
	}

	@Override
	protected void tryClosePrimary(MemorySegment ptr) throws Throwable {
		MH_CLOSE.invokeExact(ptr);
	}

}
