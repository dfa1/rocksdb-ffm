package io.github.dfa1.rocksdbffm.benchmark;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/// Runs FFM and JNI benchmarks back-to-back and prints a comparison table.
/// ```
/// ./scripts/benchmark.sh
/// ./scripts/benchmark.sh BenchmarkRunner -Djmh.forks=3
/// ```
///
/// `-Djmh.forks=<n>` overrides every class's `@Fork` count; the default of 1 fork is fine for
/// the 2x read gap, but differences of a few percent need 3 or more forks to mean anything
/// (JMH's per-run error ignores fork-to-fork variance). Every cell is printed with its 99.9%
/// error so that is visible. FFM runs entirely before JNI, so drift over the session (thermal,
/// background load) falls between the two columns; more forks narrow but don't remove that.
///
/// **These are per-call overhead numbers, not read/write throughput.** [FfmBenchmark] and
/// [JniBenchmark] both run against a 1-2 key database that is never flushed, so every get
/// is a memtable hit. That isolates the FFM-vs-JNI crossing cost, which is what this table
/// is for; it says nothing about how fast `get` is on a real database. [ScaleBenchmarkRunner]
/// sweeps 10k/100k keys with the LSM settled (still one cache-hot key per trial).
///
/// Tiers compared:
///
///   - byte[]       — FFM vs JNI
///   - ByteBuffer   — FFM vs JNI
///   - MemorySegment — FFM only (no JNI equivalent)
///   - Instant deserialize: byte[] get vs zero-copy get(key, Mapper) — FFM only (no JNI equivalent)
///   - Value read, size-swept: byte[] vs ByteBuffer vs MemorySegment vs get(key, Mapper), 8 B..1 MB,
///     hot and uniform key access (FFM only) — [FfmValueSizeBenchmark], printed as its own
///     per-size tables (ns/op) since it has no single mean to fold into the rows above
public class BenchmarkRunner {

	// Human-readable labels; insertion order is display order.
	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	static {
		// "Get*" rather than "Read": these run against a 1-2 key, never-flushed database,
		// so they are memtable hits measuring call overhead, not read throughput. Quoting
		// them as read performance is the mistake the label is there to prevent -- see the
		// class javadoc on FfmBenchmark.
		LABELS.put("readsBytes", "Get (memtable) — byte[]");
		LABELS.put("readsDirectByteBuffer", "Get (memtable) — DirectByteBuffer");
		LABELS.put("readsMemorySegment", "Get (memtable) — MemorySegment (FFM)");
		// get_into_buffer into a direct buffer, then copied out to a fresh byte[]: the
		// byte[] result of readsBytes reached through the one-downcall path instead.
		LABELS.put("getViaCopy", "Get (memtable) — byte[] via get_into_buffer (FFM)");
		LABELS.put("readsInstantViaByteArray", "Get Instant (memtable) — byte[] + deser");
		LABELS.put("readsInstantViaPinned", "Get Instant (memtable) — Mapper (FFM)");
		LABELS.put("writesBytes", "Write — byte[]");
		// Same put with the WAL disabled: what is left is the memtable insert plus the
		// binding, so the gap to writesBytes is the WAL's share of a write.
		LABELS.put("writesBytesNoWal", "Write — byte[], WAL disabled");
		// FFM only: one caller-supplied arena rewound before every put (ResettableArena)
		// instead of a fresh Arena.ofConfined() per call. No JNI equivalent.
		LABELS.put("writesBytesArena", "Write — byte[], arena reuse (FFM)");
		LABELS.put("writesDirectByteBuffer", "Write — DirectByteBuffer");
		LABELS.put("writesMemorySegment", "Write — MemorySegment (FFM)");
		LABELS.put("writesMemorySegmentArena", "Write — MemorySegment, arena reuse (FFM)");
		LABELS.put("batchWrites", "Batch write (100 ops)");
		// FFM only: batchWrites above pays for a fresh Arena.ofConfined() per put (100 per
		// batch). This row uses one Arena across the whole batch via
		// WriteBatch.put(Arena, byte[], byte[]). JNI has no arena concept.
		LABELS.put("batchWritesArena", "Batch write, arena reuse (100 ops, FFM)");
		LABELS.put("batchWritesByteBuffer", "Batch write — DirectByteBuffer, zero-copy (100 ops, FFM)");
		LABELS.put("batchWritesMemorySegment", "Batch write — MemorySegment, zero-copy (100 ops, FFM)");
	}

	// Value-size sweep tiers, in column order.
	private static final String[] SWEEP_TIERS = {
			"readsValueViaByteArray", "readsValueViaDirectByteBuffer", "readsValueViaMemorySegment",
			"readsValueViaPinned", "readsValueViaPinnedCopy"};

	static void main() throws Exception {
		// LinkedHashMap preserves insertion order (FFM benchmarks come first).
		// Each value is {ffmMean, jniMean, ffmError, jniError}; 0 means "not run".
		Map<String, double[]> scores = new LinkedHashMap<>();

		run(FfmBenchmark.class, "FFM", scores, 0);
		run(JniBenchmark.class, "JNI", scores, 1);

		printTable(scores);

		runValueSizeSweep();
	}

