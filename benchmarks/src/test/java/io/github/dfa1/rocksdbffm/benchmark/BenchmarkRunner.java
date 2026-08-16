package io.github.dfa1.rocksdbffm.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/// Runs FFM and JNI benchmarks back-to-back and prints a comparison table.
/// ```
/// ./scripts/benchmark.sh
/// ```
///
/// **These are per-call overhead numbers, not read/write throughput.** [FfmBenchmark] and
/// [JniBenchmark] both run against a 1-2 key database that is never flushed, so every get
/// is a memtable hit. That isolates the FFM-vs-JNI crossing cost, which is what this table
/// is for; it says nothing about how fast `get` is on a real database. For that, run
/// [ScaleBenchmarkRunner], which sweeps 10k/100k keys with the LSM settled.
///
/// Tiers compared:
///
///   - byte[]       — FFM vs JNI
///   - ByteBuffer   — FFM vs JNI
///   - MemorySegment — FFM only (no JNI equivalent)
///   - Instant deserialize: byte[] get vs zero-copy get(key, Mapper) — FFM only (no JNI equivalent)
///   - Value read, size-swept: byte[] vs MemorySegment vs get(key, Mapper), 8 B..1 MB (FFM only) —
///     [FfmValueSizeBenchmark], printed as its own per-size table since it has no single mean
///     to fold into the row above (folding it in either drops five of the six sizes measured,
///     or silently keeps only the last one, which is what happened before this table existed)
public class BenchmarkRunner {

	// Display order drives LABELS iteration — ROW_ORDER is unused (LABELS is LinkedHashMap)

	// Human-readable labels
	private static final Map<String, String> LABELS = new LinkedHashMap<>();

	static {
		// "Get*" rather than "Read": these run against a 1-2 key, never-flushed database,
		// so they are memtable hits measuring call overhead, not read throughput. Quoting
		// them as read performance is the mistake the label is there to prevent -- see the
		// class javadoc on FfmBenchmark, and ScaleBenchmarkRunner for real read numbers.
		LABELS.put("readsBytes", "Get (memtable) — byte[]");
		LABELS.put("readsDirectByteBuffer", "Get (memtable) — DirectByteBuffer");
		LABELS.put("readsMemorySegment", "Get (memtable) — MemorySegment (FFM)");
		LABELS.put("readsInstantViaByteArray", "Get Instant (memtable) — byte[] + deser");
		LABELS.put("readsInstantViaPinned", "Get Instant (memtable) — Mapper (FFM)");
		LABELS.put("writesBytes", "Write — byte[]");
		LABELS.put("writesDirectByteBuffer", "Write — DirectByteBuffer");
		LABELS.put("writesMemorySegment", "Write — MemorySegment (FFM)");
		LABELS.put("batchWrites", "Batch write (100 ops)");
		// FFM only: batchWrites above pays for a fresh Arena.ofConfined() per put (100 per
		// batch), which dominates once the native call itself is a cheap in-memory buffer
		// append rather than an I/O-adjacent op. This row shows the fix: one Arena reused
		// across the whole batch via WriteBatch.put(Arena, byte[], byte[]). JNI has no
		// arena concept, so its column is N/A rather than a second measurement of the same
		// JNI batchWrites run.
		LABELS.put("batchWritesArena", "Batch write, arena reuse (100 ops, FFM)");
	}

	static void main() throws Exception {
		// LinkedHashMap preserves insertion order (FFM benchmarks come first)
		Map<String, double[]> scores = new LinkedHashMap<>();

		run(FfmBenchmark.class, "FFM", scores, 0);
		run(JniBenchmark.class, "JNI", scores, 1);

		printTable(scores);

		runValueSizeSweep();
	}

	private static void run(Class<?> benchClass, String label, Map<String, double[]> scores, int col)
			throws Exception {
		System.out.printf("%n=== %s ===%n%n", label);
		Options opt = new OptionsBuilder()
				.include(benchClass.getSimpleName())
				.build();
		Collection<RunResult> results = new Runner(opt).run();
		for (RunResult r : results) {
			String name = r.getPrimaryResult().getLabel();
			scores.computeIfAbsent(name, k -> new double[2])[col] =
					r.getPrimaryResult().getStatistics().getMean();
		}
	}

	private static void printTable(Map<String, double[]> scores) {
		System.out.println();
		System.out.println("=".repeat(76));
		System.out.printf("%-32s %14s %14s %8s%n", "Benchmark", "FFM (ops/s)", "JNI (ops/s)", "Gain");
		System.out.println("-".repeat(76));

		// Print in defined order, skipping unknowns; append any remainder
		java.util.Set<String> printed = new java.util.LinkedHashSet<>();
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
		System.out.println("=".repeat(76));
	}

	private static void printRow(String key, double[] vals) {
		String label = LABELS.getOrDefault(key, key);
		double ffm = vals[0];
		double jni = vals[1];
		boolean jniAvail = jni > 0;
		String jniStr = jniAvail ? String.format("%,14.0f", jni) : "           N/A";
		String gainStr = jniAvail ? String.format("%+7.1f%%", (ffm - jni) / jni * 100) : "    N/A";
		System.out.printf("%-32s %,14.0f %s %s%n", label, ffm, jniStr, gainStr);
	}

	private static void runValueSizeSweep() throws Exception {
		System.out.printf("%n=== FFM: read by value size ===%n%n");
		Options opt = new OptionsBuilder()
				.include(FfmValueSizeBenchmark.class.getSimpleName())
				.build();
		Collection<RunResult> results = new Runner(opt).run();

		// valueSize -> [byte[], MemorySegment, Pinned (Mapper)]
		Map<Long, double[]> bySize = new TreeMap<>();
		for (RunResult r : results) {
			String name = r.getPrimaryResult().getLabel();
			long size = Long.parseLong(r.getParams().getParam("valueSize"));
			double mean = r.getPrimaryResult().getStatistics().getMean();
			double[] row = bySize.computeIfAbsent(size, k -> new double[3]);
			switch (name) {
				case "readsValueViaByteArray" -> row[0] = mean;
				case "readsValueViaMemorySegment" -> row[1] = mean;
				case "readsValueViaPinned" -> row[2] = mean;
				default -> throw new IllegalStateException("unexpected benchmark: " + name);
			}
		}

		System.out.println();
		System.out.println("=".repeat(88));
		System.out.printf("%-12s %16s %16s %18s %10s%n",
				"Value size", "byte[]", "MemorySegment", "Pinned (Mapper)", "Gain*");
		System.out.println("-".repeat(88));
		for (Map.Entry<Long, double[]> e : bySize.entrySet()) {
			double[] v = e.getValue();
			double gain = (v[2] - v[0]) / v[0] * 100;
			System.out.printf("%-12s %,16.0f %,16.0f %,18.0f %+9.1f%%%n",
					formatSize(e.getKey()), v[0], v[1], v[2], gain);
		}
		System.out.println("-".repeat(88));
		System.out.println("* Pinned (Mapper) vs byte[]");
		System.out.println("=".repeat(88));
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
