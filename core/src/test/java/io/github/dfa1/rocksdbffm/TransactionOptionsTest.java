package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionOptionsTest {

	@Test
	void setSetSnapshot_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setSetSnapshot(true);

			// Then
			assertThat(sut.getSetSnapshot()).isTrue();
		}
	}

	@Test
	void setDeadlockDetect_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setDeadlockDetect(true);

			// Then
			assertThat(sut.getDeadlockDetect()).isTrue();
		}
	}

	@Test
	void setUseOnlyTheLastCommitTimeBatchForRecovery_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setUseOnlyTheLastCommitTimeBatchForRecovery(true);

			// Then
			assertThat(sut.getUseOnlyTheLastCommitTimeBatchForRecovery()).isTrue();
		}
	}

	@Test
	void setLockTimeout_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setLockTimeout(5000);

			// Then
			assertThat(sut.getLockTimeout()).isEqualTo(5000);
		}
	}

	@Test
	void setDeadlockTimeoutUs_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setDeadlockTimeoutUs(250_000);

			// Then
			assertThat(sut.getDeadlockTimeoutUs()).isEqualTo(250_000);
		}
	}

	@Test
	void setExpiration_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setExpiration(60);

			// Then
			assertThat(sut.getExpiration()).isEqualTo(60);
		}
	}

	@Test
	void setDeadlockDetectDepth_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setDeadlockDetectDepth(100);

			// Then
			assertThat(sut.getDeadlockDetectDepth()).isEqualTo(100);
		}
	}

	@Test
	void setMaxWriteBatchSize_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setMaxWriteBatchSize(4096);

			// Then
			assertThat(sut.getMaxWriteBatchSize()).isEqualTo(4096);
		}
	}

	@Test
	void setSkipConcurrencyControl_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setSkipConcurrencyControl(true);

			// Then
			assertThat(sut.getSkipConcurrencyControl()).isTrue();
		}
	}

	@Test
	void setSkipPrepare_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setSkipPrepare(true);

			// Then
			assertThat(sut.getSkipPrepare()).isTrue();
		}
	}

	@Test
	void setWriteBatchFlushThreshold_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setWriteBatchFlushThreshold(2048);

			// Then
			assertThat(sut.getWriteBatchFlushThreshold()).isEqualTo(2048);
		}
	}

	@Test
	void setWriteBatchTrackTimestampSize_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setWriteBatchTrackTimestampSize(true);

			// Then
			assertThat(sut.getWriteBatchTrackTimestampSize()).isTrue();
		}
	}

	@Test
	void setCommitBypassMemtable_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setCommitBypassMemtable(true);

			// Then
			assertThat(sut.getCommitBypassMemtable()).isTrue();
		}
	}

	@Test
	void setLargeTxnCommitOptimizeThreshold_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setLargeTxnCommitOptimizeThreshold(500);

			// Then
			assertThat(sut.getLargeTxnCommitOptimizeThreshold()).isEqualTo(500);
		}
	}

	@Test
	void setLargeTxnCommitOptimizeByteThreshold_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setLargeTxnCommitOptimizeByteThreshold(1_048_576);

			// Then
			assertThat(sut.getLargeTxnCommitOptimizeByteThreshold()).isEqualTo(1_048_576);
		}
	}
}
