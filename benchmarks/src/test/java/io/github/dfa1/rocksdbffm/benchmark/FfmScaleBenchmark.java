package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.ReadWriteDB;
import io.github.dfa1.rocksdbffm.RocksDB;
import io.github.dfa1.rocksdbffm.RocksIterator;
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
import java.util.concurrent.TimeUnit;

/// Get and iteration throughput against a database sized by `@Param keyCount`, with a
/// fixed per-key value size also swept via `@Param` — 8 bytes (typical counter/flag-sized
/// value) and 1024 bytes (typical small-document-sized value). Compares the copy tier
/// (byte[]) against the zero-copy tier ([ReadWriteDB#get(MemorySegment, Mapper)] /
/// [RocksIterator#value(Mapper)]) on both operations.
///
/// See [JniScaleBenchmark] for the JNI baseline (copy tier only — the JNI API has no
/// zero-copy read path) and [ScaleBenchmarkRunner] for the side-by-side table.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class FfmScaleBenchmark {

	private static final int KEY_SIZE = 16;

	// JMH requires @Param fields to be public.
	@Param({"10000", "100000"})
	public int keyCount;

	@Param({"8", "1024"})
	public int valueSize;

	private ReadWriteDB db;
	private Path dbPath;
	private Arena arena;
	private RocksIterator iterator;

	// Fixed lookup key for the get() benchmarks: the middle key of the generated set,
	// consistent with the rest of this project's benchmarks using a single fixed read
	// key rather than cycling through random keys per invocation, which would mix
	// key-selection overhead into the measurement itself.
	private byte[] lookupKeyBytes;
	private MemorySegment lookupKeySegment;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-ffm-scale-");
		db = RocksDB.openReadWrite(dbPath);
		arena = Arena.ofConfined();

		byte[][] keys = TestData.randomBytes(keyCount, KEY_SIZE);
		byte[][] values = TestData.randomBytes(keyCount, valueSize);
		for (int i = 0; i < keyCount; i++) {
			db.put(keys[i], values[i]);
		}

		lookupKeyBytes = keys[keyCount / 2];
		lookupKeySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, lookupKeyBytes);

		iterator = db.newIterator();
		iterator.seekToFirst();
	}

	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		iterator.close();
		db.close();
		arena.close();
		TestData.deleteDir(dbPath);
	}

	// ---- get ----------------------------------------------------------------

	@Benchmark
	public byte[] getByteArray() {
		return db.get(lookupKeyBytes);
	}

	@Benchmark
	public long getZeroCopy() {
		return db.get(lookupKeySegment, MemorySegment::byteSize).orElseThrow();
	}

	// ---- iteration ------------------------------------------------------------
	// Each invocation advances by one entry, wrapping to the start when exhausted:
	// steady-state per-entry cost, the standard JMH pattern for measuring iterator
	// throughput over a dataset larger than a single measurement window.

	@Benchmark
	public byte[] iterateByteArray() {
		if (!iterator.isValid()) {
			iterator.seekToFirst();
		}
		byte[] result = iterator.value();
		iterator.next();
		return result;
	}

	@Benchmark
	public long iterateZeroCopy() {
		if (!iterator.isValid()) {
			iterator.seekToFirst();
		}
		long result = iterator.value(MemorySegment::byteSize);
		iterator.next();
		return result;
	}

	static void main() throws Exception {
		org.openjdk.jmh.runner.options.Options opt = new OptionsBuilder()
				.addProfiler(GCProfiler.class)
				.include(FfmScaleBenchmark.class.getSimpleName())
				.build();

		new org.openjdk.jmh.runner.Runner(opt).run();
	}
}
