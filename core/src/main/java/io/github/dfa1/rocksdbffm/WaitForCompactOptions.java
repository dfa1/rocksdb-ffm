package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.time.Duration;

/// Options controlling the behavior of [ReadWriteDB#waitForCompact].
///
/// ```
/// try (WaitForCompactOptions opts = WaitForCompactOptions.create()
///         .setFlush(true)
///         .setTimeout(Duration.ofSeconds(30))) {
///     db.waitForCompact(opts);
/// }
/// ```
public final class WaitForCompactOptions extends AbstractOptions {

	/// `rocksdb_wait_for_compact_options_t* rocksdb_wait_for_compact_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_wait_for_compact_options_destroy(rocksdb_wait_for_compact_options_t* opt);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_wait_for_compact_options_set_abort_on_pause(rocksdb_wait_for_compact_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_ABORT_ON_PAUSE;
	/// `unsigned char rocksdb_wait_for_compact_options_get_abort_on_pause(rocksdb_wait_for_compact_options_t* opt);`
	private static final MethodHandle MH_GET_ABORT_ON_PAUSE;
	/// `void rocksdb_wait_for_compact_options_set_flush(rocksdb_wait_for_compact_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_FLUSH;
	/// `unsigned char rocksdb_wait_for_compact_options_get_flush(rocksdb_wait_for_compact_options_t* opt);`
	private static final MethodHandle MH_GET_FLUSH;
	/// `void rocksdb_wait_for_compact_options_set_close_db(rocksdb_wait_for_compact_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_CLOSE_DB;
	/// `unsigned char rocksdb_wait_for_compact_options_get_close_db(rocksdb_wait_for_compact_options_t* opt);`
	private static final MethodHandle MH_GET_CLOSE_DB;
	/// `void rocksdb_wait_for_compact_options_set_timeout(rocksdb_wait_for_compact_options_t* opt, uint64_t microseconds);`
	private static final MethodHandle MH_SET_TIMEOUT;
	/// `uint64_t rocksdb_wait_for_compact_options_get_timeout(rocksdb_wait_for_compact_options_t* opt);`
	private static final MethodHandle MH_GET_TIMEOUT;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_wait_for_compact_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_wait_for_compact_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_ABORT_ON_PAUSE = NativeLibrary.lookup("rocksdb_wait_for_compact_options_set_abort_on_pause",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ABORT_ON_PAUSE = NativeLibrary.lookup("rocksdb_wait_for_compact_options_get_abort_on_pause",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_FLUSH = NativeLibrary.lookup("rocksdb_wait_for_compact_options_set_flush",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_FLUSH = NativeLibrary.lookup("rocksdb_wait_for_compact_options_get_flush",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_CLOSE_DB = NativeLibrary.lookup("rocksdb_wait_for_compact_options_set_close_db",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_CLOSE_DB = NativeLibrary.lookup("rocksdb_wait_for_compact_options_get_close_db",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_TIMEOUT = NativeLibrary.lookup("rocksdb_wait_for_compact_options_set_timeout",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_TIMEOUT = NativeLibrary.lookup("rocksdb_wait_for_compact_options_get_timeout",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
	}

	private WaitForCompactOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a new [WaitForCompactOptions] with default settings.
	///
	/// @return a new [WaitForCompactOptions]; caller must close it
	public static WaitForCompactOptions create() {
		try {
			return new WaitForCompactOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("WaitForCompactOptions create failed", t);
		}
	}

	/// If `true`, [ReadWriteDB#waitForCompact] throws [RocksDBException] when background work
	/// is paused rather than blocking indefinitely.
	/// Default: `false`.
	///
	/// @param value `true` to abort on pause instead of blocking
	/// @return `this` for chaining
	public WaitForCompactOptions setAbortOnPause(boolean value) {
		setBoolean(MH_SET_ABORT_ON_PAUSE, value);
		return this;
	}

	/// Returns `true` if the wait will abort when background work is paused.
	///
	/// @return `true` if abort-on-pause is enabled
	public boolean isAbortOnPause() {
		return getBoolean(MH_GET_ABORT_ON_PAUSE);
	}

	/// If `true`, triggers a memtable flush before waiting for compaction.
	/// Default: `false`.
	///
	/// @param value `true` to flush before waiting
	/// @return `this` for chaining
	public WaitForCompactOptions setFlush(boolean value) {
		setBoolean(MH_SET_FLUSH, value);
		return this;
	}

	/// Returns `true` if a memtable flush will be triggered before waiting.
	///
	/// @return `true` if flush-before-wait is enabled
	public boolean isFlush() {
		return getBoolean(MH_GET_FLUSH);
	}

	/// If `true`, closes the DB after all compactions finish.
	/// Default: `false`.
	///
	/// @param value `true` to close the DB once compaction is complete
	/// @return `this` for chaining
	public WaitForCompactOptions setCloseDb(boolean value) {
		setBoolean(MH_SET_CLOSE_DB, value);
		return this;
	}

	/// Returns `true` if the DB will be closed after compaction finishes.
	///
	/// @return `true` if close-after-compact is enabled
	public boolean isCloseDb() {
		return getBoolean(MH_GET_CLOSE_DB);
	}

	/// Maximum time to wait. [Duration#ZERO] means no timeout.
	/// Default: no timeout.
	///
	/// @param timeout maximum wait duration; [Duration#ZERO] disables the timeout
	/// @return `this` for chaining
	public WaitForCompactOptions setTimeout(Duration timeout) {
		setLong(MH_SET_TIMEOUT, timeout.toNanos() / 1_000L);
		return this;
	}

	/// Returns the maximum wait duration. [Duration#ZERO] means no timeout.
	///
	/// @return configured timeout duration
	public Duration getTimeout() {
		return Duration.ofNanos(getLong(MH_GET_TIMEOUT) * 1000L);
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
