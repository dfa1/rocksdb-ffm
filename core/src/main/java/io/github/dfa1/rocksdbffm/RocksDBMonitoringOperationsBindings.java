package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;

/// FFM plumbing for [RocksDBMonitoringOperations#getApproximateSizes]'s four overloads. Kept out
/// of [RocksDB] itself so that class doesn't also have to hold every feature area's `MH_` fields
/// -- see [#131](https://github.com/dfa1/rocksdbffm/issues/131). The rest of
/// [RocksDBMonitoringOperations] (`getLiveFiles`, `getLiveFilesStorageInfo`) has no `MH_` fields
/// of its own; [LiveFiles]/[LiveFilesStorageInfo] own that plumbing directly.
final class RocksDBMonitoringOperationsBindings {

	/// `void rocksdb_approximate_sizes(rocksdb_t* db, int num_ranges, const char* const* range_start_key, const size_t* range_start_key_len, const char* const* range_limit_key, const size_t* range_limit_key_len, uint64_t* sizes, char** errptr);`
	private static final MethodHandle MH_APPROXIMATE_SIZES;
	/// `void rocksdb_approximate_sizes_cf(rocksdb_t* db, rocksdb_column_family_handle_t* column_family, int num_ranges, const char* const* range_start_key, const size_t* range_start_key_len, const char* const* range_limit_key, const size_t* range_limit_key_len, uint64_t* sizes, char** errptr);`
	private static final MethodHandle MH_APPROXIMATE_SIZES_CF;
	/// `void rocksdb_approximate_sizes_with_options(rocksdb_t* db, const rocksdb_size_approximation_options_t* options, int num_ranges, const char* const* range_start_key, const size_t* range_start_key_len, const char* const* range_limit_key, const size_t* range_limit_key_len, uint64_t* sizes, char** errptr);`
	private static final MethodHandle MH_APPROXIMATE_SIZES_WITH_OPTIONS;
	/// `void rocksdb_approximate_sizes_cf_with_options(rocksdb_t* db, rocksdb_column_family_handle_t* column_family, const rocksdb_size_approximation_options_t* options, int num_ranges, const char* const* range_start_key, const size_t* range_start_key_len, const char* const* range_limit_key, const size_t* range_limit_key_len, uint64_t* sizes, char** errptr);`
	private static final MethodHandle MH_APPROXIMATE_SIZES_CF_WITH_OPTIONS;

	static {
		MH_APPROXIMATE_SIZES = NativeLibrary.lookup("rocksdb_approximate_sizes",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,   // db
						ValueLayout.JAVA_INT,  // num_ranges
						ValueLayout.ADDRESS,   // range_start_key
						ValueLayout.ADDRESS,   // range_start_key_len
						ValueLayout.ADDRESS,   // range_limit_key
						ValueLayout.ADDRESS,   // range_limit_key_len
						ValueLayout.ADDRESS,   // sizes
						ValueLayout.ADDRESS)); // errptr

