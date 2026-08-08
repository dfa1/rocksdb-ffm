package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.CopyResult;
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

/// Blob read, value size swept via `@Param`: three tiers — byte[] get (allocates a new array
/// every call), get(MemorySegment,MemorySegment) (caller-preallocated buffer, still a native
/// memcpy into it), and get(key, Mapper) (zero-copy, no allocation and no copy at all).
///
/// Kept in its own class/state, separate from [FfmBenchmark]: `@Param` is applied at the
/// `@State` level, so every benchmark method sharing that state reruns once per parameter
/// value even when the method doesn't use the parameter. Folding this sweep into
/// [FfmBenchmark] used to force its unrelated byte[]/ByteBuffer/MemorySegment/Instant
/// benchmarks to run six times each (once per blob size) for no reason — for the Instant
/// benchmarks specifically, it also meant each trial ran alongside a co-resident blob value
/// (up to 1 MB) written into the same DB, silently skewing block-cache behavior for those
/// unrelated reads.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class FfmBlobSizeBenchmark {

	private ReadWriteDB db;
	private Path dbPath;
	private Arena arena;

	// JMH requires @Param fields to be public.
	@Param({"8", "16", "1024", "4096", "65536", "1048576"})
	public int blobValueSize;

	private byte[] blobKeyBytes;
	private MemorySegment blobKeyMemorySegment;
	// Pre-allocated exactly once at trial size, reused across all invocations — models a
	// caller who already knows the value size and sizes their buffer accordingly.
	private MemorySegment blobValueMemorySegment;

	@Setup(Level.Trial)
	public void setup() throws Exception {
		dbPath = Files.createTempDirectory("bench-ffm-blob-");
		db = RocksDB.openReadWrite(dbPath);
		arena = Arena.ofConfined();

		blobKeyBytes = "blob-key".getBytes();
		blobKeyMemorySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, blobKeyBytes);
		byte[] blobValue = new byte[blobValueSize];
		new Random(42).nextBytes(blobValue);
		db.put(blobKeyBytes, blobValue);
		blobValueMemorySegment = arena.allocate(blobValueSize);
	}

	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		db.close();
		arena.close();
		TestData.deleteDir(dbPath);
	}

	@Benchmark
	public int readsBlobViaByteArray() {
		return db.get(blobKeyBytes).length;
	}

	@Benchmark
	public CopyResult readsBlobViaMemorySegment() {
		return db.get(blobKeyMemorySegment, blobValueMemorySegment);
	}

	@Benchmark
	public long readsBlobViaPinned() {
		return db.get(blobKeyMemorySegment, MemorySegment::byteSize).orElseThrow();
	}

	static void main() throws Exception {
		org.openjdk.jmh.runner.options.Options opt = new OptionsBuilder()
				.addProfiler(GCProfiler.class)
				.include(FfmBlobSizeBenchmark.class.getSimpleName())
				.build();

		new org.openjdk.jmh.runner.Runner(opt).run();
	}
}
