package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_compactoptions_t`.
///
/// ```
/// try (var opts = CompactOptions.newCompactOptions().setChangeLevel(true).setTargetLevel(2)) {
///     db.compactRange(opts, null, null);
/// }
/// ```
public final class CompactOptions extends AbstractOptions {

	/// `rocksdb_compactoptions_t* rocksdb_compactoptions_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_compactoptions_destroy(rocksdb_compactoptions_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_compactoptions_set_exclusive_manual_compaction(rocksdb_compactoptions_t*, unsigned char);`
	private static final MethodHandle MH_SET_EXCLUSIVE;
	/// `unsigned char rocksdb_compactoptions_get_exclusive_manual_compaction(rocksdb_compactoptions_t*);`
	private static final MethodHandle MH_GET_EXCLUSIVE;
	/// `void rocksdb_compactoptions_set_bottommost_level_compaction(rocksdb_compactoptions_t*, unsigned char);`
	private static final MethodHandle MH_SET_BOTTOMMOST;
	/// `unsigned char rocksdb_compactoptions_get_bottommost_level_compaction(rocksdb_compactoptions_t*);`
	private static final MethodHandle MH_GET_BOTTOMMOST;
	/// `void rocksdb_compactoptions_set_change_level(rocksdb_compactoptions_t*, unsigned char);`
	private static final MethodHandle MH_SET_CHANGE_LEVEL;
	/// `unsigned char rocksdb_compactoptions_get_change_level(rocksdb_compactoptions_t*);`
	private static final MethodHandle MH_GET_CHANGE_LEVEL;
	/// `void rocksdb_compactoptions_set_target_level(rocksdb_compactoptions_t*, int);`
	private static final MethodHandle MH_SET_TARGET_LEVEL;
	/// `int rocksdb_compactoptions_get_target_level(rocksdb_compactoptions_t*);`
	private static final MethodHandle MH_GET_TARGET_LEVEL;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_compactoptions_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_compactoptions_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_EXCLUSIVE = NativeLibrary.lookup(
				"rocksdb_compactoptions_set_exclusive_manual_compaction",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_EXCLUSIVE = NativeLibrary.lookup(
				"rocksdb_compactoptions_get_exclusive_manual_compaction",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_BOTTOMMOST = NativeLibrary.lookup(
				"rocksdb_compactoptions_set_bottommost_level_compaction",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_BOTTOMMOST = NativeLibrary.lookup(
				"rocksdb_compactoptions_get_bottommost_level_compaction",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_CHANGE_LEVEL = NativeLibrary.lookup(
				"rocksdb_compactoptions_set_change_level",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_CHANGE_LEVEL = NativeLibrary.lookup(
				"rocksdb_compactoptions_get_change_level",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_TARGET_LEVEL = NativeLibrary.lookup(
				"rocksdb_compactoptions_set_target_level",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_TARGET_LEVEL = NativeLibrary.lookup(
				"rocksdb_compactoptions_get_target_level",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	}

	private CompactOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a [CompactOptions] with RocksDB defaults.
	///
	/// @return a new [CompactOptions] instance; caller must close it
	public static CompactOptions newCompactOptions() {
		MemorySegment result;
		try {
			result = (MemorySegment) MH_CREATE.invokeExact();
		} catch (Throwable e) {
			throw RocksDB.wrapInvokeFailure(e.getMessage(), e);
		}
		return new CompactOptions(result);
	}

	/// If `true`, no other manual compaction will run in parallel.
	/// Default: `true`.
	///
	/// @param value `true` to prevent concurrent manual compactions
	/// @return `this` for chaining
	public CompactOptions setExclusiveManualCompaction(boolean value) {
		setBoolean(MH_SET_EXCLUSIVE, value);
		return this;
	}

	/// Returns whether exclusive manual compaction is enabled.
	///
	/// @return `true` if no concurrent manual compactions are allowed
	public boolean isExclusiveManualCompaction() {
		return getBoolean(MH_GET_EXCLUSIVE);
	}

	/// The native field is RocksDB's 4-valued `BottommostLevelCompaction` enum
	/// (`kSkip`=0, `kIfHaveCompactionFilter`=1, `kForce`=2, `kForceOptimized`=3), not a plain
	/// bool — this API only exposes the two ends callers actually need. `RocksDB.toByte`/
	/// `fromByte` (0/1) must not be used to read or write it: `true` written as `1` lands on
	/// `kIfHaveCompactionFilter`, which silently skips bottommost recompaction whenever no
	/// compaction filter is configured, instead of forcing it as documented.
	private static final byte BOTTOMMOST_SKIP = 0;
	private static final byte BOTTOMMOST_FORCE = 2;

	/// If `true`, the compaction will compact all data at the bottommost level, even files
	/// that would otherwise be left untouched (e.g. a single file with no overlapping input).
	/// Default: `false`.
	///
	/// @param value `true` to force bottommost-level compaction
	/// @return `this` for chaining
	public CompactOptions setBottommostLevelCompaction(boolean value) {
		try {
			MH_SET_BOTTOMMOST.invokeExact(ptr(), value ? BOTTOMMOST_FORCE : BOTTOMMOST_SKIP);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setBottommostLevelCompaction failed", t);
		}
		return this;
	}

	/// Returns whether bottommost-level compaction is forced.
	///
	/// @return `true` if all data will be compacted at the bottommost level
	public boolean isBottommostLevelCompaction() {
		try {
			return (byte) MH_GET_BOTTOMMOST.invokeExact(ptr()) == BOTTOMMOST_FORCE;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("isBottommostLevelCompaction failed", t);
		}
	}

	/// If `true`, compacted output will be moved to the level set by
	/// [#setTargetLevel(int)].
	/// Default: `false`.
	///
	/// @param value `true` to move compacted output to the target level
	/// @return `this` for chaining
	public CompactOptions setChangeLevel(boolean value) {
		setBoolean(MH_SET_CHANGE_LEVEL, value);
		return this;
	}

	/// Returns whether compacted output will be moved to the target level.
	///
	/// @return `true` if level change is enabled
	public boolean isChangeLevel() {
		return getBoolean(MH_GET_CHANGE_LEVEL);
	}

	/// Target output level for the compaction when [#setChangeLevel(boolean)] is
	/// `true`. `-1` means the bottommost level. Default: `-1`.
	///
	/// @param level target level index, or `-1` for the bottommost level
	/// @return `this` for chaining
	public CompactOptions setTargetLevel(int level) {
		setInt(MH_SET_TARGET_LEVEL, level);
		return this;
	}

	/// Returns the target level for compacted output.
	///
	/// @return target level index, or `-1` for the bottommost level
	public int getTargetLevel() {
		return getInt(MH_GET_TARGET_LEVEL);
	}

	@Override
	public void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
