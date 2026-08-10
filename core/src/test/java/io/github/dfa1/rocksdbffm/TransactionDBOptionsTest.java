package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDBOptionsTest {

	@Test
	void setMaxNumLocks_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setMaxNumLocks(42);

			// Then
			assertThat(sut.getMaxNumLocks()).isEqualTo(42);
		}
	}

	@Test
	void setMaxNumDeadlocks_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setMaxNumDeadlocks(10);

			// Then
			assertThat(sut.getMaxNumDeadlocks()).isEqualTo(10);
		}
	}

	@Test
	void setNumStripes_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setNumStripes(32);

			// Then
			assertThat(sut.getNumStripes()).isEqualTo(32);
		}
	}

	@Test
	void setTransactionLockTimeout_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setTransactionLockTimeout(5000);

			// Then
			assertThat(sut.getTransactionLockTimeout()).isEqualTo(5000);
		}
	}

	@Test
	void setDefaultLockTimeout_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setDefaultLockTimeout(2000);

			// Then
			assertThat(sut.getDefaultLockTimeout()).isEqualTo(2000);
		}
	}

	@Test
	void setWritePolicy_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setWritePolicy(TxnDBWritePolicy.WRITE_PREPARED);

			// Then
			assertThat(sut.getWritePolicy()).isEqualTo(TxnDBWritePolicy.WRITE_PREPARED);
		}
	}

	@Test
	void setRollbackMergeOperands_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setRollbackMergeOperands(true);

			// Then
			assertThat(sut.getRollbackMergeOperands()).isTrue();
		}
	}

	@Test
	void setUsePerKeyPointLockMgr_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setUsePerKeyPointLockMgr(true);

			// Then
			assertThat(sut.getUsePerKeyPointLockMgr()).isTrue();
		}
	}

	@Test
	void setSkipConcurrencyControl_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setSkipConcurrencyControl(true);

			// Then
			assertThat(sut.getSkipConcurrencyControl()).isTrue();
		}
	}

	@Test
	void setDefaultWriteBatchFlushThreshold_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setDefaultWriteBatchFlushThreshold(1024);

			// Then
			assertThat(sut.getDefaultWriteBatchFlushThreshold()).isEqualTo(1024);
		}
	}

	@Test
	void setEnableUdtValidation_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setEnableUdtValidation(true);

			// Then
			assertThat(sut.getEnableUdtValidation()).isTrue();
		}
	}

	@Test
	void setTxnCommitBypassMemtableThreshold_roundTrips() {
		// Given
		try (var sut = TransactionDBOptions.newTransactionDBOptions()) {

			// When
			sut.setTxnCommitBypassMemtableThreshold(100);

			// Then
			assertThat(sut.getTxnCommitBypassMemtableThreshold()).isEqualTo(100);
		}
	}
}
