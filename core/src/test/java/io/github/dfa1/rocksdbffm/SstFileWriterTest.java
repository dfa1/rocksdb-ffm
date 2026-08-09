package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SstFileWriterTest {

	// -----------------------------------------------------------------------
	// SstFileWriter — basic write and ingest
	// -----------------------------------------------------------------------

	@Test
	void ingest_singleFile_keysAreReadable(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("aaa".getBytes(), "val1".getBytes());
			writer.put("bbb".getBytes(), "val2".getBytes());
			writer.put("ccc".getBytes(), "val3".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("aaa".getBytes())).isEqualTo("val1".getBytes());
			assertThat(db.get("bbb".getBytes())).isEqualTo("val2".getBytes());
			assertThat(db.get("ccc".getBytes())).isEqualTo("val3".getBytes());
		}
	}

	@Test
	void ingest_multipleFiles_allKeysReadable(@TempDir Path dir) {
		// Given — two non-overlapping SST files
		Path sst1 = dir.resolve("file1.sst");
		Path sst2 = dir.resolve("file2.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sst1);
			writer.put("aaa".getBytes(), "v1".getBytes());
			writer.put("bbb".getBytes(), "v2".getBytes());
			writer.finish();

			writer.open(sst2);
			writer.put("ccc".getBytes(), "v3".getBytes());
			writer.put("ddd".getBytes(), "v4".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(List.of(sst1, sst2));

			// Then
			assertThat(db.get("aaa".getBytes())).isEqualTo("v1".getBytes());
			assertThat(db.get("bbb".getBytes())).isEqualTo("v2".getBytes());
			assertThat(db.get("ccc".getBytes())).isEqualTo("v3".getBytes());
			assertThat(db.get("ddd".getBytes())).isEqualTo("v4".getBytes());
		}
	}

	@Test
	void ingest_withExplicitOptions_moveFiles(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("key".getBytes(), "value".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath);
		     var ingestOpts = IngestExternalFileOptions.newIngestExternalFileOptions().setMoveFiles(true)) {
			db.ingestExternalFile(sstPath, ingestOpts);

			// Then
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void ingest_doesNotAffectExistingKeys(@TempDir Path dir) {
		// Given — DB has an existing key; SST has a different key
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("sst-key".getBytes(), "sst-val".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.put("existing".getBytes(), "original".getBytes());
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("existing".getBytes())).isEqualTo("original".getBytes());
			assertThat(db.get("sst-key".getBytes())).isEqualTo("sst-val".getBytes());
		}
	}

	@Test
	void fileSize_returnsPositiveSizeAfterFinish(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			// When
			writer.open(sstPath);
			writer.put("key1".getBytes(), "value1".getBytes());
			writer.put("key2".getBytes(), "value2".getBytes());
			writer.finish();

			// Then
			assertThat(writer.fileSize()).isGreaterThan(MemorySize.ZERO);
		}
	}

	// -----------------------------------------------------------------------
	// SstFileWriter — put tiers
	// -----------------------------------------------------------------------

	@Test
	void put_byteBuffer_valueIsIngestable(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			var key = ByteBuffer.allocateDirect(3).put("key".getBytes()).flip();
			var value = ByteBuffer.allocateDirect(5).put("value".getBytes()).flip();
			writer.put(key, value);
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void put_memorySegment_valueIsIngestable(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts);
		     var arena = Arena.ofConfined()) {
			writer.open(sstPath);
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "key".getBytes());
			var value = arena.allocateFrom(ValueLayout.JAVA_BYTE, "value".getBytes());
			writer.put(key, value);
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// SstFileWriter — delete tiers
	// -----------------------------------------------------------------------

	@Test
	void delete_removesKeyAfterIngest(@TempDir Path dir) {
		// Given — key already present in the DB
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.put("key".getBytes(), "value".getBytes());
		}

		try (var opts = Options.newOptions();
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.delete("key".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("key".getBytes())).isNull();
		}
	}

	@Test
	void delete_byteBuffer_removesKeyAfterIngest(@TempDir Path dir) {
		// Given — key already present in the DB
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.put("key".getBytes(), "value".getBytes());
		}

		try (var opts = Options.newOptions();
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			var key = ByteBuffer.allocateDirect(3).put("key".getBytes()).flip();
			writer.delete(key);
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("key".getBytes())).isNull();
		}
	}

	@Test
	void delete_memorySegment_removesKeyAfterIngest(@TempDir Path dir) {
		// Given — key already present in the DB
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.put("key".getBytes(), "value".getBytes());
		}

		try (var opts = Options.newOptions();
		     var writer = SstFileWriter.newSstFileWriter(opts);
		     var arena = Arena.ofConfined()) {
			writer.open(sstPath);
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "key".getBytes());
			writer.delete(key);
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("key".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// SstFileWriter — deleteRange tiers
	// -----------------------------------------------------------------------

	@Test
	void deleteRange_removesKeyRangeAfterIngest(@TempDir Path dir) {
		// Given — keys a, b, c already present in the DB
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			db.put("c".getBytes(), "3".getBytes());
		}

		try (var opts = Options.newOptions();
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.deleteRange("a".getBytes(), "c".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then — [a, c) is gone, c survives
			assertThat(db.get("a".getBytes())).isNull();
			assertThat(db.get("b".getBytes())).isNull();
			assertThat(db.get("c".getBytes())).isEqualTo("3".getBytes());
		}
	}

	@Test
	void deleteRange_byteBuffer_removesKeyRangeAfterIngest(@TempDir Path dir) {
		// Given — keys a, b already present in the DB
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
		}

		try (var opts = Options.newOptions();
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			var start = ByteBuffer.allocateDirect(1).put((byte) 'a').flip();
			var end = ByteBuffer.allocateDirect(1).put((byte) 'b').flip();
			writer.deleteRange(start, end);
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("a".getBytes())).isNull();
			assertThat(db.get("b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void deleteRange_memorySegment_removesKeyRangeAfterIngest(@TempDir Path dir) {
		// Given — keys a, b already present in the DB
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
		}

		try (var opts = Options.newOptions();
		     var writer = SstFileWriter.newSstFileWriter(opts);
		     var arena = Arena.ofConfined()) {
			writer.open(sstPath);
			var start = arena.allocateFrom(ValueLayout.JAVA_BYTE, "a".getBytes());
			var end = arena.allocateFrom(ValueLayout.JAVA_BYTE, "b".getBytes());
			writer.deleteRange(start, end);
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dbPath)) {
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("a".getBytes())).isNull();
			assertThat(db.get("b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// IngestExternalFileOptions — option round-trips
	// -----------------------------------------------------------------------

	@Test
	void ingestExternalFileOptions_setMoveFiles_doesNotThrow() {
		// Given / When / Then
		try (var opts = IngestExternalFileOptions.newIngestExternalFileOptions()) {
			opts.setMoveFiles(true);
			opts.setMoveFiles(false);
		}
	}

	@Test
	void ingestExternalFileOptions_allSetters_chaining() {
		// Given / When / Then — verify fluent API compiles and does not throw
		try (var opts = IngestExternalFileOptions.newIngestExternalFileOptions()
				.setMoveFiles(false)
				.setSnapshotConsistency(true)
				.setAllowGlobalSeqno(true)
				.setAllowBlockingFlush(true)
				.setIngestBehind(false)
				.setFailIfNotBottommostLevel(false)) {
			assertThat(opts).isNotNull();
		}
	}

	// -----------------------------------------------------------------------
	// Error handling
	// -----------------------------------------------------------------------

	@Test
	void ingest_emptyFileList_isNoOp(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			// When
			db.ingestExternalFile(List.of());

			// Then — no exception
		}
	}

	@Test
	void open_nonExistentDirectory_throws(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("nonexistent").resolve("data.sst");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			// When
			var thrown = assertThatThrownBy(() -> writer.open(sstPath));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void put_outOfOrder_throws(@TempDir Path dir) {
		// Given — keys must be in ascending order
		Path sstPath = dir.resolve("data.sst");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("zzz".getBytes(), "v1".getBytes());

			// When
			var thrown = assertThatThrownBy(() -> writer.put("aaa".getBytes(), "v2".getBytes()));

			// Then — adding a key that sorts before the previous one must fail
			thrown.isInstanceOf(RocksDBException.class);
		}
	}
}