		MH_APPROXIMATE_SIZES_CF = NativeLibrary.lookup("rocksdb_approximate_sizes_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,   // db
						ValueLayout.ADDRESS,   // column_family
						ValueLayout.JAVA_INT,  // num_ranges
						ValueLayout.ADDRESS,   // range_start_key
						ValueLayout.ADDRESS,   // range_start_key_len
						ValueLayout.ADDRESS,   // range_limit_key
						ValueLayout.ADDRESS,   // range_limit_key_len
						ValueLayout.ADDRESS,   // sizes
						ValueLayout.ADDRESS)); // errptr

		MH_APPROXIMATE_SIZES_WITH_OPTIONS = NativeLibrary.lookup("rocksdb_approximate_sizes_with_options",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,   // db
						ValueLayout.ADDRESS,   // options
						ValueLayout.JAVA_INT,  // num_ranges
						ValueLayout.ADDRESS,   // range_start_key
						ValueLayout.ADDRESS,   // range_start_key_len
						ValueLayout.ADDRESS,   // range_limit_key
						ValueLayout.ADDRESS,   // range_limit_key_len
						ValueLayout.ADDRESS,   // sizes
						ValueLayout.ADDRESS)); // errptr

		MH_APPROXIMATE_SIZES_CF_WITH_OPTIONS = NativeLibrary.lookup("rocksdb_approximate_sizes_cf_with_options",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,   // db
						ValueLayout.ADDRESS,   // column_family
						ValueLayout.ADDRESS,   // options
						ValueLayout.JAVA_INT,  // num_ranges
						ValueLayout.ADDRESS,   // range_start_key
						ValueLayout.ADDRESS,   // range_start_key_len
						ValueLayout.ADDRESS,   // range_limit_key
						ValueLayout.ADDRESS,   // range_limit_key_len
						ValueLayout.ADDRESS,   // sizes
						ValueLayout.ADDRESS)); // errptr
	}

	private RocksDBMonitoringOperationsBindings() {
	}

	/// The four parallel native arrays every `rocksdb_approximate_sizes*` call needs: start-key
	/// pointers, start-key lengths, limit-key pointers, limit-key lengths -- one entry per
	/// [Range].
	private record RangeArrays(MemorySegment starts, MemorySegment startLens,
			MemorySegment limits, MemorySegment limitLens) {
	}

	private static RangeArrays buildRangeArrays(Arena arena, List<Range> ranges) {
		int n = ranges.size();
		MemorySegment starts = arena.allocate(ValueLayout.ADDRESS, n);
		MemorySegment startLens = arena.allocate(ValueLayout.JAVA_LONG, n);
		MemorySegment limits = arena.allocate(ValueLayout.ADDRESS, n);
		MemorySegment limitLens = arena.allocate(ValueLayout.JAVA_LONG, n);
		for (int i = 0; i < n; i++) {
			Range range = ranges.get(i);
			starts.setAtIndex(ValueLayout.ADDRESS, i, NativeCalls.toNative(arena, range.startKey()));
			startLens.setAtIndex(ValueLayout.JAVA_LONG, i, range.startKey().length);
			limits.setAtIndex(ValueLayout.ADDRESS, i, NativeCalls.toNative(arena, range.endKey()));
			limitLens.setAtIndex(ValueLayout.JAVA_LONG, i, range.endKey().length);
		}
		return new RangeArrays(starts, startLens, limits, limitLens);
	}

	static long[] approximateSizes(MemorySegment db, List<Range> ranges) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			RangeArrays r = buildRangeArrays(arena, ranges);
			MemorySegment sizes = arena.allocate(ValueLayout.JAVA_LONG, ranges.size());
			MH_APPROXIMATE_SIZES.invokeExact(db, ranges.size(),
					r.starts(), r.startLens(), r.limits(), r.limitLens(), sizes, err);
			NativeCalls.checkError(err);
			return sizes.toArray(ValueLayout.JAVA_LONG);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getApproximateSizes failed", t);
		}
	}

	static long[] approximateSizesCf(MemorySegment db, ColumnFamilyHandle cf, List<Range> ranges) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			RangeArrays r = buildRangeArrays(arena, ranges);
			MemorySegment sizes = arena.allocate(ValueLayout.JAVA_LONG, ranges.size());
			MH_APPROXIMATE_SIZES_CF.invokeExact(db, cf.ptr(), ranges.size(),
					r.starts(), r.startLens(), r.limits(), r.limitLens(), sizes, err);
			NativeCalls.checkError(err);
			return sizes.toArray(ValueLayout.JAVA_LONG);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getApproximateSizes failed", t);
		}
	}

	static long[] approximateSizesWithOptions(MemorySegment db, SizeApproximationOptions options, List<Range> ranges) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			RangeArrays r = buildRangeArrays(arena, ranges);
			MemorySegment sizes = arena.allocate(ValueLayout.JAVA_LONG, ranges.size());
			MH_APPROXIMATE_SIZES_WITH_OPTIONS.invokeExact(db, options.ptr(), ranges.size(),
					r.starts(), r.startLens(), r.limits(), r.limitLens(), sizes, err);
			NativeCalls.checkError(err);
			return sizes.toArray(ValueLayout.JAVA_LONG);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getApproximateSizes failed", t);
		}
	}

	static long[] approximateSizesCfWithOptions(MemorySegment db, ColumnFamilyHandle cf,
			SizeApproximationOptions options, List<Range> ranges) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			RangeArrays r = buildRangeArrays(arena, ranges);
			MemorySegment sizes = arena.allocate(ValueLayout.JAVA_LONG, ranges.size());
			MH_APPROXIMATE_SIZES_CF_WITH_OPTIONS.invokeExact(db, cf.ptr(), options.ptr(), ranges.size(),
					r.starts(), r.startLens(), r.limits(), r.limitLens(), sizes, err);
			NativeCalls.checkError(err);
			return sizes.toArray(ValueLayout.JAVA_LONG);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getApproximateSizes failed", t);
		}
	}
}
