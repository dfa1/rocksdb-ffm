package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static io.github.dfa1.rocksdbffm.TestBytes.directBuffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CompactionControlTest {

	// -----------------------------------------------------------------------
	// compactRange — full keyspace
	// -----------------------------------------------------------------------

	@Test
	void compactRange_noArgs_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			// When
			db.compactRange();

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
			assertThat(db.get("b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// compactRange(byte[], byte[])
	// -----------------------------------------------------------------------

	@Test
	void compactRange_byteArray_fullRange(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("z".getBytes(), "2".getBytes());

			// When
			db.compactRange("a".getBytes(), "z".getBytes());

			// Then — data still accessible after compaction
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
			assertThat(db.get("z".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void compactRange_byteArray_nullBounds(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put("k".getBytes(), "v".getBytes());

			// When — null means open-ended
			db.compactRange((byte[]) null, (byte[]) null);

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// compactRange(ByteBuffer, ByteBuffer)
	// -----------------------------------------------------------------------

	@Test
	void compactRange_byteBuffer(@TempDir Path dir) {
		// Given — ByteBuffer tier requires direct buffers
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			byte[] startBytes = "a".getBytes();
			byte[] endBytes = "b".getBytes();
			ByteBuffer start = directBuffer(startBytes);
			ByteBuffer end = directBuffer(endBytes);

			// When
			db.compactRange(start, end);

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
		}
	}

	@Test
	void compactRange_byteBuffer_nullBounds(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.compactRange((ByteBuffer) null, (ByteBuffer) null);

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// compactRange(CompactOptions, byte[], byte[])
	// -----------------------------------------------------------------------

	@Test
	void compactRange_withOptions_fullRange(@TempDir Path dir) {
		// Given
		try (var dbOpts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, dbOpts);
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
	void compactRange_withOptions_changeLevel(@TempDir Path dir) {
		// Given
		try (var dbOpts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, dbOpts);
		     var compact = CompactOptions.newCompactOptions()
					 .setChangeLevel(true)
					 .setTargetLevel(-1)) {  // -1 = bottommost

			db.put("k".getBytes(), "v".getBytes());

			// When
			db.compactRange(compact, null, null);

			// Then — no throw
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// CompactOptions accessors
	// -----------------------------------------------------------------------

	@Test
	void compactOptions_defaults() {
		// Given / When
		try (var opts = CompactOptions.newCompactOptions()) {
			// Then — verify getters don't throw and return consistent boolean values
			assertThat(opts.isExclusiveManualCompaction()).isIn(true, false);
			assertThat(opts.isBottommostLevelCompaction()).isIn(true, false);
			assertThat(opts.isChangeLevel()).isFalse();
		}
	}

	@Test
	void compactOptions_roundtrip() {
		// Given / When
		try (var opts = CompactOptions.newCompactOptions()
				.setExclusiveManualCompaction(false)
				.setBottommostLevelCompaction(true)
				.setChangeLevel(true)
				.setTargetLevel(2)) {

			// Then
			assertThat(opts.isExclusiveManualCompaction()).isFalse();
			assertThat(opts.isBottommostLevelCompaction()).isTrue();
			assertThat(opts.isChangeLevel()).isTrue();
			assertThat(opts.getTargetLevel()).isEqualTo(2);
		}
	}

	// -----------------------------------------------------------------------
	// suggestCompactRange
	// -----------------------------------------------------------------------

	@Test
	void suggestCompactRange_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("z".getBytes(), "2".getBytes());

			// When
			ThrowingCallable action = () -> db.suggestCompactRange("a".getBytes(), "z".getBytes());

			// Then — hint only; no guarantee of compaction, just that it doesn't throw
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	@Test
	void suggestCompactRange_nullBounds(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			ThrowingCallable action = () -> db.suggestCompactRange(null, null);

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	// -----------------------------------------------------------------------
	// disableFileDeletions / enableFileDeletions
	// -----------------------------------------------------------------------

	@Test
	void disableAndEnableFileDeletions_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
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
	// blob files — compactRange / suggestCompactRange / file deletions
	// (gained via RocksDBWriteOperations)
	// -----------------------------------------------------------------------

	@Test
	void compactRange_blobDb_doesNotThrow(@TempDir Path dir) {
		// Given
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true).setEnableBlobFiles(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
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
	void suggestCompactRange_blobDb_doesNotThrow(@TempDir Path dir) {
		// Given
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true).setEnableBlobFiles(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("z".getBytes(), "2".getBytes());

			// When
			ThrowingCallable action = () -> db.suggestCompactRange("a".getBytes(), "z".getBytes());

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	@Test
	void disableAndEnableFileDeletions_blobDb_doesNotThrow(@TempDir Path dir) {
		// Given
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true).setEnableBlobFiles(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
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
	// deleteFilesInRange
	// -----------------------------------------------------------------------

	@Test
	void deleteFilesInRange_byteArray_coveringRange_removesFileAndKeys(@TempDir Path dir) {
		// Given — one SST file spanning [b, y], compacted out of level 0
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
			db.put("b".getBytes(), "1".getBytes());
			db.put("m".getBytes(), "2".getBytes());
			db.put("y".getBytes(), "3".getBytes());
			db.compactRange();

			// When
			db.deleteFilesInRange("a".getBytes(), "z".getBytes());

			// Then — the file and all of its keys are gone
			try (var files = db.getLiveFiles()) {
				assertThat(files.size()).isZero();
			}
			assertThat(db.get("b".getBytes())).isNull();
			assertThat(db.get("m".getBytes())).isNull();
			assertThat(db.get("y".getBytes())).isNull();
		}
	}

	@Test
	void deleteFilesInRange_byteArray_nullBounds_removesWholeKeySpace(@TempDir Path dir) {
		// Given
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
			db.put("b".getBytes(), "1".getBytes());
			db.put("y".getBytes(), "2".getBytes());
			db.compactRange();

			// When
			db.deleteFilesInRange((byte[]) null, null);

			// Then
			assertThat(db.get("b".getBytes())).isNull();
			assertThat(db.get("y".getBytes())).isNull();
		}
	}

	@Test
	void deleteFilesInRange_byteArray_partiallyOverlappingRange_keepsFile(@TempDir Path dir) {
		// Given — one SST file spanning [b, y], compacted out of level 0
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
			db.put("b".getBytes(), "1".getBytes());
			db.put("m".getBytes(), "2".getBytes());
			db.put("y".getBytes(), "3".getBytes());
			db.compactRange();

			// When — the range covers only part of the file (y is past the limit)
			db.deleteFilesInRange("a".getBytes(), "m".getBytes());

			// Then — the file is not entirely within the range, so it is kept untouched
			try (var files = db.getLiveFiles()) {
				assertThat(files.size()).isEqualTo(1);
			}
			assertThat(db.get("b".getBytes())).isEqualTo("1".getBytes());
			assertThat(db.get("y".getBytes())).isEqualTo("3".getBytes());
		}
	}

	@Test
	void deleteFilesInRange_byteArray_levelZeroFile_isKept(@TempDir Path dir) {
		// Given — a freshly flushed file, still in level 0
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts);
		     var flushOptions = FlushOptions.newFlushOptions()) {
			db.put("b".getBytes(), "1".getBytes());
			db.flush(flushOptions);

			// When
			db.deleteFilesInRange("a".getBytes(), "z".getBytes());

			// Then — RocksDB never deletes level-0 files this way
			assertThat(db.get("b".getBytes())).isEqualTo("1".getBytes());
		}
	}

	@Test
	void deleteFilesInRange_byteBuffer_coveringRange_removesKeys(@TempDir Path dir) {
		// Given
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
			db.put("b".getBytes(), "1".getBytes());
			db.put("m".getBytes(), "2".getBytes());
			db.compactRange();
			ByteBuffer start = directBuffer("a");
			ByteBuffer end = directBuffer("z");

			// When
			db.deleteFilesInRange(start, end);

			// Then
			assertThat(db.get("b".getBytes())).isNull();
			assertThat(db.get("m".getBytes())).isNull();
		}
	}

	@Test
	void deleteFilesInRange_memorySegment_coveringRange_removesKeys(@TempDir Path dir) {
		// Given
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts);
		     Arena arena = Arena.ofConfined()) {
			db.put("b".getBytes(), "1".getBytes());
			db.put("m".getBytes(), "2".getBytes());
			db.compactRange();
			MemorySegment start = arena.allocateFrom("a").asSlice(0, 1);
			MemorySegment end = arena.allocateFrom("z").asSlice(0, 1);

			// When
			db.deleteFilesInRange(start, end);

			// Then
			assertThat(db.get("b".getBytes())).isNull();
			assertThat(db.get("m".getBytes())).isNull();
		}
	}

	@Test
	void deleteFilesInRange_columnFamily_byteArray_removesOnlyThatFamily(@TempDir Path dir) {
		// Given — the same keys in the default family and in cf1, both compacted out of level 0
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(ColumnFamilyName.of("cf1")));
			db.put("b".getBytes(), "default".getBytes());
			db.put(cf, "b".getBytes(), "1".getBytes());
			db.put(cf, "y".getBytes(), "2".getBytes());
			db.compactRange();
			db.compactRange(cf);

			// When
			db.deleteFilesInRange(cf, "a".getBytes(), "z".getBytes());

			// Then
			assertThat(db.get(cf, "b".getBytes())).isNull();
			assertThat(db.get(cf, "y".getBytes())).isNull();
			assertThat(db.get("b".getBytes())).isEqualTo("default".getBytes());
		}
	}

	@Test
	void deleteFilesInRange_columnFamily_byteBuffer_removesKeys(@TempDir Path dir) {
		// Given
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts)) {
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(ColumnFamilyName.of("cf1")));
			db.put(cf, "b".getBytes(), "1".getBytes());
			db.compactRange(cf);
			ByteBuffer start = directBuffer("a");
			ByteBuffer end = directBuffer("z");

			// When
			db.deleteFilesInRange(cf, start, end);

			// Then
			assertThat(db.get(cf, "b".getBytes())).isNull();
		}
	}

	@Test
	void deleteFilesInRange_columnFamily_memorySegment_removesKeys(@TempDir Path dir) {
		// Given
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir, dbOpts);
		     Arena arena = Arena.ofConfined()) {
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(ColumnFamilyName.of("cf1")));
			db.put(cf, "b".getBytes(), "1".getBytes());
			db.compactRange(cf);
			MemorySegment start = arena.allocateFrom("a").asSlice(0, 1);
			MemorySegment end = arena.allocateFrom("z").asSlice(0, 1);

			// When
			db.deleteFilesInRange(cf, start, end);

			// Then
			assertThat(db.get(cf, "b".getBytes())).isNull();
		}
	}
}
