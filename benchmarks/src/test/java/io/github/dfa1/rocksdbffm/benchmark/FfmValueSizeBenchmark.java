package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.CopyResult;
import io.github.dfa1.rocksdbffm.FlushOptions;
import io.github.dfa1.rocksdbffm.Mapper;
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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/// Read cost as a function of value size, to find where reading into a caller-preallocated
/// buffer starts beating `get(byte[])`. Reported as average time per op (ns/op) so each tier's
/// cost can be read as a fixed per-call part plus a per-byte part, and the crossover is where
/// those lines cross.
///
/// Five tiers, and what each one really does per call (see `RocksDBReadOperationsBindings`):
///
///   - `byte[]` -- `rocksdb_get_pinned_v2` + `pinnable_handle_get_value` + destroy (three
///     downcalls, a heap-allocated native handle), then a new `byte[]` and one copy into it.
///   - `DirectByteBuffer` / `MemorySegment` -- one `rocksdb_get_into_buffer` downcall that
///     copies straight into the caller's preallocated buffer: no Java allocation, but still
///     one copy and three small arena allocations (err holder, length, found flag).
///   - Mapper (size only) -- the same three downcalls as `byte[]`, then reads only the pinned
///     value's size: no copy and no use of the bytes, the "never touch it" floor.
///   - Mapper (copy) -- as above, but copies the pinned bytes into the same preallocated
///     segment the `MemorySegment` tier uses, so it is like-for-like with `get_into_buffer`.
///
/// So the `byte[]` and buffer tiers copy exactly once each; what separates them is the Java
/// heap allocation (plus zeroing and GC) and two extra downcalls, not an extra copy.
///
/// `access` picks the key each call reads. `hot` always reads the same key -- block cache,
/// CPU caches and branch predictors all warm -- which makes the fixed lookup cost look
/// smaller than it is and so pulls the crossover earlier. `uniform` walks a shuffled order
/// over every key in the DB, the more realistic case; for the largest values the DB exceeds
/// the 32 MB default block cache, so some of those reads come from the OS page cache. Both
/// go through the same index-array lookup so the access code itself is identical.
///
/// Like the scale benchmarks, `setup()` populates many keys and then flushes and compacts,
/// so reads resolve through the LSM rather than out of a still-warm memtable.
///
/// **Compare tiers within a row, not across rows.** Key count is derived from
/// [#TARGET_DB_BYTES] so the trial size stays bounded: it is a constant 20,000 up to ~1.6 KB
/// values, then falls as `valueSize` rises (50 at 1 MB). Above that point rows differ in
/// both value size and key count.
///
/// The heap is pinned (`-Xms4g -Xmx4g -XX:+AlwaysPreTouch`): at 1 MB the `byte[]` tier
/// allocates gigabytes per second, and leaving heap sizing to the default (a quarter of RAM,
/// grown on demand) would make its result depend on young-GC sizing more than on the binding.
///
/// Kept in its own class/state, separate from [FfmBenchmark]: `@Param` is applied at the
/// `@State` level, so every benchmark method sharing that state reruns once per parameter
/// value even when the method doesn't use the parameter.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
		"-Xms4g", "-Xmx4g", "-XX:+AlwaysPreTouch"})
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

	// JMH requires @Param fields to be public. Dense between 1 KB and 64 KB, where the
	// crossover sits.
	@Param({"8", "64", "256", "1024", "2048", "4096", "8192", "16384", "32768", "65536", "262144", "1048576"})
	public int valueSize;

	@Param({"hot", "uniform"})
	public String access;

	// The same keys in each tier's own representation, indexed by order[cursor].
	private byte[][] keysBytes;
	private MemorySegment[] keysMemorySegment;
	private ByteBuffer[] keysByteBuffer;
	private int[] order;
	private int cursor;

	// Pre-allocated exactly once at trial size, reused across all invocations -- models a
	// caller who already knows the value size and sizes their buffer accordingly.
	private MemorySegment valueMemorySegment;
	private ByteBuffer valueByteBuffer;

	// Mappers are created once and return a shared constant, so neither tier allocates per
	// call (a fresh capturing lambda or an autoboxed Long would); same idea as
	// MultiGetScaleBenchmark's zeroCopyMapper.
	private long sizeSink;
	private final Mapper<Boolean> sizeMapper = value -> {
		sizeSink += value.byteSize();
		return Boolean.TRUE;
	};
	private final Mapper<Boolean> copyMapper = value -> {
		MemorySegment.copy(value, 0, valueMemorySegment, 0, value.byteSize());
		return Boolean.TRUE;
	};

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-ffm-valuesize-");
		db = RocksDB.openReadWrite(dbPath);
		arena = Arena.ofConfined();

		int keyCount = (int) Math.clamp(TARGET_DB_BYTES / valueSize, MIN_KEYS, MAX_KEYS);

		Random random = new Random(42);
		keysBytes = new byte[keyCount][];
		keysMemorySegment = new MemorySegment[keyCount];
		keysByteBuffer = new ByteBuffer[keyCount];
		byte[] value = new byte[valueSize];
		for (int i = 0; i < keyCount; i++) {
			byte[] key = new byte[KEY_SIZE];
			random.nextBytes(key);
			random.nextBytes(value);
			db.put(key, value);
			keysBytes[i] = key;
			keysMemorySegment[i] = arena.allocateFrom(ValueLayout.JAVA_BYTE, key);
			keysByteBuffer[i] = ByteBuffer.allocateDirect(KEY_SIZE).put(key).flip();
		}

		// Settle the LSM before measuring, for the same reason as FfmScaleBenchmark: an
		// unflushed memtable makes the read a skiplist probe rather than a real lookup,
		// and leaves the result at the mercy of background compaction timing.
		try (FlushOptions flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
			db.flush(flushOptions);
		}
		db.compactRange();

		order = new int[keyCount];
		if (access.equals("hot")) {
			Arrays.fill(order, keyCount / 2);
		} else {
			for (int i = 0; i < keyCount; i++) {
				order[i] = i;
			}
			for (int i = keyCount - 1; i > 0; i--) {
				int j = random.nextInt(i + 1);
				int tmp = order[i];
				order[i] = order[j];
				order[j] = tmp;
			}
		}

		valueMemorySegment = arena.allocate(valueSize);
		valueByteBuffer = ByteBuffer.allocateDirect(valueSize);

		verifyEveryTierReadsTheValue();
	}

	/// One call per tier before measuring, so a tier silently returning NotFound or
	/// NotEnoughCapacity (and so measuring a cheaper path) fails the trial instead.
	private void verifyEveryTierReadsTheValue() {
		check(readsValueViaByteArray().length == valueSize, "byte[]");
		check(readsValueViaDirectByteBuffer() instanceof CopyResult.Copied, "DirectByteBuffer");
		check(readsValueViaMemorySegment() instanceof CopyResult.Copied, "MemorySegment");
		sizeSink = 0;
		check(readsValueViaPinned() == Boolean.TRUE && sizeSink == valueSize, "Mapper (size)");
		check(readsValueViaPinnedCopy() == Boolean.TRUE, "Mapper (copy)");
		cursor = 0;
	}

	private static void check(boolean ok, String tier) {
		if (!ok) {
			throw new IllegalStateException(tier + " tier did not read the full value");
		}
	}

	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		db.close();
		arena.close();
		TestData.deleteDir(dbPath);
	}

	private int nextKey() {
		int i = order[cursor];
		if (++cursor == order.length) {
			cursor = 0;
		}
		return i;
	}

	@Benchmark
	public byte[] readsValueViaByteArray() {
		return db.get(keysBytes[nextKey()]);
	}

	@Benchmark
	public CopyResult readsValueViaDirectByteBuffer() {
		ByteBuffer key = keysByteBuffer[nextKey()];
		key.rewind();
		valueByteBuffer.clear();
		return db.get(key, valueByteBuffer);
	}

	@Benchmark
	public CopyResult readsValueViaMemorySegment() {
		return db.get(keysMemorySegment[nextKey()], valueMemorySegment);
	}

	@Benchmark
	public Boolean readsValueViaPinned() {
		return db.get(keysMemorySegment[nextKey()], sizeMapper);
	}

	@Benchmark
	public Boolean readsValueViaPinnedCopy() {
		return db.get(keysMemorySegment[nextKey()], copyMapper);
	}

	/// Runs this class with [GCProfiler] attached, so `gc.alloc.rate.norm` shows each tier's
	/// per-call heap allocation next to its time. `-Djmh.forks=<n>` overrides the `@Fork` count
	/// above, and `-Djmh.access=hot` or `=uniform` runs one access pattern only.
	static void main() throws Exception {
		OptionsBuilder builder = new OptionsBuilder();
		builder.addProfiler(GCProfiler.class);
		builder.include(FfmValueSizeBenchmark.class.getSimpleName());
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		String access = System.getProperty("jmh.access");
		if (access != null) {
			builder.param("access", access);
		}
		new org.openjdk.jmh.runner.Runner(builder.build()).run();
	}
}
