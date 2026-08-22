package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WriteOptionsTest {

	@Test
	void setSync_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setSync(true);

			// Then
			assertThat(sut.isSync()).isTrue();
		}
	}

	@Test
	void setDisableWal_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setDisableWal(true);

			// Then
			assertThat(sut.isDisableWal()).isTrue();
		}
	}

	@Test
	void setIgnoreMissingColumnFamilies_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setIgnoreMissingColumnFamilies(true);

			// Then
			assertThat(sut.isIgnoreMissingColumnFamilies()).isTrue();
		}
	}

	@Test
	void setNoSlowdown_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setNoSlowdown(true);

			// Then
			assertThat(sut.isNoSlowdown()).isTrue();
		}
	}

	@Test
	void setLowPri_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setLowPri(true);

			// Then
			assertThat(sut.isLowPri()).isTrue();
		}
	}

	@Test
	void setMemtableInsertHintPerBatch_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setMemtableInsertHintPerBatch(true);

			// Then
			assertThat(sut.isMemtableInsertHintPerBatch()).isTrue();
		}
	}

	@Test
	void setRateLimiterPriority_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setRateLimiterPriority(IOPriority.HIGH);

			// Then
			assertThat(sut.getRateLimiterPriority()).isEqualTo(IOPriority.HIGH);
		}
	}

	@Test
	void setIoActivity_roundTrips() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When
			sut.setIoActivity(IOActivity.FLUSH);

			// Then
			assertThat(sut.getIoActivity()).isEqualTo(IOActivity.FLUSH);
		}
	}

	@Test
	void defaults_matchRocksDbDefaults() {
		// Given
		try (var sut = WriteOptions.newWriteOptions()) {

			// When / Then
			assertThat(sut.isSync()).isFalse();
			assertThat(sut.isDisableWal()).isFalse();
			assertThat(sut.isIgnoreMissingColumnFamilies()).isFalse();
			assertThat(sut.isNoSlowdown()).isFalse();
			assertThat(sut.isLowPri()).isFalse();
			assertThat(sut.isMemtableInsertHintPerBatch()).isFalse();
			assertThat(sut.getRateLimiterPriority()).isEqualTo(IOPriority.TOTAL);
			assertThat(sut.getIoActivity()).isEqualTo(IOActivity.UNKNOWN);
		}
	}

	// -----------------------------------------------------------------------
	// Explicit WriteOptions overloads actually reach the native write —
	// proven via WAL absence, not just "the overload compiles"
	// -----------------------------------------------------------------------

	@Test
	void put_withExplicitDisableWal_isReadableButAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.put(wo, "k".getBytes(), "v".getBytes());

			// Then — the write is visible through a normal read...
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			// ...but never reached the WAL, proving the explicit WriteOptions was
			// actually threaded through rather than the default (WAL-enabled) options
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void put_withoutExplicitOptions_appearsInWal(@TempDir Path dir) {
		// Given — same workload as above, but via the default-options overload
		try (var db = RocksDB.openReadWrite(dir)) {
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then — contrast case: the default WriteOptions has WAL enabled
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isTrue();
			}
		}
	}

	@Test
	void put_withExplicitDisableWal_cfScoped_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.put(cf, wo, "k".getBytes(), "v".getBytes());

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void put_arena_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.put(arena, wo, "k".getBytes(), "v".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void put_byteBuffer_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();
			ByteBuffer key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			ByteBuffer value = ByteBuffer.allocateDirect(1).put((byte) 'v').flip();

			// When
			db.put(wo, key, value);

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void put_memorySegment_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var value = arena.allocateFrom(ValueLayout.JAVA_BYTE, "v".getBytes());

			// When
			db.put(wo, key, value);

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void put_arenaMemorySegment_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var value = arena.allocateFrom(ValueLayout.JAVA_BYTE, "v".getBytes());

			// When
			db.put(arena, wo, key, value);

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void put_cfScoped_byteBuffer_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();
			ByteBuffer key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();
			ByteBuffer value = ByteBuffer.allocateDirect(1).put((byte) 'v').flip();

			// When
			db.put(cf, wo, key, value);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void put_cfScoped_memorySegment_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());
			var value = arena.allocateFrom(ValueLayout.JAVA_BYTE, "v".getBytes());

			// When
			db.put(cf, wo, key, value);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void merge_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true).setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir);
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.merge(wo, "k".getBytes(), "v".getBytes());

			// Then
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void delete_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			// Setup write is itself WAL-disabled: getUpdatesSince(seq) is inclusive of the
			// batch at seq, so a WAL-enabled setup put here would make start coincide with
			// a real WAL entry and the assertion below would pass for the wrong reason.
			db.put(wo, "k".getBytes(), "v".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.delete(wo, "k".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void delete_byteBuffer_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			// Setup write is itself WAL-disabled — see comment in
			// delete_withExplicitDisableWal_isAbsentFromWal for why.
			db.put(wo, "k".getBytes(), "v".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();
			ByteBuffer key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();

			// When
			db.delete(wo, key);

			// Then
			assertThat(db.get("k".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void delete_memorySegment_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			// Setup write is itself WAL-disabled — see comment in
			// delete_withExplicitDisableWal_isAbsentFromWal for why.
			db.put(wo, "k".getBytes(), "v".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());

			// When
			db.delete(wo, key);

			// Then
			assertThat(db.get("k".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void delete_cfScoped_bytes_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			db.put(cf, wo, "k".getBytes(), "v".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.delete(cf, wo, "k".getBytes());

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void delete_cfScoped_byteBuffer_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			db.put(cf, wo, "k".getBytes(), "v".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();
			ByteBuffer key = ByteBuffer.allocateDirect(1).put((byte) 'k').flip();

			// When
			db.delete(cf, wo, key);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void delete_cfScoped_memorySegment_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			db.put(cf, wo, "k".getBytes(), "v".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k".getBytes());

			// When
			db.delete(cf, wo, key);

			// Then
			assertThat(db.get(cf, "k".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void deleteRange_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			// Setup writes are themselves WAL-disabled — see comment in
			// delete_withExplicitDisableWal_isAbsentFromWal for why.
			db.put(wo, "a".getBytes(), "1".getBytes());
			db.put(wo, "b".getBytes(), "2".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.deleteRange(wo, "a".getBytes(), "c".getBytes());

			// Then
			assertThat(db.get("a".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void deleteRange_byteBuffer_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			db.put(wo, "a".getBytes(), "1".getBytes());
			db.put(wo, "b".getBytes(), "2".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();
			ByteBuffer startKey = ByteBuffer.allocateDirect(1).put((byte) 'a').flip();
			ByteBuffer endKey = ByteBuffer.allocateDirect(1).put((byte) 'c').flip();

			// When
			db.deleteRange(wo, startKey, endKey);

			// Then
			assertThat(db.get("a".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void deleteRange_memorySegment_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			db.put(wo, "a".getBytes(), "1".getBytes());
			db.put(wo, "b".getBytes(), "2".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();
			var startKey = arena.allocateFrom(ValueLayout.JAVA_BYTE, "a".getBytes());
			var endKey = arena.allocateFrom(ValueLayout.JAVA_BYTE, "c".getBytes());

			// When
			db.deleteRange(wo, startKey, endKey);

			// Then
			assertThat(db.get("a".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void deleteRange_cfScoped_bytes_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			db.put(cf, wo, "a".getBytes(), "1".getBytes());
			db.put(cf, wo, "b".getBytes(), "2".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.deleteRange(cf, wo, "a".getBytes(), "c".getBytes());

			// Then
			assertThat(db.get(cf, "a".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void deleteRange_cfScoped_byteBuffer_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			db.put(cf, wo, "a".getBytes(), "1".getBytes());
			db.put(cf, wo, "b".getBytes(), "2".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();
			ByteBuffer startKey = ByteBuffer.allocateDirect(1).put((byte) 'a').flip();
			ByteBuffer endKey = ByteBuffer.allocateDirect(1).put((byte) 'c').flip();

			// When
			db.deleteRange(cf, wo, startKey, endKey);

			// Then
			assertThat(db.get(cf, "a".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void deleteRange_cfScoped_memorySegment_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true)) {
			db.put(cf, wo, "a".getBytes(), "1".getBytes());
			db.put(cf, wo, "b".getBytes(), "2".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();
			var startKey = arena.allocateFrom(ValueLayout.JAVA_BYTE, "a".getBytes());
			var endKey = arena.allocateFrom(ValueLayout.JAVA_BYTE, "c".getBytes());

			// When
			db.deleteRange(cf, wo, startKey, endKey);

			// Then
			assertThat(db.get(cf, "a".getBytes())).isNull();
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void write_batch_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true);
		     var batch = WriteBatch.create()) {
			batch.put("k".getBytes(), "v".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.write(wo, batch);

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void write_batch_arena_withExplicitDisableWal_isAbsentFromWal(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var arena = Arena.ofConfined();
		     var wo = WriteOptions.newWriteOptions().setDisableWal(true);
		     var batch = WriteBatch.create()) {
			batch.put("k".getBytes(), "v".getBytes());
			SequenceNumber start = db.getLatestSequenceNumber();

			// When
			db.write(arena, wo, batch);

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			try (WalIterator it = db.getUpdatesSince(start)) {
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	// -----------------------------------------------------------------------
	// TransactionDB direct ops — WriteOptions overload wiring
	// -----------------------------------------------------------------------

	@Test
	void transactionDB_put_withExplicitWriteOptions_isReadable(@TempDir Path dir) {
		// Given
		try (var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir);
		     var wo = WriteOptions.newWriteOptions().setSync(false)) {

			// When
			db.put(wo, "k".getBytes(), "v".getBytes());

			// Then — the explicit-options overload is not silently ignored: the value lands
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void transactionDB_put_cfScoped_withExplicitWriteOptions_isReadable(@TempDir Path dir) {
		// Given
		try (var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var wo = WriteOptions.newWriteOptions().setSync(false)) {

			// When
			db.put(cf, wo, "k".getBytes(), "v".getBytes());

			// Then
			assertThat(db.get(cf, "k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void transactionDB_delete_withExplicitWriteOptions_removesKey(@TempDir Path dir) {
		// Given
		try (var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir);
		     var wo = WriteOptions.newWriteOptions().setSync(false)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.delete(wo, "k".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}
}
