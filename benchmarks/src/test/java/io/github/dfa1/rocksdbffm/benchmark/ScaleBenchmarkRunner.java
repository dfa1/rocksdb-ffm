package io.github.dfa1.rocksdbffm.benchmark;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/// Runs [FfmScaleBenchmark] and [JniScaleBenchmark] back-to-back and prints a
/// side-by-side comparison, grouped by `(keyCount, valueSize)`, for both get and
/// iteration.
/// ```
/// ./scripts/benchmark.sh ScaleBenchmarkRunner
/// ```
///
/// Set `-Djmh.forks=<n>` to override the `@Fork` count declared on the benchmark classes,
/// e.g. to trade confidence for a shorter run.
///
/// Each operation gets two tables: throughput (ops/s) and allocation (bytes/op, from
/// [GCProfiler]'s `gc.alloc.rate.norm`). Allocation is the metric the zero-copy tiers
/// exist to move, and unlike throughput it is near-noiseless — a byte[] tier allocates
/// its result array on every call, a zero-copy tier should be flat at 0.
public class ScaleBenchmarkRunner {

	private record ComboKey(int keyCount, int valueSize) implements Comparable<ComboKey> {
		@Override
		public int compareTo(ComboKey o) {
			int c = Integer.compare(keyCount, o.keyCount);
			return c != 0 ? c : Integer.compare(valueSize, o.valueSize);
		}
	}

	/// Throughput and allocation for one `(keyCount, valueSize)` row, indexed by the
	/// column constants below.
	private record Row(double[] ops, double[] alloc) {
		/// Cells start negative, meaning "not measured" -- the benchmark class owning this
		/// column has no such method (JNI has no zero-copy tier, iteration has no orThrow
		/// variant). They print as N/A instead of as a 0 indistinguishable from a real
		/// zero-allocation reading.
		static Row empty() {
			double[] ops = new double[COLUMNS];
			double[] alloc = new double[COLUMNS];
			Arrays.fill(ops, -1);
			Arrays.fill(alloc, -1);
			return new Row(ops, alloc);
		}
	}

	private static final int FFM_BYTE_ARRAY = 0;
	private static final int FFM_ZERO_COPY = 1;
	private static final int JNI_BYTE_ARRAY = 2;
	private static final int COLUMNS = 3;

	/// JMH secondary result carrying allocation normalized per operation.
	private static final String ALLOC_NORM = "gc.alloc.rate.norm";

	static void main() throws Exception {
		Map<ComboKey, Row> getResults = new TreeMap<>();
		Map<ComboKey, Row> iterateResults = new TreeMap<>();

		System.out.printf("%n=== FFM ===%n%n");
		collect(FfmScaleBenchmark.class, getResults, iterateResults,
				Map.of("getByteArray", FFM_BYTE_ARRAY,
						"getZeroCopy", FFM_ZERO_COPY),
				Map.of("iterateByteArray", FFM_BYTE_ARRAY,
						"iterateZeroCopy", FFM_ZERO_COPY));

		System.out.printf("%n=== JNI ===%n%n");
		collect(JniScaleBenchmark.class, getResults, iterateResults,
				Map.of("getByteArray", JNI_BYTE_ARRAY),
				Map.of("iterateByteArray", JNI_BYTE_ARRAY));

		printTable("get(key)", getResults);
		printTable("iterator.next() + value()", iterateResults);
	}

	private static void collect(Class<?> benchClass, Map<ComboKey, Row> getResults,
	                            Map<ComboKey, Row> iterateResults, Map<String, Integer> getColumns,
	                            Map<String, Integer> iterateColumns) throws Exception {
		OptionsBuilder builder = new OptionsBuilder();
		builder.include(benchClass.getSimpleName());
		builder.addProfiler(GCProfiler.class);
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		Options opt = builder.build();
		Collection<RunResult> results = new Runner(opt).run();
		for (RunResult r : results) {
			String name = r.getPrimaryResult().getLabel();
			int keyCount = Integer.parseInt(r.getParams().getParam("keyCount"));
			int valueSize = Integer.parseInt(r.getParams().getParam("valueSize"));
			ComboKey key = new ComboKey(keyCount, valueSize);

			Integer getColumn = getColumns.get(name);
			if (getColumn != null) {
				record(getResults.computeIfAbsent(key, k -> Row.empty()), getColumn, r);
			}
			Integer iterateColumn = iterateColumns.get(name);
			if (iterateColumn != null) {
				record(iterateResults.computeIfAbsent(key, k -> Row.empty()), iterateColumn, r);
			}
		}
	}

