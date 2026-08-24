package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SliceTransformTest {

	@Test
	void newFixedPrefix_negativePrefixLen_throws() {
		// Given / When / Then
		assertThatThrownBy(() -> SliceTransform.newFixedPrefix(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void newFixedPrefix_closesWithoutOwnershipTransfer() {
		// Given
		var sut = SliceTransform.newFixedPrefix(4);

		// When
		sut.close();

		// Then — tryClose (native destroy) actually ran; ptr() now reports closed
		assertThatThrownBy(sut::ptr).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void close_isIdempotent() {
		// Given
		var sut = SliceTransform.newFixedPrefix(4);
		sut.close();

		// When — NativeObject.close() swaps its pointer reference to NULL atomically before
		// calling tryClose, so a second close() is guaranteed to skip tryClose entirely rather
		// than double-free; this only re-confirms close() itself never throws, since
		// NativeObject.close() always catches and logs rather than rethrowing
		ThrowingCallable action = sut::close;

		// Then
		assertThatCode(action).doesNotThrowAnyException();
	}

	@Test
	void setPrefixExtractor_transferredTransform_closeIsSafeNoOp() {
		// Given
		var transform = SliceTransform.newFixedPrefix(4);
		try (var opts = Options.newOptions().setPrefixExtractor(transform)) {

			// When — ownership already transferred to opts; transform.ptr() is NULL, so
			// transform.close() finds nothing to free (same guarantee as close_isIdempotent
			// above: this confirms close() doesn't throw, not that no native call was made)
			ThrowingCallable action = transform::close;

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	@Test
	void setPrefixExtractor_transferredTransform_ptrThrows() {
		// Given
		var transform = SliceTransform.newFixedPrefix(4);

		// When
		try (var opts = Options.newOptions().setPrefixExtractor(transform)) {

			// Then
			assertThatThrownBy(transform::ptr).isInstanceOf(IllegalStateException.class);
		}
	}
}
