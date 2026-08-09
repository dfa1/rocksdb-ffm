package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.ReadWriteDB;
import io.github.dfa1.rocksdbffm.RocksDB;
import io.github.dfa1.rocksdbffm.WriteBatch;
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
import java.util.concurrent.TimeUnit;

/// Measures [WriteBatch] put throughput (one commit per batch) across a range
/// of batch sizes and the byte[]/ByteBuffer/MemorySegment tiers.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class WriteBatchSizeBenchmark {

	@Param({"1", "10", "20", "50", "100", "200", "400"})
	private int batchSize;

	private ReadWriteDB db;
	private Path dbPath;
	private WriteBatch batch;
	private Arena arena;

	private byte[][] batchKeys;
	private byte[][] batchValues;
	private ByteBuffer[] batchKeysByteBuffer;
	private ByteBuffer[] batchValuesByteBuffer;
	private MemorySegment[] batchKeysMemorySegment;
	private MemorySegment[] batchValuesMemorySegment;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-writebatch-");
		db = RocksDB.openReadWrite(dbPath);
		batch = WriteBatch.create();
		arena = Arena.ofConfined();

		batchKeys = TestData.batchKeys(batchSize);
		batchValues = TestData.batchValues(batchSize);

		batchKeysByteBuffer = new ByteBuffer[batchSize];
		batchValuesByteBuffer = new ByteBuffer[batchSize];
		for (int i = 0; i < batchSize; i++) {
			batchKeysByteBuffer[i] = ByteBuffer.allocateDirect(batchKeys[i].length).put(batchKeys[i]).flip();
			batchValuesByteBuffer[i] = ByteBuffer.allocateDirect(batchValues[i].length).put(batchValues[i]).flip();
		}

		batchKeysMemorySegment = new MemorySegment[batchSize];
		batchValuesMemorySegment = new MemorySegment[batchSize];
		for (int i = 0; i < batchSize; i++) {
			batchKeysMemorySegment[i] = arena.allocateFrom(ValueLayout.JAVA_BYTE, batchKeys[i]);
			batchValuesMemorySegment[i] = arena.allocateFrom(ValueLayout.JAVA_BYTE, batchValues[i]);
		}
	}

	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		batch.close();
		db.close();
		arena.close();
		TestData.deleteDir(dbPath);
	}

	// ---- byte[] tier ---------------------------------------------------

	@Benchmark
	public void batchWrites() {
		batch.clear();
		for (int i = 0; i < batchSize; i++) {
			batch.put(batchKeys[i], batchValues[i]);
		}
		db.write(batch);
	}

	@Benchmark
	public void batchWritesArena() {
		batch.clear();
		try (Arena txArena = Arena.ofConfined()) {
			for (int i = 0; i < batchSize; i++) {
				batch.put(txArena, batchKeys[i], batchValues[i]);
			}
			db.write(txArena, batch);
		}
	}

	// ---- ByteBuffer tier (zero-copy) ------------------------------------

	@Benchmark
	public void batchWritesByteBuffer() {
		batch.clear();
		for (int i = 0; i < batchSize; i++) {
			batchKeysByteBuffer[i].rewind();
			batchValuesByteBuffer[i].rewind();
			batch.put(batchKeysByteBuffer[i], batchValuesByteBuffer[i]);
		}
		db.write(batch);
	}

	// ---- MemorySegment tier (zero-copy) ----------------------------------

	@Benchmark
	public void batchWritesMemorySegment() {
		batch.clear();
		for (int i = 0; i < batchSize; i++) {
			batch.put(batchKeysMemorySegment[i], batchValuesMemorySegment[i]);
		}
		db.write(batch);
	}

	static void main() throws Exception {
		org.openjdk.jmh.runner.options.Options opt = new OptionsBuilder()
				.addProfiler(GCProfiler.class)
				.include(WriteBatchSizeBenchmark.class.getSimpleName())
				.build();

		new org.openjdk.jmh.runner.Runner(opt).run();
	}
}
