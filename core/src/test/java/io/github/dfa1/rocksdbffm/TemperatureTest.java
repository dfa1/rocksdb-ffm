package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TemperatureTest {

	@ParameterizedTest
	@EnumSource(Temperature.class)
	void fromValue_roundTrips(Temperature temperature) {
		// Given / When
		var result = Temperature.fromValue(temperature.getValue());

		// Then
		assertThat(result).isEqualTo(temperature);
	}

	@Test
	void fromValue_unknownValueFallsBackToUnknown() {
		// Given / When
		var result = Temperature.fromValue(-1);

		// Then
		assertThat(result).isEqualTo(Temperature.UNKNOWN);
	}

	// -----------------------------------------------------------------------
	// Options integration
	// -----------------------------------------------------------------------

	@Test
	void options_defaultTemperature_isUnknown() {
		// Given / When
		try (Options opts = Options.newOptions()) {

			// Then
			assertThat(opts.getMetadataWriteTemperature()).isEqualTo(Temperature.UNKNOWN);
			assertThat(opts.getWalWriteTemperature()).isEqualTo(Temperature.UNKNOWN);
			assertThat(opts.getLastLevelTemperature()).isEqualTo(Temperature.UNKNOWN);
			assertThat(opts.getDefaultWriteTemperature()).isEqualTo(Temperature.UNKNOWN);
			assertThat(opts.getDefaultTemperature()).isEqualTo(Temperature.UNKNOWN);
		}
	}

	@Test
	void options_setMetadataWriteTemperature_roundTrips() {
		// Given
		try (Options opts = Options.newOptions()) {

			// When
			opts.setMetadataWriteTemperature(Temperature.HOT);

			// Then
			assertThat(opts.getMetadataWriteTemperature()).isEqualTo(Temperature.HOT);
		}
	}

	@Test
	void options_setWalWriteTemperature_roundTrips() {
		// Given
		try (Options opts = Options.newOptions()) {

			// When
			opts.setWalWriteTemperature(Temperature.WARM);

			// Then
			assertThat(opts.getWalWriteTemperature()).isEqualTo(Temperature.WARM);
		}
	}

	@Test
	void options_setLastLevelTemperature_roundTrips() {
		// Given
		try (Options opts = Options.newOptions()) {

			// When
			opts.setLastLevelTemperature(Temperature.COLD);

			// Then
			assertThat(opts.getLastLevelTemperature()).isEqualTo(Temperature.COLD);
		}
	}

	@Test
	void options_setDefaultWriteTemperature_roundTrips() {
		// Given
		try (Options opts = Options.newOptions()) {

			// When
			opts.setDefaultWriteTemperature(Temperature.COOL);

			// Then
			assertThat(opts.getDefaultWriteTemperature()).isEqualTo(Temperature.COOL);
		}
	}

	@Test
	void options_setDefaultTemperature_roundTrips() {
		// Given
		try (Options opts = Options.newOptions()) {

			// When
			opts.setDefaultTemperature(Temperature.ICE);

			// Then
			assertThat(opts.getDefaultTemperature()).isEqualTo(Temperature.ICE);
		}
	}

	@Test
	void options_setTemperature_chaining() {
		// Given / When
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setDefaultTemperature(Temperature.HOT)) {

			// Then
			assertThat(opts.getDefaultTemperature()).isEqualTo(Temperature.HOT);
		}
	}
}
