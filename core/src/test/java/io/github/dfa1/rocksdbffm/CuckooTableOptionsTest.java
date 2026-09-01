package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CuckooTableOptionsTest {

	@Test
	void cuckooTable_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var cuckoo = CuckooTableOptions.newCuckooTableOptions().setHashTableRatio(0.75);
		     var opts = Options.newOptions()
				     .setCreateIfMissing(true)
				     .setTableFormatConfig(cuckoo);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void newCuckooTableOptions_hasRocksDBDefaults() {
		// Given

		// When
		try (var cuckoo = CuckooTableOptions.newCuckooTableOptions()) {

			// Then
			assertThat(cuckoo.getHashTableRatio()).isEqualTo(0.9);
			assertThat(cuckoo.getMaxSearchDepth()).isEqualTo(100);
			assertThat(cuckoo.getCuckooBlockSize()).isEqualTo(5);
			assertThat(cuckoo.getIdentityAsFirstHash()).isFalse();
			assertThat(cuckoo.getUseModuleHash()).isTrue();
		}
	}

	@Test
	void setHashTableRatio_roundTrips() {
		// Given
		try (var cuckoo = CuckooTableOptions.newCuckooTableOptions().setHashTableRatio(0.5)) {

			// When
			var result = cuckoo.getHashTableRatio();

			// Then
			assertThat(result).isEqualTo(0.5);
		}
	}

	@Test
	void setMaxSearchDepth_roundTrips() {
		// Given
		try (var cuckoo = CuckooTableOptions.newCuckooTableOptions().setMaxSearchDepth(200)) {

			// When
			var result = cuckoo.getMaxSearchDepth();

			// Then
			assertThat(result).isEqualTo(200);
		}
	}

	@Test
	void setCuckooBlockSize_roundTrips() {
		// Given
		try (var cuckoo = CuckooTableOptions.newCuckooTableOptions().setCuckooBlockSize(10)) {

			// When
			var result = cuckoo.getCuckooBlockSize();

			// Then
			assertThat(result).isEqualTo(10);
		}
	}

	@Test
	void setIdentityAsFirstHash_roundTrips() {
		// Given
		try (var cuckoo = CuckooTableOptions.newCuckooTableOptions().setIdentityAsFirstHash(true)) {

			// When
			var result = cuckoo.getIdentityAsFirstHash();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setUseModuleHash_roundTrips() {
		// Given
		try (var cuckoo = CuckooTableOptions.newCuckooTableOptions().setUseModuleHash(false)) {

			// When
			var result = cuckoo.getUseModuleHash();

			// Then
			assertThat(result).isFalse();
		}
	}
}
