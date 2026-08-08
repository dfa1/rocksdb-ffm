package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlobDBTest {

	// -----------------------------------------------------------------------
	// put / get / delete — byte[] tier
	// -----------------------------------------------------------------------

	@Test
	void put_get_roundtrip(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {

			// When
			db.put("key".getBytes(), "value".getBytes());

			// Then
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void put_withArena_isVisibleViaGet(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     Arena arena = Arena.ofConfined()) {

			// When
			db.put(arena, "key".getBytes(), "value".getBytes());

			// Then
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void get_withReadOptions_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     var ro = ReadOptions.newReadOptions()) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get(ro, "k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void delete_removesKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.delete("k".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// put / get / delete — MemorySegment tier
	// -----------------------------------------------------------------------

	@Test
	void put_get_memorySegment_roundtrip(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     Arena arena = Arena.ofConfined()) {
			var key = arena.allocateFrom("seg-k");
			var value = arena.allocateFrom("seg-v");

			// When
			db.put(key.asSlice(0, 5), value.asSlice(0, 5));

			// Then
			assertThat(db.get("seg-k".getBytes())).isEqualTo("seg-v".getBytes());
		}
	}

	@Test
	void get_memorySegment_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     Arena arena = Arena.ofConfined()) {
			db.put("k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom("k");
			var out = arena.allocate(32);

			// When
			CopyResult result = db.get(key.asSlice(0, 1), out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.Copied());
			assertThat(out.asSlice(0, 1).toArray(ValueLayout.JAVA_BYTE)).isEqualTo("v".getBytes());
		}
	}

	@Test
	void delete_memorySegment_removesKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     Arena arena = Arena.ofConfined()) {
			db.put("k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom("k");

			// When
			db.delete(key.asSlice(0, 1));

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// delete — ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void delete_byteBuffer_removesKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1);
			key.put("k".getBytes()).flip();

			// When
			db.delete(key);

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// WriteBatch
	// -----------------------------------------------------------------------

	@Test
	void write_appliesBatchAtomically(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     var batch = WriteBatch.create()) {
			batch.put("k1".getBytes(), "v1".getBytes());
			batch.put("k2".getBytes(), "v2".getBytes());

			// When
			db.write(batch);

			// Then
			assertThat(db.get("k1".getBytes())).isEqualTo("v1".getBytes());
			assertThat(db.get("k2".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Snapshot
	// -----------------------------------------------------------------------

	@Test
	void getSnapshot_isolatesReads(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("k".getBytes(), "v1".getBytes());

			// When — take snapshot, then overwrite
			try (var snap = db.getSnapshot();
			     var ro = ReadOptions.newReadOptions().setSnapshot(snap)) {
				db.put("k".getBytes(), "v2".getBytes());

				// Then — snapshot still sees v1
				assertThat(db.get(ro, "k".getBytes())).isEqualTo("v1".getBytes());
			}

			// And current read sees v2
			assertThat(db.get("k".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Iterator
	// -----------------------------------------------------------------------

	@Test
	void newIterator_iteratesData(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			// When
			try (var it = db.newIterator()) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("a".getBytes());
			}
		}
	}

	@Test
	void newIterator_withReadOptions(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     var ro = ReadOptions.newReadOptions()) {
			db.put("x".getBytes(), "y".getBytes());

			// When
			try (var it = db.newIterator(ro)) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("x".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// Flush
	// -----------------------------------------------------------------------

	@Test
	void flush_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     var fo = FlushOptions.newFlushOptions().setWait(true)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.flush(fo);

			// Then — no exception means flush succeeded
		}
	}

	@Test
	void flushWal_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.flushWal(true);

			// Then — no exception means flush succeeded
		}
	}

	// -----------------------------------------------------------------------
	// DB Properties
	// -----------------------------------------------------------------------

	@Test
	void getProperty_blobStats_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.getProperty(Property.BLOB_STATS);

			// Then
			assertThat(result).isPresent();
		}
	}

	@Test
	void getLongProperty_numBlobFiles_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.getLongProperty(Property.NUM_BLOB_FILES);

			// Then
			assertThat(result).isPresent();
		}
	}

	// -----------------------------------------------------------------------
	// SST File Ingest
	// -----------------------------------------------------------------------

	@Test
	void ingestExternalFile_keysAreReadable(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("aaa".getBytes(), "val1".getBytes());
			writer.put("bbb".getBytes(), "val2".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openWithBlobFiles(dbPath);
		     var ingestOpts = IngestExternalFileOptions.newIngestExternalFileOptions()) {
			db.ingestExternalFile(List.of(sstPath), ingestOpts);

			// Then
			assertThat(db.get("aaa".getBytes())).isEqualTo("val1".getBytes());
			assertThat(db.get("bbb".getBytes())).isEqualTo("val2".getBytes());
		}
	}

	@Test
	void ingestExternalFile_emptyFileList_isNoOp(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openWithBlobFiles(dir);
		     var ingestOpts = IngestExternalFileOptions.newIngestExternalFileOptions()) {

			// When
			db.ingestExternalFile(List.of(), ingestOpts);

			// Then — no exception
		}
	}

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
