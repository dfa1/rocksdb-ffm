package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
		// Given
		var sut = RestoreOptions.create();

		// When
		sut.close();

		// Then — closing twice must not crash the JVM
		sut.close();
	}
}
