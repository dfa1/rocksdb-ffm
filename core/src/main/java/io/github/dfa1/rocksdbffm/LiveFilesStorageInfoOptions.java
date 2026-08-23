package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// Options controlling [MonitoringOperations#getLiveFilesStorageInfo(LiveFilesStorageInfoOptions)].
///
/// ```
/// try (var opts = LiveFilesStorageInfoOptions.create().setIncludeChecksumInfo(true);
///      var info = db.getLiveFilesStorageInfo(opts)) {
///     // ...
/// }
/// ```
public final class LiveFilesStorageInfoOptions extends NativeObject {

	/// `rocksdb_livefiles_storage_info_options_t* rocksdb_livefiles_storage_info_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_livefiles_storage_info_options_destroy(rocksdb_livefiles_storage_info_options_t* options);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_livefiles_storage_info_options_set_include_checksum_info(rocksdb_livefiles_storage_info_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_INCLUDE_CHECKSUM_INFO;
	/// `unsigned char rocksdb_livefiles_storage_info_options_get_include_checksum_info(rocksdb_livefiles_storage_info_options_t* opt);`
	private static final MethodHandle MH_GET_INCLUDE_CHECKSUM_INFO;
	/// `void rocksdb_livefiles_storage_info_options_set_wal_size_for_flush(rocksdb_livefiles_storage_info_options_t* opt, uint64_t v);`
	private static final MethodHandle MH_SET_WAL_SIZE_FOR_FLUSH;
	/// `uint64_t rocksdb_livefiles_storage_info_options_get_wal_size_for_flush(rocksdb_livefiles_storage_info_options_t* opt);`
	private static final MethodHandle MH_GET_WAL_SIZE_FOR_FLUSH;
	/// `void rocksdb_livefiles_storage_info_options_set_atomic_flush(rocksdb_livefiles_storage_info_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_ATOMIC_FLUSH;
	/// `unsigned char rocksdb_livefiles_storage_info_options_get_atomic_flush(rocksdb_livefiles_storage_info_options_t* opt);`
	private static final MethodHandle MH_GET_ATOMIC_FLUSH;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_livefiles_storage_info_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_livefiles_storage_info_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_INCLUDE_CHECKSUM_INFO = NativeLibrary.lookup(
				"rocksdb_livefiles_storage_info_options_set_include_checksum_info",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_INCLUDE_CHECKSUM_INFO = NativeLibrary.lookup(
				"rocksdb_livefiles_storage_info_options_get_include_checksum_info",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_WAL_SIZE_FOR_FLUSH = NativeLibrary.lookup(
				"rocksdb_livefiles_storage_info_options_set_wal_size_for_flush",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_WAL_SIZE_FOR_FLUSH = NativeLibrary.lookup(
				"rocksdb_livefiles_storage_info_options_get_wal_size_for_flush",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_ATOMIC_FLUSH = NativeLibrary.lookup(
				"rocksdb_livefiles_storage_info_options_set_atomic_flush",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ATOMIC_FLUSH = NativeLibrary.lookup(
				"rocksdb_livefiles_storage_info_options_get_atomic_flush",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));
	}

	private LiveFilesStorageInfoOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a new [LiveFilesStorageInfoOptions] with default settings.
	///
	/// @return a new [LiveFilesStorageInfoOptions]; caller must close it
	public static LiveFilesStorageInfoOptions create() {
		try {
			return new LiveFilesStorageInfoOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("LiveFilesStorageInfoOptions create failed", t);
		}
	}

	/// If `true`, populates each entry's checksum and checksum-function-name fields.
	/// Default: `false`.
	///
	/// @param value `true` to compute and include per-file checksums
	/// @return `this` for chaining
	public LiveFilesStorageInfoOptions setIncludeChecksumInfo(boolean value) {
		try {
			MH_SET_INCLUDE_CHECKSUM_INFO.invokeExact(ptr(), RocksDB.toByte(value));
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIncludeChecksumInfo failed", t);
		}
	}

	/// Returns `true` if per-file checksums will be included.
	///
	/// @return `true` if checksum info is included
	public boolean isIncludeChecksumInfo() {
		try {
			return RocksDB.fromByte((byte) MH_GET_INCLUDE_CHECKSUM_INFO.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("isIncludeChecksumInfo failed", t);
		}
	}

	/// Flushes memtables first if the total size of live WAL files is at least this size (and
	/// the database is not read-only). [MemorySize#ZERO] (the default) always forces a flush
	/// without checking WAL size first.
	///
	/// @param size WAL-size flush threshold; [MemorySize#ZERO] always flushes
	/// @return `this` for chaining
	public LiveFilesStorageInfoOptions setWalSizeForFlush(MemorySize size) {
		try {
			MH_SET_WAL_SIZE_FOR_FLUSH.invokeExact(ptr(), size.toBytes());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setWalSizeForFlush failed", t);
		}
	}

	/// Returns the configured WAL-size flush threshold.
	///
	/// @return the WAL-size flush threshold
	public MemorySize getWalSizeForFlush() {
		try {
			return MemorySize.ofBytes((long) MH_GET_WAL_SIZE_FOR_FLUSH.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getWalSizeForFlush failed", t);
		}
	}

	/// If `true`, flushes all column families atomically when a flush is performed —
	/// regardless of the database's own `atomic_flush` option — so the captured file list
	/// reflects a consistent view across column families. Only takes effect when a flush
	/// actually happens (see [#setWalSizeForFlush]). Default: `false`.
	///
	/// @param value `true` to force an atomic flush across column families
	/// @return `this` for chaining
	public LiveFilesStorageInfoOptions setAtomicFlush(boolean value) {
		try {
			MH_SET_ATOMIC_FLUSH.invokeExact(ptr(), RocksDB.toByte(value));
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setAtomicFlush failed", t);
		}
	}

	/// Returns `true` if an atomic flush across column families will be forced.
	///
	/// @return `true` if atomic flush is forced
	public boolean isAtomicFlush() {
		try {
			return RocksDB.fromByte((byte) MH_GET_ATOMIC_FLUSH.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("isAtomicFlush failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
