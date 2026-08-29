package io.github.dfa1.rocksdbffm.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.LinuxPerfNormProfiler;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/// JNI baseline for [MultiGetScaleBenchmark]: same `batchSize` sweep, same fixed-prefix
/// lookup-key setup, byte[] tier only -- `org.rocksdb`'s multiGet API has no zero-copy read
/// path to compare against. `individualGetsByteArray` loops `batchSize` separate [RocksDB#get]
/// calls; `multiGetAsList` issues one call to `RocksDB#multiGetAsList(List)`, the JNI analogue
/// of [io.github.dfa1.rocksdbffm.ReadBatch] -- both bind to the same native
/// `rocksdb_batched_multi_get_cf`-equivalent C++ `DB::MultiGet` under the hood, so this isolates
/// the JNI call-boundary cost the same way [MultiGetScaleBenchmark] isolates the FFM one.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class JniMultiGetScaleBenchmark {

	static {
		RocksDB.loadLibrary();
	}

	private static final int KEY_SIZE = 16;
	private static final int VALUE_SIZE = 1024;
	private static final int KEY_COUNT = 10_000;

	// JMH requires @Param fields to be public.
	@Param({"1", "2", "4", "8", "16", "32", "64", "128"})
	public int batchSize;

	private RocksDB db;
	private Options options;
	private Path dbPath;

	private byte[][] lookupKeysBytes;
	private List<byte[]> lookupKeysBytesList;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-jni-multiget-");
		options = new Options().setCreateIfMissing(true);
		db = RocksDB.open(options, dbPath.toString());

		byte[][] keys = TestData.randomBytes(KEY_COUNT, KEY_SIZE);
		byte[][] values = TestData.randomBytes(KEY_COUNT, VALUE_SIZE);
		for (int i = 0; i < KEY_COUNT; i++) {
			db.put(keys[i], values[i]);
		}

		// Same LSM-settling rationale as MultiGetScaleBenchmark.setup() / FfmScaleBenchmark.setup().
		try (FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
			db.flush(flushOptions);
		}
		db.compactRange();

		lookupKeysBytes = new byte[batchSize][];
		System.arraycopy(keys, 0, lookupKeysBytes, 0, batchSize);
		lookupKeysBytesList = List.of(lookupKeysBytes);
	}

	@TearDown(Level.Trial)
	public void teardown() throws Exception {
		db.close();
		options.close();
		TestData.deleteDir(dbPath);
	}

	@Benchmark
	public int individualGetsByteArray() throws Exception {
		int total = 0;
		for (byte[] key : lookupKeysBytes) {
			total += db.get(key).length;
		}
		return total;
	}

	@Benchmark
	public int multiGetAsList() throws Exception {
		int total = 0;
		for (byte[] value : db.multiGetAsList(lookupKeysBytesList)) {
			total += value.length;
		}
		return total;
	}

	/// Runs this class with [GCProfiler] and [LinuxPerfNormProfiler] attached. `-Djmh.include=<regex>`
	/// narrows which benchmarks run and `-Djmh.forks=<n>` overrides the `@Fork` count above.
	static void main() throws Exception {
		OptionsBuilder builder = new OptionsBuilder();
		builder.addProfiler(GCProfiler.class);
		builder.addProfiler(LinuxPerfNormProfiler.class);
		builder.include(System.getProperty("jmh.include", JniMultiGetScaleBenchmark.class.getName()));
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		new org.openjdk.jmh.runner.Runner(builder.build()).run();
	}
}
