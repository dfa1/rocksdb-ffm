package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
		     ReadWriteDB db = RocksDB.openReadWrite(dir, opts)) {
			db.put("k".getBytes(), "v".getBytes());
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@ParameterizedTest
	@EnumSource(value = CompressionType.class, names = {"ZSTD", "LZ4", "SNAPPY", "ZLIB", "XPRESS"})
	void openDb_withHermeticCompression_writesAndReadsBack(CompressionType type, @TempDir Path dir) {
		assumeTrue(type.isSupported(), type + " is not supported on this platform");
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(type);
		     ReadWriteDB db = RocksDB.openReadWrite(dir, opts)) {
			db.put("k".getBytes(), "v".getBytes());
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// A round-trip test alone can't tell a working codec from a no-op one, since
	// get/put succeeds either way. Comparing on-disk SST size against
	// NO_COMPRESSION for deliberately repetitive data catches that failure mode.
	@ParameterizedTest
 	@EnumSource(value = CompressionType.class, names = {"ZSTD", "LZ4", "SNAPPY", "ZLIB", "BZLIB2", "XPRESS"})

	void openDb_withHermeticCompression_actuallyCompressesOnDisk(
			CompressionType type, @TempDir Path noCompressionDir, @TempDir Path compressedDir) {
		assumeTrue(type.isSupported(), type + " is not supported on this platform");
		byte[] value = "a".repeat(64 * 1024).getBytes();

		long uncompressedSize;
		try (Options opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setCompression(CompressionType.NO_COMPRESSION);
		     ReadWriteDB db = RocksDB.openReadWrite(noCompressionDir, opts);
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
		     ReadWriteDB db = RocksDB.openReadWrite(compressedDir, opts);
		     FlushOptions flushOptions = FlushOptions.newFlushOptions()) {
			for (int i = 0; i < 100; i++) {
				db.put(("k" + i).getBytes(), value);
			}
			db.flush(flushOptions);
			compressedSize = db.getLongProperty(Property.TOTAL_SST_FILES_SIZE).orElseThrow();
		}

		assertThat(compressedSize).isLessThan(uncompressedSize / 2);
	}

	// XPRESS wraps the Windows compression API, so it is rejected eagerly on every other
    // platform rather than letting RocksDB::Open fail later with an opaque native error.
   @DisabledOnOs(OS.WINDOWS)
   void setCompression_withXpressOutsideWindows_throws() {
           try (Options opts = Options.newOptions()) {

                   // Given / When / Then
                   assertThatThrownBy(() -> opts.setCompression(CompressionType.XPRESS))
                                   .isInstanceOf(UnsupportedOperationException.class);
           }
   }

   @Test
   @DisabledOnOs(OS.WINDOWS)
   void setBlobCompressionType_withXpressOutsideWindows_throws() {
           try (Options opts = Options.newOptions()) {

                   // Given / When / Then
                   assertThatThrownBy(() -> opts.setBlobCompressionType(CompressionType.XPRESS))
                                   .isInstanceOf(UnsupportedOperationException.class);
           }
   }
}
