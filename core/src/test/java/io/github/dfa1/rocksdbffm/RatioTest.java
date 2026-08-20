package io.github.dfa1.rocksdbffm;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatioTest {

	@Test
	void of_storesExactValue() {
		// Given
		var sut = Ratio.of(0.25);

		// When
		var result = sut.toDouble();

		// Then
		assertThat(result).isEqualTo(0.25);
	}

	@Test
	void of_acceptsZero() {
		// Given
		var sut = Ratio.of(0.0);

		// When
		var result = sut.toDouble();

		// Then
		assertThat(result).isZero();
	}

	@Test
	void of_acceptsOne() {
		// Given
		var sut = Ratio.of(1.0);

		// When
		var result = sut.toDouble();

		// Then
		assertThat(result).isEqualTo(1.0);
	}

	@Test
	void of_rejectsValuesBelowZero() {
		// Given / When / Then
		assertThatThrownBy(() -> Ratio.of(-0.01)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void of_rejectsValuesAboveOne() {
		// Given / When / Then
		assertThatThrownBy(() -> Ratio.of(1.01)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void of_rejectsNaN() {
		// Given / When / Then
		assertThatThrownBy(() -> Ratio.of(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void of_rejectsPositiveInfinity() {
		// Given / When / Then
		assertThatThrownBy(() -> Ratio.of(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void of_rejectsNegativeInfinity() {
		// Given / When / Then
		assertThatThrownBy(() -> Ratio.of(Double.NEGATIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void zero_equalsOfZero() {
		// Given
		var sut = Ratio.ZERO;

		// When
		var result = sut.equals(Ratio.of(0.0));

		// Then
		assertThat(result).isTrue();
	}

	@Test
	void one_equalsOfOne() {
		// Given
		var sut = Ratio.ONE;

		// When
		var result = sut.equals(Ratio.of(1.0));

		// Then
		assertThat(result).isTrue();
	}

	@Test
	void compareTo_ordersCorrectly() {
		// Given
		var a = Ratio.of(0.25);
		var b = Ratio.of(0.5);
		var c = Ratio.of(0.5);

		// When
		var abOrder = a.compareTo(b);
		var baOrder = b.compareTo(a);
		var bcOrder = b.compareTo(c);

		// Then
		assertThat(abOrder).isNegative();
		assertThat(baOrder).isPositive();
		assertThat(bcOrder).isZero();
	}

	@Test
	void equalsAndHashCode_satisfyContract() {
		// Given / When / Then
		EqualsVerifier.forClass(Ratio.class).verify();
	}

	@Test
	void toString_includesValue() {
		// Given
		var sut = Ratio.of(0.5);

		// When
		var result = sut.toString();

		// Then
		assertThat(result).isEqualTo("Ratio(0.5)");
	}
}
