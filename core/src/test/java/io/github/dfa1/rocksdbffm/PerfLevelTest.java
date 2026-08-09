package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PerfLevelTest {

	@Test
	void fromValue_resolvesEveryDeclaredConstant() {
		for (PerfLevel level : PerfLevel.values()) {
			// Given / When
			var result = PerfLevel.fromValue(level.value);

			// Then
			assertThat(result).isEqualTo(level);
		}
	}

	@Test
	void fromValue_rejectsUnknownValue() {
		// Given / When / Then
		assertThatThrownBy(() -> PerfLevel.fromValue(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void values_matchExpectedOrdinals() {
		// Given / When / Then
		assertThat(PerfLevel.UNINITIALIZED.value).isZero();
		assertThat(PerfLevel.DISABLE.value).isEqualTo(1);
		assertThat(PerfLevel.ENABLE_COUNT.value).isEqualTo(2);
		assertThat(PerfLevel.ENABLE_TIME_EXCEPT_FOR_MUTEX.value).isEqualTo(3);
		assertThat(PerfLevel.ENABLE_TIME.value).isEqualTo(4);
	}
}
