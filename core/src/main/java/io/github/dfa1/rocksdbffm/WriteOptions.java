package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_writeoptions_t`.
public final class WriteOptions extends NativeObject {

	/// `rocksdb_writeoptions_t* rocksdb_writeoptions_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_writeoptions_destroy(rocksdb_writeoptions_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_writeoptions_set_sync(rocksdb_writeoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_SYNC;
	/// `unsigned char rocksdb_writeoptions_get_sync(rocksdb_writeoptions_t* opt);`
	private static final MethodHandle MH_GET_SYNC;
	/// `void rocksdb_writeoptions_disable_WAL(rocksdb_writeoptions_t* opt, int v);`
	private static final MethodHandle MH_DISABLE_WAL;
	/// `unsigned char rocksdb_writeoptions_get_disable_WAL(rocksdb_writeoptions_t* opt);`
	private static final MethodHandle MH_GET_DISABLE_WAL;
	/// `void rocksdb_writeoptions_set_ignore_missing_column_families(rocksdb_writeoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_IGNORE_MISSING_COLUMN_FAMILIES;
	/// `unsigned char rocksdb_writeoptions_get_ignore_missing_column_families(rocksdb_writeoptions_t* opt);`
	private static final MethodHandle MH_GET_IGNORE_MISSING_COLUMN_FAMILIES;
	/// `void rocksdb_writeoptions_set_no_slowdown(rocksdb_writeoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_NO_SLOWDOWN;
	/// `unsigned char rocksdb_writeoptions_get_no_slowdown(rocksdb_writeoptions_t* opt);`
	private static final MethodHandle MH_GET_NO_SLOWDOWN;
	/// `void rocksdb_writeoptions_set_low_pri(rocksdb_writeoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_LOW_PRI;
	/// `unsigned char rocksdb_writeoptions_get_low_pri(rocksdb_writeoptions_t* opt);`
	private static final MethodHandle MH_GET_LOW_PRI;
	/// `void rocksdb_writeoptions_set_memtable_insert_hint_per_batch(rocksdb_writeoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_MEMTABLE_INSERT_HINT_PER_BATCH;
	/// `unsigned char rocksdb_writeoptions_get_memtable_insert_hint_per_batch(rocksdb_writeoptions_t* opt);`
	private static final MethodHandle MH_GET_MEMTABLE_INSERT_HINT_PER_BATCH;
	/// `void rocksdb_writeoptions_set_rate_limiter_priority(rocksdb_writeoptions_t* opt, int v);`
	private static final MethodHandle MH_SET_RATE_LIMITER_PRIORITY;
	/// `int rocksdb_writeoptions_get_rate_limiter_priority(rocksdb_writeoptions_t* opt);`
	private static final MethodHandle MH_GET_RATE_LIMITER_PRIORITY;
	/// `void rocksdb_writeoptions_set_io_activity(rocksdb_writeoptions_t* opt, int v);`
	private static final MethodHandle MH_SET_IO_ACTIVITY;
	/// `int rocksdb_writeoptions_get_io_activity(rocksdb_writeoptions_t* opt);`
	private static final MethodHandle MH_GET_IO_ACTIVITY;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_writeoptions_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_writeoptions_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_SYNC = NativeLibrary.lookup("rocksdb_writeoptions_set_sync",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_SYNC = NativeLibrary.lookup("rocksdb_writeoptions_get_sync",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_DISABLE_WAL = NativeLibrary.lookup("rocksdb_writeoptions_disable_WAL",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_DISABLE_WAL = NativeLibrary.lookup("rocksdb_writeoptions_get_disable_WAL",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_IGNORE_MISSING_COLUMN_FAMILIES = NativeLibrary.lookup(
				"rocksdb_writeoptions_set_ignore_missing_column_families",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_IGNORE_MISSING_COLUMN_FAMILIES = NativeLibrary.lookup(
				"rocksdb_writeoptions_get_ignore_missing_column_families",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_NO_SLOWDOWN = NativeLibrary.lookup("rocksdb_writeoptions_set_no_slowdown",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_NO_SLOWDOWN = NativeLibrary.lookup("rocksdb_writeoptions_get_no_slowdown",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_LOW_PRI = NativeLibrary.lookup("rocksdb_writeoptions_set_low_pri",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_LOW_PRI = NativeLibrary.lookup("rocksdb_writeoptions_get_low_pri",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_MEMTABLE_INSERT_HINT_PER_BATCH = NativeLibrary.lookup(
				"rocksdb_writeoptions_set_memtable_insert_hint_per_batch",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_MEMTABLE_INSERT_HINT_PER_BATCH = NativeLibrary.lookup(
				"rocksdb_writeoptions_get_memtable_insert_hint_per_batch",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_RATE_LIMITER_PRIORITY = NativeLibrary.lookup("rocksdb_writeoptions_set_rate_limiter_priority",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_RATE_LIMITER_PRIORITY = NativeLibrary.lookup("rocksdb_writeoptions_get_rate_limiter_priority",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_IO_ACTIVITY = NativeLibrary.lookup("rocksdb_writeoptions_set_io_activity",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_IO_ACTIVITY = NativeLibrary.lookup("rocksdb_writeoptions_get_io_activity",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	}

	private WriteOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a new [WriteOptions] with default settings.
	///
	/// @return a new [WriteOptions]; caller must close it
	public static WriteOptions newWriteOptions() {
		try {
			return new WriteOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDBException.wrap("writeoptions create failed", t);
		}
	}

	/// If `true`, the write waits for the write to be flushed to the OS's page cache and
	/// fsync'd to disk before returning. Default: `false`.
	///
	/// @param sync `true` to fsync before returning
	/// @return this instance for chaining
	public WriteOptions setSync(boolean sync) {
		try {
			MH_SET_SYNC.invokeExact(ptr(), sync ? (byte) 1 : (byte) 0);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setSync failed", t);
		}
		return this;
	}

	/// Returns whether this write fsyncs before returning.
	///
	/// @return `true` if fsync is enabled
	public boolean isSync() {
		try {
			return (byte) MH_GET_SYNC.invokeExact(ptr()) != 0;
		} catch (Throwable t) {
			throw RocksDBException.wrap("isSync failed", t);
		}
	}

	/// If `true`, writes bypass the write-ahead log entirely: faster, but a crash before the
	/// next flush loses them. Default: `false`.
	///
	/// @param disableWal `true` to skip the WAL
	/// @return this instance for chaining
	public WriteOptions setDisableWal(boolean disableWal) {
		try {
			MH_DISABLE_WAL.invokeExact(ptr(), disableWal ? 1 : 0);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setDisableWal failed", t);
		}
		return this;
	}

	/// Returns whether this write bypasses the WAL.
	///
	/// @return `true` if the WAL is skipped
	public boolean isDisableWal() {
		try {
			return (byte) MH_GET_DISABLE_WAL.invokeExact(ptr()) != 0;
		} catch (Throwable t) {
			throw RocksDBException.wrap("isDisableWal failed", t);
		}
	}

	/// If `true`, a `put`/`delete` targeting a column family dropped since the handle was
	/// obtained is silently ignored instead of failing. Default: `false`.
	///
	/// @param ignoreMissingColumnFamilies `true` to ignore a dropped column family
	/// @return this instance for chaining
	public WriteOptions setIgnoreMissingColumnFamilies(boolean ignoreMissingColumnFamilies) {
		try {
			MH_SET_IGNORE_MISSING_COLUMN_FAMILIES.invokeExact(ptr(),
					ignoreMissingColumnFamilies ? (byte) 1 : (byte) 0);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setIgnoreMissingColumnFamilies failed", t);
		}
		return this;
	}

	/// Returns whether a dropped column family is silently ignored rather than failing the write.
	///
	/// @return `true` if a missing column family is ignored
	public boolean isIgnoreMissingColumnFamilies() {
		try {
			return (byte) MH_GET_IGNORE_MISSING_COLUMN_FAMILIES.invokeExact(ptr()) != 0;
		} catch (Throwable t) {
			throw RocksDBException.wrap("isIgnoreMissingColumnFamilies failed", t);
		}
	}

	/// If `true`, the write fails immediately with an error instead of blocking when it would
	/// otherwise stall (e.g. too many memtables queued for flush). Default: `false`.
	///
	/// @param noSlowdown `true` to fail fast instead of blocking on a write stall
	/// @return this instance for chaining
	public WriteOptions setNoSlowdown(boolean noSlowdown) {
		try {
			MH_SET_NO_SLOWDOWN.invokeExact(ptr(), noSlowdown ? (byte) 1 : (byte) 0);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setNoSlowdown failed", t);
		}
		return this;
	}

	/// Returns whether this write fails fast instead of blocking on a write stall.
	///
	/// @return `true` if the write fails fast rather than blocking
	public boolean isNoSlowdown() {
		try {
			return (byte) MH_GET_NO_SLOWDOWN.invokeExact(ptr()) != 0;
		} catch (Throwable t) {
			throw RocksDBException.wrap("isNoSlowdown failed", t);
		}
	}

	/// If `true`, marks this write as low priority: throttled ahead of normal-priority writes
	/// whenever [#setNoSlowdown] would otherwise apply. Default: `false`.
	///
	/// @param lowPri `true` to mark this write as low priority
	/// @return this instance for chaining
	public WriteOptions setLowPri(boolean lowPri) {
		try {
			MH_SET_LOW_PRI.invokeExact(ptr(), lowPri ? (byte) 1 : (byte) 0);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setLowPri failed", t);
		}
		return this;
	}

	/// Returns whether this write is marked low priority.
	///
	/// @return `true` if this write is low priority
	public boolean isLowPri() {
		try {
			return (byte) MH_GET_LOW_PRI.invokeExact(ptr()) != 0;
		} catch (Throwable t) {
			throw RocksDBException.wrap("isLowPri failed", t);
		}
	}

	/// If `true`, a hint is inserted into the memtable once per write batch instead of once per
	/// key, trading a small memory-usage increase for faster batched inserts. Default: `false`.
	///
	/// @param memtableInsertHintPerBatch `true` to hint once per batch instead of once per key
	/// @return this instance for chaining
	public WriteOptions setMemtableInsertHintPerBatch(boolean memtableInsertHintPerBatch) {
		try {
			MH_SET_MEMTABLE_INSERT_HINT_PER_BATCH.invokeExact(ptr(),
					memtableInsertHintPerBatch ? (byte) 1 : (byte) 0);
		} catch (Throwable t) {
			throw RocksDBException.wrap("setMemtableInsertHintPerBatch failed", t);
		}
		return this;
	}

	/// Returns whether the memtable insert hint is batched once per write batch.
	///
	/// @return `true` if the hint is inserted once per batch
	public boolean isMemtableInsertHintPerBatch() {
		try {
			return (byte) MH_GET_MEMTABLE_INSERT_HINT_PER_BATCH.invokeExact(ptr()) != 0;
		} catch (Throwable t) {
			throw RocksDBException.wrap("isMemtableInsertHintPerBatch failed", t);
		}
	}

	/// Overrides the rate limiter priority for this write. Only [IOPriority#USER] and
	/// [IOPriority#TOTAL] are valid here (a RocksDB implementation constraint specific to
	/// `WriteOptions`, not a general restriction on [IOPriority]) — this setter does not
	/// validate that itself, but the write call fails with an error later if any other value
	/// was set. Default: [IOPriority#TOTAL].
	///
	/// @param rateLimiterPriority the priority to request bytes at ([IOPriority#USER] or
	///                            [IOPriority#TOTAL] only)
	/// @return this instance for chaining
	public WriteOptions setRateLimiterPriority(IOPriority rateLimiterPriority) {
		try {
			MH_SET_RATE_LIMITER_PRIORITY.invokeExact(ptr(), rateLimiterPriority.getValue());
		} catch (Throwable t) {
			throw RocksDBException.wrap("setRateLimiterPriority failed", t);
		}
		return this;
	}

	/// Returns the configured rate limiter priority.
	///
	/// @return the active [IOPriority]
	public IOPriority getRateLimiterPriority() {
		try {
			int value = (int) MH_GET_RATE_LIMITER_PRIORITY.invokeExact(ptr());
			return IOPriority.fromValue(value);
		} catch (Throwable t) {
			throw RocksDBException.wrap("getRateLimiterPriority failed", t);
		}
	}

	/// Classifies this write's originating operation for I/O tracing. `EXPERIMENTAL` and "for
	/// RocksDB internal use only" upstream — see [IOActivity]. Default: [IOActivity#UNKNOWN].
	///
	/// @param ioActivity the activity to classify this write as
	/// @return this instance for chaining
	public WriteOptions setIoActivity(IOActivity ioActivity) {
		try {
			MH_SET_IO_ACTIVITY.invokeExact(ptr(), ioActivity.getValue());
		} catch (Throwable t) {
			throw RocksDBException.wrap("setIoActivity failed", t);
		}
		return this;
	}

	/// Returns the configured I/O activity classification.
	///
	/// @return the active [IOActivity]
	public IOActivity getIoActivity() {
		try {
			int value = (int) MH_GET_IO_ACTIVITY.invokeExact(ptr());
			return IOActivity.fromValue(value);
		} catch (Throwable t) {
			throw RocksDBException.wrap("getIoActivity failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
