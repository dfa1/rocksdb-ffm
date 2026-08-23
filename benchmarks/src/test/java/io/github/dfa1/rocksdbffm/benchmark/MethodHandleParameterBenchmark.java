package io.github.dfa1.rocksdbffm.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

/// Isolates the one variable behind CLAUDE.md's "never pass a `MethodHandle` as a method
/// parameter" rule: does reading the downcall target from a `static final` field at the
/// `invokeExact` call site actually out-perform reading the same target through a parameter,
/// on a JDK 25 JIT -- and if it does, *where* does it start costing something? See
/// [ADR 0006](../../../../../../../../docs/adr/0006-method-handles-usage.md)
/// for why this question matters.
///
/// `degree1` through `degree8` are the same shape repeated with a growing, compile-time-constant
/// case count: `rotation++ % N` selecting among `N` independently-bound handles (all targeting
/// the *same* native symbol, `memcmp` -- only the object identity varies, not the underlying
/// native function, so `N` is the only thing changing between methods). This grows by hand,
/// method by method, on purpose rather than through a `MethodHandle[]` indexed by a swept
/// `@Param` field: an early version tried exactly that and it silently added its own overhead
/// to the measurement -- array bounds-checking, and a `% degree` where `degree` is a JMH-managed
/// instance field the JIT cannot treat as a compile-time constant. That version's `degree = 1`
/// case already ran 41% slower than `direct` before a single rotation had even happened, which
/// is a benchmark-mechanism artifact, not a finding about `MethodHandle`s: a `% literal` against
/// a small fixed set of `switch` cases -- the shape every method below uses -- is what the
/// duplicated-call-site alternative in `TransactionDB`/`Transaction` would actually look like,
/// and what has to be measured instead.
///
/// `direct` is the fixed baseline: today's rule exactly, one `static final` field read at its own
/// call site, untouched by any of the above.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class MethodHandleParameterBenchmark {

	private static final FunctionDescriptor DESC = FunctionDescriptor.of(ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

	private static MethodHandle bind() {
		return Linker.nativeLinker().downcallHandle(
				Linker.nativeLinker().defaultLookup().find("memcmp").orElseThrow(
						() -> new UnsatisfiedLinkError("Symbol not found: memcmp")),
				DESC);
	}

	// Independently-bound handles, all targeting the same native symbol: object identity is the
	// only thing that varies from one to the next, not native-side cost.
	private static final MethodHandle H1 = bind();
	private static final MethodHandle H2 = bind();
	private static final MethodHandle H3 = bind();
	private static final MethodHandle H4 = bind();
	private static final MethodHandle H5 = bind();
	private static final MethodHandle H6 = bind();
	private static final MethodHandle H7 = bind();
	private static final MethodHandle H8 = bind();

	/// The rule this benchmark exists to check: `static final`, read directly at the call site.
	private static final MethodHandle MH_DIRECT = bind();

	private static final Arena ARENA = Arena.ofAuto();
	private static final MemorySegment A = ARENA.allocateFrom("benchmark-a");
	private static final MemorySegment B = ARENA.allocateFrom("benchmark-b");
	private static final long LEN = 11;

	private int rotation;

	/// Shared helper a genericized `RocksDB.putBytes`-style method would need: the target is a
	/// parameter, not a field, so `invokeExact` sees whatever the caller passed in.
	private static int invokeViaParameter(MethodHandle mh, MemorySegment a, MemorySegment b, long len) {
		try {
			return (int) mh.invokeExact(a, b, len);
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	// ---- baseline ---------------------------------------------------------------------

	@Benchmark
	public int direct() {
		try {
			return (int) MH_DIRECT.invokeExact(A, B, LEN);
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	// ---- swept: 1 through 8 distinct handle identities at one shared call site ---------

	@Benchmark
	public int degree1() {
		MethodHandle mh = switch (rotation++ % 1) {
			default -> H1;
		};
		return invokeViaParameter(mh, A, B, LEN);
	}

	@Benchmark
	public int degree2() {
		MethodHandle mh = switch (rotation++ % 2) {
			case 0 -> H1;
			default -> H2;
		};
		return invokeViaParameter(mh, A, B, LEN);
	}

	@Benchmark
	public int degree3() {
		MethodHandle mh = switch (rotation++ % 3) {
			case 0 -> H1;
			case 1 -> H2;
			default -> H3;
		};
		return invokeViaParameter(mh, A, B, LEN);
	}

	@Benchmark
	public int degree4() {
		MethodHandle mh = switch (rotation++ % 4) {
			case 0 -> H1;
			case 1 -> H2;
			case 2 -> H3;
			default -> H4;
		};
		return invokeViaParameter(mh, A, B, LEN);
	}

	@Benchmark
	public int degree5() {
		MethodHandle mh = switch (rotation++ % 5) {
			case 0 -> H1;
			case 1 -> H2;
			case 2 -> H3;
			case 3 -> H4;
			default -> H5;
		};
		return invokeViaParameter(mh, A, B, LEN);
	}

	@Benchmark
	public int degree6() {
		MethodHandle mh = switch (rotation++ % 6) {
			case 0 -> H1;
			case 1 -> H2;
			case 2 -> H3;
			case 3 -> H4;
			case 4 -> H5;
			default -> H6;
		};
		return invokeViaParameter(mh, A, B, LEN);
	}

	@Benchmark
	public int degree7() {
		MethodHandle mh = switch (rotation++ % 7) {
			case 0 -> H1;
			case 1 -> H2;
			case 2 -> H3;
			case 3 -> H4;
			case 4 -> H5;
			case 5 -> H6;
			default -> H7;
		};
		return invokeViaParameter(mh, A, B, LEN);
	}

	@Benchmark
	public int degree8() {
		MethodHandle mh = switch (rotation++ % 8) {
			case 0 -> H1;
			case 1 -> H2;
			case 2 -> H3;
			case 3 -> H4;
			case 4 -> H5;
			case 5 -> H6;
			case 6 -> H7;
			default -> H8;
		};
		return invokeViaParameter(mh, A, B, LEN);
	}

	static void main() throws Exception {
		org.openjdk.jmh.runner.options.Options opt = new OptionsBuilder()
				.include(MethodHandleParameterBenchmark.class.getSimpleName())
				.build();

		new org.openjdk.jmh.runner.Runner(opt).run();
	}
}
