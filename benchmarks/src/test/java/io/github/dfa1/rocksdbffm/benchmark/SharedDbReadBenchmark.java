package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.Options;
import io.github.dfa1.rocksdbffm.ReadWriteDB;
import io.github.dfa1.rocksdbffm.RocksDB;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/// Point reads against one database shared by every benchmark thread: run with `-t N` to see whether
/// per-call bookkeeping on the shared handle scales.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class SharedDbReadBenchmark {

	private ReadWriteDB db;
	private Path dbPath;

	/// One key segment per thread: a confined arena cannot be shared.
	@State(Scope.Thread)
	public static class PerThread {
		Arena arena;
		MemorySegment key;
		byte[] keyBytes;

		@Setup(Level.Trial)
		public void setup() {
			arena = Arena.ofConfined();
			keyBytes = TestData.READ_KEY_BYTES.clone();
			key = arena.allocateFrom(ValueLayout.JAVA_BYTE, keyBytes);
		}

		@TearDown(Level.Trial)
		public void teardown() {
			arena.close();
		}
	}

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-shared-");
		try (Options createOptions = Options.newOptions().setCreateIfMissing(true)) {
			db = RocksDB.openReadWrite(dbPath, createOptions);
		}
		db.put(TestData.READ_KEY_BYTES, TestData.READ_VALUE_BYTES);
	}

	@TearDown(Level.Trial)
	public void teardown() throws Exception {
		db.close();
		TestData.deleteDir(dbPath);
	}

	@Benchmark
	public byte[] getByteArray(PerThread thread) {
		return db.get(thread.keyBytes);
	}

	@Benchmark
	public long getZeroCopy(PerThread thread) {
		return db.get(thread.key, MemorySegment::byteSize);
	}
}
