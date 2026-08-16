package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatsLevelTest {

	// EXCEPT_TICKERS shares native value 0 with DISABLE_ALL (kExceptTickers = kDisableAll
	// upstream), so it is excluded here and covered by its own test below.
	@ParameterizedTest
	@EnumSource(value = StatsLevel.class, names = "EXCEPT_TICKERS", mode = EnumSource.Mode.EXCLUDE)
	void fromValue_roundTrips(StatsLevel level) {
		// Given / When
		var result = StatsLevel.fromValue(level.getValue());

		// Then
		assertThat(result).isEqualTo(level);
	}

	@Test
	void fromValue_exceptTickersAliasesToDisableAll() {
		// Given / When
		var result = StatsLevel.fromValue(StatsLevel.EXCEPT_TICKERS.getValue());

		// Then
		assertThat(result).isEqualTo(StatsLevel.DISABLE_ALL);
	}

	@Test
	void fromValue_unknownValue_fallsBackToDisableAll() {
		// Given / When
		var result = StatsLevel.fromValue(99);

		// Then
		assertThat(result).isEqualTo(StatsLevel.DISABLE_ALL);
	}
}
