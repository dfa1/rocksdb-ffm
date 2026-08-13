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
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/// JNI baseline for [FfmScaleBenchmark]: same `keyCount`/`valueSize` sweep, byte[]-copy
/// tier only — `org.rocksdb`'s `get`/`RocksIterator` API has no zero-copy read path to
/// compare against.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class JniScaleBenchmark {

	static {
		RocksDB.loadLibrary();
	}

	private static final int KEY_SIZE = 16;

	// JMH requires @Param fields to be public.
	@Param({"10000", "100000"})
	public int keyCount;

	@Param({"8", "1024"})
	public int valueSize;

	private RocksDB db;
	private Options options;
	private Path dbPath;
	private RocksIterator iterator;

	// Same fixed-key rationale as FfmScaleBenchmark.
	private byte[] lookupKey;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-jni-scale-");
		options = new Options().setCreateIfMissing(true);
		db = RocksDB.open(options, dbPath.toString());

		byte[][] keys = TestData.randomBytes(keyCount, KEY_SIZE);
		byte[][] values = TestData.randomBytes(keyCount, valueSize);
		for (int i = 0; i < keyCount; i++) {
			db.put(keys[i], values[i]);
		}

		lookupKey = keys[keyCount / 2];

		iterator = db.newIterator();
		iterator.seekToFirst();
	}

	@TearDown(Level.Trial)
	public void teardown() throws Exception {
		iterator.close();
		db.close();
		options.close();
		TestData.deleteDir(dbPath);
	}

	@Benchmark
	public byte[] getByteArray() throws Exception {
		return db.get(lookupKey);
	}

	@Benchmark
	public byte[] iterateByteArray() {
		if (!iterator.isValid()) {
			iterator.seekToFirst();
		}
		byte[] result = iterator.value();
		iterator.next();
		return result;
	}

	static void main() throws Exception {
		org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
				.include(JniScaleBenchmark.class.getSimpleName())
				.build();

		new org.openjdk.jmh.runner.Runner(opt).run();
	}
}
