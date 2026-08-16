package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class WritePolicyTest {

	@ParameterizedTest
	@EnumSource(WritePolicy.class)
	void fromValue_roundTrips(WritePolicy policy) {
		// Given / When
		var result = WritePolicy.fromValue(policy.getValue());

		// Then
		assertThat(result).isEqualTo(policy);
	}

	@Test
	void fromValue_unknownValue_fallsBackToWriteCommitted() {
		// Given / When
		var result = WritePolicy.fromValue(99);

		// Then
		assertThat(result).isEqualTo(WritePolicy.WRITE_COMMITTED);
	}
}
