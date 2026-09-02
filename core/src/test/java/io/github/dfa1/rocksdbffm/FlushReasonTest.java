package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlushReasonTest {

	@ParameterizedTest
	@CsvSource({
			"0, OTHERS",
			"1, GET_LIVE_FILES",
			"2, SHUT_DOWN",
			"3, EXTERNAL_FILE_INGESTION",
			"4, MANUAL_COMPACTION",
			"5, WRITE_BUFFER_MANAGER",
			"6, WRITE_BUFFER_FULL",
			"7, TEST",
			"8, DELETE_FILES",
			"9, AUTO_COMPACTION",
			"10, MANUAL_FLUSH",
			"11, ERROR_RECOVERY",
			"12, ERROR_RECOVERY_RETRY_FLUSH",
			"13, WAL_FULL",
			"14, CATCH_UP_AFTER_ERROR_RECOVERY",
			"15, MEMTABLE_MAX_RANGE_DELETIONS",
	})
	void fromValue_mapsEveryKnownValue(int value, FlushReason expected) {
		// Given / When / Then
		assertThat(FlushReason.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"-1", "16"})
	void fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> FlushReason.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
