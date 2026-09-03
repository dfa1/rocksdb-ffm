package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_sst_file_manager_t`.
///
/// Tracks SST files on disk, enforces disk-space limits, and rate-limits
/// the deletion of obsolete SST files (trash files).
///
/// ```
/// try (Env env = Env.defaultEnv();
///      SstFileManager sfm = SstFileManager.create(env)
///          .setMaxAllowedSpaceUsage(MemorySize.ofGB(10))
///          .setDeleteRateBytesPerSecond(MemorySize.ofMB(64));
///      Options opts = Options.newOptions()
///          .setCreateIfMissing(true)
///          .setSstFileManager(sfm)) {
///     var db = RocksDB.openReadWrite(opts, path);
/// }
/// ```
///
/// ## Lifecycle
///
/// [SstFileManager] uses shared ownership: passing it to
/// [Options#setSstFileManager] does not transfer ownership — both objects
/// may be closed independently. The [Env] passed to [#create(Env)] must
/// remain open for the lifetime of the manager.
public final class SstFileManager extends NativeObject {

	/// `rocksdb_sst_file_manager_t* rocksdb_sst_file_manager_create(rocksdb_env_t* env);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_sst_file_manager_destroy(rocksdb_sst_file_manager_t* sfm);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_sst_file_manager_set_max_allowed_space_usage(rocksdb_sst_file_manager_t* sfm, uint64_t max_allowed_space);`
	private static final MethodHandle MH_SET_MAX_ALLOWED_SPACE_USAGE;
	/// `void rocksdb_sst_file_manager_set_compaction_buffer_size(rocksdb_sst_file_manager_t* sfm, uint64_t compaction_buffer_size);`
	private static final MethodHandle MH_SET_COMPACTION_BUFFER_SIZE;
	/// `bool rocksdb_sst_file_manager_is_max_allowed_space_reached(rocksdb_sst_file_manager_t* sfm);`
	private static final MethodHandle MH_IS_MAX_ALLOWED_SPACE_REACHED;
	/// `bool rocksdb_sst_file_manager_is_max_allowed_space_reached_including_compactions(rocksdb_sst_file_manager_t* sfm);`
	private static final MethodHandle MH_IS_MAX_ALLOWED_SPACE_REACHED_INCLUDING_COMPACTIONS;
	/// `uint64_t rocksdb_sst_file_manager_get_total_size(rocksdb_sst_file_manager_t* sfm);`
	private static final MethodHandle MH_GET_TOTAL_SIZE;
	/// `int64_t rocksdb_sst_file_manager_get_delete_rate_bytes_per_second(rocksdb_sst_file_manager_t* sfm);`
	private static final MethodHandle MH_GET_DELETE_RATE_BYTES_PER_SECOND;
	/// `void rocksdb_sst_file_manager_set_delete_rate_bytes_per_second(rocksdb_sst_file_manager_t* sfm, int64_t delete_rate);`
	private static final MethodHandle MH_SET_DELETE_RATE_BYTES_PER_SECOND;
	/// `double rocksdb_sst_file_manager_get_max_trash_db_ratio(rocksdb_sst_file_manager_t* sfm);`
	private static final MethodHandle MH_GET_MAX_TRASH_DB_RATIO;
	/// `void rocksdb_sst_file_manager_set_max_trash_db_ratio(rocksdb_sst_file_manager_t* sfm, double ratio);`
	private static final MethodHandle MH_SET_MAX_TRASH_DB_RATIO;
	/// `uint64_t rocksdb_sst_file_manager_get_total_trash_size(rocksdb_sst_file_manager_t* sfm);`
	private static final MethodHandle MH_GET_TOTAL_TRASH_SIZE;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_sst_file_manager_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_sst_file_manager_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_MAX_ALLOWED_SPACE_USAGE = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_set_max_allowed_space_usage",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_SET_COMPACTION_BUFFER_SIZE = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_set_compaction_buffer_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_IS_MAX_ALLOWED_SPACE_REACHED = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_is_max_allowed_space_reached",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_IS_MAX_ALLOWED_SPACE_REACHED_INCLUDING_COMPACTIONS = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_is_max_allowed_space_reached_including_compactions",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_GET_TOTAL_SIZE = NativeLibrary.lookup("rocksdb_sst_file_manager_get_total_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_GET_DELETE_RATE_BYTES_PER_SECOND = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_get_delete_rate_bytes_per_second",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_DELETE_RATE_BYTES_PER_SECOND = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_set_delete_rate_bytes_per_second",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_TRASH_DB_RATIO = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_get_max_trash_db_ratio",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

