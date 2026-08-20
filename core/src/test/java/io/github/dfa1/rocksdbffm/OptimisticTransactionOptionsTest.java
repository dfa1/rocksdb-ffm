package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisticTransactionOptionsTest {

	@Test
	void setSetSnapshot_roundTrips() {
		// Given
		try (var sut = OptimisticTransactionOptions.newOptimisticTransactionOptions()) {

			// When
			sut.setSetSnapshot(true);

			// Then
			assertThat(sut.getSetSnapshot()).isTrue();
		}
	}

	@Test
	void setSetSnapshot_defaultsToFalse() {
		// Given / When
		try (var sut = OptimisticTransactionOptions.newOptimisticTransactionOptions()) {

			// Then
			assertThat(sut.getSetSnapshot()).isFalse();
		}
	}
}
