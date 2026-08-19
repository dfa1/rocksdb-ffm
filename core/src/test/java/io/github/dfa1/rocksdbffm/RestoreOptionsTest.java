package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RestoreOptionsTest {

	@Test
	void create_returnsUsableInstance() {
		// Given / When
		try (var sut = RestoreOptions.create()) {

			// Then
			assertThat(sut).isNotNull();
		}
	}

	@Test
	void setKeepLogFiles_returnsSameInstanceForChaining() {
		// Given
		try (var sut = RestoreOptions.create()) {

			// When
			var result = sut.setKeepLogFiles(true);

			// Then
			assertThat(result).isSameAs(sut);
		}
	}

	@Test
	void close_isIdempotent() {
		// Given — an already-closed instance
		var sut = RestoreOptions.create();
		sut.close();

		// When
		ThrowingCallable secondClose = sut::close;

		// Then — closing twice must not crash the JVM
		assertThatCode(secondClose).doesNotThrowAnyException();
	}
}
