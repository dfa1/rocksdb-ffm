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
		var result = CompressionType.fromValue(type.getValue());

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

	@Test
	void openDb_withZstdCompression_writesAndReadsBack(@TempDir Path dir) {
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(CompressionType.ZSTD);
		     ReadWriteDB db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// A round-trip test alone can't tell a working codec from a no-op one, since
	// get/put succeeds either way. Comparing on-disk SST size against
	// NO_COMPRESSION for deliberately repetitive data catches that failure mode.
	@Test
	void openDb_withZstdCompression_actuallyCompressesOnDisk(@TempDir Path noCompressionDir, @TempDir Path zstdDir) {
		byte[] value = "a".repeat(64 * 1024).getBytes();

		long uncompressedSize;
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(CompressionType.NO_COMPRESSION);
		     ReadWriteDB db = RocksDB.openReadWrite(opts, noCompressionDir);
		     FlushOptions flushOptions = FlushOptions.newFlushOptions()) {
			for (int i = 0; i < 100; i++) {
				db.put(("k" + i).getBytes(), value);
			}
			db.flush(flushOptions);
			uncompressedSize = db.getLongProperty(Property.TOTAL_SST_FILES_SIZE).orElseThrow();
		}

		long zstdSize;
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(CompressionType.ZSTD);
		     ReadWriteDB db = RocksDB.openReadWrite(opts, zstdDir);
		     FlushOptions flushOptions = FlushOptions.newFlushOptions()) {
			for (int i = 0; i < 100; i++) {
				db.put(("k" + i).getBytes(), value);
			}
			db.flush(flushOptions);
			zstdSize = db.getLongProperty(Property.TOTAL_SST_FILES_SIZE).orElseThrow();
		}

		assertThat(zstdSize).isLessThan(uncompressedSize / 2);
	}

	// SNAPPY is force-disabled in the bundled native library (see
	// scripts/build-rocksdb.sh), so this exercises RocksDB's own
	// ColumnFamilyData::ValidateOptions -> CheckCompressionSupported check: DB::Open
	// rejects a compression type that isn't linked into the binary with
	// Status::InvalidArgument, rather than silently opening and no-op'ing the
	// compression, so misconfiguration is caught at open time, not discovered later
	// as an unexplained lack of compression.
	@Test
	void openDb_withUnsupportedCompression_throws(@TempDir Path dir) {
		// Given
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(CompressionType.SNAPPY)) {

			// When / Then
			assertThatThrownBy(() -> RocksDB.openReadWrite(opts, dir))
					.isInstanceOf(RocksDBException.class)
					.hasMessageContaining("not linked with the binary");
		}
	}
}
