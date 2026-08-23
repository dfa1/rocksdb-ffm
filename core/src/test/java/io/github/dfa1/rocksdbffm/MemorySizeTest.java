package io.github.dfa1.rocksdbffm;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemorySizeTest {

	@Test
	void ofBytes_storesExactValue() {
		// Given / When
		var sut = MemorySize.ofBytes(42);

		// Then
		assertThat(sut.toBytes()).isEqualTo(42);
	}

	@Test
	void ofKB_convertsToBytes() {
		// Given / When
		var sut = MemorySize.ofKB(1);

		// Then
		assertThat(sut.toBytes()).isEqualTo(1024);
	}

	@Test
	void ofMB_convertsToBytes() {
		// Given / When
		var sut = MemorySize.ofMB(1);

		// Then
		assertThat(sut.toBytes()).isEqualTo(1024 * 1024);
	}

	@Test
	void ofGB_convertsToBytes() {
		// Given / When
		var sut = MemorySize.ofGB(1);

		// Then
		assertThat(sut.toBytes()).isEqualTo(1024 * 1024 * 1024L);
	}

	@Test
	void zero_hasZeroBytes() {
		assertThat(MemorySize.ZERO.toBytes()).isZero();
		assertThat(MemorySize.ZERO).isEqualTo(MemorySize.ofBytes(0));
	}

	@Test
	void constructor_rejectsNegativeValues() {
		assertThatThrownBy(() -> MemorySize.ofBytes(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MemorySize.ofKB(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MemorySize.ofMB(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MemorySize.ofGB(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void factory_throwsOnOverflow() {
		assertThatThrownBy(() -> MemorySize.ofGB(Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> MemorySize.ofMB(Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void equals_isByteExact() {
		// Given / When / Then
		assertThat(MemorySize.ofKB(1024)).isEqualTo(MemorySize.ofMB(1));
		assertThat(MemorySize.ofMB(1024)).isEqualTo(MemorySize.ofGB(1));
		assertThat(MemorySize.ofKB(1)).isNotEqualTo(MemorySize.ofMB(1));
	}

	@Test
	void equalsAndHashCode_satisfyContract() {
		// Given / When / Then
		EqualsVerifier.forClass(MemorySize.class).verify();
	}

	@Test
	void compareTo_ordersCorrectly() {
		assertThat(MemorySize.ofKB(1)).isLessThan(MemorySize.ofMB(1));
		assertThat(MemorySize.ofGB(1)).isGreaterThan(MemorySize.ofMB(1));
		assertThat(MemorySize.ofMB(1)).isEqualByComparingTo(MemorySize.ofKB(1024));
	}

	@Test
	void toString_showsLargestEvenUnit() {
		assertThat(MemorySize.ofGB(1)).hasToString("1GB");
		assertThat(MemorySize.ofMB(64)).hasToString("64MB");
		assertThat(MemorySize.ofKB(16)).hasToString("16KB");
		assertThat(MemorySize.ofBytes(100)).hasToString("100B");
		assertThat(MemorySize.ZERO).hasToString("0B");
		// 1536 = 1.5 KB — not evenly divisible by KB
		assertThat(MemorySize.ofBytes(1536)).hasToString("1536B");
	}
}
