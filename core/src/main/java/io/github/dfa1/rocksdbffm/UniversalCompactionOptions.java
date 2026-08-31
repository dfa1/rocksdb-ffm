package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_universal_compaction_options_t`.
///
/// Attach via [Options#setUniversalCompactionOptions(UniversalCompactionOptions)] after also
/// setting [Options#setCompactionStyle(Options.CompactionStyle)] to
/// [Options.CompactionStyle#UNIVERSAL] -- RocksDB ignores these settings under any other
/// compaction style. `UniversalCompactionOptions` may be closed once attached; RocksDB copies
/// the underlying struct by value.
///
/// ```
/// try (var universal = UniversalCompactionOptions.newUniversalCompactionOptions()
///         .setMaxSizeAmplificationPercent(200);
///      var opts = Options.newOptions()
///          .setCreateIfMissing(true)
///          .setCompactionStyle(Options.CompactionStyle.UNIVERSAL)
///          .setUniversalCompactionOptions(universal)) {
///     ...
/// }
/// ```
public final class UniversalCompactionOptions extends NativeObject {

	/// When a universal compaction run stops adding more files to the batch, per
	/// `universal_compaction.h`'s `CompactionStopStyle`.
	public enum StopStyle {
		/// Stop once the next candidate file's size is not "similar" to the files already
		/// picked (per [#setSizeRatio]).
		SIMILAR_SIZE(0),
		/// Stop once the total size of files already picked exceeds the next candidate file's
		/// size. Default.
		TOTAL_SIZE(1);

		final int value;

		StopStyle(int value) {
			this.value = value;
		}

		static StopStyle fromValue(int value) {
			return switch (value) {
				case 0 -> SIMILAR_SIZE;
				case 1 -> TOTAL_SIZE;
				default -> throw new IllegalArgumentException("Unknown StopStyle value: " + value);
			};
		}
	}

