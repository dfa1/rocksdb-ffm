package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_optimistictransactiondb_t` — a RocksDB database with
/// optimistic (lock-free) transaction support.
///
/// Optimistic transactions do _not_ acquire locks on read. Instead, conflicts
/// are detected at [Transaction#commit()] time. If another writer has modified a
/// key that this transaction read or wrote since the transaction began,
/// [Transaction#commit()] throws [RocksDBException] (status "busy").
/// The caller should then abort and retry.
///
/// Direct (non-transactional) put/get/delete/merge/iterate/flush/property/CF-management
/// methods come from [RocksDBReadOperations]/[RocksDBWriteOperations] — this class has no
/// `MethodHandle`s of its own for them, unlike [TransactionDB], because there is no
/// dedicated `rocksdb_optimistictransactiondb_*` C API for direct ops: they always go
/// through the base `rocksdb_t*` ([#dbPtr()]), the same native calls [ReadWriteDB]/[TtlDB]/
/// [BlobDB] already share via those interfaces.
///
/// ```
/// try (Options opts = Options.newOptions().setCreateIfMissing(true);
///      OptimisticTransactionDB db = RocksDB.openOptimistic(opts, path)) {
///     try (WriteOptions wo = WriteOptions.newWriteOptions();
///          Transaction txn = db.beginTransaction(wo)) {
///         txn.put("key".getBytes(), "value".getBytes());
///         txn.commit(); // throws RocksDBException if conflict detected
///     }
/// }
/// ```
public final class OptimisticTransactionDB extends NativeObjectWithBaseDb
		implements RocksDBReadOperations, RocksDBWriteOperations {

	// -----------------------------------------------------------------------
	// Method handles unique to OptimisticTransactionDB
	// -----------------------------------------------------------------------

	/// `void rocksdb_optimistictransactiondb_close(rocksdb_optimistictransactiondb_t* otxn_db);`
	private static final MethodHandle MH_CLOSE;
	/// `rocksdb_transaction_t* rocksdb_optimistictransaction_begin(rocksdb_optimistictransactiondb_t* otxn_db, const rocksdb_writeoptions_t* write_options, const rocksdb_optimistictransaction_options_t* otxn_options, rocksdb_transaction_t* old_txn);`
	private static final MethodHandle MH_BEGIN;
	/// `void rocksdb_optimistictransactiondb_close_base_db(rocksdb_t* base_db);`
	private static final MethodHandle MH_CLOSE_BASE_DB;

	static {
		MH_CLOSE = NativeLibrary.lookup("rocksdb_optimistictransactiondb_close",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_BEGIN = NativeLibrary.lookup("rocksdb_optimistictransaction_begin",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_CLOSE_BASE_DB = NativeLibrary.lookup("rocksdb_optimistictransactiondb_close_base_db",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	OptimisticTransactionDB(MemorySegment ptr, MemorySegment baseDb) {
		super(ptr, baseDb);
	}

	/// Returns the base `rocksdb_t*` pointer. Overridden only to widen visibility to `public`,
	/// satisfying [RocksDBReadOperations#dbPtr()]/[RocksDBWriteOperations#dbPtr()] — the guard
	/// itself lives in [NativeObjectWithBaseDb#dbPtr()].
	///
	/// @return the base DB pointer
	/// @throws IllegalStateException if this optimistic transaction DB has been closed
	@Override
	public MemorySegment dbPtr() {
		return super.dbPtr();
	}

	// -----------------------------------------------------------------------
	// Transaction API
	// -----------------------------------------------------------------------

	/// Begins a new optimistic transaction using the supplied write options and
	/// default [OptimisticTransactionOptions].
	///
	/// @param writeOptions write options for the transaction
	/// @return a new [Transaction]; caller must close it
	public Transaction beginTransaction(WriteOptions writeOptions) {
		try (OptimisticTransactionOptions txnOpts = OptimisticTransactionOptions.newOptimisticTransactionOptions()) {
			return beginTransaction(writeOptions, txnOpts);
		}
	}

	/// Begins a new optimistic transaction using the supplied write options and
	/// transaction options.
	///
	/// @param writeOptions write options for the transaction
	/// @param txnOptions   optimistic transaction options
	/// @return a new [Transaction]; caller must close it
	public Transaction beginTransaction(WriteOptions writeOptions, OptimisticTransactionOptions txnOptions) {
		try {
			MemorySegment txnPtr = (MemorySegment) MH_BEGIN.invokeExact(
					ptr(), writeOptions.ptr(), txnOptions.ptr(), MemorySegment.NULL);
			return new Transaction(txnPtr, txnOptions.getSetSnapshot());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("beginTransaction failed", t);
		}
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
