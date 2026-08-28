package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.FlushOptions;
import io.github.dfa1.rocksdbffm.ReadBatch;
import io.github.dfa1.rocksdbffm.ReadWriteDB;
import io.github.dfa1.rocksdbffm.RocksDB;
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
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/// `batchSize` individual [ReadWriteDB#get] calls vs. one [ReadBatch#get] call, swept across a
/// range of batch sizes to see where each approach's per-call overhead starts (or stops) paying
/// for itself, byte[] and zero-copy ([io.github.dfa1.rocksdbffm.Mapper]) tiers — `ReadBatch` is
/// the only batched multiGet entry point in this library (preallocated, reused across every
/// invocation instead of allocating fresh bookkeeping arrays per call).
///
/// Keys are a fixed prefix of the populated set — same "settle the LSM tree first" setup as
/// [FfmScaleBenchmark], for the same reason: without it, which level serves a given lookup
/// varies per fork and dominates the measurement.
///
/// See [JniMultiGetScaleBenchmark] for the JNI baseline (byte[] tier only — the JNI API has
/// no zero-copy read path).
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class MultiGetScaleBenchmark {

	private static final int KEY_SIZE = 16;
	private static final int VALUE_SIZE = 1024;
	private static final int KEY_COUNT = 10_000;

	// JMH requires @Param fields to be public.
	@Param({"1", "2", "4", "8", "16", "32", "64", "128"})
	public int batchSize;

	private ReadWriteDB db;
	private Path dbPath;
	private Arena arena;
	private ReadBatch readBatch;

	private byte[][] lookupKeysBytes;
	private List<byte[]> lookupKeysBytesList;
	private List<MemorySegment> lookupKeysSegmentList;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-multiget-");
		db = RocksDB.openReadWrite(dbPath);
		arena = Arena.ofConfined();

		byte[][] keys = TestData.randomBytes(KEY_COUNT, KEY_SIZE);
		byte[][] values = TestData.randomBytes(KEY_COUNT, VALUE_SIZE);
		for (int i = 0; i < KEY_COUNT; i++) {
			db.put(keys[i], values[i]);
		}

		// See FfmScaleBenchmark's setup for why: settles the LSM tree so every fork
		// serves lookups from the same level instead of a partly-flushed memtable.
		try (FlushOptions flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
			db.flush(flushOptions);
		}
		db.compactRange();

		lookupKeysBytes = new byte[batchSize][];
		System.arraycopy(keys, 0, lookupKeysBytes, 0, batchSize);
		lookupKeysBytesList = List.of(lookupKeysBytes);

		lookupKeysSegmentList = new ArrayList<>(batchSize);
		for (byte[] key : lookupKeysBytes) {
			lookupKeysSegmentList.add(arena.allocateFrom(ValueLayout.JAVA_BYTE, key));
		}

		readBatch = ReadBatch.create(db, batchSize);
	}

	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		readBatch.close();
		db.close();
		arena.close();
		TestData.deleteDir(dbPath);
	}

	// ---- byte[] tier ----------------------------------------------------------

	@Benchmark
	public int individualGetsByteArray() {
		int total = 0;
		for (byte[] key : lookupKeysBytes) {
			total += db.get(key).length;
		}
		return total;
	}

	@Benchmark
	public int readBatchByteArray() {
		int total = 0;
		for (byte[] value : readBatch.get(lookupKeysBytesList)) {
			total += value.length;
		}
		return total;
	}

	// ---- zero-copy (Mapper) tier -----------------------------------------------

	@Benchmark
	public long individualGetsZeroCopy() {
		long total = 0;
		for (MemorySegment key : lookupKeysSegmentList) {
			total += db.get(key, MemorySegment::byteSize);
		}
		return total;
	}

	@Benchmark
	public long readBatchZeroCopy() {
		long total = 0;
		for (Long size : readBatch.get(lookupKeysSegmentList, MemorySegment::byteSize)) {
			total += size;
		}
		return total;
	}

	/// Runs this class with [GCProfiler] attached. `-Djmh.include=<regex>` narrows which
	/// benchmarks run and `-Djmh.forks=<n>` overrides the `@Fork` count above.
	static void main() throws Exception {
		OptionsBuilder builder = new OptionsBuilder();
		builder.addProfiler(GCProfiler.class);
		builder.include(System.getProperty("jmh.include", MultiGetScaleBenchmark.class.getSimpleName()));
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		new org.openjdk.jmh.runner.Runner(builder.build()).run();
	}
}
