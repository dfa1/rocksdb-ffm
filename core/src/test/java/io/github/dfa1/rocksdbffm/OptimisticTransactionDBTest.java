package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticTransactionDBTest {

	// -----------------------------------------------------------------------
	// Basic open / close
	// -----------------------------------------------------------------------

	@Test
	void open_createsDb(@TempDir Path dir) {
		// Given / When / Then — no exception means success
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			assertThat(db).isNotNull();
		}
	}

	// -----------------------------------------------------------------------
	// Direct (non-transactional) operations — byte[] tier
	// -----------------------------------------------------------------------

	@Test
	void put_and_get_roundtrip(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {

			// When
			db.put("key".getBytes(), "value".getBytes());

			// Then
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void delete_removesKey(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.delete("k".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	@Test
	void get_returnsNull_whenKeyMissing(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {

			// When
			var result = db.get("missing".getBytes());

			// Then
			assertThat(result).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// Direct (non-transactional) operations — ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void put_and_get_byteBuffer(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {

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
	void get_byteBuffer_returnsValue(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			var key = ByteBuffer.allocateDirect(1);
			key.put("k".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(32);

			// When
			CopyResult result = db.get(key, out);

			// Then
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			out.flip();
			assertThat(out.remaining()).isEqualTo(1);
		}
	}

	@Test
	void delete_byteBuffer_removesKey(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
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
	// Direct (non-transactional) operations — MemorySegment tier
	// -----------------------------------------------------------------------

	@Test
	void put_and_get_memorySegment(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     Arena arena = Arena.ofConfined()) {

			var key = arena.allocateFrom("seg-key");
			var value = arena.allocateFrom("seg-val");

			// When
			db.put(key.asSlice(0, 7), value.asSlice(0, 7));

			// Then
			assertThat(db.get("seg-key".getBytes())).isEqualTo("seg-val".getBytes());
		}
	}

	@Test
	void get_memorySegment_returnsValue(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     Arena arena = Arena.ofConfined()) {
			db.put("k".getBytes(), "v".getBytes());

			var key = arena.allocateFrom("k");
			var out = arena.allocate(32);

			// When
			CopyResult result = db.get(key.asSlice(0, 1), out);

			// Then
			assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
			assertThat(out.asSlice(0, 1).toArray(ValueLayout.JAVA_BYTE)).isEqualTo("v".getBytes());
		}
	}

	@Test
	void delete_memorySegment_removesKey(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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
	// Direct (non-transactional) operations — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void put_get_columnFamily(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {

			// When
			db.put(cf, "k".getBytes(), "v".getBytes());

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void get_columnFamily_withReadOptions(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			var key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var value = ByteBuffer.allocateDirect(1).put((byte) 'v').flip();

			// When
			db.put(cf, key, value);

			var getKey = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			var getVal = ByteBuffer.allocateDirect(32);

			// Then
			assertThat(db.get(cf, getKey, getVal)).isEqualTo(CopyResult.Copied.INSTANCE);
		}
	}

	@Test
	void delete_columnFamily_byteBuffer_removesKey(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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
	void put_get_columnFamily_memorySegment(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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

	@Test
	void delete_columnFamily_memorySegment_removesKey(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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

	@Test
	void deleteRange_removesKeyRange(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
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
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
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
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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

	@Test
	void deleteRange_columnFamily_removesKeyRange(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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

	@Test
	void newIterator_columnFamily_scansKeys(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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

	@Test
	void flush_columnFamily_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var fo = FlushOptions.newFlushOptions()) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			ThrowingCallable action = () -> db.flush(cf, fo);

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	@Test
	void getProperty_columnFamily_returnsValue(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
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
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.getLongProperty(cf, Property.ESTIMATE_NUM_KEYS);

			// Then
			assertThat(result).isPresent();
		}
	}

	@Test
	void dropColumnFamily_removesFamily(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("to-drop"));
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			db.dropColumnFamily(cf);
			cf.close();

			// Then — family list should only contain default
			try (var listOpts = Options.newOptions()) {
				List<byte[]> families = RocksDB.listColumnFamilies(listOpts, dir);
				assertThat(families).hasSize(1);
			}
		}
	}

	// -----------------------------------------------------------------------
	// openOptimistic — column families (opened at startup, not created later)
	// -----------------------------------------------------------------------

	@Test
	void openOptimistic_withColumnFamilies_reopensExistingFamily(@TempDir Path dir) {
		// Given — create a DB with a custom CF, then close it
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());
		}

		// When — reopen with both CFs via the CF-aware factory overload
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var db = RocksDB.openOptimistic(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles)) {
			var cf = handles.get(1);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	// -----------------------------------------------------------------------
	// Iterator
	// -----------------------------------------------------------------------

	@Test
	void newIterator_iteratesData(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
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

	// -----------------------------------------------------------------------
	// Transaction commit
	// -----------------------------------------------------------------------

	@Test
	void transaction_commit_persists(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var wo = WriteOptions.newWriteOptions()) {

			// When
			try (var txn = db.beginTransaction(wo)) {
				txn.put("k".getBytes(), "v".getBytes());
				txn.commit();
			}

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void transaction_rollback_discardsChanges(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var wo = WriteOptions.newWriteOptions()) {

			// When
			try (var txn = db.beginTransaction(wo)) {
				txn.put("k".getBytes(), "v".getBytes());
				txn.rollback();
			}

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	@Test
	void transaction_get_seesUncommittedWrites(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var wo = WriteOptions.newWriteOptions()) {

			// When — transaction can read its own uncommitted writes
			try (var txn = db.beginTransaction(wo)) {
				txn.put("k".getBytes(), "v".getBytes());
				assertThat(txn.get(ReadOptions.newReadOptions(), "k".getBytes())).isEqualTo("v".getBytes());
				txn.rollback();
			}
		}
	}

	// -----------------------------------------------------------------------
	// Conflict detection
	// -----------------------------------------------------------------------

	@Test
	void transaction_conflict_throwsOnCommit(@TempDir Path dir) {
		// Given — two transactions read the same key; the first committer wins
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var wo = WriteOptions.newWriteOptions()) {

			db.put("k".getBytes(), "original".getBytes());

			try (var txn1 = db.beginTransaction(wo);
			     var txn2 = db.beginTransaction(wo)) {

				// Both read the key
				txn1.getForUpdate(ReadOptions.newReadOptions(), "k".getBytes(), true);
				txn2.getForUpdate(ReadOptions.newReadOptions(), "k".getBytes(), true);

				// Both write to it
				txn1.put("k".getBytes(), "txn1".getBytes());
				txn2.put("k".getBytes(), "txn2".getBytes());

				// txn1 commits first — succeeds
				txn1.commit();

				// txn2 commits second — conflict: txn1 already modified "k"
				assertThatThrownBy(txn2::commit)
						.isInstanceOf(RocksDBException.class);

				txn2.rollback();
			}

			// Then — txn1's write wins
			assertThat(db.get("k".getBytes())).isEqualTo("txn1".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// OptimisticTransactionOptions
	// -----------------------------------------------------------------------

	@Test
	void beginTransaction_withOptions_setSnapshot(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txnOpts = OptimisticTransactionOptions.newOptimisticTransactionOptions().setSetSnapshot(true)) {

			db.put("before".getBytes(), "yes".getBytes());

			// When — transaction with snapshot sees only pre-snapshot data
			try (var txn = db.beginTransaction(wo, txnOpts)) {
				db.put("after".getBytes(), "no".getBytes());

				// getForUpdate through the transaction sees committed data
				assertThat(txn.get(ReadOptions.newReadOptions(), "before".getBytes())).isEqualTo("yes".getBytes());

				txn.commit();
			}
		}
	}

	// -----------------------------------------------------------------------
	// Snapshot
	// -----------------------------------------------------------------------

	@Test
	void getSnapshot_isolatesReads(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {

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
	// Flush
	// -----------------------------------------------------------------------

	@Test
	void flush_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var fo = FlushOptions.newFlushOptions()) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			ThrowingCallable action = () -> db.flush(fo);

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	// -----------------------------------------------------------------------
	// get — scoped zero-copy (Mapper)
	// -----------------------------------------------------------------------

	@Test
	void get_zeroCopy_returnsValue(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     Arena arena = Arena.ofConfined()) {
			db.put("k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom("k").asSlice(0, 1);

			// When
			var result = db.get(key, value -> value.toArray(ValueLayout.JAVA_BYTE));

			// Then
			assertThat(result).contains("v".getBytes());
		}
	}

	@Test
	void get_zeroCopy_returnsEmpty_whenKeyAbsent(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     Arena arena = Arena.ofConfined()) {
			var key = arena.allocateFrom("missing").asSlice(0, 7);

			// When
			var result = db.get(key, value -> value.toArray(ValueLayout.JAVA_BYTE));

			// Then
			assertThat(result).isEmpty();
		}
	}

	@Test
	void get_zeroCopy_fromColumnFamily(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     Arena arena = Arena.ofConfined()) {
			db.put(cf, "k".getBytes(), "v".getBytes());
			var key = arena.allocateFrom("k").asSlice(0, 1);

			// When
			var result = db.get(cf, key, value -> value.toArray(ValueLayout.JAVA_BYTE));

			// Then
			assertThat(result).contains("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// DB Properties
	// -----------------------------------------------------------------------

	@Test
	void getLongProperty_returnsValue(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.getLongProperty(Property.ESTIMATE_NUM_KEYS);

			// Then
			assertThat(result).isPresent();
		}
	}

	// -----------------------------------------------------------------------
	// Capability gained from implementing RocksDBReadOperations/RocksDBWriteOperations
	// -----------------------------------------------------------------------
	// Not previously exposed on OptimisticTransactionDB; smoke-tested here since the shared
	// interfaces' default methods themselves already have thorough coverage elsewhere
	// (BackgroundJobsTest, CompactionControlTest, WalIteratorTest, KeyMayExistTest, ...).

	@Test
	void keyMayExist_returnsFalse_whenKeyAbsent(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {

			// When
			var result = db.keyMayExist("missing".getBytes());

			// Then
			assertThat(result).isFalse();
		}
	}

	@Test
	void keyMayExist_returnsTrue_whenKeyPresent(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.keyMayExist("k".getBytes());

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void write_appliesBatchAtomically(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var batch = WriteBatch.create()) {
			batch.put("a".getBytes(), "1".getBytes());
			batch.put("b".getBytes(), "2".getBytes());

			// When
			db.write(batch);

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
			assertThat(db.get("b".getBytes())).isEqualTo("2".getBytes());
		}
	}

	@Test
	void cancelAllBackgroundWork_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When / Then — no exception
			db.cancelAllBackgroundWork(false);
		}
	}

	@Test
	void disableAndEnableManualCompaction_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {

			// When / Then — no exception
			db.disableManualCompaction();
			db.enableManualCompaction();
		}
	}

	@Test
	void waitForCompact_defaultOptions_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir);
		     var waitOpts = WaitForCompactOptions.create()) {
			db.put("k".getBytes(), "v".getBytes());

			// When / Then — no exception
			db.waitForCompact(waitOpts);
		}
	}

	@Test
	void getLatestSequenceNumber_advancesAfterWrites(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			var before = db.getLatestSequenceNumber();

			// When
			db.put("k".getBytes(), "v".getBytes());
			var after = db.getLatestSequenceNumber();

			// Then
			assertThat(after.isAfter(before)).isTrue();
		}
	}

	@Test
	void getUpdatesSince_yieldsWrittenBatch(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			var start = db.getLatestSequenceNumber();
			db.put("k".getBytes(), "v".getBytes());

			// When
			int count = 0;
			try (var it = db.getUpdatesSince(start)) {
				for (; it.isValid(); it.next()) {
					try (var result = it.getBatch()) {
						count += result.writeBatch().count();
					}
				}
				it.checkStatus();
			}

			// Then
			assertThat(count).isEqualTo(1);
		}
	}

	@Test
	void disableAndEnableFileDeletions_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When / Then — no exception
			db.disableFileDeletions();
			db.enableFileDeletions();
		}
	}

	@Test
	void compactRange_noArgs_doesNotThrow(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When / Then — no exception
			db.compactRange();
		}
	}

	@Test
	void ingestExternalFile_keysAreReadable(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");

		try (var writerOpts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(writerOpts)) {
			writer.open(sstPath);
			writer.put("a".getBytes(), "1".getBytes());
			writer.finish();
		}

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dbPath)) {

			// When
			db.ingestExternalFile(sstPath);

			// Then
			assertThat(db.get("a".getBytes())).isEqualTo("1".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Calling methods after close() must throw, not crash the JVM
	// -----------------------------------------------------------------------
	// Direct ops go through a second native pointer (the "base DB") that RocksDB frees
	// separately when this OptimisticTransactionDB closes; verify it's guarded.

	@Test
	void put_afterClose_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openOptimistic(opts, dir);
		db.close();
		opts.close();

		// When
		ThrowingCallable callable = () -> db.put("k".getBytes(), "v".getBytes());

		// Then
		assertThatThrownBy(callable).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void get_afterClose_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openOptimistic(opts, dir);
		db.put("k".getBytes(), "v".getBytes());
		db.close();
		opts.close();

		// When
		ThrowingCallable callable = () -> db.get("k".getBytes());

		// Then
		assertThatThrownBy(callable).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void getProperty_afterClose_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openOptimistic(opts, dir);
		db.close();
		opts.close();

		// When
		ThrowingCallable callable = () -> db.getProperty(Property.STATS);

		// Then
		assertThatThrownBy(callable).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void newIterator_afterClose_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openOptimistic(opts, dir);
		db.close();
		opts.close();

		// When
		ThrowingCallable callable = db::newIterator;

		// Then
		assertThatThrownBy(callable).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void close_isIdempotent(@TempDir Path dir) {
		// Given — an already-closed OptimisticTransactionDB; close() must have released both
		// the primary pointer and the base DB pointer (NativeObjectWithBaseDb#tryCloseBaseDb)
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openOptimistic(opts, dir);
		db.close();
		opts.close();

		// When
		ThrowingCallable secondClose = db::close;

		// Then — normally a double-free would crash the JVM
		assertThatCode(secondClose).doesNotThrowAnyException();
	}
}
