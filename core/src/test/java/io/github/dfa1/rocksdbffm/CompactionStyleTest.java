package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Pure `fromValue(int)` decoder coverage for [Options.CompactionStyle] -- existing tests only
/// exercise [Options.CompactionStyle#FIFO] and [Options.CompactionStyle#UNIVERSAL] via their
/// respective options classes, leaving [Options.CompactionStyle#LEVEL] (the default) and the
/// default-throw branch uncovered.
class CompactionStyleTest {

	@ParameterizedTest
	@CsvSource({
			"0, LEVEL",
			"1, UNIVERSAL",
			"2, FIFO",
	})
	void fromValue_mapsEveryKnownValue(int value, Options.CompactionStyle expected) {
		// Given / When / Then
		assertThat(Options.CompactionStyle.fromValue(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"-1", "3"})
	void fromValue_rejectsUnknownValue(int value) {
		// Given / When / Then
		assertThatThrownBy(() -> Options.CompactionStyle.fromValue(value))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
