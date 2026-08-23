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

/// The second, independent axis behind this project's call-site duplication: every
/// `invokeExact` call is followed by its own `try { ... } catch (Throwable t) { throw
/// RocksDB.wrapInvokeFailure(...); }` -- 485 of them across 41 files, at last count. ADR 0004
/// already centralized the *classification* logic in `wrapInvokeFailure`; what stays
/// duplicated is the `try`/`catch` syntax itself, since `invokeExact`'s `throws Throwable`
/// forces one at every syntactic call site.
///
/// This checks whether that syntax can be centralized too, behind a functional-interface
/// helper (`RocksDB.invoke(() -> MH_X.invokeExact(...))`), without touching the
/// `MethodHandle`-inlining property [MethodHandleParameterBenchmark] is about -- the
/// `invokeExact` call still lives in the lambda body, which still reads the `static final`
/// field directly, so nothing here should cost what passing the handle itself as a parameter
/// costs. The open question is only the wrapper's own overhead: one lambda instantiation and
/// one virtual dispatch per call, and (for the value-returning shape) boxing the `int` result
/// through a generic type parameter.
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
public class TryCatchWrapperBenchmark {

	private static final MethodHandle MH_MEMCMP = Linker.nativeLinker().downcallHandle(
			Linker.nativeLinker().defaultLookup().find("memcmp").orElseThrow(),
			FunctionDescriptor.of(ValueLayout.JAVA_INT,
					ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

	private static final Arena ARENA = Arena.ofAuto();
	private static final MemorySegment A = ARENA.allocateFrom("benchmark-a");
	private static final MemorySegment B = ARENA.allocateFrom("benchmark-b");
	private static final long LEN = 11;

	@FunctionalInterface
	private interface ThrowingIntCall {
		int call() throws Throwable;
	}

	@FunctionalInterface
	private interface ThrowingVoidCall {
		void call() throws Throwable;
	}

	/// Stand-in for `RocksDB.wrapInvokeFailure` — same shape (classify, wrap, rethrow), called
	/// from one place instead of being duplicated as inline `catch` bodies.
	private static int invoke(ThrowingIntCall call) {
		try {
			return call.call();
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	private static void invoke(ThrowingVoidCall call) {
		try {
			call.call();
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	// ---- int-returning shape (models get/getLongProperty) --------------------------------

	@Benchmark
	public int directTryCatch() {
		try {
			return (int) MH_MEMCMP.invokeExact(A, B, LEN);
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	@Benchmark
	public int viaWrapper() {
		return invoke(() -> (int) MH_MEMCMP.invokeExact(A, B, LEN));
	}

	// ---- void-returning shape (models put/delete — the common case) ----------------------

	@Benchmark
	public void directTryCatchVoid() {
		try {
			int ignored = (int) MH_MEMCMP.invokeExact(A, B, LEN);
		} catch (Throwable t) {
			throw new AssertionError(t);
		}
	}

	@Benchmark
	public void viaWrapperVoid() {
		invoke(() -> {
			int ignored = (int) MH_MEMCMP.invokeExact(A, B, LEN);
		});
	}

	static void main() throws Exception {
		org.openjdk.jmh.runner.options.Options opt = new OptionsBuilder()
				.include(TryCatchWrapperBenchmark.class.getSimpleName())
				.build();

		new org.openjdk.jmh.runner.Runner(opt).run();
	}
}
