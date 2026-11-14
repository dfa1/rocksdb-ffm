package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.ColumnFamilyDescriptor;
import io.github.dfa1.rocksdbffm.ColumnFamilyName;
import io.github.dfa1.rocksdbffm.ColumnFamilyHandle;
import io.github.dfa1.rocksdbffm.CopyResult;
import io.github.dfa1.rocksdbffm.FlushOptions;
import io.github.dfa1.rocksdbffm.ReadWriteDB;
import io.github.dfa1.rocksdbffm.RocksDB;
import io.github.dfa1.rocksdbffm.WaitForCompactOptions;
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
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/// Our counterpart of the scenario in RocksDB's "Java API Performance Improvements" blog post
/// (2023-11-06, <https://rocksdb.org/blog/2023/11/06/java-jni-benchmarks.html>):
/// `GetBenchmarks.get` / `preallocatedByteBufferGet` with `keyCount=1000,50000 keySize=128
/// valueSize=1024,16384 columnFamilyTestType=1_column_family`, throughput in ops/s. The JNI twin
/// is [JniBlogGetBenchmark]; **the two must stay identical** apart from the API under test.
///
/// What is matched to upstream's `GetBenchmarks` (facebook/rocksdb `java/jmh`):
///
///   - default `Options` apart from create-if-missing (snappy, 64 MB write buffer, 32 MB cache);
///   - data written to both `default` and `cf1`, reads go to `cf1` through an explicit handle;
///   - keys `"key"+j` and values `"value"+j`, both right-padded with `'0'` to the target size
///     (so values are highly compressible, as upstream's are);
///   - sequential round-robin key order;
///   - no manual compaction.
///
/// Deliberate differences, applied identically to both twins:
///
///   - keys are built once in setup, not per call (upstream concatenates a `String` and calls
///     `getBytes` inside the timed path), so the numbers are not comparable to the blog's chart;
///   - JMH settings are shorter than upstream's defaults (5 forks, 5 x 10 s + 5 x 10 s) and the
///     heap is pinned, so `byte[]` allocation does not turn into a GC-sizing test.
///
/// Upstream's `preallocatedGet` (key `byte[]`, value copied into a heap `byte[]`) has no FFM
/// counterpart: `core` has no `get(byte[], byte[])`, so the JNI twin carries it alone.
///
/// `flushed` is LSM state: `false` is the blog's state (memtable, plus whatever the 64 MB write
/// buffer auto-flushes for the largest data set); `true` additionally flushes `cf1` to SSTs, so
/// reads resolve through the block cache. Neither compacts manually. `waitForCompact` runs after
/// loading so background work does not leak into the measurement.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(value = 3, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
		"-Xms4g", "-Xmx4g", "-XX:+AlwaysPreTouch"})
public class BlogGetBenchmark {

	private static final int KEY_SIZE = 128;

	private ReadWriteDB db;
	private ColumnFamilyHandle cf1;
	private Path dbPath;
	private Arena arena;

	// JMH requires @Param fields to be public.
	@Param({"1000", "50000"})
	public int keyCount;

	@Param({"1024", "16384"})
	public int valueSize;

	@Param({"false", "true"})
	public boolean flushed;

	private byte[][] keysBytes;
	private MemorySegment[] keysMemorySegment;
	private ByteBuffer[] keysByteBuffer;
	private int cursor;

	private MemorySegment valueMemorySegment;
	private ByteBuffer valueByteBuffer;

	/// `prefix + j`, right-padded with `'0'` to `size`, as upstream's `GetBenchmarks.ba`.
	static byte[] padded(String prefix, int j, int size) {
		byte[] result = new byte[size];
		java.util.Arrays.fill(result, (byte) '0');
		byte[] head = (prefix + j).getBytes(StandardCharsets.UTF_8);
		System.arraycopy(head, 0, result, 0, head.length);
		return result;
	}

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-ffm-blogget-");
		db = RocksDB.openReadWrite(dbPath);
		cf1 = db.createColumnFamily(ColumnFamilyDescriptor.of(ColumnFamilyName.of("cf1")));
		arena = Arena.ofConfined();

		keysBytes = new byte[keyCount][];
		keysMemorySegment = new MemorySegment[keyCount];
		keysByteBuffer = new ByteBuffer[keyCount];
		for (int i = 0; i < keyCount; i++) {
			byte[] key = padded("key", i, KEY_SIZE);
			byte[] value = padded("value", i, valueSize);
			db.put(key, value);
			db.put(cf1, key, value);
			keysBytes[i] = key;
			keysMemorySegment[i] = arena.allocateFrom(ValueLayout.JAVA_BYTE, key);
			keysByteBuffer[i] = ByteBuffer.allocateDirect(KEY_SIZE).put(key).flip();
		}

		if (flushed) {
			try (FlushOptions flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
				db.flush(cf1, flushOptions);
			}
		}
		try (WaitForCompactOptions wait = WaitForCompactOptions.create()) {
			db.waitForCompact(wait);
		}

		valueMemorySegment = arena.allocate(valueSize);
		valueByteBuffer = ByteBuffer.allocateDirect(valueSize);

		byte[] expected = padded("value", 0, valueSize);
		check(java.util.Arrays.equals(get(), expected), "byte[]");
		check(preallocatedByteBufferGet() instanceof CopyResult.Copied, "DirectByteBuffer");
		check(valueByteBuffer.position() == valueSize && valueByteBuffer.get(valueSize - 1) == '0', "DirectByteBuffer content");
		check(preallocatedMemorySegmentGet() instanceof CopyResult.Copied, "MemorySegment");
		cursor = 0;
	}

	/// Fails the trial if a tier silently returns NotFound (and so measures a cheaper path).
	private static void check(boolean ok, String tier) {
		if (!ok) {
			throw new IllegalStateException(tier + " tier did not read the full value");
		}
	}

	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		cf1.close();
		db.close();
		arena.close();
		TestData.deleteDir(dbPath);
	}

	private int nextKey() {
		int i = cursor;
		if (++cursor == keyCount) {
			cursor = 0;
		}
		return i;
	}

	@Benchmark
	public byte[] get() {
		return db.get(cf1, keysBytes[nextKey()]);
	}

	@Benchmark
	public CopyResult preallocatedByteBufferGet() {
		ByteBuffer key = keysByteBuffer[nextKey()];
		key.rewind();
		valueByteBuffer.clear();
		return db.get(cf1, key, valueByteBuffer);
	}

	/// Not in the blog: the native-memory tier `core` offers on top of the blog's three.
	@Benchmark
	public CopyResult preallocatedMemorySegmentGet() {
		return db.get(cf1, keysMemorySegment[nextKey()], valueMemorySegment);
	}

	/// `-Djmh.forks=<n>` overrides the `@Fork` count above.
	static void main() throws Exception {
		OptionsBuilder builder = new OptionsBuilder();
		builder.include(BlogGetBenchmark.class.getSimpleName());
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		new org.openjdk.jmh.runner.Runner(builder.build()).run();
	}
}
