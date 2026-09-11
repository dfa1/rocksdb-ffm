package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeCallsTest {

	// -----------------------------------------------------------------------
	// wrapInvokeFailure — shared catch-block plumbing behind every `invokeExact` call site,
	// tested directly here since the call sites themselves can't force a MethodHandle to throw
	// without sabotaging it (see ADR 0004: a correctly configured downcall handle should never
	// reach its own catch block).
	// -----------------------------------------------------------------------

	@Test
	void wrapInvokeFailure_rethrowsARuntimeExceptionUnwrapped() {
		// Given
		var original = new IllegalStateException("boom");

		// When
		var thrown = assertThatThrownBy(() -> NativeCalls.wrapInvokeFailure("op failed", original));

		// Then
		thrown.isSameAs(original);
	}

	@Test
	void wrapInvokeFailure_wrapsAnIOExceptionAsUnchecked() {
		// Given
		var original = new IOException("disk full");

		// When
		var thrown = assertThatThrownBy(() -> NativeCalls.wrapInvokeFailure("op failed", original));

		// Then
		thrown.isInstanceOf(UncheckedIOException.class)
				.hasMessage("op failed")
				.hasCause(original);
	}

	@Test
	void wrapInvokeFailure_wrapsAnythingElseAsAnAssertionError() {
		// Given
		var original = new OutOfMemoryError("native alloc failed");

		// When
		var thrown = assertThatThrownBy(() -> NativeCalls.wrapInvokeFailure("op failed", original));

		// Then
		thrown.isInstanceOf(AssertionError.class)
				.hasMessage("op failed")
				.hasCause(original);
	}
}
