package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SizeApproximationOptionsTest {

	@Test
	void create_hasRocksDBDefaults() {
		// Given

		// When
		try (var opts = SizeApproximationOptions.create()) {

			// Then
			assertThat(opts.isIncludeMemtables()).isFalse();
			assertThat(opts.isIncludeFiles()).isTrue();
			assertThat(opts.isIncludeBlobFiles()).isFalse();
			assertThat(opts.getFilesSizeErrorMargin()).isEqualTo(-1.0);
		}
	}

	@Test
	void setIncludeMemtables_roundTrips() {
		// Given
		try (var opts = SizeApproximationOptions.create().setIncludeMemtables(true)) {

			// When
			var result = opts.isIncludeMemtables();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setIncludeFiles_roundTrips() {
		// Given
		try (var opts = SizeApproximationOptions.create().setIncludeFiles(false)) {

			// When
			var result = opts.isIncludeFiles();

			// Then
			assertThat(result).isFalse();
		}
	}

	@Test
	void setIncludeBlobFiles_roundTrips() {
		// Given
		try (var opts = SizeApproximationOptions.create().setIncludeBlobFiles(true)) {

			// When
			var result = opts.isIncludeBlobFiles();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setFilesSizeErrorMargin_roundTrips() {
		// Given
		try (var opts = SizeApproximationOptions.create().setFilesSizeErrorMargin(0.1)) {

			// When
			var result = opts.getFilesSizeErrorMargin();

			// Then
			assertThat(result).isEqualTo(0.1);
		}
	}
}
