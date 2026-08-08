package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDBTest {

	private static TransactionDB openDb(Path path) {
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var txnDbOpts = TransactionDBOptions.newTransactionDBOptions()) {
			return RocksDB.openTransaction(opts, txnDbOpts, path);
		}
	}

	// -----------------------------------------------------------------------
	// commit / rollback
	// -----------------------------------------------------------------------

	@Test
	void commit_makesChangesVisible(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {

			txn.put("k".getBytes(), "v".getBytes());

			// When
			txn.commit();

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void rollback_discardsChanges(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {

			txn.put("k".getBytes(), "v".getBytes());

			// When
			txn.rollback();

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// get within a transaction
	// -----------------------------------------------------------------------

	@Test
	void get_readsUncommittedWritesWithinSameTransaction(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {

			txn.put("k".getBytes(), "v".getBytes());

			// When
			var result = txn.get(ro, "k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
			txn.commit();
		}
	}

	@Test
	void get_returnsNull_forAbsentKey(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {

			// When
			var result = txn.get(ro, "missing".getBytes());

			// Then
			assertThat(result).isNull();
			txn.rollback();
		}
	}

	@Test
	void getForUpdate_locksAndReturnsValue(@TempDir Path dir) {
		// Given
		seed(dir, "k", "original");

		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {

			// When
			var val = txn.getForUpdate(ro, "k".getBytes(), true);

			// Then
			assertThat(val).isEqualTo("original".getBytes());

			txn.put("k".getBytes(), "updated".getBytes());
			txn.commit();
		}

		try (var db = openDb(dir)) {
			assertThat(db.get("k".getBytes())).isEqualTo("updated".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// delete within a transaction
	// -----------------------------------------------------------------------

	@Test
	void delete_removesKeyWithinTransaction(@TempDir Path dir) {
		// Given
		seed(dir, "k", "v");

		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {

			// When
			txn.delete("k".getBytes());

			// Then — invisible within the transaction immediately
			assertThat(txn.get(ro, "k".getBytes())).isNull();
			txn.commit();
		}

		try (var db = openDb(dir)) {
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// savepoints
	// -----------------------------------------------------------------------

	@Test
	void rollbackToSavePoint_restoresPartialState(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {

			txn.put("k1".getBytes(), "v1".getBytes());
			txn.setSavePoint();
			txn.put("k2".getBytes(), "v2".getBytes());

			// When
			txn.rollbackToSavePoint();

			// Then — k1 still staged, k2 discarded
			assertThat(txn.get(ro, "k1".getBytes())).isEqualTo("v1".getBytes());
			assertThat(txn.get(ro, "k2".getBytes())).isNull();

			txn.commit();
		}

		try (var db = openDb(dir)) {
			assertThat(db.get("k1".getBytes())).isEqualTo("v1".getBytes());
			assertThat(db.get("k2".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// Transaction — column family overloads
	// -----------------------------------------------------------------------

	private static TransactionDB openDbWithCf(Path path, List<ColumnFamilyHandle> handles) {
		var db = openDb(path);
		handles.add(db.createColumnFamily(ColumnFamilyDescriptor.of("cf1")));
		return db;
	}

	@Test
	void transaction_put_get_columnFamily(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var cf = handles.get(0);

			// When
			txn.put(cf, "k".getBytes(), "v".getBytes());

			// Then
			assertThat(txn.get(cf, ro, "k".getBytes())).isEqualTo("v".getBytes());
			txn.commit();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void transaction_delete_columnFamily_removesKey(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var cf = handles.get(0);
			txn.put(cf, "k".getBytes(), "v".getBytes());

			// When
			txn.delete(cf, "k".getBytes());

			// Then
			assertThat(txn.get(cf, ro, "k".getBytes())).isNull();
			txn.commit();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void transaction_getForUpdate_columnFamily_locksAndReturnsValue(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions()) {
			var cf = handles.get(0);

			try (var seedTxn = db.beginTransaction(wo)) {
				seedTxn.put(cf, "k".getBytes(), "original".getBytes());
				seedTxn.commit();
			}

			try (var txn = db.beginTransaction(wo)) {
				// When
				var val = txn.getForUpdate(cf, ro, "k".getBytes(), true);

				// Then
				assertThat(val).isEqualTo("original".getBytes());
				txn.commit();
			}
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void transaction_newIterator_columnFamily_scansKeys(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var cf = handles.get(0);
			txn.put(cf, "a".getBytes(), "1".getBytes());
			txn.put(cf, "b".getBytes(), "2".getBytes());

			// When
			List<String> keys = new ArrayList<>();
			try (var it = txn.newIterator(cf, ro)) {
				for (it.seekToFirst(); it.isValid(); it.next()) {
					keys.add(new String(it.key(), StandardCharsets.UTF_8));
				}
			}

			// Then
			assertThat(keys).containsExactly("a", "b");
			txn.commit();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	// -----------------------------------------------------------------------
	// direct (non-transactional) operations
	// -----------------------------------------------------------------------

	@Test
	void directPut_isVisibleViaDirectGet(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir)) {
			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void directDelete_removesKey(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.delete("k".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// direct operations — ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void directPut_byteBuffer_isVisibleViaGet(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir)) {
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
	void directGet_byteBuffer_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			var key = ByteBuffer.allocateDirect(1);
			key.put("k".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(32);

			// When
			CopyResult result = db.get(key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.Copied());
			out.flip();
			assertThat(out.remaining()).isEqualTo(1);
		}
	}

	@Test
	void directDelete_byteBuffer_removesKey(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir)) {
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
	// direct operations — MemorySegment tier
	// -----------------------------------------------------------------------

	@Test
	void directPut_memorySegment_isVisibleViaGet(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
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
	void directGet_memorySegment_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
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
	void directDelete_memorySegment_removesKey(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
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
	// direct operations — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void directPut_get_columnFamily(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles)) {
			var cf = handles.get(0);

			// When
			db.put(cf, "k".getBytes(), "v".getBytes());

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void directGet_columnFamily_withReadOptions(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var ro = ReadOptions.newReadOptions()) {
			var cf = handles.get(0);
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.get(cf, ro, "k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void directPut_get_columnFamily_byteBuffer(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles)) {
			var cf = handles.get(0);
			var key = ByteBuffer.allocateDirect(3);
			key.put("key".getBytes()).flip();
			var value = ByteBuffer.allocateDirect(5);
			value.put("value".getBytes()).flip();

			// When
			db.put(cf, key, value);

			// Then
			assertThat(db.get(cf, "key".getBytes())).isEqualTo("value".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void directDelete_columnFamily_byteBuffer_removesKey(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles)) {
			var cf = handles.get(0);
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = ByteBuffer.allocateDirect(1);
			key.put("k".getBytes()).flip();

			// When
			db.delete(cf, key);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void directPut_get_columnFamily_memorySegment(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     Arena arena = Arena.ofConfined()) {
			var cf = handles.get(0);
			var key = arena.allocateFrom("seg-k");
			var value = arena.allocateFrom("seg-v");

			// When
			db.put(cf, key.asSlice(0, 5), value.asSlice(0, 5));

			// Then
			assertThat(db.get(cf, "seg-k".getBytes())).isEqualTo("seg-v".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void directDelete_columnFamily_memorySegment_removesKey(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     Arena arena = Arena.ofConfined()) {
			var cf = handles.get(0);
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom("k");

			// When
			db.delete(cf, key.asSlice(0, 1));

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void newIterator_columnFamily_scansKeys(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles)) {
			var cf = handles.get(0);
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
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void newIterator_columnFamily_withReadOptions(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var ro = ReadOptions.newReadOptions()) {
			var cf = handles.get(0);
			db.put(cf, "x".getBytes(), "y".getBytes());

			// When
			try (var it = db.newIterator(cf, ro)) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("x".getBytes());
			}
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void flush_columnFamily_doesNotThrow(@TempDir Path dir) {
		// Given
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var db = openDbWithCf(dir, handles);
		     var fo = FlushOptions.newFlushOptions().setWait(true)) {
			var cf = handles.get(0);
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			db.flush(cf, fo);

			// Then — no exception means flush succeeded
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void getProperty_columnFamily_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {

			// When
			var result = db.getProperty(cf, Property.NUM_ENTRIES_ACTIVE_MEM_TABLE);

			// Then
			assertThat(result).isPresent();
		}
	}

	@Test
	void getLongProperty_columnFamily_returnsValue(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {

			// When
			var result = db.getLongProperty(cf, Property.ESTIMATE_NUM_KEYS);

			// Then
			assertThat(result).isPresent();
		}
	}

	@Test
	void dropColumnFamily_removesFamily(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir)) {
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("to-drop"));

			// When
			db.dropColumnFamily(cf);
			cf.close();

			// Then — family list should only contain default
			try (var opts = Options.newOptions()) {
				List<byte[]> families = RocksDB.listColumnFamilies(opts, dir);
				List<String> names = families.stream()
						.map(b -> new String(b, StandardCharsets.UTF_8))
						.toList();
				assertThat(names).containsExactly("default");
			}
		}
	}

	// -----------------------------------------------------------------------
	// TransactionOptions
	// -----------------------------------------------------------------------

	@Test
	void transactionOptions_setSnapshot_doesNotBreakNormalFlow(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txnOpts = TransactionOptions.newTransactionOptions().setSetSnapshot(true);
		     var txn = db.beginTransaction(wo, txnOpts)) {

			txn.put("k".getBytes(), "v".getBytes());

			// When
			txn.commit();

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private void seed(Path dir, String key, String value) {
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {
			txn.put(key.getBytes(), value.getBytes());
			txn.commit();
		}
	}
}
