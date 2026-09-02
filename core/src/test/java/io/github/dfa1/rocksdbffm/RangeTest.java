package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// [Range] holds `byte[]` components, so its `equals`/`hashCode`/`toString` are hand-written to
/// compare/print array content rather than array identity, unlike a record's generated defaults.
class RangeTest {

	@Test
	void equals_comparesArrayContentNotIdentity() {
		// Given
		var a = Range.of("start".getBytes(), "end".getBytes());
		var b = Range.of("start".getBytes(), "end".getBytes());

		// When
		var equal = a.equals(b);

		// Then
		assertThat(equal).isTrue();
	}

	@Test
	void hashCode_isConsistentWithEquals() {
		// Given
		var a = Range.of("start".getBytes(), "end".getBytes());
		var b = Range.of("start".getBytes(), "end".getBytes());

		// When
		var sameHash = a.hashCode() == b.hashCode();

		// Then
		assertThat(sameHash).isTrue();
	}

	@Test
	void equals_detectsDifferentContent() {
		// Given
		var a = Range.of("start".getBytes(), "end".getBytes());
		var b = Range.of("start".getBytes(), "different-end".getBytes());

		// When
		var equal = a.equals(b);

		// Then
		assertThat(equal).isFalse();
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
