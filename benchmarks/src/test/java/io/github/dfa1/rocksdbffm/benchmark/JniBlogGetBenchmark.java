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
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/// JNI twin of [BlogGetBenchmark] -- read its Javadoc for what is matched to upstream's
/// `GetBenchmarks` and what deliberately differs. State, data, key order, LSM settling, JMH
/// settings and JVM arguments are kept identical; only the API under test differs. All three of
/// upstream's tiers are here, including `preallocatedGet` (heap `byte[]` destination).
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(value = 3, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
		"-Xms4g", "-Xmx4g", "-XX:+AlwaysPreTouch"})
public class JniBlogGetBenchmark {

	static {
		RocksDB.loadLibrary();
	}

	private static final int KEY_SIZE = 128;

	private RocksDB db;
	private Options options;
	private ColumnFamilyHandle cf1;
	private ReadOptions readOptions;
	private Path dbPath;

	// JMH requires @Param fields to be public.
	@Param({"1000", "50000"})
	public int keyCount;

	@Param({"1024", "16384"})
	public int valueSize;

	@Param({"false", "true"})
	public boolean flushed;

	private byte[][] keysBytes;
	private ByteBuffer[] keysByteBuffer;
	private int cursor;

	private byte[] valueBytes;
	private ByteBuffer valueByteBuffer;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-jni-blogget-");
		options = new Options().setCreateIfMissing(true);
		db = RocksDB.open(options, dbPath.toString());
		cf1 = db.createColumnFamily(new ColumnFamilyDescriptor("cf1".getBytes(StandardCharsets.UTF_8)));
		readOptions = new ReadOptions();

		keysBytes = new byte[keyCount][];
		keysByteBuffer = new ByteBuffer[keyCount];
		for (int i = 0; i < keyCount; i++) {
			byte[] key = BlogGetBenchmark.padded("key", i, KEY_SIZE);
			byte[] value = BlogGetBenchmark.padded("value", i, valueSize);
			db.put(key, value);
			db.put(cf1, key, value);
			keysBytes[i] = key;
			keysByteBuffer[i] = ByteBuffer.allocateDirect(KEY_SIZE).put(key).flip();
		}

		if (flushed) {
			try (FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
				db.flush(flushOptions, cf1);
			}
		}
		waitForBackgroundWork();

		valueBytes = new byte[valueSize];
		valueByteBuffer = ByteBuffer.allocateDirect(valueSize);

		byte[] expected = BlogGetBenchmark.padded("value", 0, valueSize);
		check(java.util.Arrays.equals(get(), expected), "byte[]");
		cursor = 0;
		check(preallocatedGet() == valueSize && java.util.Arrays.equals(valueBytes, expected), "preallocated byte[]");
		cursor = 0;
		check(preallocatedByteBufferGet() == valueSize && valueByteBuffer.get(valueSize - 1) == '0', "DirectByteBuffer");
		cursor = 0;
	}

	/// rocksdbjni 11.8.1 has no `waitForCompact`, so poll the same state it waits on.
	private void waitForBackgroundWork() throws Exception {
		while (db.getLongProperty("rocksdb.num-running-flushes") > 0
				|| db.getLongProperty("rocksdb.mem-table-flush-pending") > 0
				|| db.getLongProperty("rocksdb.num-running-compactions") > 0
				|| db.getLongProperty("rocksdb.compaction-pending") > 0) {
			Thread.sleep(50);
		}
	}

	/// Fails the trial if a tier silently returns NotFound (and so measures a cheaper path).
	private static void check(boolean ok, String tier) {
		if (!ok) {
			throw new IllegalStateException(tier + " tier did not read the full value");
		}
	}

	@TearDown(Level.Trial)
	public void teardown() throws Exception {
		readOptions.close();
		cf1.close();
		db.close();
		options.close();
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
	public byte[] get() throws Exception {
		return db.get(cf1, keysBytes[nextKey()]);
	}

	@Benchmark
	public int preallocatedGet() throws Exception {
		return db.get(cf1, keysBytes[nextKey()], valueBytes);
	}

	@Benchmark
	public int preallocatedByteBufferGet() throws Exception {
		ByteBuffer key = keysByteBuffer[nextKey()];
		key.rewind();
		valueByteBuffer.clear();
		return db.get(cf1, readOptions, key, valueByteBuffer);
	}

	/// `-Djmh.forks=<n>` overrides the `@Fork` count above.
	static void main() throws Exception {
		org.openjdk.jmh.runner.options.OptionsBuilder builder = new org.openjdk.jmh.runner.options.OptionsBuilder();
		builder.include(JniBlogGetBenchmark.class.getSimpleName());
		String forks = System.getProperty("jmh.forks");
		if (forks != null) {
			builder.forks(Integer.parseInt(forks));
		}
		new org.openjdk.jmh.runner.Runner(builder.build()).run();
	}
}
