package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Pure `fromValue(int)` decoder coverage for [BlockBasedTableOptions]'s nested enums --
/// the round-trip setter/getter tests in `TableOptionsTest` only exercise one branch of each
/// switch, leaving the rest of the branches (and the default-throw) uncovered.
class BlockBasedTableOptionsEnumsTest {

	@ParameterizedTest
	@CsvSource({
			"2, V2",
			"3, V3",
			"4, V4",
			"5, V5",
			"6, V6",
			"7, V7",
	})
	void formatVersion_fromValue_mapsEveryKnownValue(int value, BlockBasedTableOptions.FormatVersion expected) {
		// Given / When / Then
		assertThat(BlockBasedTableOptions.FormatVersion.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"1", "8"})
	void formatVersion_fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> BlockBasedTableOptions.FormatVersion.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({
			"0, NO_SHORTENING",
			"1, SHORTEN_SEPARATORS",
			"2, SHORTEN_SEPARATORS_AND_SUCCESSOR",
	})
	void indexShorteningMode_fromValue_mapsEveryKnownValue(int value, BlockBasedTableOptions.IndexShorteningMode expected) {
		// Given / When / Then
		assertThat(BlockBasedTableOptions.IndexShorteningMode.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"-1", "3"})
	void indexShorteningMode_fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> BlockBasedTableOptions.IndexShorteningMode.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({
			"0, BINARY",
			"1, INTERPOLATION",
			"2, AUTO",
	})
	void indexSearchType_fromValue_mapsEveryKnownValue(int value, BlockBasedTableOptions.IndexSearchType expected) {
		// Given / When / Then
		assertThat(BlockBasedTableOptions.IndexSearchType.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"-1", "3"})
	void indexSearchType_fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> BlockBasedTableOptions.IndexSearchType.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({
			"0, BINARY_SEARCH",
			"1, BINARY_AND_HASH",
	})
	void dataBlockIndexType_fromValue_mapsEveryKnownValue(int value, BlockBasedTableOptions.DataBlockIndexType expected) {
		// Given / When / Then
		assertThat(BlockBasedTableOptions.DataBlockIndexType.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"-1", "2"})
	void dataBlockIndexType_fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> BlockBasedTableOptions.DataBlockIndexType.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({
			"0, NO_CHECKSUM",
			"1, CRC32C",
			"2, XX_HASH",
			"3, XX_HASH64",
			"4, XXH3",
	})
	void checksumType_fromValue_mapsEveryKnownValue(int value, BlockBasedTableOptions.ChecksumType expected) {
		// Given / When / Then
		assertThat(BlockBasedTableOptions.ChecksumType.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"-1", "5"})
	void checksumType_fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> BlockBasedTableOptions.ChecksumType.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({
			"0, DISABLE",
			"1, FLUSH_ONLY",
			"2, FLUSH_AND_COMPACTION",
	})
	void prepopulateBlockCache_fromValue_mapsEveryKnownValue(int value, BlockBasedTableOptions.PrepopulateBlockCache expected) {
		// Given / When / Then
		assertThat(BlockBasedTableOptions.PrepopulateBlockCache.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"-1", "3"})
	void prepopulateBlockCache_fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> BlockBasedTableOptions.PrepopulateBlockCache.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
