package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotTest {

	// -----------------------------------------------------------------------
	// Snapshot isolation
	// -----------------------------------------------------------------------

	@Test
	void snapshot_readsDoNotSeeWritesAfterSnapshot(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("key".getBytes(), "before".getBytes());

			// When — take snapshot, then overwrite the key
			try (Snapshot snap = db.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap)) {

				db.put("key".getBytes(), "after".getBytes());

				// Then — read via snapshot sees the pre-write value
				assertThat(db.get(ro, "key".getBytes())).isEqualTo("before".getBytes());
				// And a plain read sees the new value
				assertThat(db.get("key".getBytes())).isEqualTo("after".getBytes());
			}
		}
	}

	@Test
	void snapshot_readsDoNotSeeDeletesAfterSnapshot(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("key".getBytes(), "value".getBytes());

			// When
			try (Snapshot snap = db.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap)) {

				db.delete("key".getBytes());

				// Then — snapshot still sees the deleted key
				assertThat(db.get(ro, "key".getBytes())).isEqualTo("value".getBytes());
				assertThat(db.get("key".getBytes())).isNull();
			}
		}
	}

	@Test
	void snapshot_sequenceNumberIsNonNegative(@TempDir Path dir) {
		// Given / When
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("k".getBytes(), "v".getBytes());
			try (Snapshot snap = db.getSnapshot()) {

				// Then
				assertThat(snap.sequenceNumber().toLong()).isGreaterThan(0);
			}
		}
	}

	@Test
	void snapshot_sequenceNumberIncreasesWithWrites(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			try (Snapshot snap1 = db.getSnapshot()) {
				db.put("k".getBytes(), "v".getBytes());
				try (Snapshot snap2 = db.getSnapshot()) {

					// Then
					assertThat(snap2.sequenceNumber().isAfter(snap1.sequenceNumber())).isTrue();
				}
			}
		}
	}

	// -----------------------------------------------------------------------
	// ReadOptions.setSnapshot(null) clears the snapshot
	// -----------------------------------------------------------------------

	@Test
	void setSnapshot_null_clearsSnapshot(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     ReadOptions ro = ReadOptions.newReadOptions()) {
			db.put("key".getBytes(), "v1".getBytes());

			try (Snapshot snap = db.getSnapshot()) {
				ro.setSnapshot(snap);
				db.put("key".getBytes(), "v2".getBytes());

				// Snapshot pinned — sees v1
				assertThat(db.get(ro, "key".getBytes())).isEqualTo("v1".getBytes());

				// When — clear snapshot
				ro.setSnapshot(null);

				// Then — sees latest value
				assertThat(db.get(ro, "key".getBytes())).isEqualTo("v2".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// Snapshot + iterator
	// -----------------------------------------------------------------------

	@Test
	void snapshot_iteratorDoesNotSeeWritesAfterSnapshot(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			try (Snapshot snap = db.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap);
			     RocksIterator it = db.newIterator(ro)) {

				// Write after snapshot
				db.put("c".getBytes(), "3".getBytes());

				// When
				java.util.List<String> keys = new java.util.ArrayList<>();
				for (it.seekToFirst(); it.isValid(); it.next()) {
					keys.add(new String(it.key()));
				}
				it.checkError();

				// Then — "c" was written after snapshot, must not appear
				assertThat(keys).containsExactly("a", "b");
			}
		}
	}

	// -----------------------------------------------------------------------
	// Snapshot + zero-copy get overloads
	// -----------------------------------------------------------------------

	@Test
	void snapshot_get_byteBuffer_isolatesFromWritesAfterSnapshot(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("key".getBytes(), "before".getBytes());

			try (Snapshot snap = db.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap)) {

				db.put("key".getBytes(), "after".getBytes());

				// When
				var key = ByteBuffer.allocateDirect(3).put("key".getBytes()).flip();
				var out = ByteBuffer.allocateDirect(16);
				CopyResult result = db.get(ro, key, out);

				// Then — snapshot read sees the pre-write value
				assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
				out.flip();
				var copied = new byte[out.remaining()];
				out.get(copied);
				assertThat(copied).isEqualTo("before".getBytes());
			}
		}
	}

	@Test
	void snapshot_get_memorySegment_isolatesFromWritesAfterSnapshot(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("key".getBytes(), "before".getBytes());

			try (Snapshot snap = db.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap);
			     Arena arena = Arena.ofConfined()) {

				db.put("key".getBytes(), "after".getBytes());

				// When
				MemorySegment key = arena.allocateFrom("key").asSlice(0, 3);
				MemorySegment out = arena.allocate(16);
				CopyResult result = db.get(ro, key, out);

				// Then — snapshot read sees the pre-write value
				assertThat(result).isEqualTo(CopyResult.Copied.INSTANCE);
				assertThat(out.asSlice(0, "before".getBytes().length).toArray(ValueLayout.JAVA_BYTE))
						.isEqualTo("before".getBytes());
			}
		}
	}

	@Test
	void snapshot_get_mapper_isolatesFromWritesAfterSnapshot(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("key".getBytes(), "before".getBytes());

			try (Snapshot snap = db.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap);
			     Arena arena = Arena.ofConfined()) {

				db.put("key".getBytes(), "after".getBytes());

				// When
				MemorySegment key = arena.allocateFrom("key").asSlice(0, 3);
				var result = db.get(ro, key, value -> value.toArray(ValueLayout.JAVA_BYTE));

				// Then — snapshot read sees the pre-write value
				assertThat(result).contains("before".getBytes());
			}
		}
	}

	@Test
	void snapshot_get_cf_isolatesFromWritesAfterSnapshot(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "key".getBytes(), "before".getBytes());

			try (Snapshot snap = db.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap);
			     Arena arena = Arena.ofConfined()) {

				db.put(cf, "key".getBytes(), "after".getBytes());

				// When
				var byteBufferKey = ByteBuffer.allocateDirect(3).put("key".getBytes()).flip();
				var byteBufferOut = ByteBuffer.allocateDirect(16);
				CopyResult byteBufferResult = db.get(cf, ro, byteBufferKey, byteBufferOut);

				MemorySegment segmentKey = arena.allocateFrom("key").asSlice(0, 3);
				MemorySegment segmentOut = arena.allocate(16);
				CopyResult segmentResult = db.get(cf, ro, segmentKey, segmentOut);

				var mapperResult = db.get(cf, ro, segmentKey, value -> value.toArray(ValueLayout.JAVA_BYTE));

				// Then — every overload's snapshot read sees the pre-write value
				assertThat(byteBufferResult).isEqualTo(CopyResult.Copied.INSTANCE);
				byteBufferOut.flip();
				var copied = new byte[byteBufferOut.remaining()];
				byteBufferOut.get(copied);
				assertThat(copied).isEqualTo("before".getBytes());

				assertThat(segmentResult).isEqualTo(CopyResult.Copied.INSTANCE);
				assertThat(segmentOut.asSlice(0, "before".getBytes().length).toArray(ValueLayout.JAVA_BYTE))
						.isEqualTo("before".getBytes());

				assertThat(mapperResult).contains("before".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// TransactionDB snapshot
	// -----------------------------------------------------------------------

	@Test
	void transactionDB_snapshot_isolation(@TempDir Path dir) {
		// Given
		try (var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir)) {

			db.put("key".getBytes(), "before".getBytes());

			// When
			try (Snapshot snap = db.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap);
			     WriteOptions wo = WriteOptions.newWriteOptions();
			     Transaction txn = db.beginTransaction(wo)) {

				txn.put("key".getBytes(), "after".getBytes());
				txn.commit();

				// Then — snapshot read still sees the pre-commit value
				assertThat(db.get(ro, "key".getBytes())).isEqualTo("before".getBytes());
			}
		}
	}

	@Test
	void snapshot_double_close(@TempDir Path dir) {
		// Given — an already-closed snapshot
		try (var db = RocksDB.openReadWrite(dir)) {
			Snapshot snap = db.getSnapshot();
			snap.close();

			// When
			ThrowingCallable secondClose = snap::close;

			// Then — normally this would trigger a JVM crash
			assertThatCode(secondClose).doesNotThrowAnyException();
		}
	}

	@Test
	void snapshot_closedAfterOwningDb_doesNotCrash(@TempDir Path dir) {
		// Given — a snapshot that is never explicitly closed by the caller
		var db = RocksDB.openReadWrite(dir);
		Snapshot snap = db.getSnapshot();

		// When — closing the DB must release the still-outstanding snapshot itself
		// (synchronously, before its own native handle is destroyed) rather than leaving a
		// dangling pointer for a later snap.close() to crash on
		db.close();

		// Then — the snapshot is already closed as a side effect of db.close()
		assertThatThrownBy(snap::ptr).isInstanceOf(IllegalStateException.class);

		// And closing it again explicitly is still a safe no-op
		ThrowingCallable closeAfterDb = snap::close;
		assertThatCode(closeAfterDb).doesNotThrowAnyException();
	}

	// -----------------------------------------------------------------------
	// Transaction snapshot — dbPtr-less variant, released via rocksdb_free
	// -----------------------------------------------------------------------

	@Test
	void transaction_snapshot_isolation(@TempDir Path dir) {
		// Given
		try (var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txnOpts = TransactionOptions.newTransactionOptions().setSetSnapshot(true)) {

			db.put("key".getBytes(), "before".getBytes());

			// When
			try (Transaction txn = db.beginTransaction(wo, txnOpts);
			     Snapshot snap = txn.getSnapshot();
			     ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap)) {

				db.put("key".getBytes(), "after".getBytes());

				// Then — snapshot taken at transaction start still sees the pre-write value
				assertThat(db.get(ro, "key".getBytes())).isEqualTo("before".getBytes());
			}
		}
	}

	@Test
	void transaction_snapshot_sequenceNumberIsNonNegative(@TempDir Path dir) {
		// Given
		try (var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txnOpts = TransactionOptions.newTransactionOptions().setSetSnapshot(true)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			try (Transaction txn = db.beginTransaction(wo, txnOpts);
			     Snapshot snap = txn.getSnapshot()) {

				// Then
				assertThat(snap.sequenceNumber().toLong()).isGreaterThan(0);
			}
		}
	}

	@Test
	void transaction_getSnapshot_withoutSetSnapshot_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given — a transaction begun with default TransactionOptions (setSetSnapshot defaults
		// to false); RocksDB's C API would otherwise hand back a snapshot wrapper whose `rep` is
		// null, and any use of it (e.g. sequenceNumber()) segfaults the JVM instead of raising
		// a Java exception
		try (var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {

			// When / Then
			assertThatThrownBy(txn::getSnapshot).isInstanceOf(IllegalStateException.class);
		}
	}
}
