package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers the [WriteBatch] overloads not already exercised by [RocksDBTest],
/// [ColumnFamilyTest], [DeleteRangeTest], or [WalIteratorTest]: the caller-arena
/// put overload, clear(), and the column-family ByteBuffer/MemorySegment tiers.
class WriteBatchTest {

	// -----------------------------------------------------------------------
	// put(Arena, byte[], byte[])
	// -----------------------------------------------------------------------

	@Test
	void put_withCallerArena_isVisibleAfterWrite(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create();
		     var arena = Arena.ofConfined()) {

			// When
			batch.put(arena, "k".getBytes(), "v".getBytes());
			db.write(batch);

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// clear()
	// -----------------------------------------------------------------------

	@Test
	void clear_removesQueuedMutations_beforeCommit(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create()) {
			batch.put("k1".getBytes(), "v1".getBytes());
			batch.put("k2".getBytes(), "v2".getBytes());

			// When
			batch.clear();
			db.write(batch);

			// Then
			assertThat(batch.count()).isZero();
			assertThat(db.get("k1".getBytes())).isNull();
			assertThat(db.get("k2".getBytes())).isNull();
		}
	}

	@Test
	void clear_allowsReuseOfSameBatch(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create()) {
			batch.put("stale".getBytes(), "v".getBytes());
			batch.clear();

			// When
			batch.put("fresh".getBytes(), "v".getBytes());
			db.write(batch);

			// Then
			assertThat(db.get("stale".getBytes())).isNull();
			assertThat(db.get("fresh".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// put — column family, ByteBuffer/MemorySegment tiers
	// -----------------------------------------------------------------------

	@Test
	void put_columnFamily_byteBuffer_isVisibleAfterWrite(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var batch = WriteBatch.create()) {
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var value = ByteBuffer.allocateDirect(1).put((byte) 'v').flip();

			// When
			batch.put(cf, key, value);
			db.write(batch);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void put_columnFamily_memorySegment_isVisibleAfterWrite(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var batch = WriteBatch.create();
		     var arena = Arena.ofConfined()) {
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var value = arena.allocateFrom(ValueLayout.JAVA_BYTE, "v".getBytes());

			// When
			batch.put(cf, key, value);
			db.write(batch);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// delete — column family, ByteBuffer/MemorySegment tiers
	// -----------------------------------------------------------------------

	@Test
	void delete_columnFamily_byteBuffer_removesKeyAfterWrite(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var batch = WriteBatch.create()) {
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();

			// When
			batch.delete(cf, key);
			db.write(batch);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
		}
	}

	@Test
	void delete_columnFamily_memorySegment_removesKeyAfterWrite(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var batch = WriteBatch.create();
		     var arena = Arena.ofConfined()) {
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());

			// When
			batch.delete(cf, key);
			db.write(batch);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// deleteRange — column family, ByteBuffer/MemorySegment tiers
	// -----------------------------------------------------------------------

	@Test
	void deleteRange_columnFamily_byteBuffer_removesKeyRangeAfterWrite(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var batch = WriteBatch.create()) {
			db.put(cf, "a".getBytes(), "1".getBytes());
			db.put(cf, "b".getBytes(), "2".getBytes());
			var start = ByteBuffer.allocateDirect(1).put((byte) 'a').flip();
			var end = ByteBuffer.allocateDirect(1).put((byte) 'b').flip();

			// When
			batch.deleteRange(cf, start, end);
			db.write(batch);

			// Then
			assertThat(db.get(cf, "a".getBytes())).isNull();
			assertThat(db.get(cf, "b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void deleteRange_columnFamily_memorySegment_removesKeyRangeAfterWrite(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var batch = WriteBatch.create();
		     var arena = Arena.ofConfined()) {
			db.put(cf, "a".getBytes(), "1".getBytes());
			db.put(cf, "b".getBytes(), "2".getBytes());
			var start = arena.allocateFrom(ValueLayout.JAVA_BYTE, "a".getBytes());
			var end = arena.allocateFrom(ValueLayout.JAVA_BYTE, "b".getBytes());

			// When
			batch.deleteRange(cf, start, end);
			db.write(batch);

			// Then
			assertThat(db.get(cf, "a".getBytes())).isNull();
			assertThat(db.get(cf, "b".getBytes())).isEqualTo("2".getBytes());
		}
	}
}
