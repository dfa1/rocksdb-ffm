package io.github.dfa1.rocksdbffm.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/// Runs [FfmScaleBenchmark] and [JniScaleBenchmark] back-to-back and prints a
/// side-by-side comparison, grouped by `(keyCount, valueSize)`, for both get and
/// iteration.
/// ```
/// ./scripts/benchmark.sh ScaleBenchmarkRunner
/// ```
public class ScaleBenchmarkRunner {

	private record ComboKey(int keyCount, int valueSize) implements Comparable<ComboKey> {
		@Override
		public int compareTo(ComboKey o) {
			int c = Integer.compare(keyCount, o.keyCount);
			return c != 0 ? c : Integer.compare(valueSize, o.valueSize);
		}
	}

	// [ffmByteArray, ffmZeroCopy, jniByteArray]
	private static final int FFM_BYTE_ARRAY = 0;
	private static final int FFM_ZERO_COPY = 1;
	private static final int JNI_BYTE_ARRAY = 2;

	static void main() throws Exception {
		Map<ComboKey, double[]> getResults = new TreeMap<>();
		Map<ComboKey, double[]> iterateResults = new TreeMap<>();

		System.out.printf("%n=== FFM ===%n%n");
		collect(FfmScaleBenchmark.class, getResults, iterateResults,
				"getByteArray", "getZeroCopy", "iterateByteArray", "iterateZeroCopy",
				FFM_BYTE_ARRAY, FFM_ZERO_COPY);

		System.out.printf("%n=== JNI ===%n%n");
		collect(JniScaleBenchmark.class, getResults, iterateResults,
				"getByteArray", null, "iterateByteArray", null,
				JNI_BYTE_ARRAY, -1);

		printTable("get(key)", getResults);
		printTable("iterator.next() + value()", iterateResults);
	}

	private static void collect(Class<?> benchClass, Map<ComboKey, double[]> getResults,
	                             Map<ComboKey, double[]> iterateResults, String getByteArrayName,
	                             String getZeroCopyName, String iterateByteArrayName, String iterateZeroCopyName,
	                             int byteArrayCol, int zeroCopyCol) throws Exception {
		Options opt = new OptionsBuilder()
				.include(benchClass.getSimpleName())
				.build();
		Collection<RunResult> results = new Runner(opt).run();
		for (RunResult r : results) {
			String name = r.getPrimaryResult().getLabel();
			int keyCount = Integer.parseInt(r.getParams().getParam("keyCount"));
			int valueSize = Integer.parseInt(r.getParams().getParam("valueSize"));
			double mean = r.getPrimaryResult().getStatistics().getMean();
			ComboKey key = new ComboKey(keyCount, valueSize);

			if (name.equals(getByteArrayName)) {
				getResults.computeIfAbsent(key, k -> new double[3])[byteArrayCol] = mean;
			} else if (name.equals(getZeroCopyName)) {
				getResults.computeIfAbsent(key, k -> new double[3])[zeroCopyCol] = mean;
			} else if (name.equals(iterateByteArrayName)) {
				iterateResults.computeIfAbsent(key, k -> new double[3])[byteArrayCol] = mean;
			} else if (name.equals(iterateZeroCopyName)) {
				iterateResults.computeIfAbsent(key, k -> new double[3])[zeroCopyCol] = mean;
			}
		}
	}

	private static void printTable(String title, Map<ComboKey, double[]> results) {
		System.out.printf("%n=== %s ===%n%n", title);
		System.out.println("=".repeat(104));
		System.out.printf("%-10s %-10s %16s %16s %16s %10s %10s%n",
				"Keys", "ValueSize", "FFM byte[]", "FFM zero-copy", "JNI byte[]", "ZC gain*", "FFM gain**");
		System.out.println("-".repeat(104));
		for (Map.Entry<ComboKey, double[]> e : results.entrySet()) {
			ComboKey key = e.getKey();
			double[] v = e.getValue();
			double ffmByteArray = v[FFM_BYTE_ARRAY];
			double ffmZeroCopy = v[FFM_ZERO_COPY];
			double jniByteArray = v[JNI_BYTE_ARRAY];
			String zcGain = ffmByteArray > 0
					? String.format("%+9.1f%%", (ffmZeroCopy - ffmByteArray) / ffmByteArray * 100)
					: "      N/A";
			String ffmGain = jniByteArray > 0
					? String.format("%+9.1f%%", (ffmByteArray - jniByteArray) / jniByteArray * 100)
					: "      N/A";
			System.out.printf("%-10d %-10d %,16.0f %,16.0f %,16.0f %s %s%n",
					key.keyCount(), key.valueSize(), ffmByteArray, ffmZeroCopy, jniByteArray, zcGain, ffmGain);
		}
		System.out.println("-".repeat(104));
		System.out.println("* FFM zero-copy vs FFM byte[]   ** FFM byte[] vs JNI byte[]");
		System.out.println("=".repeat(104));
	}

	private ScaleBenchmarkRunner() {
		// no instances
	}
}
