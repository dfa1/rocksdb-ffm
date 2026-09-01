package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayOptionsTest {

	@Test
	void newReplayOptions_hasRocksDBDefaults() {
		// Given

		// When
		try (var opts = ReplayOptions.newReplayOptions()) {

			// Then
			assertThat(opts.getNumThreads()).isEqualTo(1);
			assertThat(opts.getFastForward()).isEqualTo(1.0);
		}
	}

	@Test
	void setNumThreads_roundTrips() {
		// Given
		try (var opts = ReplayOptions.newReplayOptions().setNumThreads(4)) {

			// When
			var result = opts.getNumThreads();

			// Then
			assertThat(result).isEqualTo(4);
		}
	}

	@Test
	void setFastForward_roundTrips() {
		// Given
		try (var opts = ReplayOptions.newReplayOptions().setFastForward(2.5)) {

			// When
			var result = opts.getFastForward();

			// Then
			assertThat(result).isEqualTo(2.5);
		}
	}
}