	private static void record(Row row, int column, RunResult r) {
		row.ops()[column] = r.getPrimaryResult().getStatistics().getMean();
		Result<?> alloc = r.getSecondaryResults().get(ALLOC_NORM);
		// Negative marks "profiler produced no value", which prints as N/A rather than
		// as a misleading 0 B/op -- 0 is a real, meaningful reading for the zero-copy tiers.
		row.alloc()[column] = alloc != null ? alloc.getScore() : -1;
	}

	private static void printTable(String title, Map<ComboKey, Row> results) {
		System.out.printf("%n=== %s: throughput (ops/s) ===%n%n", title);
		System.out.println("=".repeat(104));
		System.out.printf("%-10s %-10s %16s %16s %16s %10s %10s%n",
				"Keys", "ValueSize", "FFM byte[]", "FFM zero-copy", "JNI byte[]",
				"ZC gain*", "FFM gain**");
		System.out.println("-".repeat(104));
		for (Map.Entry<ComboKey, Row> e : results.entrySet()) {
			ComboKey key = e.getKey();
			double[] v = e.getValue().ops();
			String zcGain = gain(v[FFM_ZERO_COPY], v[FFM_BYTE_ARRAY]);
			String ffmGain = gain(v[FFM_BYTE_ARRAY], v[JNI_BYTE_ARRAY]);
			System.out.printf("%-10d %-10d %16s %16s %16s %s %s%n",
					key.keyCount(), key.valueSize(), ops(v[FFM_BYTE_ARRAY]), ops(v[FFM_ZERO_COPY]),
					ops(v[JNI_BYTE_ARRAY]), zcGain, ffmGain);
		}
		System.out.println("-".repeat(104));
		System.out.println("* FFM zero-copy vs FFM byte[]   ** FFM byte[] vs JNI byte[]");
		System.out.println("=".repeat(104));

		System.out.printf("%n=== %s: allocation (bytes/op) ===%n%n", title);
		System.out.println("=".repeat(77));
		System.out.printf("%-10s %-10s %16s %16s %16s%n",
				"Keys", "ValueSize", "FFM byte[]", "FFM zero-copy", "JNI byte[]");
		System.out.println("-".repeat(77));
		for (Map.Entry<ComboKey, Row> e : results.entrySet()) {
			ComboKey key = e.getKey();
			double[] v = e.getValue().alloc();
			System.out.printf("%-10d %-10d %16s %16s %16s%n",
					key.keyCount(), key.valueSize(), bytes(v[FFM_BYTE_ARRAY]), bytes(v[FFM_ZERO_COPY]),
					bytes(v[JNI_BYTE_ARRAY]));
		}
		System.out.println("=".repeat(77));
	}

	// Locale.ROOT: the default locale renders the grouping separator per-machine (a Swiss
	// JVM prints 2'131'318), which makes committed benchmark numbers differ by who ran them.
	private static String gain(double value, double baseline) {
		return baseline > 0 && value >= 0
				? String.format(Locale.ROOT, "%+9.1f%%", (value - baseline) / baseline * 100)
				: "      N/A";
	}

	private static String ops(double value) {
		return value < 0 ? "N/A" : String.format(Locale.ROOT, "%,.0f", value);
	}

	private static String bytes(double value) {
		return value < 0 ? "N/A" : String.format(Locale.ROOT, "%,.1f", value);
	}

	private ScaleBenchmarkRunner() {
		// no instances
	}
}
