package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockCacheTraceOptionsTest {

	@Test
	void newBlockCacheTraceOptions_hasRocksDBDefaults() {
		// Given

		// When
		try (var opts = BlockCacheTraceOptions.newBlockCacheTraceOptions()) {

			// Then
			assertThat(opts.getSamplingFrequency()).isEqualTo(1);
		}
	}

	@Test
	void setSamplingFrequency_roundTrips() {
		// Given
		try (var opts = BlockCacheTraceOptions.newBlockCacheTraceOptions().setSamplingFrequency(10)) {

			// When
			var result = opts.getSamplingFrequency();

			// Then
			assertThat(result).isEqualTo(10);
		}
	}
}
