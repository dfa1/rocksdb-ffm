package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockCacheTraceWriterOptionsTest {

	@Test
	void newBlockCacheTraceWriterOptions_hasRocksDBDefaults() {
		// Given

		// When
		try (var opts = BlockCacheTraceWriterOptions.newBlockCacheTraceWriterOptions()) {

			// Then
			assertThat(opts.getMaxTraceFileSize()).isEqualTo(MemorySize.ofBytes(64L * 1024 * 1024 * 1024));
		}
	}

	@Test
	void setMaxTraceFileSize_roundTrips() {
		// Given
		try (var opts = BlockCacheTraceWriterOptions.newBlockCacheTraceWriterOptions()
				     .setMaxTraceFileSize(MemorySize.ofMB(10))) {

			// When
			var result = opts.getMaxTraceFileSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(10));
		}
	}
}