	private static ChainedOptionsBuilder options(Class<?> benchClass) {
		ChainedOptionsBuilder builder = new OptionsBuilder().include(benchClass.getSimpleName());
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		return builder;
	}

	private static void run(Class<?> benchClass, String label, Map<String, double[]> scores, int col)
			throws Exception {
		System.out.printf("%n=== %s ===%n%n", label);
		Collection<RunResult> results = new Runner(options(benchClass).build()).run();
		for (RunResult r : results) {
			Result<?> primary = r.getPrimaryResult();
			double[] row = scores.computeIfAbsent(primary.getLabel(), k -> new double[4]);
			row[col] = primary.getScore();
			row[col + 2] = primary.getScoreError();
		}
	}

	private static void printTable(Map<String, double[]> scores) {
		int width = 118;
		System.out.println();
		System.out.println("=".repeat(width));
		System.out.printf("%-52s %26s %26s %10s%n", "Benchmark", "FFM (ops/s)", "JNI (ops/s)", "Gain");
		System.out.println("-".repeat(width));

		Set<String> printed = new LinkedHashSet<>();
		for (String key : LABELS.keySet()) {
			if (scores.containsKey(key)) {
				printRow(key, scores.get(key));
				printed.add(key);
			}
		}
		// Any benchmark not in LABELS (future additions)
		for (Map.Entry<String, double[]> e : scores.entrySet()) {
			if (!printed.contains(e.getKey())) {
				printRow(e.getKey(), e.getValue());
			}
		}
		System.out.println("=".repeat(width));
	}

	private static void printRow(String key, double[] vals) {
		String label = LABELS.getOrDefault(key, key);
		double ffm = vals[0];
		double jni = vals[1];
		boolean jniAvail = jni > 0;
		String ffmStr = String.format("%,14.0f ± %,9.0f", ffm, vals[2]);
		String jniStr = jniAvail ? String.format("%,14.0f ± %,9.0f", jni, vals[3]) : String.format("%26s", "N/A");
		String gainStr = jniAvail ? String.format("%+9.1f%%", (ffm - jni) / jni * 100) : String.format("%10s", "N/A");
		System.out.printf("%-52s %s %s %s%n", label, ffmStr, jniStr, gainStr);
	}

	private static void runValueSizeSweep() throws Exception {
		System.out.printf("%n=== FFM: read by value size ===%n%n");
		ChainedOptionsBuilder builder = new OptionsBuilder()
				.include(FfmValueSizeBenchmark.class.getSimpleName())
				.addProfiler(GCProfiler.class);
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		Collection<RunResult> results = new Runner(builder.build()).run();

		// access -> valueSize -> {mean, error} per tier, in SWEEP_TIERS order
		Map<String, Map<Long, double[][]>> byAccess = new TreeMap<>();
		for (RunResult r : results) {
			Result<?> primary = r.getPrimaryResult();
			String access = r.getParams().getParam("access");
			long size = Long.parseLong(r.getParams().getParam("valueSize"));
			double[][] row = byAccess.computeIfAbsent(access, k -> new TreeMap<>())
					.computeIfAbsent(size, k -> new double[SWEEP_TIERS.length][2]);
			int tier = tierIndex(primary.getLabel());
			row[tier][0] = primary.getScore();
			row[tier][1] = primary.getScoreError();
		}

		int width = 124;
		for (Map.Entry<String, Map<Long, double[][]>> accessEntry : byAccess.entrySet()) {
			System.out.println();
			System.out.println("=".repeat(width));
			System.out.printf("access = %s  (ns/op, lower is better; ± is the 99.9%% error)%n", accessEntry.getKey());
			System.out.printf("%-10s %18s %18s %18s %18s %18s %10s%n",
					"Value size", "byte[]", "ByteBuffer", "MemorySegment", "Mapper (size)", "Mapper (copy)", "MS/byte[]");
			System.out.println("-".repeat(width));
			for (Map.Entry<Long, double[][]> e : accessEntry.getValue().entrySet()) {
				double[][] v = e.getValue();
				StringBuilder line = new StringBuilder(String.format("%-10s", formatSize(e.getKey())));
				for (double[] cell : v) {
					line.append(String.format(" %,10.0f ± %,5.0f", cell[0], cell[1]));
				}
				// < 1.00 means the MemorySegment tier is faster than byte[]
				line.append(String.format(" %10.2f", v[2][0] / v[0][0]));
				System.out.println(line);
			}
			System.out.println("=".repeat(width));
		}
	}

	private static int tierIndex(String benchmark) {
		for (int i = 0; i < SWEEP_TIERS.length; i++) {
			if (SWEEP_TIERS[i].equals(benchmark)) {
				return i;
			}
		}
		throw new IllegalStateException("unexpected benchmark: " + benchmark);
	}

	private static String formatSize(long bytes) {
		if (bytes >= 1024 * 1024) {
			return (bytes / (1024 * 1024)) + " MB";
		}
		if (bytes >= 1024) {
			return (bytes / 1024) + " KB";
		}
		return bytes + " B";
	}

	private BenchmarkRunner() {
		// no instances
	}
}
