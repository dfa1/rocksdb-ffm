package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptionsTest {

	@Test
	void newOptions_hasRocksDBLsmDefaults() {
		// Given

		// When
		try (var opts = Options.newOptions()) {

			// Then
			assertThat(opts.getWriteBufferSize()).isEqualTo(MemorySize.ofMB(64));
			assertThat(opts.getNumLevels()).isEqualTo(7);
			assertThat(opts.getLevel0FileNumCompactionTrigger()).isEqualTo(4);
			assertThat(opts.getTargetFileSizeBase()).isEqualTo(MemorySize.ofMB(64));
			assertThat(opts.getMaxBytesForLevelBase()).isEqualTo(MemorySize.ofMB(256));
		}
	}

	@Test
	void setWriteBufferSize_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setWriteBufferSize(MemorySize.ofKB(64))) {

			// When
			var result = opts.getWriteBufferSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofKB(64));
		}
	}

	@Test
	void setNumLevels_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setNumLevels(3)) {

			// When
			var result = opts.getNumLevels();

			// Then
			assertThat(result).isEqualTo(3);
		}
	}

	@Test
	void setLevel0FileNumCompactionTrigger_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setLevel0FileNumCompactionTrigger(2)) {

			// When
			var result = opts.getLevel0FileNumCompactionTrigger();

			// Then
			assertThat(result).isEqualTo(2);
		}
	}

	@Test
	void setTargetFileSizeBase_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setTargetFileSizeBase(MemorySize.ofMB(2))) {

			// When
			var result = opts.getTargetFileSizeBase();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(2));
		}
	}

	@Test
	void setMaxBytesForLevelBase_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMaxBytesForLevelBase(MemorySize.ofMB(8))) {

			// When
			var result = opts.getMaxBytesForLevelBase();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(8));
		}
	}
}
