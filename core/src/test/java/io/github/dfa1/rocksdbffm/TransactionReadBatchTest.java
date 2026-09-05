package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionReadBatchTest {

	private static TransactionDB openDb(Path path) {
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var txnDbOpts = TransactionDBOptions.newTransactionDBOptions()) {
			return RocksDB.openTransaction(opts, txnDbOpts, path);
		}
	}

	// -----------------------------------------------------------------------
	// General
	// -----------------------------------------------------------------------

	@Test
	void create_nonPositiveCapacity_throws(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {

			// When / Then
			assertThatThrownBy(() -> TransactionReadBatch.create(txn, 0)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> TransactionReadBatch.create(txn, -1)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void capacity_returnsConfiguredValue(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var batch = TransactionReadBatch.create(txn, 7)) {

			// When
			int capacity = batch.capacity();

			// Then
			assertThat(capacity).isEqualTo(7);
		}
	}

	@Test
	void close_isIdempotent(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {
			var batch = TransactionReadBatch.create(txn, 2);
			batch.close();

			// When / Then
			assertThatCode(batch::close).doesNotThrowAnyException();
		}
	}

	// -----------------------------------------------------------------------
	// byte[] tier — get / getForUpdate
	// -----------------------------------------------------------------------

	@Test
	void get_returnsValuesInOrderWithNullForNotFound(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var batch = TransactionReadBatch.create(txn, 3)) {
			txn.put("a".getBytes(), "1".getBytes());
			txn.put("b".getBytes(), "2".getBytes());

			// When
			List<byte[]> result = batch.get(List.of("a".getBytes(), "missing".getBytes(), "b".getBytes()));

			// Then
			assertThat(result.get(0)).isEqualTo("1".getBytes());
			assertThat(result.get(1)).isNull();
			assertThat(result.get(2)).isEqualTo("2".getBytes());
		}
	}

	@Test
	void getForUpdate_locksAndReturnsValuesInOrder(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions()) {
			try (var seedTxn = db.beginTransaction(wo)) {
				seedTxn.put("a".getBytes(), "1".getBytes());
				seedTxn.put("b".getBytes(), "2".getBytes());
				seedTxn.commit();
			}

			try (var txn = db.beginTransaction(wo);
			     var batch = TransactionReadBatch.create(txn, 3)) {

				// When
				List<byte[]> result = batch.getForUpdate(List.of("a".getBytes(), "missing".getBytes(), "b".getBytes()));

				// Then
				assertThat(result.get(0)).isEqualTo("1".getBytes());
				assertThat(result.get(1)).isNull();
				assertThat(result.get(2)).isEqualTo("2".getBytes());
				txn.commit();
			}
		}
	}

	@Test
	void get_exceedsCapacity_throws(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var batch = TransactionReadBatch.create(txn, 1)) {

			// When / Then
			assertThatThrownBy(() -> batch.get(List.of("a".getBytes(), "b".getBytes())))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void get_emptyKeyList_returnsEmptyList(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var batch = TransactionReadBatch.create(txn, 4)) {

			// When
			List<byte[]> result = batch.get(List.<byte[]>of());

			// Then
			assertThat(result).isEmpty();
		}
	}

	@Test
	void get_explicitColumnFamily(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var batch = TransactionReadBatch.create(txn, cf, 2)) {
			txn.put(cf, "x".getBytes(), "9".getBytes());

			// When
			List<byte[]> result = batch.get(List.of("x".getBytes(), "y".getBytes()));

			// Then
			assertThat(result.get(0)).isEqualTo("9".getBytes());
			assertThat(result.get(1)).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void get_byteBuffer_copiesFoundValuesAndAdvancesPosition(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var batch = TransactionReadBatch.create(txn, 2)) {
			txn.put("a".getBytes(), "1".getBytes());

			List<ByteBuffer> keys = List.of(directBuffer("a"), directBuffer("missing"));
			ByteBuffer foundValue = ByteBuffer.allocateDirect(16);
			ByteBuffer missingValue = ByteBuffer.allocateDirect(16);

			// When
			List<CopyResult> result = batch.get(keys, List.of(foundValue, missingValue));

			// Then
			assertThat(result.get(0)).isEqualTo(new CopyResult.Copied());
			assertThat(foundValue.position()).isEqualTo(1);
			assertThat(result.get(1)).isEqualTo(new CopyResult.NotFound());
		}
	}

	@Test
	void getForUpdate_byteBuffer_copiesFoundValues(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions()) {
			try (var seedTxn = db.beginTransaction(wo)) {
				seedTxn.put("a".getBytes(), "1".getBytes());
				seedTxn.commit();
			}

			try (var txn = db.beginTransaction(wo);
			     var batch = TransactionReadBatch.create(txn, 1)) {
				ByteBuffer foundValue = ByteBuffer.allocateDirect(16);

				// When
				List<CopyResult> result = batch.getForUpdate(List.of(directBuffer("a")), List.of(foundValue));

				// Then
				assertThat(result.get(0)).isEqualTo(new CopyResult.Copied());
				assertThat(foundValue.position()).isEqualTo(1);
				txn.commit();
			}
		}
	}

	@Test
	void get_byteBuffer_mismatchedSizes_throws(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     var batch = TransactionReadBatch.create(txn, 2)) {

			// When / Then
			assertThatThrownBy(() -> batch.get(List.of(directBuffer("a"), directBuffer("b")),
					List.of(ByteBuffer.allocateDirect(1))))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// -----------------------------------------------------------------------
	// MemorySegment tier (Mapper)
	// -----------------------------------------------------------------------

	@Test
	void get_mapper_returnsValuesInOrderWithNullForNotFound(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo);
		     Arena arena = Arena.ofConfined();
		     var batch = TransactionReadBatch.create(txn, 3)) {
			txn.put("a".getBytes(), "1".getBytes());
			txn.put("b".getBytes(), "2".getBytes());

			// When
			List<String> result = batch.get(
					List.of(nativeKey(arena, "a"), nativeKey(arena, "missing"), nativeKey(arena, "b")),
					TransactionReadBatchTest::decode);

			// Then
			assertThat(result).containsExactly("1", null, "2");
		}
	}

	@Test
	void getForUpdate_mapper_returnsValuesInOrder(@TempDir Path dir) {
		// Given
		try (var db = openDb(dir);
		     var wo = WriteOptions.newWriteOptions()) {
			try (var seedTxn = db.beginTransaction(wo)) {
				seedTxn.put("a".getBytes(), "1".getBytes());
				seedTxn.commit();
			}

			try (var txn = db.beginTransaction(wo);
			     Arena arena = Arena.ofConfined();
			     var batch = TransactionReadBatch.create(txn, 1)) {

				// When
				List<String> result = batch.getForUpdate(List.of(nativeKey(arena, "a")),
						TransactionReadBatchTest::decode);

				// Then
				assertThat(result).containsExactly("1");
				txn.commit();
			}
		}
	}

	private static MemorySegment nativeKey(Arena arena, String s) {
		MemorySegment withNul = arena.allocateFrom(s, StandardCharsets.UTF_8);
		return withNul.asSlice(0, withNul.byteSize() - 1);
	}

	private static String decode(MemorySegment value) {
		byte[] out = new byte[(int) value.byteSize()];
		MemorySegment.copy(value, 0, MemorySegment.ofArray(out), 0, value.byteSize());
		return new String(out, StandardCharsets.UTF_8);
	}

	private static ByteBuffer directBuffer(String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		return ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
	}
}
