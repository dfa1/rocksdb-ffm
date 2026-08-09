package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TtlDBTest {

	// -----------------------------------------------------------------------
	// Basic open / read-back
	// -----------------------------------------------------------------------

	@Test
	void openTtl_storesAndRetrievesValues(@TempDir Path dir) {
		// Given / When
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("k".getBytes(), "v".getBytes());

			// Then — key is readable immediately (TTL not yet elapsed)
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void openTtl_withExplicitOptions(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dir, Duration.ofMinutes(1))) {

			db.put("key".getBytes(), "value".getBytes());

			// When
			var result = db.get("key".getBytes());

			// Then
			assertThat(result).isEqualTo("value".getBytes());
		}
	}

	@Test
	void openTtl_zeroTtl_disablesExpiry(@TempDir Path dir) {
		// Given — TTL=0 means no expiry
		try (var db = RocksDB.openTtl(dir, Duration.ZERO)) {
			db.put("k".getBytes(), "v".getBytes());

			// Then — key survives indefinitely
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Full API surface (TTL DB is a regular rocksdb_t*)
	// -----------------------------------------------------------------------

	@Test
	void openTtl_supportsDelete(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.delete("k".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	@Test
	void openTtl_supportsWriteBatch(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
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

	@Test
	void openTtl_supportsIterator(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
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
	void openTtl_surviveReopenWithSameTtl(@TempDir Path dir) {
		// Given — write, close, reopen, verify data persists
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("persistent".getBytes(), "yes".getBytes());
		}

		// When
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {

			// Then
			assertThat(db.get("persistent".getBytes())).isEqualTo("yes".getBytes());
		}
	}

	@Test
	void getTtl_returnsConfiguredDuration(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {

			// When
			var ttl = db.getTtl();

			// Then
			assertThat(ttl).isEqualTo(Duration.ofSeconds(60));
		}
	}

	// -----------------------------------------------------------------------
	// put — Arena / ByteBuffer / MemorySegment tiers
	// -----------------------------------------------------------------------

	@Test
	void put_withArena_isVisibleViaGet(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     Arena arena = Arena.ofConfined()) {

			// When
			db.put(arena, "key".getBytes(), "value".getBytes());

			// Then
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void put_byteBuffer_isVisibleViaGet(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			var key = ByteBuffer.allocateDirect(3);
			key.put("key".getBytes()).flip();
			var value = ByteBuffer.allocateDirect(5);
			value.put("value".getBytes()).flip();

			// When
			db.put(key, value);

			// Then
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void put_memorySegment_isVisibleViaGet(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
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
	void put_arena_memorySegment_isVisibleViaGet(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     Arena arena = Arena.ofConfined()) {
			var key = arena.allocateFrom("seg-k2");
			var value = arena.allocateFrom("seg-v2");

			// When
			db.put(arena, key.asSlice(0, 6), value.asSlice(0, 6));

			// Then
			assertThat(db.get("seg-k2".getBytes())).isEqualTo("seg-v2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// get — ReadOptions / ByteBuffer / MemorySegment tiers
	// -----------------------------------------------------------------------

	@Test
	void get_withReadOptions_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var ro = ReadOptions.newReadOptions()) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get(ro, "k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void get_byteBuffer_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("key".getBytes(), "value".getBytes());
			var key = ByteBuffer.allocateDirect(3);
			key.put("key".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(32);

			// When
			CopyResult result = db.get(key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.Copied());
		}
	}

	@Test
	void get_memorySegment_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
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

	// -----------------------------------------------------------------------
	// delete — ByteBuffer / MemorySegment tiers
	// -----------------------------------------------------------------------

	@Test
	void delete_byteBuffer_removesKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1);
			key.put("k".getBytes()).flip();

			// When
			db.delete(key);

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	@Test
	void delete_memorySegment_removesKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
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
	// deleteRange — byte[] / ByteBuffer / MemorySegment tiers
	// -----------------------------------------------------------------------

	@Test
	void deleteRange_removesKeyRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			db.put("c".getBytes(), "3".getBytes());

			// When
			db.deleteRange("a".getBytes(), "c".getBytes());

			// Then
			assertThat(db.get("a".getBytes())).isNull();
			assertThat(db.get("b".getBytes())).isNull();
			assertThat(db.get("c".getBytes())).isEqualTo("3".getBytes());
		}
	}

	@Test
	void deleteRange_byteBuffer_removesKeyRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			var start = ByteBuffer.allocateDirect(1).put((byte) 'a').flip();
			var end = ByteBuffer.allocateDirect(1).put((byte) 'b').flip();

			// When
			db.deleteRange(start, end);

			// Then
			assertThat(db.get("a".getBytes())).isNull();
			assertThat(db.get("b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void deleteRange_memorySegment_removesKeyRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     Arena arena = Arena.ofConfined()) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			var start = arena.allocateFrom("a");
			var end = arena.allocateFrom("b");

			// When
			db.deleteRange(start.asSlice(0, 1), end.asSlice(0, 1));

			// Then
			assertThat(db.get("a".getBytes())).isNull();
			assertThat(db.get("b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// WriteBatch — Arena overload
	// -----------------------------------------------------------------------

	@Test
	void write_withArena_appliesBatchAtomically(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var batch = WriteBatch.create();
		     Arena arena = Arena.ofConfined()) {
			batch.put("k1".getBytes(), "v1".getBytes());
			batch.put("k2".getBytes(), "v2".getBytes());

			// When
			db.write(arena, batch);

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
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
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
	// Iterator — explicit ReadOptions
	// -----------------------------------------------------------------------

	@Test
	void newIterator_withReadOptions(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
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
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
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
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
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
	void getProperty_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.getProperty(Property.STATS);

			// Then
			assertThat(result).isPresent();
		}
	}

	@Test
	void getLongProperty_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.getLongProperty(Property.ESTIMATE_NUM_KEYS);

			// Then
			assertThat(result).isPresent();
		}
	}

	// -----------------------------------------------------------------------
	// Compaction
	// -----------------------------------------------------------------------

	@Test
	void compactRange_noArgs_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			// When
			db.compactRange();

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
			assertThat(db.get("b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void compactRange_byteArray_fullRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("z".getBytes(), "2".getBytes());

			// When
			db.compactRange("a".getBytes(), "z".getBytes());

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
			assertThat(db.get("z".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void compactRange_byteBuffer(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			var start = ByteBuffer.allocateDirect(1).put((byte) 'a').flip();
			var end = ByteBuffer.allocateDirect(1).put((byte) 'b').flip();

			// When
			db.compactRange(start, end);

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
		}
	}

	@Test
	void compactRange_memorySegment(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     Arena arena = Arena.ofConfined()) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			var start = arena.allocateFrom("a");
			var end = arena.allocateFrom("b");

			// When
			db.compactRange(start.asSlice(0, 1), end.asSlice(0, 1));

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
		}
	}

	@Test
	void compactRange_withOptions_fullRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var compact = CompactOptions.newCompactOptions()
					 .setExclusiveManualCompaction(false)
					 .setBottommostLevelCompaction(true)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("z".getBytes(), "2".getBytes());

			// When
			db.compactRange(compact, null, null);

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
			assertThat(db.get("z".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void suggestCompactRange_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("z".getBytes(), "2".getBytes());

			// When
			db.suggestCompactRange("a".getBytes(), "z".getBytes());

			// Then — hint only; no guarantee of compaction, no exception
		}
	}

	// -----------------------------------------------------------------------
	// File deletions
	// -----------------------------------------------------------------------

	@Test
	void disableAndEnableFileDeletions_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.put("k".getBytes(), "v".getBytes());

			// When — disable, do some work, re-enable
			db.disableFileDeletions();
			db.put("k2".getBytes(), "v2".getBytes());
			db.enableFileDeletions();

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			assertThat(db.get("k2".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// SST File Ingest
	// -----------------------------------------------------------------------

	@Test
	void ingestExternalFile_singlePath_doesNotThrowOnIngest(@TempDir Path dir) {
		// Given — TtlDB stores every value with an appended expiry timestamp; a plain
		// SST written via SstFileWriter has no such suffix. rocksdb does not validate
		// the timestamp suffix at ingest time (only, inconsistently, on later reads),
		// so this only exercises that the ingest call itself succeeds.
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("aaa".getBytes(), "val1".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openTtl(dbPath, Duration.ofSeconds(60))) {
			db.ingestExternalFile(sstPath);

			// Then — no exception means the ingest call itself succeeded
		}
	}

	@Test
	void ingestExternalFile_singlePath_withOptions_doesNotThrowOnIngest(@TempDir Path dir) {
		// Given — same timestamp-suffix caveat as the no-options overload
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("key".getBytes(), "value".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openTtl(dbPath, Duration.ofSeconds(60));
		     var ingestOpts = IngestExternalFileOptions.newIngestExternalFileOptions().setMoveFiles(true)) {
			db.ingestExternalFile(sstPath, ingestOpts);

			// Then — no exception means the ingest call itself succeeded
		}
	}

	@Test
	void ingestExternalFile_emptyFileList_isNoOp(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var ingestOpts = IngestExternalFileOptions.newIngestExternalFileOptions()) {

			// When
			db.ingestExternalFile(List.of(), ingestOpts);

			// Then — no exception
		}
	}

	// -----------------------------------------------------------------------
	// Column family management
	// -----------------------------------------------------------------------

	@Test
	void createColumnFamily_canPutAndGetInNewFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {

			// When
			db.put(cf, "key".getBytes(), "value".getBytes());

			// Then
			assertThat(db.get(cf, "key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void dropColumnFamily_removesFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("to-drop"));
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			db.dropColumnFamily(cf);
			cf.close();

			// Then — no exception means drop succeeded
		}
	}

	// -----------------------------------------------------------------------
	// get — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void get_columnFamily_withReadOptions(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var ro = ReadOptions.newReadOptions()) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.get(cf, ro, "k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void put_get_columnFamily_byteBuffer(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var value = ByteBuffer.allocateDirect(1).put((byte) 'v').flip();

			// When
			db.put(cf, key, value);

			var getKey = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var getVal = ByteBuffer.allocateDirect(32);

			// Then
			assertThat(db.get(cf, getKey, getVal)).isEqualTo(new CopyResult.Copied());
		}
	}

	@Test
	void put_get_columnFamily_memorySegment(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     Arena arena = Arena.ofConfined()) {
			var key = arena.allocateFrom("seg-k");
			var value = arena.allocateFrom("seg-v");

			// When
			db.put(cf, key.asSlice(0, 5), value.asSlice(0, 5));

			// Then
			assertThat(db.get(cf, "seg-k".getBytes())).isEqualTo("seg-v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// delete — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void delete_columnFamily_removesKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			db.delete(cf, "k".getBytes());

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
		}
	}

	@Test
	void delete_columnFamily_byteBuffer_removesKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();

			// When
			db.delete(cf, key);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
		}
	}

	@Test
	void delete_columnFamily_memorySegment_removesKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     Arena arena = Arena.ofConfined()) {
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom("k");

			// When
			db.delete(cf, key.asSlice(0, 1));

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// deleteRange — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void deleteRange_columnFamily_removesKeyRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "a".getBytes(), "1".getBytes());
			db.put(cf, "b".getBytes(), "2".getBytes());
			db.put(cf, "c".getBytes(), "3".getBytes());

			// When
			db.deleteRange(cf, "a".getBytes(), "c".getBytes());

			// Then
			assertThat(db.get(cf, "a".getBytes())).isNull();
			assertThat(db.get(cf, "b".getBytes())).isNull();
			assertThat(db.get(cf, "c".getBytes())).isEqualTo("3".getBytes());
		}
	}

	@Test
	void deleteRange_columnFamily_byteBuffer_removesKeyRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "a".getBytes(), "1".getBytes());
			db.put(cf, "b".getBytes(), "2".getBytes());
			var start = ByteBuffer.allocateDirect(1).put((byte) 'a').flip();
			var end = ByteBuffer.allocateDirect(1).put((byte) 'b').flip();

			// When
			db.deleteRange(cf, start, end);

			// Then
			assertThat(db.get(cf, "a".getBytes())).isNull();
			assertThat(db.get(cf, "b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void deleteRange_columnFamily_memorySegment_removesKeyRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     Arena arena = Arena.ofConfined()) {
			db.put(cf, "a".getBytes(), "1".getBytes());
			db.put(cf, "b".getBytes(), "2".getBytes());
			var start = arena.allocateFrom("a");
			var end = arena.allocateFrom("b");

			// When
			db.deleteRange(cf, start.asSlice(0, 1), end.asSlice(0, 1));

			// Then
			assertThat(db.get(cf, "a".getBytes())).isNull();
			assertThat(db.get(cf, "b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// keyMayExist — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void keyMayExist_columnFamily_returnsTrueForPresentKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.keyMayExist(cf, "k".getBytes());

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void keyMayExist_columnFamily_withReadOptions(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var ro = ReadOptions.newReadOptions()) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.keyMayExist(cf, ro, "k".getBytes());

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void keyMayExist_columnFamily_byteBuffer(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();

			// When
			var result = db.keyMayExist(cf, key);

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void keyMayExist_columnFamily_memorySegment(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     Arena arena = Arena.ofConfined()) {
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom("k");

			// When
			var result = db.keyMayExist(cf, key.asSlice(0, 1));

			// Then
			assertThat(result).isTrue();
		}
	}

	// -----------------------------------------------------------------------
	// Iterator — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void newIterator_columnFamily_scansKeys(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "a".getBytes(), "1".getBytes());
			db.put(cf, "b".getBytes(), "2".getBytes());

			// When
			List<String> keys = new ArrayList<>();
			try (var it = db.newIterator(cf)) {
				for (it.seekToFirst(); it.isValid(); it.next()) {
					keys.add(new String(it.key(), StandardCharsets.UTF_8));
				}
			}

			// Then
			assertThat(keys).containsExactly("a", "b");
		}
	}

	@Test
	void newIterator_columnFamily_withReadOptions(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var ro = ReadOptions.newReadOptions()) {
			db.put(cf, "x".getBytes(), "y".getBytes());

			// When
			try (var it = db.newIterator(cf, ro)) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("x".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// flush / getProperty — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void flush_columnFamily_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var fo = FlushOptions.newFlushOptions().setWait(true)) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			db.flush(cf, fo);

			// Then — no exception means flush succeeded
		}
	}

	@Test
	void getProperty_columnFamily_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.getProperty(cf, Property.NUM_ENTRIES_ACTIVE_MEM_TABLE);

			// Then
			assertThat(result).isPresent();
		}
	}

	@Test
	void getLongProperty_columnFamily_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.getLongProperty(cf, Property.ESTIMATE_NUM_KEYS);

			// Then
			assertThat(result).isPresent();
		}
	}

	// -----------------------------------------------------------------------
	// openTtl — column families with per-CF TTL
	// -----------------------------------------------------------------------

	@Test
	void openTtl_withColumnFamilies_reopensWithPerCfTtl(@TempDir Path dir) {
		// Given — create a DB with a custom CF, then close it
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60));
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());
		}

		// When — reopen with both CFs, each given its own TTL
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var db = RocksDB.openTtl(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     List.of(Duration.ofSeconds(60), Duration.ZERO),
				     handles)) {
			var cf = handles.get(1);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void openTtl_withColumnFamilies_populatesHandlesInDescriptorOrder(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			db.createColumnFamily(ColumnFamilyDescriptor.of("cf1")).close();
		}

		// When
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var db = RocksDB.openTtl(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     List.of(Duration.ZERO, Duration.ZERO),
				     handles)) {

			// Then
			assertThat(handles).hasSize(2);
			assertThat(handles.get(0).getName()).isEqualTo("default");
			assertThat(handles.get(1).getName()).isEqualTo("cf1");
			handles.forEach(ColumnFamilyHandle::close);
		}
	}
}
