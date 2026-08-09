package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupIdTest {

	@Test
	void of_storesExactValue() {
		// Given
		var sut = BackupId.of(42);

		// When
		var result = sut.toLong();

		// Then
		assertThat(result).isEqualTo(42);
	}

	@Test
	void of_acceptsZero() {
		// Given
		var sut = BackupId.of(0);

		// When
		var result = sut.toLong();

		// Then
		assertThat(result).isZero();
	}

	@Test
	void of_rejectsNegativeValues() {
		// Given / When / Then
		assertThatThrownBy(() -> BackupId.of(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void of_acceptsMaxUint32Value() {
		// Given
		var sut = BackupId.of(0xFFFFFFFFL);

		// When
		var result = sut.toLong();

		// Then
		assertThat(result).isEqualTo(0xFFFFFFFFL);
	}

	@Test
	void of_rejectsValuesAboveUint32Range() {
		// Given / When / Then
		assertThatThrownBy(() -> BackupId.of(0xFFFFFFFFL + 1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void fromNative_interpretsNegativeIntAsUnsigned() {
		// Given — a native uint32_t with the high bit set overflows into a negative Java int
		int nativeValue = -1; // bit pattern 0xFFFFFFFF

		// When
		var result = BackupId.fromNative(nativeValue);

		// Then
		assertThat(result.toLong()).isEqualTo(0xFFFFFFFFL);
	}

	@Test
	void toNativeInt_roundTripsThroughFromNative() {
		// Given
		var sut = BackupId.of(0xFFFFFFFFL);

		// When
		var result = sut.toNativeInt();

		// Then
		assertThat(BackupId.fromNative(result)).isEqualTo(sut);
	}

	@Test
	void compareTo_ordersUnsignedNearUint32Boundary() {
		// Given — 0x7FFFFFFF (as signed long) vs 0x80000000 (would be negative as a signed int)
		var belowBoundary = BackupId.of(0x7FFFFFFFL);
		var atBoundary = BackupId.of(0x80000000L);

		// When
		var result = belowBoundary.compareTo(atBoundary);

		// Then
		assertThat(result).isNegative();
	}

	@Test
	void equals_isByValue() {
		// Given / When / Then
		assertThat(BackupId.of(10)).isEqualTo(BackupId.of(10));
		assertThat(BackupId.of(10)).isNotEqualTo(BackupId.of(11));
	}

	@Test
	void equals_returnsFalseForNonBackupId() {
		// Given
		var sut = BackupId.of(1);

		// When
		var result = sut.equals("not a backup id");

		// Then
		assertThat(result).isFalse();
	}

	@Test
	void hashCode_isConsistentWithEquals() {
		// Given
		var a = BackupId.of(99);
		var b = BackupId.of(99);

		// When
		var hashA = a.hashCode();
		var hashB = b.hashCode();

		// Then
		assertThat(a).isEqualTo(b);
		assertThat(hashA).isEqualTo(hashB);
	}

	@Test
	void toString_includesValue() {
		// Given
		var sut = BackupId.of(7);

		// When
		var result = sut.toString();

		// Then
		assertThat(result).isEqualTo("BackupId(7)");
	}
}
