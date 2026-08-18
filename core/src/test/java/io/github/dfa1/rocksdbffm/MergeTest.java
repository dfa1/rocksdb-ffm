package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers `merge()` across every write-capable type when no [MergeOperator] is configured — the
/// default state for any [Options] that never calls `setMergeOperator`. Every direct `merge()`
/// call is expected to surface RocksDB's own
/// `Status::InvalidArgument("Merge requires ColumnFamilyOptions::merge_operator != nullptr")`
/// as a [RocksDBException], confirmed against `rocksdb/db/write_batch.cc`'s
/// `MemTableInserter::Merge`. `Transaction` and `WriteBatch` only queue a merge record and don't
/// touch the memtable until `commit()`/`write()`, so those two throw later than the others.
/// For the configured-operator case (merges that actually merge something), see
/// `MergeOperatorTest`.
class MergeTest {

	// -----------------------------------------------------------------------
	// ReadWriteDB
	// -----------------------------------------------------------------------

	@Test
	void readWriteDb_merge_bytes_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			// When
			var thrown = assertThatThrownBy(() -> db.merge("k".getBytes(), "v".getBytes()));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void readWriteDb_merge_byteBuffer_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			ByteBuffer key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			ByteBuffer value = ByteBuffer.allocateDirect(1).put((byte) 'v').flip();

			// When
			var thrown = assertThatThrownBy(() -> db.merge(key, value));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void readWriteDb_merge_memorySegment_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir); var arena = Arena.ofConfined()) {
			MemorySegment key = arena.allocateFrom("k");
			MemorySegment value = arena.allocateFrom("v");

			// When
			var thrown = assertThatThrownBy(() -> db.merge(key, value));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void readWriteDb_merge_withCallerArena_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir); var arena = Arena.ofConfined()) {
			// When
			var thrown = assertThatThrownBy(() -> db.merge(arena, "k".getBytes(), "v".getBytes()));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void readWriteDb_merge_columnFamily_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			// When
			var thrown = assertThatThrownBy(() -> db.merge(cf, "k".getBytes(), "v".getBytes()));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	// -----------------------------------------------------------------------
	// TtlDB
	// -----------------------------------------------------------------------

	@Test
	void ttlDb_merge_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openTtl(dir, Duration.ofSeconds(60))) {
			// When
			var thrown = assertThatThrownBy(() -> db.merge("k".getBytes(), "v".getBytes()));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	// -----------------------------------------------------------------------
	// BlobDB
	// -----------------------------------------------------------------------

	@Test
	void blobDb_merge_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openBlob(dir)) {
			// When
			var thrown = assertThatThrownBy(() -> db.merge("k".getBytes(), "v".getBytes()));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	// -----------------------------------------------------------------------
	// OptimisticTransactionDB
	// -----------------------------------------------------------------------

	@Test
	void optimisticTransactionDb_merge_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(opts, dir)) {
			// When
			var thrown = assertThatThrownBy(() -> db.merge("k".getBytes(), "v".getBytes()));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	// -----------------------------------------------------------------------
	// TransactionDB — direct (non-transactional) merge
	// -----------------------------------------------------------------------

	@Test
	void transactionDb_directMerge_throwsWithoutMergeOperator(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir)) {
			// When
			var thrown = assertThatThrownBy(() -> db.merge("k".getBytes(), "v".getBytes()));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	// -----------------------------------------------------------------------
	// Transaction — merge only queues; failure surfaces at commit()
	// -----------------------------------------------------------------------

	@Test
	void transaction_merge_queuesWithoutError_thenCommitThrows(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {

			// When — queuing itself must not throw
			txn.merge("k".getBytes(), "v".getBytes());

			// Then — the missing merge operator only surfaces once the write reaches the memtable
			assertThatThrownBy(txn::commit).isInstanceOf(RocksDBException.class);
		}
	}

	// -----------------------------------------------------------------------
	// WriteBatch — merge only queues; failure surfaces at write()
	// -----------------------------------------------------------------------

	@Test
	void writeBatch_merge_queuesWithoutError_thenWriteThrows(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create()) {

			// When — queuing itself must not throw
			batch.merge("k".getBytes(), "v".getBytes());

			// Then
			assertThat(batch.count()).isEqualTo(1);
			assertThatThrownBy(() -> db.write(batch)).isInstanceOf(RocksDBException.class);
		}
	}
}
