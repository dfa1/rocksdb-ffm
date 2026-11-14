package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.dfa1.rocksdbffm.TestBytes.decodeUint64;
import static io.github.dfa1.rocksdbffm.TestBytes.directBuffer;
import static io.github.dfa1.rocksdbffm.TestBytes.encodeUint64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers the ByteBuffer/MemorySegment tiers of [Transaction] — the byte[] tier
/// is already exercised via [TransactionDBTest].
class TransactionTest {

	private static TransactionDB openDb(Path path) {
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var txnDbOpts = TransactionDBOptions.newTransactionDBOptions()) {
			return RocksDB.openTransaction(path, opts, txnDbOpts);
		}
	}

	private static TransactionDB openDbWithCf(Path path, List<ColumnFamilyHandle> handles) {
		var db = openDb(path);
		handles.add(db.createColumnFamily(ColumnFamilyDescriptor.of(ColumnFamilyName.of("cf1"))));
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
			var key = directBuffer("k");
			var value = directBuffer("v");

			// When
			txn.put(key, value);

			// Then
			var out = ByteBuffer.allocateDirect(64);
			CopyResult result = txn.get(ro, directBuffer("k"), out);
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
			var key = directBuffer("k");
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
			var key = directBuffer("k");

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
			var key = directBuffer("k");
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
			var key = directBuffer("k");
			var value = directBuffer("v");

			// When
			txn.put(cf, key, value);

			// Then
			var out = ByteBuffer.allocateDirect(64);
			CopyResult result = txn.get(cf, ro, directBuffer("k"), out);
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
			var key = directBuffer("k");

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
			var key = directBuffer("k");
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

	// -----------------------------------------------------------------------
	// not-found paths for every get / getForUpdate tier
	// -----------------------------------------------------------------------

	@Test
	void get_memorySegment_returnsNotFound_whenAbsent(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var out = arena.allocate(64);

			// When
			CopyResult result = txn.get(ro, key, out);

			// Then
			assertThat(result).isEqualTo(CopyResult.NotFound.INSTANCE);
		}
	}

	@Test
	void getForUpdate_notFoundTiers_returnNotFound(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			var cf = handles.get(0);
			var keySeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var outSeg = arena.allocate(64);

			// When
			CopyResult bb = txn.getForUpdate(ro, directBuffer("k"), ByteBuffer.allocateDirect(64), true);
			CopyResult ms = txn.getForUpdate(ro, keySeg, outSeg, true);
			CopyResult cfBb = txn.getForUpdate(cf, ro, directBuffer("k"), ByteBuffer.allocateDirect(64), true);
			CopyResult cfMs = txn.getForUpdate(cf, ro, keySeg, outSeg, true);

			// Then
			assertThat(List.of(bb, ms, cfBb, cfMs)).containsOnly(CopyResult.NotFound.INSTANCE);
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void get_columnFamily_notFoundTiers_returnNotFound(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			var cf = handles.get(0);
			var keySeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());

			// When
			CopyResult bb = txn.get(cf, ro, directBuffer("k"), ByteBuffer.allocateDirect(64));
			CopyResult ms = txn.get(cf, ro, keySeg, arena.allocate(64));
			String pinned = txn.get(cf, ro, keySeg, seg -> "found");

			// Then
			assertThat(bb).isEqualTo(CopyResult.NotFound.INSTANCE);
			assertThat(ms).isEqualTo(CopyResult.NotFound.INSTANCE);
			assertThat(pinned).isNull();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void get_columnFamily_pinned_returnsMappedValue_whenPresent(@TempDir Path dir) {
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
			Long size = txn.get(cf, ro, key, seg -> seg.byteSize());

			// Then
			assertThat(size).isEqualTo(1L);
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	// -----------------------------------------------------------------------
	// rollback / rollbackToSavePoint
	// -----------------------------------------------------------------------

	@Test
	void rollback_discardsStagedWrites_beforeClose(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			txn.put("k".getBytes(), "v".getBytes());

			// When
			txn.rollback();

			// Then — the transaction's own view no longer sees the write
			var out = ByteBuffer.allocateDirect(64);
			CopyResult result = txn.get(ro, directBuffer("k"), out);
			assertThat(result).isEqualTo(CopyResult.NotFound.INSTANCE);
		}
	}

	@Test
	void rollbackToSavePoint_withoutSavePoint_throws(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {

			// When
			var thrown = assertThatThrownBy(txn::rollbackToSavePoint);

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	// -----------------------------------------------------------------------
	// merge tiers — uint64Add operands staged in a transaction, summed after commit
	// -----------------------------------------------------------------------

	private static TransactionDB openMergeDb(Path path) {
		try (var opts = Options.newOptions().setCreateIfMissing(true).setMergeOperator(MergeOperator.uint64Add());
		     var txnDbOpts = TransactionDBOptions.newTransactionDBOptions()) {
			return RocksDB.openTransaction(path, opts, txnDbOpts);
		}
	}

	@Test
	void merge_byteBufferAndMemorySegment_sumOperandsAfterCommit(@TempDir Path dir) {
		// Given
		try (var db = openMergeDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			txn.merge(directBuffer("views".getBytes()), directBuffer(encodeUint64(1)));
			txn.merge(arena.allocateFrom(ValueLayout.JAVA_BYTE, "views".getBytes()),
					arena.allocateFrom(ValueLayout.JAVA_BYTE, encodeUint64(4)));

			// When
			txn.commit();

			// Then
			assertThat(decodeUint64(db.get("views".getBytes()))).isEqualTo(5);
		}
	}

	@Test
	void merge_columnFamily_allTiers_sumOperandsAfterCommit(@TempDir Path dir) {
		// Given
		try (var db = openMergeDb(dir);
		     var cfOpts = Options.newOptions().setMergeOperator(MergeOperator.uint64Add());
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(ColumnFamilyName.of("cf1"), cfOpts));
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var arena = Arena.ofConfined()) {
			txn.merge(cf, "views".getBytes(), encodeUint64(1));
			txn.merge(cf, directBuffer("views".getBytes()), directBuffer(encodeUint64(2)));
			txn.merge(cf, arena.allocateFrom(ValueLayout.JAVA_BYTE, "views".getBytes()),
					arena.allocateFrom(ValueLayout.JAVA_BYTE, encodeUint64(4)));

			// When
			txn.commit();

			// Then
			assertThat(decodeUint64(db.get(cf, "views".getBytes()))).isEqualTo(7);
		}
	}
}
