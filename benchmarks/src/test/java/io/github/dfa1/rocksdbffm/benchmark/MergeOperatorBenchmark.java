package io.github.dfa1.rocksdbffm.benchmark;

import io.github.dfa1.rocksdbffm.MergeOperator;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/// Answers issue #94: is `MergeOperator.custom`'s `byte[]`-copying `FullMergeFn` actually the
/// bottleneck in `merge()` + `get()`, or is the cost elsewhere (the upcall itself, or RocksDB's
/// own merge machinery)? Only worth adding a zero-copy `MemorySegment` `FullMergeFn` overload if
/// the copies show up here.
///
/// `get()` is measured, not `merge()`: `full_merge` runs on read (and during flush/compaction),
/// never on write — `rocksdb_merge` just appends an operand to the memtable/WAL. So the copies
/// named in the issue can only show up in the read path, which is what every benchmark method
/// here exercises. The DB is never flushed, so every `get()` recomputes the merge from
/// [#OPERAND_COUNT] memtable-resident operands -- a repeatable steady-state cost, not a one-off.
///
/// Three operators, same operand count, `valueSize` (operand size) swept via `@Param`:
///
///   - [#getAfterMerges_uint64Add] — RocksDB's built-in operator: no Java callback at all. Fixed
///     8-byte operands regardless of `valueSize`, so this row is a flat reference line across the
///     sweep -- the floor for "RocksDB's own merge machinery" with zero FFM involvement.
///   - [#getAfterMerges_customTrivial] — [MergeOperator#custom] with a `FullMergeFn` that returns
///     the last operand untouched: minimal Java-side work, so its cost is (RocksDB machinery) +
///     (upcall dispatch) + (the `byte[]` copies `fullMergeDispatch` does regardless of what the
///     function does with them). If this row tracks flat across `valueSize`, the copies are not
///     the bottleneck -- dispatch/machinery is. If it climbs with `valueSize`, they are.
///   - [#getAfterMerges_customConcat] — same copies and dispatch, plus a `FullMergeFn` that
///     actually allocates (concatenates every operand): shows what doing real work in Java costs
///     on top of trivial, for context on how much headroom the copies leave.
///
/// Read [BenchmarkRunner]'s and [FfmValueSizeBenchmark]'s class docs first for the same
/// "compare within a row, not throughput across unrelated rows" caveat -- it applies here too.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class MergeOperatorBenchmark {

	private static final byte[] MERGE_KEY = "merge-key".getBytes();

	/// Operands queued before the first `get()`. Fixed across the sweep so `valueSize` is the
	/// only thing moving -- an operand-count sweep would be a different, useful benchmark but a
	/// second @Param axis on this same state would cross-multiply with valueSize (see
	/// FfmValueSizeBenchmark's class doc on why that split matters), so it stays out of scope
	/// here.
	private static final int OPERAND_COUNT = 8;

	// JMH requires @Param fields to be public.
	@Param({"8", "64", "256", "1024", "8192", "65536"})
	public int valueSize;

	private Path uint64AddDbPath;
	private Path customTrivialDbPath;
	private Path customConcatDbPath;
	private ReadWriteDB uint64AddDb;
	private ReadWriteDB customTrivialDb;
	private ReadWriteDB customConcatDb;

	private static final MergeOperator.FullMergeFn TRIVIAL = (key, existingValue, operands) ->
			operands.get(operands.size() - 1);

	private static final MergeOperator.FullMergeFn CONCAT = (key, existingValue, operands) -> {
		int total = existingValue != null ? existingValue.length : 0;
		for (byte[] operand : operands) {
			total += operand.length;
		}
		byte[] result = new byte[total];
		int pos = 0;
		if (existingValue != null) {
			System.arraycopy(existingValue, 0, result, 0, existingValue.length);
			pos = existingValue.length;
		}
		for (byte[] operand : operands) {
			System.arraycopy(operand, 0, result, pos, operand.length);
			pos += operand.length;
		}
		return result;
	};

	@Setup(Level.Trial)
	public void setup() throws Exception {
		uint64AddDbPath = Files.createTempDirectory("bench-merge-uint64add-");
		customTrivialDbPath = Files.createTempDirectory("bench-merge-trivial-");
		customConcatDbPath = Files.createTempDirectory("bench-merge-concat-");

		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add())) {
			uint64AddDb = RocksDB.openReadWrite(opts, uint64AddDbPath);
		}
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.custom("trivial", TRIVIAL))) {
			customTrivialDb = RocksDB.openReadWrite(opts, customTrivialDbPath);
		}
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.custom("concat", CONCAT))) {
			customConcatDb = RocksDB.openReadWrite(opts, customConcatDbPath);
		}

		Random random = new Random(42);
		ByteBuffer counter = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
		byte[] operand = new byte[valueSize];
		for (int i = 0; i < OPERAND_COUNT; i++) {
			counter.clear();
			counter.putLong(1L);
			uint64AddDb.merge(MERGE_KEY, counter.array());

			random.nextBytes(operand);
			customTrivialDb.merge(MERGE_KEY, operand.clone());

			random.nextBytes(operand);
			customConcatDb.merge(MERGE_KEY, operand.clone());
		}
	}

	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		uint64AddDb.close();
		customTrivialDb.close();
		customConcatDb.close();
		TestData.deleteDir(uint64AddDbPath);
		TestData.deleteDir(customTrivialDbPath);
		TestData.deleteDir(customConcatDbPath);
	}

	@Benchmark
	public byte[] getAfterMerges_uint64Add() {
		return uint64AddDb.get(MERGE_KEY);
	}

	@Benchmark
	public byte[] getAfterMerges_customTrivial() {
		return customTrivialDb.get(MERGE_KEY);
	}

	@Benchmark
	public byte[] getAfterMerges_customConcat() {
		return customConcatDb.get(MERGE_KEY);
	}

	static void main() throws Exception {
		org.openjdk.jmh.runner.options.Options opt = new OptionsBuilder()
				.addProfiler(GCProfiler.class)
				.include(MergeOperatorBenchmark.class.getSimpleName())
				.build();

		new org.openjdk.jmh.runner.Runner(opt).run();
	}
}
