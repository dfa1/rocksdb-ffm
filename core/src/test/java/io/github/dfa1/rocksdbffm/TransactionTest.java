package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers the ByteBuffer/MemorySegment tiers of [Transaction] — the byte[] tier
/// is already exercised via [TransactionDBTest].
class TransactionTest {

	private static TransactionDB openDb(Path path) {
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var txnDbOpts = TransactionDBOptions.newTransactionDBOptions()) {
			return RocksDB.openTransaction(opts, txnDbOpts, path);
		}
	}

	private static TransactionDB openDbWithCf(Path path, List<ColumnFamilyHandle> handles) {
		var db = openDb(path);
		handles.add(db.createColumnFamily(ColumnFamilyDescriptor.of("cf1")));
		return db;
	}

	// -----------------------------------------------------------------------
	// put / get — ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void put_get_byteBuffer(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var value = ByteBuffer.allocateDirect(1).put((byte) 'v').flip();

			// When
			txn.put(key, value);

			// Then
			var out = ByteBuffer.allocateDirect(64);
			CopyResult result = txn.get(ro, ByteBuffer.allocateDirect(1).put((byte) 'k').flip(), out);
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			txn.commit();
		}
	}

	@Test
	void get_byteBuffer_returnsNotFound_whenAbsent(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var out = ByteBuffer.allocateDirect(64);

			// When
			CopyResult result = txn.get(ro, key, out);

			// Then
			assertThat(result).isEqualTo(CopyResult.NotFound.INSTANCE);
		}
	}

	@Test
	void delete_byteBuffer_removesKeyWithinTransaction(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			txn.put("k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();

			// When
			txn.delete(key);

			// Then
			assertThat(txn.get(ro, "k".getBytes())).isNull();
		}
	}

	@Test
	void getForUpdate_byteBuffer_locksAndReturnsValue(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			txn.put("k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var out = ByteBuffer.allocateDirect(64);

			// When
			CopyResult result = txn.getForUpdate(ro, key, out, true);

			// Then
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			txn.commit();
		}
	}

	// -----------------------------------------------------------------------
	// put / get — MemorySegment tier
	// -----------------------------------------------------------------------

	@Test
	void put_get_memorySegment(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var value = arena.allocateFrom(ValueLayout.JAVA_BYTE, "v".getBytes());

			// When
			txn.put(key, value);

			// Then
			var out = arena.allocate(64);
			CopyResult result = txn.get(ro, key, out);
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			txn.commit();
		}
	}

	@Test
	void delete_memorySegment_removesKeyWithinTransaction(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			txn.put("k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());

			// When
			txn.delete(key);

			// Then
			assertThat(txn.get(ro, "k".getBytes())).isNull();
		}
	}

	@Test
	void getForUpdate_memorySegment_locksAndReturnsValue(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			txn.put("k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var out = arena.allocate(64);

			// When
			CopyResult result = txn.getForUpdate(ro, key, out, true);

			// Then
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			txn.commit();
		}
	}

	// -----------------------------------------------------------------------
	// put / get / delete — column family, ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void put_get_columnFamily_byteBuffer(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var cf = handles.get(0);
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var value = ByteBuffer.allocateDirect(1).put((byte) 'v').flip();

			// When
			txn.put(cf, key, value);

			// Then
			var out = ByteBuffer.allocateDirect(64);
			CopyResult result = txn.get(cf, ro, ByteBuffer.allocateDirect(1).put((byte) 'k').flip(), out);
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			txn.commit();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void delete_columnFamily_byteBuffer_removesKey(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var cf = handles.get(0);
			txn.put(cf, "k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();

			// When
			txn.delete(cf, key);

			// Then
			assertThat(txn.get(cf, ro, "k".getBytes())).isNull();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void getForUpdate_columnFamily_byteBuffer_locksAndReturnsValue(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var cf = handles.get(0);
			txn.put(cf, "k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var out = ByteBuffer.allocateDirect(64);

			// When
			CopyResult result = txn.getForUpdate(cf, ro, key, out, true);

			// Then
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			txn.commit();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	// -----------------------------------------------------------------------
	// put / get / delete — column family, MemorySegment tier
	// -----------------------------------------------------------------------

	@Test
	void put_get_columnFamily_memorySegment(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			var cf = handles.get(0);
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var value = arena.allocateFrom(ValueLayout.JAVA_BYTE, "v".getBytes());

			// When
			txn.put(cf, key, value);

			// Then
			var out = arena.allocate(64);
			CopyResult result = txn.get(cf, ro, key, out);
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			txn.commit();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void delete_columnFamily_memorySegment_removesKey(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			var cf = handles.get(0);
			txn.put(cf, "k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());

			// When
			txn.delete(cf, key);

			// Then
			assertThat(txn.get(cf, ro, "k".getBytes())).isNull();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void getForUpdate_columnFamily_memorySegment_locksAndReturnsValue(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			var cf = handles.get(0);
			txn.put(cf, "k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var out = arena.allocate(64);

			// When
			CopyResult result = txn.getForUpdate(cf, ro, key, out, true);

			// Then
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			txn.commit();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}
}
