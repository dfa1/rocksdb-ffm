package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.time.Duration;

/// FFM wrapper for `rocksdb_transactiondb_options_t` — global, database-wide settings for a
/// [TransactionDB] (lock manager sizing, timeouts, and write policy). Per-transaction settings
/// (snapshot, deadlock detection, per-call lock timeout) live on [TransactionOptions] instead.
public final class TransactionDBOptions extends NativeObject {

	/// `rocksdb_transactiondb_options_t* rocksdb_transactiondb_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_transactiondb_options_destroy(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_transactiondb_options_set_max_num_locks(rocksdb_transactiondb_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_MAX_NUM_LOCKS;
	/// `int64_t rocksdb_transactiondb_options_get_max_num_locks(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_MAX_NUM_LOCKS;
	/// `void rocksdb_transactiondb_options_set_max_num_deadlocks(rocksdb_transactiondb_options_t* opt, uint32_t v);`
	private static final MethodHandle MH_SET_MAX_NUM_DEADLOCKS;
	/// `uint32_t rocksdb_transactiondb_options_get_max_num_deadlocks(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_MAX_NUM_DEADLOCKS;
	/// `void rocksdb_transactiondb_options_set_num_stripes(rocksdb_transactiondb_options_t* opt, size_t v);`
	private static final MethodHandle MH_SET_NUM_STRIPES;
	/// `size_t rocksdb_transactiondb_options_get_num_stripes(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_NUM_STRIPES;
	/// `void rocksdb_transactiondb_options_set_transaction_lock_timeout(rocksdb_transactiondb_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_TRANSACTION_LOCK_TIMEOUT;
	/// `int64_t rocksdb_transactiondb_options_get_transaction_lock_timeout(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_TRANSACTION_LOCK_TIMEOUT;
	/// `void rocksdb_transactiondb_options_set_default_lock_timeout(rocksdb_transactiondb_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_DEFAULT_LOCK_TIMEOUT;
	/// `int64_t rocksdb_transactiondb_options_get_default_lock_timeout(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_DEFAULT_LOCK_TIMEOUT;
	/// `void rocksdb_transactiondb_options_set_write_policy(rocksdb_transactiondb_options_t* opt, int v);`
	private static final MethodHandle MH_SET_WRITE_POLICY;
	/// `int rocksdb_transactiondb_options_get_write_policy(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_WRITE_POLICY;
	/// `void rocksdb_transactiondb_options_set_rollback_merge_operands(rocksdb_transactiondb_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_ROLLBACK_MERGE_OPERANDS;
	/// `unsigned char rocksdb_transactiondb_options_get_rollback_merge_operands(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_ROLLBACK_MERGE_OPERANDS;
	/// `void rocksdb_transactiondb_options_set_use_per_key_point_lock_mgr(rocksdb_transactiondb_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_USE_PER_KEY_POINT_LOCK_MGR;
	/// `unsigned char rocksdb_transactiondb_options_get_use_per_key_point_lock_mgr(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_USE_PER_KEY_POINT_LOCK_MGR;
	/// `void rocksdb_transactiondb_options_set_skip_concurrency_control(rocksdb_transactiondb_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_SKIP_CONCURRENCY_CONTROL;
	/// `unsigned char rocksdb_transactiondb_options_get_skip_concurrency_control(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_SKIP_CONCURRENCY_CONTROL;
	/// `void rocksdb_transactiondb_options_set_default_write_batch_flush_threshold(rocksdb_transactiondb_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_DEFAULT_WRITE_BATCH_FLUSH_THRESHOLD;
	/// `int64_t rocksdb_transactiondb_options_get_default_write_batch_flush_threshold(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_DEFAULT_WRITE_BATCH_FLUSH_THRESHOLD;
	/// `void rocksdb_transactiondb_options_set_enable_udt_validation(rocksdb_transactiondb_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_ENABLE_UDT_VALIDATION;
	/// `unsigned char rocksdb_transactiondb_options_get_enable_udt_validation(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_ENABLE_UDT_VALIDATION;
	/// `void rocksdb_transactiondb_options_set_txn_commit_bypass_memtable_threshold(rocksdb_transactiondb_options_t* opt, uint32_t v);`
	private static final MethodHandle MH_SET_TXN_COMMIT_BYPASS_MEMTABLE_THRESHOLD;
	/// `uint32_t rocksdb_transactiondb_options_get_txn_commit_bypass_memtable_threshold(rocksdb_transactiondb_options_t* opt);`
	private static final MethodHandle MH_GET_TXN_COMMIT_BYPASS_MEMTABLE_THRESHOLD;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_transactiondb_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_transactiondb_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_MAX_NUM_LOCKS = NativeLibrary.lookup("rocksdb_transactiondb_options_set_max_num_locks",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_NUM_LOCKS = NativeLibrary.lookup("rocksdb_transactiondb_options_get_max_num_locks",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_MAX_NUM_DEADLOCKS = NativeLibrary.lookup("rocksdb_transactiondb_options_set_max_num_deadlocks",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MAX_NUM_DEADLOCKS = NativeLibrary.lookup("rocksdb_transactiondb_options_get_max_num_deadlocks",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_NUM_STRIPES = NativeLibrary.lookup("rocksdb_transactiondb_options_set_num_stripes",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_NUM_STRIPES = NativeLibrary.lookup("rocksdb_transactiondb_options_get_num_stripes",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_TRANSACTION_LOCK_TIMEOUT = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_set_transaction_lock_timeout",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_TRANSACTION_LOCK_TIMEOUT = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_get_transaction_lock_timeout",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_DEFAULT_LOCK_TIMEOUT = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_set_default_lock_timeout",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_DEFAULT_LOCK_TIMEOUT = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_get_default_lock_timeout",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_WRITE_POLICY = NativeLibrary.lookup("rocksdb_transactiondb_options_set_write_policy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_WRITE_POLICY = NativeLibrary.lookup("rocksdb_transactiondb_options_get_write_policy",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_ROLLBACK_MERGE_OPERANDS = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_set_rollback_merge_operands",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ROLLBACK_MERGE_OPERANDS = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_get_rollback_merge_operands",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_USE_PER_KEY_POINT_LOCK_MGR = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_set_use_per_key_point_lock_mgr",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_PER_KEY_POINT_LOCK_MGR = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_get_use_per_key_point_lock_mgr",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_SKIP_CONCURRENCY_CONTROL = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_set_skip_concurrency_control",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_SKIP_CONCURRENCY_CONTROL = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_get_skip_concurrency_control",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_DEFAULT_WRITE_BATCH_FLUSH_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_set_default_write_batch_flush_threshold",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_DEFAULT_WRITE_BATCH_FLUSH_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_get_default_write_batch_flush_threshold",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_ENABLE_UDT_VALIDATION = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_set_enable_udt_validation",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ENABLE_UDT_VALIDATION = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_get_enable_udt_validation",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_TXN_COMMIT_BYPASS_MEMTABLE_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_set_txn_commit_bypass_memtable_threshold",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_TXN_COMMIT_BYPASS_MEMTABLE_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transactiondb_options_get_txn_commit_bypass_memtable_threshold",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	}

	private TransactionDBOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a new [TransactionDBOptions] with default settings.
	///
	/// @return a new [TransactionDBOptions]; caller must close it
	public static TransactionDBOptions newTransactionDBOptions() {
		try {
			return new TransactionDBOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("transactiondb options create failed", t);
		}
	}

	/// Maximum number of locks held simultaneously. Default: -1 (unlimited).
	///
	/// @param maxNumLocks max locks; `-1` for unlimited
	/// @return this instance for chaining
	public TransactionDBOptions setMaxNumLocks(long maxNumLocks) {
		NativeFields.setLong(MH_SET_MAX_NUM_LOCKS, ptr(), maxNumLocks);
		return this;
	}

	/// Returns the maximum number of locks held simultaneously.
	///
	/// @return max locks; `-1` means unlimited
	public long getMaxNumLocks() {
		return NativeFields.getLong(MH_GET_MAX_NUM_LOCKS, ptr());
	}

	/// Maximum number of deadlocks to track in the deadlock detection buffer. Default: 5.
	///
	/// @param maxNumDeadlocks max tracked deadlocks
	/// @return this instance for chaining
	public TransactionDBOptions setMaxNumDeadlocks(int maxNumDeadlocks) {
		NativeFields.setInt(MH_SET_MAX_NUM_DEADLOCKS, ptr(), maxNumDeadlocks);
		return this;
	}

	/// Returns the maximum number of deadlocks tracked in the deadlock detection buffer.
	///
	/// @return max tracked deadlocks
	public int getMaxNumDeadlocks() {
		return NativeFields.getInt(MH_GET_MAX_NUM_DEADLOCKS, ptr());
	}

	/// Number of sub-lock-tables. Increasing reduces lock contention. Default: 16.
	///
	/// @param numStripes number of lock-table stripes
	/// @return this instance for chaining
	public TransactionDBOptions setNumStripes(long numStripes) {
		NativeFields.setLong(MH_SET_NUM_STRIPES, ptr(), numStripes);
		return this;
	}

	/// Returns the number of sub-lock-tables.
	///
	/// @return number of lock-table stripes
	public long getNumStripes() {
		return NativeFields.getLong(MH_GET_NUM_STRIPES, ptr());
	}

	/// Default wait timeout for acquiring a lock via [TransactionDB], used when a transaction
	/// does not set its own [TransactionOptions#setLockTimeout(Duration)]. `null` waits
	/// forever; [Duration#ZERO] fails immediately. Default: 1 second.
	///
	/// @param transactionLockTimeout lock timeout, or `null` to wait forever
	/// @return this instance for chaining
	/// @throws IllegalArgumentException if `transactionLockTimeout` is negative
	public TransactionDBOptions setTransactionLockTimeout(Duration transactionLockTimeout) {
		try {
			MH_SET_TRANSACTION_LOCK_TIMEOUT.invokeExact(ptr(), toMillisOrNoTimeout(transactionLockTimeout));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setTransactionLockTimeout failed", t);
		}
		return this;
	}

	/// Returns the default transaction lock wait timeout.
	///
	/// @return lock timeout, or `null` if waiting forever
	public Duration getTransactionLockTimeout() {
		try {
			return millisToDurationOrNull((long) MH_GET_TRANSACTION_LOCK_TIMEOUT.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getTransactionLockTimeout failed", t);
		}
	}

	/// Lock wait timeout for non-transactional writes/reads issued directly against the
	/// [TransactionDB] (bypassing a [Transaction]). `null` waits forever; [Duration#ZERO]
	/// fails immediately. Default: 1 second.
	///
	/// @param defaultLockTimeout lock timeout, or `null` to wait forever
	/// @return this instance for chaining
	/// @throws IllegalArgumentException if `defaultLockTimeout` is negative
	public TransactionDBOptions setDefaultLockTimeout(Duration defaultLockTimeout) {
		try {
			MH_SET_DEFAULT_LOCK_TIMEOUT.invokeExact(ptr(), toMillisOrNoTimeout(defaultLockTimeout));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setDefaultLockTimeout failed", t);
		}
		return this;
	}

	/// Returns the default lock wait timeout for direct (non-transactional) operations.
	///
	/// @return lock timeout, or `null` if waiting forever
	public Duration getDefaultLockTimeout() {
		try {
			return millisToDurationOrNull((long) MH_GET_DEFAULT_LOCK_TIMEOUT.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getDefaultLockTimeout failed", t);
		}
	}

	/// Converts `duration` to milliseconds for a `rocksdb_transactiondb_options_t` lock-timeout
	/// field, or to the native "no timeout" sentinel (`-1`) if `duration` is `null`.
	///
	/// @param duration the timeout, or `null` for no timeout
	/// @return milliseconds, or `-1` if `duration` is `null`
	/// @throws IllegalArgumentException if `duration` is negative
	private static long toMillisOrNoTimeout(Duration duration) {
		if (duration == null) {
			return -1;
		}
		if (duration.isNegative()) {
			throw new IllegalArgumentException("duration must not be negative: " + duration);
		}
		return duration.toMillis();
	}

	/// Converts a `rocksdb_transactiondb_options_t` lock-timeout field's raw milliseconds to a
	/// [Duration], or `null` if `millis` is the native "no timeout" sentinel (negative).
	///
	/// @param millis raw milliseconds read from the native field
	/// @return the equivalent [Duration], or `null` if `millis` is negative
	private static Duration millisToDurationOrNull(long millis) {
		return millis < 0 ? null : Duration.ofMillis(millis);
	}

	/// Controls when a transaction's writes become durable. Default: [WritePolicy#WRITE_COMMITTED].
	///
	/// @param writePolicy the write policy
	/// @return this instance for chaining
	public TransactionDBOptions setWritePolicy(WritePolicy writePolicy) {
		NativeFields.setInt(MH_SET_WRITE_POLICY, ptr(), writePolicy.getValue());
		return this;
	}

	/// Returns the configured write policy.
	///
	/// @return the active [WritePolicy]
	public WritePolicy getWritePolicy() {
		return WritePolicy.fromValue(NativeFields.getInt(MH_GET_WRITE_POLICY, ptr()));
	}

	/// If `true`, [Transaction#rollback()] on a key with a merge operand rolls back by
	/// inserting the merge operand's inverse instead of the prior value. Only meaningful with
	/// [WritePolicy#WRITE_PREPARED] or [WritePolicy#WRITE_UNPREPARED]. Default: `false`.
	///
	/// @param value `true` to roll back merge operands
	/// @return this instance for chaining
	public TransactionDBOptions setRollbackMergeOperands(boolean value) {
		NativeFields.setBoolean(MH_SET_ROLLBACK_MERGE_OPERANDS, ptr(), value);
		return this;
	}

	/// Returns whether rollback re-inserts a merge operand's inverse.
	///
	/// @return `true` if merge operands are rolled back
	public boolean getRollbackMergeOperands() {
		return NativeFields.getBoolean(MH_GET_ROLLBACK_MERGE_OPERANDS, ptr());
	}

	/// If `true`, uses a lock manager that locks each key individually rather than by range,
	/// trading some throughput for more precise contention. Default: `false`.
	///
	/// @param value `true` to use the per-key point lock manager
	/// @return this instance for chaining
	public TransactionDBOptions setUsePerKeyPointLockMgr(boolean value) {
		NativeFields.setBoolean(MH_SET_USE_PER_KEY_POINT_LOCK_MGR, ptr(), value);
		return this;
	}

	/// Returns whether the per-key point lock manager is in use.
	///
	/// @return `true` if the per-key point lock manager is active
	public boolean getUsePerKeyPointLockMgr() {
		return NativeFields.getBoolean(MH_GET_USE_PER_KEY_POINT_LOCK_MGR, ptr());
	}

	/// If `true`, skips two-phase locking and relies on the write policy alone for isolation.
	/// Improves throughput at the cost of losing standard transactional conflict detection.
	/// Default: `false`.
	///
	/// @param value `true` to skip concurrency control
	/// @return this instance for chaining
	public TransactionDBOptions setSkipConcurrencyControl(boolean value) {
		NativeFields.setBoolean(MH_SET_SKIP_CONCURRENCY_CONTROL, ptr(), value);
		return this;
	}

	/// Returns whether concurrency control is skipped.
	///
	/// @return `true` if concurrency control is skipped
	public boolean getSkipConcurrencyControl() {
		return NativeFields.getBoolean(MH_GET_SKIP_CONCURRENCY_CONTROL, ptr());
	}

	/// Default write-batch size at which a transaction using [WritePolicy#WRITE_PREPARED] or
	/// [WritePolicy#WRITE_UNPREPARED] flushes its buffered writes early, used when a
	/// transaction does not set its own
	/// [TransactionOptions#setWriteBatchFlushThreshold(MemorySize)]. [MemorySize#ZERO]
	/// disables early flushing. Default: [MemorySize#ZERO].
	///
	/// @param defaultWriteBatchFlushThreshold flush threshold; [MemorySize#ZERO] disables it
	/// @return this instance for chaining
	public TransactionDBOptions setDefaultWriteBatchFlushThreshold(MemorySize defaultWriteBatchFlushThreshold) {
		NativeFields.setMemorySize(MH_SET_DEFAULT_WRITE_BATCH_FLUSH_THRESHOLD, ptr(), defaultWriteBatchFlushThreshold);
		return this;
	}

	/// Returns the default write-batch flush threshold.
	///
	/// @return flush threshold; [MemorySize#ZERO] means disabled
	public MemorySize getDefaultWriteBatchFlushThreshold() {
		return NativeFields.getMemorySize(MH_GET_DEFAULT_WRITE_BATCH_FLUSH_THRESHOLD, ptr());
	}

	/// If `true`, validates user-defined timestamp sizes for consistency across column families.
	/// Default: `false`.
	///
	/// @param value `true` to enable user-defined-timestamp validation
	/// @return this instance for chaining
	public TransactionDBOptions setEnableUdtValidation(boolean value) {
		NativeFields.setBoolean(MH_SET_ENABLE_UDT_VALIDATION, ptr(), value);
		return this;
	}

	/// Returns whether user-defined-timestamp validation is enabled.
	///
	/// @return `true` if user-defined-timestamp validation is enabled
	public boolean getEnableUdtValidation() {
		return NativeFields.getBoolean(MH_GET_ENABLE_UDT_VALIDATION, ptr());
	}

	/// Number of keys in a transaction's write batch above which commit bypasses the memtable
	/// and writes directly to L0, used when a transaction does not set its own
	/// [TransactionOptions#setCommitBypassMemtable(boolean)] explicitly. `0` disables the
	/// bypass. Default: 0.
	///
	/// @param txnCommitBypassMemtableThreshold key-count threshold; `0` disables the bypass
	/// @return this instance for chaining
	public TransactionDBOptions setTxnCommitBypassMemtableThreshold(int txnCommitBypassMemtableThreshold) {
		NativeFields.setInt(MH_SET_TXN_COMMIT_BYPASS_MEMTABLE_THRESHOLD, ptr(), txnCommitBypassMemtableThreshold);
		return this;
	}

	/// Returns the key-count threshold above which commit bypasses the memtable.
	///
	/// @return key-count threshold; `0` means the bypass is disabled
	public int getTxnCommitBypassMemtableThreshold() {
		return NativeFields.getInt(MH_GET_TXN_COMMIT_BYPASS_MEMTABLE_THRESHOLD, ptr());
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
