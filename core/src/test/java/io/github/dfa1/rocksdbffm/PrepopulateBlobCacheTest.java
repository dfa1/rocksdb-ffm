package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrepopulateBlobCacheTest {

	@ParameterizedTest
	@EnumSource(PrepopulateBlobCache.class)
	void fromValue_roundTrips(PrepopulateBlobCache v) {
		// Given / When
		var result = PrepopulateBlobCache.fromValue(v.value);

		// Then
		assertThat(result).isEqualTo(v);
	}

	@Test
	void fromValue_rejectsUnknownValue() {
		// Given / When / Then
		assertThatThrownBy(() -> PrepopulateBlobCache.fromValue(99)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void values_matchExpectedOrdinals() {
		// Given / When / Then
		assertThat(PrepopulateBlobCache.DISABLE.value).isZero();
		assertThat(PrepopulateBlobCache.FLUSH_ONLY.value).isEqualTo(1);
	}

	@Test
	void options_roundTripsThroughSetAndGet() {
		// Given
		try (var opts = Options.newOptions()) {

			// When
			opts.setPrepopulateBlobCache(PrepopulateBlobCache.FLUSH_ONLY);
			var result = opts.getPrepopulateBlobCache();

			// Then
			assertThat(result).isEqualTo(PrepopulateBlobCache.FLUSH_ONLY);
		}
	}
}
