package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UniversalCompactionOptionsTest {

	@Test
	void universalCompaction_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var universal = UniversalCompactionOptions.newUniversalCompactionOptions()
				     .setMinMergeWidth(2)
				     .setMaxMergeWidth(20)
				     .setMaxSizeAmplificationPercent(200)
				     .setSizeRatio(1)
				     .setCompressionSizePercent(-1)
				     .setStopStyle(UniversalCompactionOptions.StopStyle.TOTAL_SIZE);
		     var opts = Options.newOptions()
				     .setCreateIfMissing(true)
				     .setCompactionStyle(Options.CompactionStyle.UNIVERSAL)
				     .setUniversalCompactionOptions(universal);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void setSizeRatio_roundTrips() {
		// Given
		try (var universal = UniversalCompactionOptions.newUniversalCompactionOptions().setSizeRatio(5)) {

			// When
			var result = universal.getSizeRatio();

			// Then
			assertThat(result).isEqualTo(5);
		}
	}

	@Test
	void setMinMergeWidth_roundTrips() {
		// Given
		try (var universal = UniversalCompactionOptions.newUniversalCompactionOptions().setMinMergeWidth(3)) {

			// When
			var result = universal.getMinMergeWidth();

			// Then
			assertThat(result).isEqualTo(3);
		}
	}

	@Test
	void setMaxMergeWidth_roundTrips() {
		// Given
		try (var universal = UniversalCompactionOptions.newUniversalCompactionOptions().setMaxMergeWidth(10)) {

			// When
			var result = universal.getMaxMergeWidth();

			// Then
			assertThat(result).isEqualTo(10);
		}
	}

	@Test
	void setMaxSizeAmplificationPercent_roundTrips() {
		// Given
		try (var universal = UniversalCompactionOptions.newUniversalCompactionOptions()
				     .setMaxSizeAmplificationPercent(150)) {

			// When
			var result = universal.getMaxSizeAmplificationPercent();

			// Then
			assertThat(result).isEqualTo(150);
		}
	}

	@Test
	void setCompressionSizePercent_roundTrips() {
		// Given
		try (var universal = UniversalCompactionOptions.newUniversalCompactionOptions()
				     .setCompressionSizePercent(50)) {

			// When
			var result = universal.getCompressionSizePercent();

			// Then
			assertThat(result).isEqualTo(50);
		}
	}

	@Test
	void setStopStyle_roundTrips() {
		// Given
		try (var universal = UniversalCompactionOptions.newUniversalCompactionOptions()
				     .setStopStyle(UniversalCompactionOptions.StopStyle.SIMILAR_SIZE)) {

			// When
			var result = universal.getStopStyle();

			// Then
			assertThat(result).isEqualTo(UniversalCompactionOptions.StopStyle.SIMILAR_SIZE);
		}
	}

	@Test
	void setCompactionStyle_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setCompactionStyle(Options.CompactionStyle.UNIVERSAL)) {

			// When
			var result = opts.getCompactionStyle();

			// Then
			assertThat(result).isEqualTo(Options.CompactionStyle.UNIVERSAL);
		}
	}
}