		MH_SET_MAX_TRASH_DB_RATIO = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_set_max_trash_db_ratio",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_TOTAL_TRASH_SIZE = NativeLibrary.lookup(
				"rocksdb_sst_file_manager_get_total_trash_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
	}

	private SstFileManager(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates an SST file manager using the provided environment.
	///
	/// The [Env] must remain open for the lifetime of this manager.
	///
	/// @param env the [Env] to use for file-system operations; must outlive this manager
	/// @return a new [SstFileManager]; caller must close it
	public static SstFileManager create(Env env) {
		try {
			MemorySegment ptr = (MemorySegment) MH_CREATE.invokeExact(env.ptr());
			return new SstFileManager(ptr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("SstFileManager create failed", t);
		}
	}

	/// Sets the maximum total disk space that SST files may occupy.
	///
	/// Once reached, [#isMaxAllowedSpaceReached()] returns `true` and
	/// RocksDB will stop accepting writes. A value of `0` means no limit.
	///
	/// @param maxAllowedSpace maximum allowed space; `0` means no limit
	/// @return this instance for chaining
	public SstFileManager setMaxAllowedSpaceUsage(MemorySize maxAllowedSpace) {
		NativeFields.setMemorySize(MH_SET_MAX_ALLOWED_SPACE_USAGE, ptr(), maxAllowedSpace);
		return this;
	}

	/// Sets additional buffer space reserved during compaction on top of
	/// [#setMaxAllowedSpaceUsage]. Compactions that would exceed
	/// `maxAllowedSpace - compactionBufferSize` are blocked.
	///
	/// @param compactionBufferSize buffer space to reserve during compaction
	/// @return this instance for chaining
	public SstFileManager setCompactionBufferSize(MemorySize compactionBufferSize) {
		NativeFields.setMemorySize(MH_SET_COMPACTION_BUFFER_SIZE, ptr(), compactionBufferSize);
		return this;
	}

	/// Returns `true` if total SST file usage has reached the configured maximum.
	///
	/// @return `true` if the space limit has been reached
	public boolean isMaxAllowedSpaceReached() {
		return NativeFields.getBoolean(MH_IS_MAX_ALLOWED_SPACE_REACHED, ptr());
	}

	/// Returns `true` if total SST file usage including in-progress compaction
	/// output has reached the configured maximum.
	///
	/// @return `true` if the space limit has been reached including compaction output
	public boolean isMaxAllowedSpaceReachedIncludingCompactions() {
		return NativeFields.getBoolean(MH_IS_MAX_ALLOWED_SPACE_REACHED_INCLUDING_COMPACTIONS, ptr());
	}

	/// Returns the total size (in bytes) of all tracked SST files.
	///
	/// @return total size of all tracked SST files
	public MemorySize getTotalSize() {
		return NativeFields.getMemorySize(MH_GET_TOTAL_SIZE, ptr());
	}

	/// Returns the total size (in bytes) of all trash (pending-delete) SST files.
	///
	/// @return total size of all pending-delete SST files
	public MemorySize getTotalTrashSize() {
		return NativeFields.getMemorySize(MH_GET_TOTAL_TRASH_SIZE, ptr());
	}

	/// Returns the trash-file deletion rate.
	///
	/// @return the deletion rate, [MemorySize#ZERO] for synchronous deletion, or `null` if unlimited
	public MemorySize getDeleteRateBytesPerSecond() {
		long rate = NativeFields.getLong(MH_GET_DELETE_RATE_BYTES_PER_SECOND, ptr());
		return rate < 0 ? null : MemorySize.ofBytes(rate);
	}

	/// Sets the rate at which trash SST files are deleted.
	///
	/// Pass `null` to delete as fast as possible (no rate limit). Pass [MemorySize#ZERO] to
	/// disable background deletion entirely (files are removed synchronously) — per
	/// `delete_scheduler.cc`, RocksDB itself treats `null` and [MemorySize#ZERO] identically
	/// (no throttling, no background thread), so the distinction is purely about intent at
	/// the call site.
	///
	/// @param deleteRate deletion rate, [MemorySize#ZERO] for synchronous, or `null` for unlimited
	/// @return this instance for chaining
	public SstFileManager setDeleteRateBytesPerSecond(MemorySize deleteRate) {
		NativeFields.setLong(MH_SET_DELETE_RATE_BYTES_PER_SECOND, ptr(), deleteRate == null ? -1L : deleteRate.toBytes());
		return this;
	}

	/// Returns the maximum ratio of trash to total SST files before RocksDB
	/// deletes trash files synchronously instead of in the background.
	/// Default is `0.25`.
	///
	/// @return current max trash-to-total ratio
	public Ratio getMaxTrashDbRatio() {
		return Ratio.of(NativeFields.getDouble(MH_GET_MAX_TRASH_DB_RATIO, ptr()));
	}

	/// Sets the maximum ratio of trash to total SST files.
	///
	/// When the ratio exceeds this threshold, deletions become synchronous.
	///
	/// @param ratio max trash-to-total ratio
	/// @return this instance for chaining
	public SstFileManager setMaxTrashDbRatio(Ratio ratio) {
		NativeFields.setDouble(MH_SET_MAX_TRASH_DB_RATIO, ptr(), ratio.toDouble());
		return this;
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
