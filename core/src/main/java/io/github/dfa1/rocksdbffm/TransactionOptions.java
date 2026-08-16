package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.util.Objects;

/// FFM wrapper for `rocksdb_transaction_options_t` — per-transaction settings passed to
/// [TransactionDB#beginTransaction]. Database-wide settings (lock manager sizing, write
/// policy) live on [TransactionDBOptions] instead.
public final class TransactionOptions extends NativeObject {

	/// `rocksdb_transaction_options_t* rocksdb_transaction_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_transaction_options_destroy(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_transaction_options_set_set_snapshot(rocksdb_transaction_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_SET_SNAPSHOT;
	/// `unsigned char rocksdb_transaction_options_get_set_snapshot(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_SET_SNAPSHOT;
	/// `void rocksdb_transaction_options_set_deadlock_detect(rocksdb_transaction_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_DEADLOCK_DETECT;
	/// `unsigned char rocksdb_transaction_options_get_deadlock_detect(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_DEADLOCK_DETECT;
	/// `void rocksdb_transaction_options_set_use_only_the_last_commit_time_batch_for_recovery(rocksdb_transaction_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_USE_ONLY_THE_LAST_COMMIT_TIME_BATCH_FOR_RECOVERY;
	/// `unsigned char rocksdb_transaction_options_get_use_only_the_last_commit_time_batch_for_recovery(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_USE_ONLY_THE_LAST_COMMIT_TIME_BATCH_FOR_RECOVERY;
	/// `void rocksdb_transaction_options_set_lock_timeout(rocksdb_transaction_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_LOCK_TIMEOUT;
	/// `int64_t rocksdb_transaction_options_get_lock_timeout(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_LOCK_TIMEOUT;
	/// `void rocksdb_transaction_options_set_deadlock_timeout_us(rocksdb_transaction_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_DEADLOCK_TIMEOUT_US;
	/// `int64_t rocksdb_transaction_options_get_deadlock_timeout_us(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_DEADLOCK_TIMEOUT_US;
	/// `void rocksdb_transaction_options_set_expiration(rocksdb_transaction_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_EXPIRATION;
	/// `int64_t rocksdb_transaction_options_get_expiration(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_EXPIRATION;
	/// `void rocksdb_transaction_options_set_deadlock_detect_depth(rocksdb_transaction_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_DEADLOCK_DETECT_DEPTH;
	/// `int64_t rocksdb_transaction_options_get_deadlock_detect_depth(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_DEADLOCK_DETECT_DEPTH;
	/// `void rocksdb_transaction_options_set_max_write_batch_size(rocksdb_transaction_options_t* opt, size_t v);`
	private static final MethodHandle MH_SET_MAX_WRITE_BATCH_SIZE;
	/// `size_t rocksdb_transaction_options_get_max_write_batch_size(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_MAX_WRITE_BATCH_SIZE;
	/// `void rocksdb_transaction_options_set_skip_concurrency_control(rocksdb_transaction_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_SKIP_CONCURRENCY_CONTROL;
	/// `unsigned char rocksdb_transaction_options_get_skip_concurrency_control(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_SKIP_CONCURRENCY_CONTROL;
	/// `void rocksdb_transaction_options_set_skip_prepare(rocksdb_transaction_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_SKIP_PREPARE;
	/// `unsigned char rocksdb_transaction_options_get_skip_prepare(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_SKIP_PREPARE;
	/// `void rocksdb_transaction_options_set_write_batch_flush_threshold(rocksdb_transaction_options_t* opt, int64_t v);`
	private static final MethodHandle MH_SET_WRITE_BATCH_FLUSH_THRESHOLD;
	/// `int64_t rocksdb_transaction_options_get_write_batch_flush_threshold(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_WRITE_BATCH_FLUSH_THRESHOLD;
	/// `void rocksdb_transaction_options_set_write_batch_track_timestamp_size(rocksdb_transaction_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_WRITE_BATCH_TRACK_TIMESTAMP_SIZE;
	/// `unsigned char rocksdb_transaction_options_get_write_batch_track_timestamp_size(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_WRITE_BATCH_TRACK_TIMESTAMP_SIZE;
	/// `void rocksdb_transaction_options_set_commit_bypass_memtable(rocksdb_transaction_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_COMMIT_BYPASS_MEMTABLE;
	/// `unsigned char rocksdb_transaction_options_get_commit_bypass_memtable(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_COMMIT_BYPASS_MEMTABLE;
	/// `void rocksdb_transaction_options_set_large_txn_commit_optimize_threshold(rocksdb_transaction_options_t* opt, uint32_t v);`
	private static final MethodHandle MH_SET_LARGE_TXN_COMMIT_OPTIMIZE_THRESHOLD;
	/// `uint32_t rocksdb_transaction_options_get_large_txn_commit_optimize_threshold(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_LARGE_TXN_COMMIT_OPTIMIZE_THRESHOLD;
	/// `void rocksdb_transaction_options_set_large_txn_commit_optimize_byte_threshold(rocksdb_transaction_options_t* opt, uint64_t v);`
	private static final MethodHandle MH_SET_LARGE_TXN_COMMIT_OPTIMIZE_BYTE_THRESHOLD;
	/// `uint64_t rocksdb_transaction_options_get_large_txn_commit_optimize_byte_threshold(rocksdb_transaction_options_t* opt);`
	private static final MethodHandle MH_GET_LARGE_TXN_COMMIT_OPTIMIZE_BYTE_THRESHOLD;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_transaction_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_transaction_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_SET_SNAPSHOT = NativeLibrary.lookup("rocksdb_transaction_options_set_set_snapshot",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_SET_SNAPSHOT = NativeLibrary.lookup("rocksdb_transaction_options_get_set_snapshot",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_DEADLOCK_DETECT = NativeLibrary.lookup("rocksdb_transaction_options_set_deadlock_detect",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_DEADLOCK_DETECT = NativeLibrary.lookup("rocksdb_transaction_options_get_deadlock_detect",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_USE_ONLY_THE_LAST_COMMIT_TIME_BATCH_FOR_RECOVERY = NativeLibrary.lookup(
				"rocksdb_transaction_options_set_use_only_the_last_commit_time_batch_for_recovery",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_ONLY_THE_LAST_COMMIT_TIME_BATCH_FOR_RECOVERY = NativeLibrary.lookup(
				"rocksdb_transaction_options_get_use_only_the_last_commit_time_batch_for_recovery",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_LOCK_TIMEOUT = NativeLibrary.lookup("rocksdb_transaction_options_set_lock_timeout",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_LOCK_TIMEOUT = NativeLibrary.lookup("rocksdb_transaction_options_get_lock_timeout",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_DEADLOCK_TIMEOUT_US = NativeLibrary.lookup("rocksdb_transaction_options_set_deadlock_timeout_us",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_DEADLOCK_TIMEOUT_US = NativeLibrary.lookup("rocksdb_transaction_options_get_deadlock_timeout_us",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_EXPIRATION = NativeLibrary.lookup("rocksdb_transaction_options_set_expiration",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_EXPIRATION = NativeLibrary.lookup("rocksdb_transaction_options_get_expiration",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_DEADLOCK_DETECT_DEPTH = NativeLibrary.lookup("rocksdb_transaction_options_set_deadlock_detect_depth",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_DEADLOCK_DETECT_DEPTH = NativeLibrary.lookup("rocksdb_transaction_options_get_deadlock_detect_depth",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_MAX_WRITE_BATCH_SIZE = NativeLibrary.lookup("rocksdb_transaction_options_set_max_write_batch_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_WRITE_BATCH_SIZE = NativeLibrary.lookup("rocksdb_transaction_options_get_max_write_batch_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_SKIP_CONCURRENCY_CONTROL = NativeLibrary.lookup(
				"rocksdb_transaction_options_set_skip_concurrency_control",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_SKIP_CONCURRENCY_CONTROL = NativeLibrary.lookup(
				"rocksdb_transaction_options_get_skip_concurrency_control",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_SKIP_PREPARE = NativeLibrary.lookup("rocksdb_transaction_options_set_skip_prepare",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_SKIP_PREPARE = NativeLibrary.lookup("rocksdb_transaction_options_get_skip_prepare",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_WRITE_BATCH_FLUSH_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transaction_options_set_write_batch_flush_threshold",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_WRITE_BATCH_FLUSH_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transaction_options_get_write_batch_flush_threshold",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_WRITE_BATCH_TRACK_TIMESTAMP_SIZE = NativeLibrary.lookup(
				"rocksdb_transaction_options_set_write_batch_track_timestamp_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_WRITE_BATCH_TRACK_TIMESTAMP_SIZE = NativeLibrary.lookup(
				"rocksdb_transaction_options_get_write_batch_track_timestamp_size",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_COMMIT_BYPASS_MEMTABLE = NativeLibrary.lookup(
				"rocksdb_transaction_options_set_commit_bypass_memtable",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_COMMIT_BYPASS_MEMTABLE = NativeLibrary.lookup(
				"rocksdb_transaction_options_get_commit_bypass_memtable",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_LARGE_TXN_COMMIT_OPTIMIZE_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transaction_options_set_large_txn_commit_optimize_threshold",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_LARGE_TXN_COMMIT_OPTIMIZE_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transaction_options_get_large_txn_commit_optimize_threshold",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_LARGE_TXN_COMMIT_OPTIMIZE_BYTE_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transaction_options_set_large_txn_commit_optimize_byte_threshold",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_LARGE_TXN_COMMIT_OPTIMIZE_BYTE_THRESHOLD = NativeLibrary.lookup(
				"rocksdb_transaction_options_get_large_txn_commit_optimize_byte_threshold",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
	}

	private TransactionOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a new [TransactionOptions] with default settings.
	///
	/// @return a new [TransactionOptions]; caller must close it
	public static TransactionOptions newTransactionOptions() {
		try {
			return new TransactionOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDBException.wrap("transaction options create failed", t);
		}
	}

	/// If true, a snapshot is taken at the start of each transaction.
	/// Default: false.
	///
	/// @param value `true` to take a snapshot at transaction start
	/// @return this instance for chaining
	public TransactionOptions setSetSnapshot(boolean value) {
		try {
			MH_SET_SET_SNAPSHOT.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setSetSnapshot failed", t);
		}
		return this;
	}

	/// Returns whether a snapshot is taken at the start of each transaction.
	///
	/// @return `true` if a snapshot is taken at transaction start
	public boolean getSetSnapshot() {
		try {
			return RocksDB.fromByte((byte) MH_GET_SET_SNAPSHOT.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getSetSnapshot failed", t);
		}
	}

	/// If true, the transaction will detect deadlocks and return an error
	/// instead of waiting. Default: false.
	///
	/// @param value `true` to enable deadlock detection
	/// @return this instance for chaining
	public TransactionOptions setDeadlockDetect(boolean value) {
		try {
			MH_SET_DEADLOCK_DETECT.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setDeadlockDetect failed", t);
		}
		return this;
	}

	/// Returns whether deadlock detection is enabled.
	///
	/// @return `true` if deadlock detection is enabled
	public boolean getDeadlockDetect() {
		try {
			return RocksDB.fromByte((byte) MH_GET_DEADLOCK_DETECT.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getDeadlockDetect failed", t);
		}
	}

	/// If `true`, recovery replays only the most recent commit-time write batch for each key,
	/// instead of all of them. Only meaningful with [WritePolicy#WRITE_PREPARED] or
	/// [WritePolicy#WRITE_UNPREPARED]. Default: `false`.
	///
	/// @param value `true` to use only the last commit-time write batch during recovery
	/// @return this instance for chaining
	public TransactionOptions setUseOnlyTheLastCommitTimeBatchForRecovery(boolean value) {
		try {
			MH_SET_USE_ONLY_THE_LAST_COMMIT_TIME_BATCH_FOR_RECOVERY.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setUseOnlyTheLastCommitTimeBatchForRecovery failed", t);
		}
		return this;
	}

	/// Returns whether recovery uses only the last commit-time write batch per key.
	///
	/// @return `true` if only the last commit-time write batch is used during recovery
	public boolean getUseOnlyTheLastCommitTimeBatchForRecovery() {
		try {
			return RocksDB.fromByte((byte) MH_GET_USE_ONLY_THE_LAST_COMMIT_TIME_BATCH_FOR_RECOVERY.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getUseOnlyTheLastCommitTimeBatchForRecovery failed", t);
		}
	}

	/// Timeout to wait for a lock. `null` falls back to
	/// [TransactionDBOptions#setTransactionLockTimeout(Duration)] (which itself waits forever
	/// if that is also `null`); [Duration#ZERO] fails immediately if a lock is not available.
	/// Default: `null`.
	///
	/// @param lockTimeout lock timeout, or `null` to fall back to the [TransactionDB]-wide default
	/// @return this instance for chaining
	/// @throws IllegalArgumentException if `lockTimeout` is negative
	public TransactionOptions setLockTimeout(Duration lockTimeout) {
		try {
			MH_SET_LOCK_TIMEOUT.invokeExact(ptr(), toMillisOrNoTimeout(lockTimeout));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setLockTimeout failed", t);
		}
		return this;
	}

	/// Returns the lock wait timeout.
	///
	/// @return lock timeout, or `null` if falling back to the [TransactionDB]-wide default
	public Duration getLockTimeout() {
		try {
			return millisToDurationOrNull((long) MH_GET_LOCK_TIMEOUT.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getLockTimeout failed", t);
		}
	}

	/// Timeout for detecting deadlocks between waiting transactions, always clamped below
	/// [#setLockTimeout(Duration)] internally (`std::min` of the two). Unlike the other
	/// timeouts on this class, RocksDB documents no special meaning for a negative value here,
	/// so this does not accept `null`. Default: 500 microseconds.
	///
	/// @param deadlockTimeoutUs deadlock detection timeout, at microsecond resolution
	/// @return this instance for chaining
	/// @throws NullPointerException     if `deadlockTimeoutUs` is `null`
	/// @throws IllegalArgumentException if `deadlockTimeoutUs` is negative
	public TransactionOptions setDeadlockTimeoutUs(Duration deadlockTimeoutUs) {
		Objects.requireNonNull(deadlockTimeoutUs, "deadlockTimeoutUs must not be null");
		if (deadlockTimeoutUs.isNegative()) {
			throw new IllegalArgumentException("deadlockTimeoutUs must not be negative: " + deadlockTimeoutUs);
		}
		try {
			MH_SET_DEADLOCK_TIMEOUT_US.invokeExact(ptr(), deadlockTimeoutUs.toNanos() / 1_000L);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setDeadlockTimeoutUs failed", t);
		}
		return this;
	}

	/// Returns the deadlock detection timeout.
	///
	/// @return deadlock detection timeout, at microsecond resolution
	public Duration getDeadlockTimeoutUs() {
		try {
			long micros = (long) MH_GET_DEADLOCK_TIMEOUT_US.invokeExact(ptr());
			return Duration.ofNanos(micros * 1_000L);
		} catch (Throwable t) {
			throw RocksDBException.wrap("getDeadlockTimeoutUs failed", t);
		}
	}

	/// Duration after which this transaction, if it has not been committed, is considered
	/// expired and can be rolled back by another transaction's deadlock detection. `null`
	/// disables expiration. Default: `null`.
	///
	/// @param expiration expiration duration, or `null` to disable expiration
	/// @return this instance for chaining
	/// @throws IllegalArgumentException if `expiration` is negative
	public TransactionOptions setExpiration(Duration expiration) {
		try {
			MH_SET_EXPIRATION.invokeExact(ptr(), toMillisOrNoTimeout(expiration));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setExpiration failed", t);
		}
		return this;
	}

	/// Returns the transaction expiration duration.
	///
	/// @return expiration duration, or `null` if expiration is disabled
	public Duration getExpiration() {
		try {
			return millisToDurationOrNull((long) MH_GET_EXPIRATION.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getExpiration failed", t);
		}
	}

	/// Converts `duration` to milliseconds for a `rocksdb_transaction_options_t` field, or to
	/// the native negative sentinel (`-1`) if `duration` is `null`.
	///
	/// @param duration the duration, or `null` for the field's negative sentinel
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

	/// Converts a `rocksdb_transaction_options_t` field's raw milliseconds to a [Duration], or
	/// `null` if `millis` is negative (this class's shared negative-sentinel convention).
	///
	/// @param millis raw milliseconds read from the native field
	/// @return the equivalent [Duration], or `null` if `millis` is negative
	private static Duration millisToDurationOrNull(long millis) {
		return millis < 0 ? null : Duration.ofMillis(millis);
	}

	/// Maximum depth of the wait-for graph traversed when detecting deadlocks. Default: 50.
	///
	/// @param deadlockDetectDepth max wait-for graph depth
	/// @return this instance for chaining
	public TransactionOptions setDeadlockDetectDepth(long deadlockDetectDepth) {
		try {
			MH_SET_DEADLOCK_DETECT_DEPTH.invokeExact(ptr(), deadlockDetectDepth);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setDeadlockDetectDepth failed", t);
		}
		return this;
	}

	/// Returns the maximum wait-for graph depth traversed during deadlock detection.
	///
	/// @return max wait-for graph depth
	public long getDeadlockDetectDepth() {
		try {
			return (long) MH_GET_DEADLOCK_DETECT_DEPTH.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDBException.wrap("getDeadlockDetectDepth failed", t);
		}
	}

	/// Maximum size of the transaction's underlying write batch. [MemorySize#ZERO] means no
	/// limit. Default: [MemorySize#ZERO].
	///
	/// @param maxWriteBatchSize max write batch size; [MemorySize#ZERO] means unlimited
	/// @return this instance for chaining
	public TransactionOptions setMaxWriteBatchSize(MemorySize maxWriteBatchSize) {
		try {
			MH_SET_MAX_WRITE_BATCH_SIZE.invokeExact(ptr(), maxWriteBatchSize.toBytes());
		} catch (Throwable t) {
			throw RocksDBException.wrap("setMaxWriteBatchSize failed", t);
		}
		return this;
	}

	/// Returns the maximum write batch size.
	///
	/// @return max write batch size; [MemorySize#ZERO] means unlimited
	public MemorySize getMaxWriteBatchSize() {
		try {
			return MemorySize.ofBytes((long) MH_GET_MAX_WRITE_BATCH_SIZE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getMaxWriteBatchSize failed", t);
		}
	}

	/// If `true`, skips two-phase locking for this transaction alone, overriding
	/// [TransactionDBOptions#setSkipConcurrencyControl(boolean)]. Default: `false`.
	///
	/// @param value `true` to skip concurrency control for this transaction
	/// @return this instance for chaining
	public TransactionOptions setSkipConcurrencyControl(boolean value) {
		try {
			MH_SET_SKIP_CONCURRENCY_CONTROL.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setSkipConcurrencyControl failed", t);
		}
		return this;
	}

	/// Returns whether concurrency control is skipped for this transaction.
	///
	/// @return `true` if concurrency control is skipped
	public boolean getSkipConcurrencyControl() {
		try {
			return RocksDB.fromByte((byte) MH_GET_SKIP_CONCURRENCY_CONTROL.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getSkipConcurrencyControl failed", t);
		}
	}

	/// If `true`, skips the prepare phase of two-phase commit; the transaction commits
	/// directly. Only meaningful with [WritePolicy#WRITE_PREPARED] or
	/// [WritePolicy#WRITE_UNPREPARED]. Default: `false`.
	///
	/// @param value `true` to skip the prepare phase
	/// @return this instance for chaining
	public TransactionOptions setSkipPrepare(boolean value) {
		try {
			MH_SET_SKIP_PREPARE.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setSkipPrepare failed", t);
		}
		return this;
	}

	/// Returns whether the prepare phase is skipped.
	///
	/// @return `true` if the prepare phase is skipped
	public boolean getSkipPrepare() {
		try {
			return RocksDB.fromByte((byte) MH_GET_SKIP_PREPARE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getSkipPrepare failed", t);
		}
	}

	/// Write-batch size at which this transaction flushes its buffered writes early,
	/// overriding [TransactionDBOptions#setDefaultWriteBatchFlushThreshold(MemorySize)].
	/// [MemorySize#ZERO] disables early flushing for this transaction, overriding any
	/// DB-wide setting. `null` (the default) inherits the DB-wide setting instead — per
	/// `transaction_db.h`, the native field defaults to `-1`, not `0`: only a negative value
	/// means "use `TransactionDBOptions`'s default", while `0` explicitly means "no limit for
	/// this transaction".
	///
	/// @param writeBatchFlushThreshold flush threshold; [MemorySize#ZERO] disables it for
	///                                 this transaction, `null` inherits the DB-wide default
	/// @return this instance for chaining
	public TransactionOptions setWriteBatchFlushThreshold(MemorySize writeBatchFlushThreshold) {
		try {
			MH_SET_WRITE_BATCH_FLUSH_THRESHOLD.invokeExact(ptr(),
					writeBatchFlushThreshold == null ? -1L : writeBatchFlushThreshold.toBytes());
		} catch (Throwable t) {
			throw RocksDBException.wrap("setWriteBatchFlushThreshold failed", t);
		}
		return this;
	}

	/// Returns this transaction's write-batch flush threshold.
	///
	/// @return flush threshold, [MemorySize#ZERO] if disabled for this transaction, or `null`
	/// if inheriting the DB-wide default
	public MemorySize getWriteBatchFlushThreshold() {
		try {
			long threshold = (long) MH_GET_WRITE_BATCH_FLUSH_THRESHOLD.invokeExact(ptr());
			return threshold < 0 ? null : MemorySize.ofBytes(threshold);
		} catch (Throwable t) {
			throw RocksDBException.wrap("getWriteBatchFlushThreshold failed", t);
		}
	}

	/// If `true`, the transaction's write batch tracks the user-defined timestamp size of each
	/// column family it touches. Default: `false`.
	///
	/// @param value `true` to track per-column-family timestamp size
	/// @return this instance for chaining
	public TransactionOptions setWriteBatchTrackTimestampSize(boolean value) {
		try {
			MH_SET_WRITE_BATCH_TRACK_TIMESTAMP_SIZE.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setWriteBatchTrackTimestampSize failed", t);
		}
		return this;
	}

	/// Returns whether the write batch tracks per-column-family timestamp size.
	///
	/// @return `true` if timestamp size tracking is enabled
	public boolean getWriteBatchTrackTimestampSize() {
		try {
			return RocksDB.fromByte((byte) MH_GET_WRITE_BATCH_TRACK_TIMESTAMP_SIZE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getWriteBatchTrackTimestampSize failed", t);
		}
	}

	/// If `true`, this transaction's commit bypasses the memtable and writes directly to L0,
	/// overriding [TransactionDBOptions#setTxnCommitBypassMemtableThreshold(int)]. Default: `false`.
	///
	/// @param value `true` to bypass the memtable on commit
	/// @return this instance for chaining
	public TransactionOptions setCommitBypassMemtable(boolean value) {
		try {
			MH_SET_COMMIT_BYPASS_MEMTABLE.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDBException.wrap("setCommitBypassMemtable failed", t);
		}
		return this;
	}

	/// Returns whether commit bypasses the memtable for this transaction.
	///
	/// @return `true` if commit bypasses the memtable
	public boolean getCommitBypassMemtable() {
		try {
			return RocksDB.fromByte((byte) MH_GET_COMMIT_BYPASS_MEMTABLE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getCommitBypassMemtable failed", t);
		}
	}

	/// Number of keys above which this transaction is treated as "large" for commit
	/// optimization purposes. `0` disables the optimization. Default: 0.
	///
	/// @param largeTxnCommitOptimizeThreshold key-count threshold; `0` disables the optimization
	/// @return this instance for chaining
	public TransactionOptions setLargeTxnCommitOptimizeThreshold(int largeTxnCommitOptimizeThreshold) {
		try {
			MH_SET_LARGE_TXN_COMMIT_OPTIMIZE_THRESHOLD.invokeExact(ptr(), largeTxnCommitOptimizeThreshold);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setLargeTxnCommitOptimizeThreshold failed", t);
		}
		return this;
	}

	/// Returns the key-count threshold above which this transaction is treated as "large".
	///
	/// @return key-count threshold; `0` means the optimization is disabled
	public int getLargeTxnCommitOptimizeThreshold() {
		try {
			return (int) MH_GET_LARGE_TXN_COMMIT_OPTIMIZE_THRESHOLD.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDBException.wrap("getLargeTxnCommitOptimizeThreshold failed", t);
		}
	}

	/// Total byte size above which this transaction is treated as "large" for commit
	/// optimization purposes. [MemorySize#ZERO] disables the optimization. Default:
	/// [MemorySize#ZERO].
	///
	/// @param largeTxnCommitOptimizeByteThreshold byte-size threshold; [MemorySize#ZERO]
	///                                            disables the optimization
	/// @return this instance for chaining
	public TransactionOptions setLargeTxnCommitOptimizeByteThreshold(MemorySize largeTxnCommitOptimizeByteThreshold) {
		try {
			MH_SET_LARGE_TXN_COMMIT_OPTIMIZE_BYTE_THRESHOLD.invokeExact(ptr(), largeTxnCommitOptimizeByteThreshold.toBytes());
		} catch (Throwable t) {
			throw RocksDBException.wrap("setLargeTxnCommitOptimizeByteThreshold failed", t);
		}
		return this;
	}

	/// Returns the byte-size threshold above which this transaction is treated as "large".
	///
	/// @return byte-size threshold; [MemorySize#ZERO] means the optimization is disabled
	public MemorySize getLargeTxnCommitOptimizeByteThreshold() {
		try {
			return MemorySize.ofBytes((long) MH_GET_LARGE_TXN_COMMIT_OPTIMIZE_BYTE_THRESHOLD.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDBException.wrap("getLargeTxnCommitOptimizeByteThreshold failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
