package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactionReasonTest {

	@ParameterizedTest
	@CsvSource({
			"0, UNKNOWN",
			"1, LEVEL_L0_FILES_NUM",
			"2, LEVEL_MAX_LEVEL_SIZE",
			"3, UNIVERSAL_SIZE_AMPLIFICATION",
			"4, UNIVERSAL_SIZE_RATIO",
			"5, UNIVERSAL_SORTED_RUN_NUM",
			"6, FIFO_MAX_SIZE",
			"7, FIFO_REDUCE_NUM_FILES",
			"8, FIFO_TTL",
			"9, MANUAL_COMPACTION",
			"10, FILES_MARKED_FOR_COMPACTION",
			"11, BOTTOMMOST_FILES",
			"12, TTL",
			"13, FLUSH",
			"14, EXTERNAL_SST_INGESTION",
			"15, PERIODIC_COMPACTION",
			"16, CHANGE_TEMPERATURE",
			"17, FORCED_BLOB_GC",
			"18, ROUND_ROBIN_TTL",
			"19, REFIT_LEVEL",
			"20, READ_TRIGGERED",
	})
	void fromValue_mapsEveryKnownValue(int value, CompactionReason expected) {
		// Given / When / Then
		assertThat(CompactionReason.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"-1", "21"})
	void fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> CompactionReason.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
