package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class IOPriorityTest {

	@ParameterizedTest
	@EnumSource(IOPriority.class)
	void fromValue_roundTrips(IOPriority priority) {
		// Given / When
		var result = IOPriority.fromValue(priority.getValue());

		// Then
		assertThat(result).isEqualTo(priority);
	}

	@Test
	void fromValue_unknownValue_fallsBackToTotal() {
		// Given / When
		var result = IOPriority.fromValue(99);

		// Then
		assertThat(result).isEqualTo(IOPriority.TOTAL);
	}
}
