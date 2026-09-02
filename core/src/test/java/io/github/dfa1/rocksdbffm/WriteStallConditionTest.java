package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WriteStallConditionTest {

	@Test
	void fromValue_decodesEveryKnownValue() {
		// Given / When / Then
		assertThat(WriteStallCondition.fromValue(0)).isEqualTo(WriteStallCondition.DELAYED);
		assertThat(WriteStallCondition.fromValue(1)).isEqualTo(WriteStallCondition.STOPPED);
		assertThat(WriteStallCondition.fromValue(2)).isEqualTo(WriteStallCondition.NORMAL);
	}

	@Test
	void fromValue_unknownValue_throws() {
		// Given / When / Then
		assertThatThrownBy(() -> WriteStallCondition.fromValue(3)).isInstanceOf(IllegalArgumentException.class);
	}
}
