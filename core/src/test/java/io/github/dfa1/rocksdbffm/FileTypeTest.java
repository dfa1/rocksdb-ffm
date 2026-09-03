package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeTest {

	@ParameterizedTest
	@EnumSource(FileType.class)
	void fromValue_roundTrips(FileType fileType) {
		// Given / When
		var result = FileType.fromValue(fileType.getValue());

		// Then
		assertThat(result).isEqualTo(fileType);
	}

	@Test
	void fromValue_unknownValue_fallsBackToTempFile() {
		// Given / When
		var result = FileType.fromValue(99);

		// Then
		assertThat(result).isEqualTo(FileType.TEMP_FILE);
	}
}
