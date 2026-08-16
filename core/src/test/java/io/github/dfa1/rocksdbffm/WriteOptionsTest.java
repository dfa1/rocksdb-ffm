package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WriteOptionsTest {

	@Test
	void setSync_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setSync(true);

			// Then
			assertThat(sut.isSync()).isTrue();
		}
	}

	@Test
	void setDisableWal_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setDisableWal(true);

			// Then
			assertThat(sut.isDisableWal()).isTrue();
		}
	}

	@Test
	void setIgnoreMissingColumnFamilies_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setIgnoreMissingColumnFamilies(true);

			// Then
			assertThat(sut.isIgnoreMissingColumnFamilies()).isTrue();
		}
	}

	@Test
	void setNoSlowdown_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setNoSlowdown(true);

			// Then
			assertThat(sut.isNoSlowdown()).isTrue();
		}
	}

	@Test
	void setLowPri_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setLowPri(true);

			// Then
			assertThat(sut.isLowPri()).isTrue();
		}
	}

	@Test
	void setMemtableInsertHintPerBatch_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setMemtableInsertHintPerBatch(true);

			// Then
			assertThat(sut.isMemtableInsertHintPerBatch()).isTrue();
		}
	}

	@Test
	void setRateLimiterPriority_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setRateLimiterPriority(IOPriority.HIGH);

			// Then
			assertThat(sut.getRateLimiterPriority()).isEqualTo(IOPriority.HIGH);
		}
	}

	@Test
	void setIoActivity_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setIoActivity(IOActivity.FLUSH);

			// Then
			assertThat(sut.getIoActivity()).isEqualTo(IOActivity.FLUSH);
		}
	}

	@Test
	void defaults_matchRocksDbDefaults() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When / Then
			assertThat(sut.isSync()).isFalse();
			assertThat(sut.isDisableWal()).isFalse();
			assertThat(sut.isIgnoreMissingColumnFamilies()).isFalse();
			assertThat(sut.isNoSlowdown()).isFalse();
			assertThat(sut.isLowPri()).isFalse();
			assertThat(sut.isMemtableInsertHintPerBatch()).isFalse();
			assertThat(sut.getRateLimiterPriority()).isEqualTo(IOPriority.TOTAL);
			assertThat(sut.getIoActivity()).isEqualTo(IOActivity.UNKNOWN);
		}
	}
}
