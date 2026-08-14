package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.CopyResult;
import io.github.dfa1.rocksdbffm.FlushOptions;
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
import java.util.Random;
import java.util.concurrent.TimeUnit;

/// Read cost as a function of value size, swept via `@Param`, across three tiers — byte[]
/// get (allocates a new array every call), get(MemorySegment,MemorySegment) (caller-
/// preallocated buffer, still a native memcpy into it), and get(key, Mapper) (zero-copy,
/// no allocation and no copy at all).
///
/// This was previously called `FfmBlobSizeBenchmark` and described as a blob benchmark,
/// which it never was: it opens a plain [ReadWriteDB] via `RocksDB.openReadWrite` with no
/// blob options set, so no value ever reached blob storage regardless of size. Renamed to
/// what it actually measures. A real blob benchmark would need `RocksDB.openBlob` with
/// `min_blob_size` below the smallest swept size.
///
/// Like the scale benchmarks, `setup()` populates many keys and then flushes and compacts,
/// so reads resolve through the LSM rather than out of a still-warm memtable. An earlier
/// version wrote a single key and measured immediately, which made every tier a memtable
/// hit and the value-size sweep close to meaningless.
///
/// **Compare tiers within a row, not throughput across rows.** Key count is derived from
/// [#TARGET_DB_BYTES] so the trial size stays bounded, which means it falls as `valueSize`
/// rises (20,000 keys at 8 B, 50 at 1 MB). Rows therefore differ in both value size and
/// key count, and for the pinned tier — whose cost barely depends on value size at all —
/// the row-to-row variation is mostly the key count moving. The tier-vs-tier comparison
/// inside a single row is the number this benchmark is built to produce.
///
/// Kept in its own class/state, separate from [FfmBenchmark]: `@Param` is applied at the
/// `@State` level, so every benchmark method sharing that state reruns once per parameter
/// value even when the method doesn't use the parameter. Folding this sweep into
/// [FfmBenchmark] used to force its unrelated byte[]/ByteBuffer/MemorySegment/Instant
/// benchmarks to run six times each (once per size) for no reason — for the Instant
/// benchmarks specifically, it also meant each trial ran alongside a co-resident large
/// value (up to 1 MB) written into the same DB, silently skewing block-cache behavior for
/// those unrelated reads.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class FfmValueSizeBenchmark {

	private static final int KEY_SIZE = 16;

	/// Target on-disk size per trial. Key count is derived from this rather than fixed, so
	/// the 1 MB end of the sweep does not write a 1000 x 1 MB = 1 GB database (and then
	/// compact it) for every trial while the 8 B end writes 8 KB.
	private static final long TARGET_DB_BYTES = 32L * 1024 * 1024;

	/// Floor keeps the largest value sizes honest (a handful of keys is still an LSM, and
	/// 50 x 1 MB is a manageable 50 MB); ceiling keeps the smallest sizes from spending
	/// the whole trial in setup writing millions of tiny keys.
	private static final int MIN_KEYS = 50;
	private static final int MAX_KEYS = 20_000;

	private ReadWriteDB db;
	private Path dbPath;
	private Arena arena;

	// JMH requires @Param fields to be public.
	@Param({"8", "16", "1024", "4096", "65536", "1048576"})
	public int valueSize;

	private byte[] lookupKeyBytes;
	private MemorySegment lookupKeyMemorySegment;
	// Pre-allocated exactly once at trial size, reused across all invocations — models a
	// caller who already knows the value size and sizes their buffer accordingly.
	private MemorySegment valueMemorySegment;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-ffm-valuesize-");
		db = RocksDB.openReadWrite(dbPath);
		arena = Arena.ofConfined();

		int keyCount = (int) Math.clamp(TARGET_DB_BYTES / valueSize, MIN_KEYS, MAX_KEYS);

		Random random = new Random(42);
		byte[][] keys = new byte[keyCount][];
		byte[] value = new byte[valueSize];
		for (int i = 0; i < keyCount; i++) {
			byte[] key = new byte[KEY_SIZE];
			random.nextBytes(key);
			random.nextBytes(value);
			db.put(key, value);
			keys[i] = key;
		}

		// Settle the LSM before measuring, for the same reason as FfmScaleBenchmark: an
		// unflushed memtable makes the read a skiplist probe rather than a real lookup,
		// and leaves the result at the mercy of background compaction timing.
		try (FlushOptions flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
			db.flush(flushOptions);
		}
		db.compactRange();

		lookupKeyBytes = keys[keyCount / 2];
		lookupKeyMemorySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, lookupKeyBytes);
		valueMemorySegment = arena.allocate(valueSize);
	}

	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		db.close();
		arena.close();
		TestData.deleteDir(dbPath);
	}

	@Benchmark
	public int readsValueViaByteArray() {
		return db.get(lookupKeyBytes).length;
	}

	@Benchmark
	public CopyResult readsValueViaMemorySegment() {
		return db.get(lookupKeyMemorySegment, valueMemorySegment);
	}

	@Benchmark
	public long readsValueViaPinned() {
		return db.get(lookupKeyMemorySegment, MemorySegment::byteSize).orElseThrow();
	}

	/// Runs this class with [GCProfiler] attached. `-Djmh.forks=<n>` overrides the `@Fork`
	/// count above -- the default of 1 is fine for the allocation columns, which are
	/// deterministic, but the throughput columns need several forks before small
	/// differences between tiers mean anything.
	static void main() throws Exception {
		OptionsBuilder builder = new OptionsBuilder();
		builder.addProfiler(GCProfiler.class);
		builder.include(FfmValueSizeBenchmark.class.getSimpleName());
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		new org.openjdk.jmh.runner.Runner(builder.build()).run();
	}
}
