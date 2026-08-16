package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class IOActivityTest {

	@ParameterizedTest
	@EnumSource(IOActivity.class)
	void fromValue_roundTrips(IOActivity activity) {
		// Given / When
		var result = IOActivity.fromValue(activity.getValue());

		// Then
		assertThat(result).isEqualTo(activity);
	}

	@Test
	void fromValue_unknownValue_fallsBackToUnknown() {
		// Given / When
		var result = IOActivity.fromValue(0x42);

		// Then
		assertThat(result).isEqualTo(IOActivity.UNKNOWN);
	}
}
