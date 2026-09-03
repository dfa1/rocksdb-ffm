package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_statistics_histogram_data_t`.
public final class StatisticsHistogramData extends NativeObject {

	/// `rocksdb_statistics_histogram_data_t* rocksdb_statistics_histogram_data_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_statistics_histogram_data_destroy(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_DESTROY;
	/// `double rocksdb_statistics_histogram_data_get_median(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_MEDIAN;
	/// `double rocksdb_statistics_histogram_data_get_p95(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_P95;
	/// `double rocksdb_statistics_histogram_data_get_p99(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_P99;
	/// `double rocksdb_statistics_histogram_data_get_average(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_AVERAGE;
	/// `double rocksdb_statistics_histogram_data_get_std_dev(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_STD_DEV;
	/// `double rocksdb_statistics_histogram_data_get_max(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_MAX;
	/// `uint64_t rocksdb_statistics_histogram_data_get_count(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_COUNT;
	/// `uint64_t rocksdb_statistics_histogram_data_get_sum(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_SUM;
	/// `double rocksdb_statistics_histogram_data_get_min(rocksdb_statistics_histogram_data_t* data);`
	private static final MethodHandle MH_GET_MIN;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_statistics_histogram_data_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));
		MH_DESTROY = NativeLibrary.lookup("rocksdb_statistics_histogram_data_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
		MH_GET_MEDIAN = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_median",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
		MH_GET_P95 = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_p95",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
		MH_GET_P99 = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_p99",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
		MH_GET_AVERAGE = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_average",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
		MH_GET_STD_DEV = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_std_dev",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
		MH_GET_MAX = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_max",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
		MH_GET_COUNT = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_count",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
		MH_GET_SUM = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_sum",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
		MH_GET_MIN = NativeLibrary.lookup("rocksdb_statistics_histogram_data_get_min",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
	}

	private StatisticsHistogramData(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a new histogram data container, initially zeroed.
	///
	/// @return a new [StatisticsHistogramData]; caller must close it
	public static StatisticsHistogramData newStatisticsHistogramData() {
		try {
			return new StatisticsHistogramData((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("histogram data create failed", t);
		}
	}

	/// Returns the median value of the histogram.
	///
	/// @return median value
	public double getMedian() {
		return NativeFields.getDouble(MH_GET_MEDIAN, ptr());
	}

	/// Returns the 95th-percentile value of the histogram.
	///
	/// @return 95th-percentile value
	public double getP95() {
		return NativeFields.getDouble(MH_GET_P95, ptr());
	}

	/// Returns the 99th-percentile value of the histogram.
	///
	/// @return 99th-percentile value
	public double getP99() {
		return NativeFields.getDouble(MH_GET_P99, ptr());
	}

	/// Returns the average value of the histogram.
	///
	/// @return average value
	public double getAverage() {
		return NativeFields.getDouble(MH_GET_AVERAGE, ptr());
	}

	/// Returns the standard deviation of the histogram.
	///
	/// @return standard deviation
	public double getStdDev() {
		return NativeFields.getDouble(MH_GET_STD_DEV, ptr());
	}

	/// Returns the maximum value recorded in the histogram.
	///
	/// @return maximum value
	public double getMax() {
		return NativeFields.getDouble(MH_GET_MAX, ptr());
	}

	/// Returns the total number of samples in the histogram.
	///
	/// @return sample count
	public long getCount() {
		return NativeFields.getLong(MH_GET_COUNT, ptr());
	}

	/// Returns the sum of all samples in the histogram.
	///
	/// @return sum of all samples
	public long getSum() {
		return NativeFields.getLong(MH_GET_SUM, ptr());
	}

	/// Returns the minimum value recorded in the histogram.
	///
	/// @return minimum value
	public double getMin() {
		return NativeFields.getDouble(MH_GET_MIN, ptr());
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}

	@Override
	public String toString() {
		return String.format(
				"Histogram[count=%d, sum=%d, min=%.2f, max=%.2f, avg=%.2f, median=%.2f, p95=%.2f, p99=%.2f, stddev=%.2f]",
				getCount(), getSum(), getMin(), getMax(), getAverage(), getMedian(), getP95(), getP99(), getStdDev()
		);
	}
}
