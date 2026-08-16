package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class LogLevelTest {

	@ParameterizedTest
	@EnumSource(LogLevel.class)
	void fromValue_roundTrips(LogLevel level) {
		// Given / When
		var result = LogLevel.fromValue(level.value);

		// Then
		assertThat(result).isEqualTo(level);
	}

	@Test
	void fromValue_unknownValue_fallsBackToInfo() {
		// Given / When
		var result = LogLevel.fromValue(99);

		// Then
		assertThat(result).isEqualTo(LogLevel.INFO);
	}
}
