package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.CopyResult;
import io.github.dfa1.rocksdbffm.FlushOptions;
import io.github.dfa1.rocksdbffm.Mapper;
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
import org.openjdk.jmh.profile.LinuxPerfNormProfiler;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/// `batchSize` individual [ReadWriteDB#get] calls vs. one [ReadBatch#get] call, swept across a
/// range of batch sizes to see where each approach's per-call overhead starts (or stops) paying
/// for itself, across three tiers: byte[] (copies a fresh array per value), caller-supplied
/// `ByteBuffer` (copies into a destination the caller preallocates and reuses across calls --
/// no per-call `Arena` for keys, no per-call value array), and zero-copy
/// ([io.github.dfa1.rocksdbffm.Mapper], no copy at all, but the value is only valid inside the
/// callback) -- `ReadBatch` is the only batched multiGet entry point in this library
/// (preallocated, reused across every invocation instead of allocating fresh bookkeeping arrays
/// per call).
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
	private List<ByteBuffer> lookupKeysByteBufferList;
	private List<ByteBuffer> lookupValuesByteBufferList;
	private ByteBuffer individualValueBuffer;

	// Accumulates byteSize() out-of-band instead of returning it, so the zero-copy tier's
	// Mapper -- like CopyResult.Copied.INSTANCE on the ByteBuffer tier -- returns a shared
	// constant instead of autoboxing a fresh Long per key; otherwise the two tiers wouldn't
	// be allocation-comparable.
	private final long[] zeroCopyAccumulator = new long[1];
	private final Mapper<Boolean> zeroCopyMapper = value -> {
		zeroCopyAccumulator[0] += value.byteSize();
		return Boolean.TRUE;
	};

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

		lookupKeysByteBufferList = new ArrayList<>(batchSize);
		lookupValuesByteBufferList = new ArrayList<>(batchSize);
		for (byte[] key : lookupKeysBytes) {
			lookupKeysByteBufferList.add(ByteBuffer.allocateDirect(key.length).put(key).flip());
			lookupValuesByteBufferList.add(ByteBuffer.allocateDirect(VALUE_SIZE));
		}
		individualValueBuffer = ByteBuffer.allocateDirect(VALUE_SIZE);

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

	// ---- ByteBuffer tier (caller-supplied, reused destination buffers) --------

	@Benchmark
	public int individualGetsByteBuffer() {
		int total = 0;
		for (ByteBuffer key : lookupKeysByteBufferList) {
			individualValueBuffer.clear();
			if (db.get(key, individualValueBuffer) instanceof CopyResult.Copied) {
				total += individualValueBuffer.position();
			}
		}
		return total;
	}

	@Benchmark
	public int readBatchByteBuffer() {
		for (ByteBuffer value : lookupValuesByteBufferList) {
			value.clear();
		}
		List<CopyResult> results = readBatch.get(lookupKeysByteBufferList, lookupValuesByteBufferList);
		int total = 0;
		for (int i = 0; i < results.size(); i++) {
			if (results.get(i) instanceof CopyResult.Copied) {
				total += lookupValuesByteBufferList.get(i).position();
			}
		}
		return total;
	}

	// ---- zero-copy (Mapper) tier -----------------------------------------------

	@Benchmark
	public long individualGetsZeroCopy() {
		zeroCopyAccumulator[0] = 0;
		for (MemorySegment key : lookupKeysSegmentList) {
			db.get(key, zeroCopyMapper);
		}
		return zeroCopyAccumulator[0];
	}

	@Benchmark
	public long readBatchZeroCopy() {
		zeroCopyAccumulator[0] = 0;
		readBatch.get(lookupKeysSegmentList, zeroCopyMapper);
		return zeroCopyAccumulator[0];
	}

	/// Runs this class with [GCProfiler] and (unless forking is disabled) [LinuxPerfNormProfiler]
	/// attached — the latter shells out to `perf stat` per fork and reports hardware counters
	/// (cycles, instructions, cache misses, branches) normalized per benchmark op.
	/// `-Djmh.include=<regex>` narrows which benchmarks run, `-Djmh.forks=<n>` overrides the
	/// `@Fork` count above (external profilers need a fork to attach `perf` to, so `-Djmh.forks=0`
	/// skips [LinuxPerfNormProfiler] rather than failing), and `-Djmh.batchSize=<v1>,<v2>,...`
	/// narrows the `batchSize` sweep, e.g. to isolate the crossover zone for a `perf record` pass.
	static void main() throws Exception {
		OptionsBuilder builder = new OptionsBuilder();
		builder.addProfiler(GCProfiler.class);
		builder.include(System.getProperty("jmh.include", MultiGetScaleBenchmark.class.getName()));
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			int forkCount = Integer.parseInt(forks);
			builder.forks(forkCount);
			if (forkCount > 0) {
				builder.addProfiler(LinuxPerfNormProfiler.class);
			}
		} else {
			builder.addProfiler(LinuxPerfNormProfiler.class);
		}
		String batchSizes = System.getProperty("jmh.batchSize");
		if (batchSizes != null) {
			builder.param("batchSize", batchSizes.split(","));
		}
		new org.openjdk.jmh.runner.Runner(builder.build()).run();
	}
}
