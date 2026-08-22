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

	@ParameterizedTest
	@EnumSource(value = CompressionType.class, names = {"ZSTD", "LZ4"})
	void openDb_withHermeticCompression_writesAndReadsBack(CompressionType type, @TempDir Path dir) {
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(type);
		     ReadWriteDB db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// A round-trip test alone can't tell a working codec from a no-op one, since
	// get/put succeeds either way. Comparing on-disk SST size against
	// NO_COMPRESSION for deliberately repetitive data catches that failure mode.
	@ParameterizedTest
	@EnumSource(value = CompressionType.class, names = {"ZSTD", "LZ4"})
	void openDb_withHermeticCompression_actuallyCompressesOnDisk(
			CompressionType type, @TempDir Path noCompressionDir, @TempDir Path compressedDir) {
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

		long compressedSize;
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(type);
		     ReadWriteDB db = RocksDB.openReadWrite(opts, compressedDir);
		     FlushOptions flushOptions = FlushOptions.newFlushOptions()) {
			for (int i = 0; i < 100; i++) {
				db.put(("k" + i).getBytes(), value);
			}
			db.flush(flushOptions);
			compressedSize = db.getLongProperty(Property.TOTAL_SST_FILES_SIZE).orElseThrow();
		}

		assertThat(compressedSize).isLessThan(uncompressedSize / 2);
	}

	// SNAPPY, ZLIB, BZLIB2, and XPRESS are not linked into the bundled native library
	// (see scripts/build-rocksdb.sh); setCompression/setBlobCompressionType reject them
	// eagerly rather than letting RocksDB::Open fail later with an opaque native error.
	@ParameterizedTest
	@EnumSource(value = CompressionType.class, names = {"SNAPPY", "ZLIB", "BZLIB2", "XPRESS"})
	void setCompression_withUnsupportedType_throws(CompressionType type) {
		try (Options opts = Options.newOptions()) {

			// Given / When / Then
			assertThatThrownBy(() -> opts.setCompression(type))
					.isInstanceOf(UnsupportedOperationException.class);
		}
	}

	@ParameterizedTest
	@EnumSource(value = CompressionType.class, names = {"SNAPPY", "ZLIB", "BZLIB2", "XPRESS"})
	void setBlobCompressionType_withUnsupportedType_throws(CompressionType type) {
		try (Options opts = Options.newOptions()) {

			// Given / When / Then
			assertThatThrownBy(() -> opts.setBlobCompressionType(type))
					.isInstanceOf(UnsupportedOperationException.class);
		}
	}
}
