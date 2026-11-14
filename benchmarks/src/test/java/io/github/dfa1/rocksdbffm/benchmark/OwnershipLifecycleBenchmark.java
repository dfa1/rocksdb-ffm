package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.Options;
import io.github.dfa1.rocksdbffm.ReadOptions;
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
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/// What the ownership tree costs on the paths that create and close the objects it tracks: opening and closing a
/// [io.github.dfa1.rocksdbffm.RocksIterator] (database parent plus [ReadOptions] parent), a
/// [io.github.dfa1.rocksdbffm.Snapshot], and a [ReadOptions]. The per-key read path is not touched by the tree,
/// so it is not measured here. Run it on two commits and compare the rows.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class OwnershipLifecycleBenchmark {

	private Path dbPath;
	private ReadWriteDB db;
	private ReadOptions readOptions;

	@Setup(Level.Trial)
	public void setup() throws IOException {
		dbPath = Files.createTempDirectory("bench-ownership-");
		try (var opts = Options.newOptions().setCreateIfMissing(true)) {
			db = RocksDB.openReadWrite(dbPath, opts);
		}
		for (int i = 0; i < 1000; i++) {
			db.put(("key-" + i).getBytes(), "value".getBytes());
		}
		readOptions = ReadOptions.newReadOptions();
	}

	@TearDown(Level.Trial)
	public void tearDown() throws IOException {
		readOptions.close();
		db.close();
		TestData.deleteDir(dbPath);
	}

	/// Iterator with the immortal default options: one parent (the database).
	@Benchmark
	public void iteratorDefaultOptions() {
		try (var it = db.newIterator()) {
			it.seekToFirst();
		}
	}

	/// Iterator with explicit options: two parents (the database and the options).
	@Benchmark
	public void iteratorExplicitOptions() {
		try (var it = db.newIterator(readOptions)) {
			it.seekToFirst();
		}
	}

	/// The read path: every `key()` and `value()` reads the iterator's length slot. 1000 entries per operation.
	@Benchmark
	public long iteratorScanKeysAndValues() {
		long total = 0;
		try (var it = db.newIterator()) {
			for (it.seekToFirst(); it.isValid(); it.next()) {
				total += it.key().length + it.value().length;
			}
		}
		return total;
	}

	@Benchmark
	public void snapshotGetRelease() {
		db.getSnapshot().close();
	}

	@Benchmark
	public void readOptionsCreateClose() {
		ReadOptions.newReadOptions().close();
	}

	public static void main(String[] args) throws Exception {
		new Runner(new OptionsBuilder()
				.include(OwnershipLifecycleBenchmark.class.getSimpleName())
				.addProfiler(GCProfiler.class)
				.build()).run();
	}
}
