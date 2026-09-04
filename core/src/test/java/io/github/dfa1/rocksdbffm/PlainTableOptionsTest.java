package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTableOptionsTest {

	@Test
	void plainTable_allowsReadWrite(@TempDir Path dir) {
		// Given -- PlainTable requires a prefix extractor to build its hash buckets
		try (var opts = Options.newOptions()
				     .setCreateIfMissing(true)
				     .setPrefixExtractor(SliceTransform.newFixedPrefix(1))
				     .setTableFormatConfig(PlainTableOptions.newPlainTableOptions()
						     .setEncodingType(PlainTableOptions.EncodingType.PREFIX));
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void newPlainTableOptions_hasRocksDBDefaults() {
		// Given

		// When
		var options = PlainTableOptions.newPlainTableOptions();

		// Then
		assertThat(options.getUserKeyLength()).isEqualTo(PlainTableOptions.VARIABLE_LENGTH);
		assertThat(options.getBloomBitsPerKey()).isEqualTo(10);
		assertThat(options.getHashTableRatio()).isEqualTo(0.75);
		assertThat(options.getIndexSparseness()).isEqualTo(16);
		assertThat(options.getHugePageTlbSize()).isZero();
		assertThat(options.getEncodingType()).isEqualTo(PlainTableOptions.EncodingType.PLAIN);
		assertThat(options.isFullScanMode()).isFalse();
		assertThat(options.isStoreIndexInFile()).isFalse();
	}

	@Test
	void setUserKeyLength_roundTrips() {
		// Given
		var options = PlainTableOptions.newPlainTableOptions().setUserKeyLength(16);

		// When
		var result = options.getUserKeyLength();

		// Then
		assertThat(result).isEqualTo(16);
	}

	@Test
	void setBloomBitsPerKey_roundTrips() {
		// Given
		var options = PlainTableOptions.newPlainTableOptions().setBloomBitsPerKey(0);

		// When
		var result = options.getBloomBitsPerKey();

		// Then
		assertThat(result).isZero();
	}

	@Test
	void setHashTableRatio_roundTrips() {
		// Given
		var options = PlainTableOptions.newPlainTableOptions().setHashTableRatio(0.5);

		// When
		var result = options.getHashTableRatio();

		// Then
		assertThat(result).isEqualTo(0.5);
	}

	@Test
	void setIndexSparseness_roundTrips() {
		// Given
		var options = PlainTableOptions.newPlainTableOptions().setIndexSparseness(32);

		// When
		var result = options.getIndexSparseness();

		// Then
		assertThat(result).isEqualTo(32);
	}

	@Test
	void setHugePageTlbSize_roundTrips() {
		// Given
		var options = PlainTableOptions.newPlainTableOptions().setHugePageTlbSize(2 * 1024 * 1024);

		// When
		var result = options.getHugePageTlbSize();

		// Then
		assertThat(result).isEqualTo(2 * 1024 * 1024);
	}

	@Test
	void setEncodingType_roundTrips() {
		// Given
		var options = PlainTableOptions.newPlainTableOptions()
				.setEncodingType(PlainTableOptions.EncodingType.PREFIX);

		// When
		var result = options.getEncodingType();

		// Then
		assertThat(result).isEqualTo(PlainTableOptions.EncodingType.PREFIX);
	}

	@Test
	void setFullScanMode_roundTrips() {
		// Given
		var options = PlainTableOptions.newPlainTableOptions().setFullScanMode(true);

		// When
		var result = options.isFullScanMode();

		// Then
		assertThat(result).isTrue();
	}

	@Test
	void setStoreIndexInFile_roundTrips() {
		// Given
		var options = PlainTableOptions.newPlainTableOptions().setStoreIndexInFile(true);

		// When
		var result = options.isStoreIndexInFile();

		// Then
		assertThat(result).isTrue();
	}
}
