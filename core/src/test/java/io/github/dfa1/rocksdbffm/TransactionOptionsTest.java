package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
			sut.setLockTimeout(Duration.ofSeconds(5));

			// Then
			assertThat(sut.getLockTimeout()).isEqualTo(Duration.ofSeconds(5));
		}
	}

	@Test
	void setLockTimeout_null_fallsBackToTransactionDbDefault() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setLockTimeout(null);

			// Then
			assertThat(sut.getLockTimeout()).isNull();
		}
	}

	@Test
	void setDeadlockTimeoutUs_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setDeadlockTimeoutUs(Duration.ofMillis(250));

			// Then
			assertThat(sut.getDeadlockTimeoutUs()).isEqualTo(Duration.ofMillis(250));
		}
	}

	@Test
	void setDeadlockTimeoutUs_null_throws() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When / Then
			assertThatThrownBy(() -> sut.setDeadlockTimeoutUs(null)).isInstanceOf(NullPointerException.class);
		}
	}

	@Test
	void setExpiration_roundTrips() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setExpiration(Duration.ofSeconds(60));

			// Then
			assertThat(sut.getExpiration()).isEqualTo(Duration.ofSeconds(60));
		}
	}

	@Test
	void setExpiration_null_disablesExpiration() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setExpiration(null);

			// Then
			assertThat(sut.getExpiration()).isNull();
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
			sut.setMaxWriteBatchSize(MemorySize.ofBytes(4096));

			// Then
			assertThat(sut.getMaxWriteBatchSize()).isEqualTo(MemorySize.ofBytes(4096));
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
			sut.setWriteBatchFlushThreshold(MemorySize.ofBytes(2048));

			// Then
			assertThat(sut.getWriteBatchFlushThreshold()).isEqualTo(MemorySize.ofBytes(2048));
		}
	}

	@Test
	void setWriteBatchFlushThreshold_null_roundTripsAsInheritDbDefault() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setWriteBatchFlushThreshold(null);

			// Then
			assertThat(sut.getWriteBatchFlushThreshold()).isNull();
		}
	}

	@Test
	void setWriteBatchFlushThreshold_zero_roundTripsAsDisabled() {
		// Given
		try (var sut = TransactionOptions.newTransactionOptions()) {

			// When
			sut.setWriteBatchFlushThreshold(MemorySize.ZERO);

			// Then
			assertThat(sut.getWriteBatchFlushThreshold()).isEqualTo(MemorySize.ZERO);
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
			sut.setLargeTxnCommitOptimizeByteThreshold(MemorySize.ofBytes(1_048_576));

			// Then
			assertThat(sut.getLargeTxnCommitOptimizeByteThreshold()).isEqualTo(MemorySize.ofBytes(1_048_576));
		}
	}
}