	/// `rocksdb_universal_compaction_options_t* rocksdb_universal_compaction_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_universal_compaction_options_destroy(rocksdb_universal_compaction_options_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_universal_compaction_options_set_size_ratio(rocksdb_universal_compaction_options_t*, int);`
	private static final MethodHandle MH_SET_SIZE_RATIO;
	/// `int rocksdb_universal_compaction_options_get_size_ratio(rocksdb_universal_compaction_options_t*);`
	private static final MethodHandle MH_GET_SIZE_RATIO;
	/// `void rocksdb_universal_compaction_options_set_min_merge_width(rocksdb_universal_compaction_options_t*, int);`
	private static final MethodHandle MH_SET_MIN_MERGE_WIDTH;
	/// `int rocksdb_universal_compaction_options_get_min_merge_width(rocksdb_universal_compaction_options_t*);`
	private static final MethodHandle MH_GET_MIN_MERGE_WIDTH;
	/// `void rocksdb_universal_compaction_options_set_max_merge_width(rocksdb_universal_compaction_options_t*, int);`
	private static final MethodHandle MH_SET_MAX_MERGE_WIDTH;
	/// `int rocksdb_universal_compaction_options_get_max_merge_width(rocksdb_universal_compaction_options_t*);`
	private static final MethodHandle MH_GET_MAX_MERGE_WIDTH;
	/// `void rocksdb_universal_compaction_options_set_max_size_amplification_percent(rocksdb_universal_compaction_options_t*, int);`
	private static final MethodHandle MH_SET_MAX_SIZE_AMPLIFICATION_PERCENT;
	/// `int rocksdb_universal_compaction_options_get_max_size_amplification_percent(rocksdb_universal_compaction_options_t*);`
	private static final MethodHandle MH_GET_MAX_SIZE_AMPLIFICATION_PERCENT;
	/// `void rocksdb_universal_compaction_options_set_compression_size_percent(rocksdb_universal_compaction_options_t*, int);`
	private static final MethodHandle MH_SET_COMPRESSION_SIZE_PERCENT;
	/// `int rocksdb_universal_compaction_options_get_compression_size_percent(rocksdb_universal_compaction_options_t*);`
	private static final MethodHandle MH_GET_COMPRESSION_SIZE_PERCENT;
	/// `void rocksdb_universal_compaction_options_set_stop_style(rocksdb_universal_compaction_options_t*, int);`
	private static final MethodHandle MH_SET_STOP_STYLE;
	/// `int rocksdb_universal_compaction_options_get_stop_style(rocksdb_universal_compaction_options_t*);`
	private static final MethodHandle MH_GET_STOP_STYLE;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_universal_compaction_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_universal_compaction_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_SIZE_RATIO = NativeLibrary.lookup("rocksdb_universal_compaction_options_set_size_ratio",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_SIZE_RATIO = NativeLibrary.lookup("rocksdb_universal_compaction_options_get_size_ratio",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_MIN_MERGE_WIDTH = NativeLibrary.lookup("rocksdb_universal_compaction_options_set_min_merge_width",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MIN_MERGE_WIDTH = NativeLibrary.lookup("rocksdb_universal_compaction_options_get_min_merge_width",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_MAX_MERGE_WIDTH = NativeLibrary.lookup("rocksdb_universal_compaction_options_set_max_merge_width",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MAX_MERGE_WIDTH = NativeLibrary.lookup("rocksdb_universal_compaction_options_get_max_merge_width",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_MAX_SIZE_AMPLIFICATION_PERCENT = NativeLibrary.lookup(
				"rocksdb_universal_compaction_options_set_max_size_amplification_percent",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MAX_SIZE_AMPLIFICATION_PERCENT = NativeLibrary.lookup(
				"rocksdb_universal_compaction_options_get_max_size_amplification_percent",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_COMPRESSION_SIZE_PERCENT = NativeLibrary.lookup(
				"rocksdb_universal_compaction_options_set_compression_size_percent",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_COMPRESSION_SIZE_PERCENT = NativeLibrary.lookup(
				"rocksdb_universal_compaction_options_get_compression_size_percent",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_STOP_STYLE = NativeLibrary.lookup("rocksdb_universal_compaction_options_set_stop_style",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_STOP_STYLE = NativeLibrary.lookup("rocksdb_universal_compaction_options_get_stop_style",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	}

	private UniversalCompactionOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates universal compaction options with RocksDB defaults.
	///
	/// @return a new instance; caller must close it
	public static UniversalCompactionOptions newUniversalCompactionOptions() {
		try {
			return new UniversalCompactionOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("universal_compaction_options create failed", t);
		}
	}

	/// Percentage flexibility when merging files of different sizes: a candidate file is
	/// considered "similar enough" to merge with the files already picked if its size is
	/// within this percentage of their total. Larger values merge more aggressively. Default: 1.
	///
	/// @param percent size-similarity threshold, as a percentage
	/// @return `this` for chaining
	public UniversalCompactionOptions setSizeRatio(int percent) {
		try {
			MH_SET_SIZE_RATIO.invokeExact(ptr(), percent);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setSizeRatio failed", t);
		}
		return this;
	}

	/// Returns the configured size-ratio percentage.
	///
	/// @return current size-ratio percentage
	public int getSizeRatio() {
		try {
			return (int) MH_GET_SIZE_RATIO.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getSizeRatio failed", t);
		}
	}

	/// Minimum number of files a compaction run must merge together. Default: 2.
	///
	/// @param width minimum number of files per compaction run
	/// @return `this` for chaining
	public UniversalCompactionOptions setMinMergeWidth(int width) {
		try {
			MH_SET_MIN_MERGE_WIDTH.invokeExact(ptr(), width);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setMinMergeWidth failed", t);
		}
		return this;
	}

	/// Returns the configured minimum merge width.
	///
	/// @return current minimum merge width
	public int getMinMergeWidth() {
		try {
			return (int) MH_GET_MIN_MERGE_WIDTH.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getMinMergeWidth failed", t);
		}
	}

	/// Maximum number of files a single compaction run may merge together. The underlying
	/// C++ field is `unsigned int`, defaulting to `UINT_MAX` (unbounded), but the C API's
	/// setter/getter are a plain signed `int` -- reading this before ever setting it returns
	/// `-1` (`UINT_MAX`'s bit pattern reinterpreted as signed), not a large positive number.
	///
	/// @param width maximum number of files per compaction run
	/// @return `this` for chaining
	public UniversalCompactionOptions setMaxMergeWidth(int width) {
		try {
			MH_SET_MAX_MERGE_WIDTH.invokeExact(ptr(), width);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setMaxMergeWidth failed", t);
		}
		return this;
	}

	/// Returns the configured maximum merge width.
	///
	/// @return current maximum merge width
	public int getMaxMergeWidth() {
		try {
			return (int) MH_GET_MAX_MERGE_WIDTH.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getMaxMergeWidth failed", t);
		}
	}

	/// Once the estimated space amplification (extra space used by non-bottommost data, as a
	/// percentage of bottommost data size) exceeds this, a full compaction is triggered
	/// regardless of [#setSizeRatio]/[#setMinMergeWidth]. Default: 200 (files can take up to
	/// 3x the space of the useful data).
	///
	/// @param percent maximum tolerated space amplification, as a percentage
	/// @return `this` for chaining
	public UniversalCompactionOptions setMaxSizeAmplificationPercent(int percent) {
		try {
			MH_SET_MAX_SIZE_AMPLIFICATION_PERCENT.invokeExact(ptr(), percent);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setMaxSizeAmplificationPercent failed", t);
		}
		return this;
	}

	/// Returns the configured maximum size amplification percentage.
	///
	/// @return current maximum size amplification percentage
	public int getMaxSizeAmplificationPercent() {
		try {
			return (int) MH_GET_MAX_SIZE_AMPLIFICATION_PERCENT.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getMaxSizeAmplificationPercent failed", t);
		}
	}

	/// Percentage of the compaction size that is allowed to bypass the size-amplification
	/// ratio check and use a cheaper compression level. Default: -1 (disabled: all compacted
	/// data uses the same compression as everything else).
	///
	/// @param percent percentage of compaction size using a cheaper compression level
	/// @return `this` for chaining
	public UniversalCompactionOptions setCompressionSizePercent(int percent) {
		try {
			MH_SET_COMPRESSION_SIZE_PERCENT.invokeExact(ptr(), percent);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setCompressionSizePercent failed", t);
		}
		return this;
	}

	/// Returns the configured compression size percentage.
	///
	/// @return current compression size percentage
	public int getCompressionSizePercent() {
		try {
			return (int) MH_GET_COMPRESSION_SIZE_PERCENT.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getCompressionSizePercent failed", t);
		}
	}

	/// Sets when a compaction run stops adding more files. Default: [StopStyle#TOTAL_SIZE].
	///
	/// @param stopStyle the stop style to use
	/// @return `this` for chaining
	public UniversalCompactionOptions setStopStyle(StopStyle stopStyle) {
		try {
			MH_SET_STOP_STYLE.invokeExact(ptr(), stopStyle.value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setStopStyle failed", t);
		}
		return this;
	}

	/// Returns the configured stop style.
	///
	/// @return current stop style
	public StopStyle getStopStyle() {
		try {
			return StopStyle.fromValue((int) MH_GET_STOP_STYLE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getStopStyle failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
