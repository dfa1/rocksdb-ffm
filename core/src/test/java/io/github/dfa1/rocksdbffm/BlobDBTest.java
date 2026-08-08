package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BlobDBTest {

	// -----------------------------------------------------------------------
	// get — ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void get_byteBuffer_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("key".getBytes(), "value".getBytes());

			var key = ByteBuffer.allocateDirect(3);
			key.put("key".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(32);

			// When
			CopyResult result = db.get(key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.Copied());
			out.flip();
			var bytes = new byte[out.remaining()];
			out.get(bytes);
			assertThat(bytes).isEqualTo("value".getBytes());
		}
	}

	@Test
	void get_byteBuffer_returnsNotFound_whenKeyAbsent(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("seed".getBytes(), "val".getBytes());

			var key = ByteBuffer.allocateDirect(7);
			key.put("missing".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(32);

			// When
			CopyResult result = db.get(key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.NotFound());
		}
	}

	@Test
	void get_byteBuffer_returnsNotEnoughCapacity_whenValueDoesNotFit(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("key".getBytes(), "value".getBytes());

			var key = ByteBuffer.allocateDirect(3);
			key.put("key".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(2);

			// When
			CopyResult result = db.get(key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.NotEnoughCapacity(5));
			assertThat(out.position()).isZero();
		}
	}
}
