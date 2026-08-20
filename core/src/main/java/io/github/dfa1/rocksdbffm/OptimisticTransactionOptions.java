package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_optimistictransaction_options_t`.
///
/// Unlike pessimistic [TransactionOptions], there is no deadlock detection
/// or lock timeout — conflicts are detected at [Transaction#commit()] time.
public final class OptimisticTransactionOptions extends NativeObject {

	/// `rocksdb_optimistictransaction_options_t* rocksdb_optimistictransaction_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_optimistictransaction_options_destroy(rocksdb_optimistictransaction_options_t* opt);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_optimistictransaction_options_set_set_snapshot(rocksdb_optimistictransaction_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_SET_SNAPSHOT;
	/// `unsigned char rocksdb_optimistictransaction_options_get_set_snapshot(rocksdb_optimistictransaction_options_t* opt);`
	private static final MethodHandle MH_GET_SET_SNAPSHOT;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_optimistictransaction_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_optimistictransaction_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_SET_SNAPSHOT = NativeLibrary.lookup(
				"rocksdb_optimistictransaction_options_set_set_snapshot",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_SET_SNAPSHOT = NativeLibrary.lookup(
				"rocksdb_optimistictransaction_options_get_set_snapshot",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));
	}

	private OptimisticTransactionOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates [OptimisticTransactionOptions] with RocksDB defaults.
	///
	/// @return a new instance; caller must close it
	public static OptimisticTransactionOptions newOptimisticTransactionOptions() {
		try {
			return new OptimisticTransactionOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("optimistic transaction options create failed", t);
		}
	}

	/// If `true`, a snapshot is taken at the start of the transaction.
	/// The transaction will then check whether any keys it reads or writes
	/// have been modified since that snapshot when [Transaction#commit()] is called.
	/// Default: `false`.
	///
	/// @param value `true` to take a snapshot at transaction start
	/// @return `this` for chaining
	public OptimisticTransactionOptions setSetSnapshot(boolean value) {
		try {
			MH_SET_SET_SNAPSHOT.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setSetSnapshot failed", t);
		}
		return this;
	}

	/// Returns whether a snapshot is taken at the start of the transaction.
	///
	/// @return `true` if a snapshot is taken at transaction start
	public boolean getSetSnapshot() {
		try {
			return RocksDB.fromByte((byte) MH_GET_SET_SNAPSHOT.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getSetSnapshot failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
