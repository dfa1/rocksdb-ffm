package io.github.dfa1.rocksdbffm;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// [Range] holds `byte[]` components, so its `equals`/`hashCode`/`toString` are hand-written to
/// compare/print array content rather than array identity, unlike a record's generated defaults.
class RangeTest {

	@Test
	void equalsAndHashCode_satisfyContract() {
		// Given / When / Then
		EqualsVerifier.forClass(Range.class).verify();
	}

	@Test
	void toString_printsArrayContent() {
		// Given
		var range = Range.of("a".getBytes(), "b".getBytes());

		// When
		var result = range.toString();

		// Then
		assertThat(result).isEqualTo("Range[startKey=[97], endKey=[98]]");
	}
}
