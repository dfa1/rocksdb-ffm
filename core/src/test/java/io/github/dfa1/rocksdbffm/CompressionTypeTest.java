package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompressionTypeTest {

	@ParameterizedTest
	@EnumSource(CompressionType.class)
	void fromValue_roundTrips(CompressionType type) {
		// Given / When
		var result = CompressionType.fromValue(type.value);

		// Then
		assertThat(result).isEqualTo(type);
	}

	@Test
	void fromValue_rejectsUnknownValue() {
		// Given / When / Then
		assertThatThrownBy(() -> CompressionType.fromValue(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	// -----------------------------------------------------------------------
	// Options integration
	// -----------------------------------------------------------------------

	@Test
	void options_setCompression_roundTrips() {
		try (Options opts = Options.newOptions()) {
			opts.setCompression(CompressionType.NO_COMPRESSION);
			assertThat(opts.getCompression()).isEqualTo(CompressionType.NO_COMPRESSION);
		}
	}

	@Test
	void options_setCompression_chaining() {
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(CompressionType.NO_COMPRESSION)) {
			assertThat(opts.getCompression()).isEqualTo(CompressionType.NO_COMPRESSION);
		}
	}

	@Test
	void openDb_withSupportedCompression_writesAndReadsBack(@TempDir Path dir) {
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(CompressionType.NO_COMPRESSION);
		     ReadWriteDB db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}
}
