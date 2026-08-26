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

class ReadBatchTest {

	// -----------------------------------------------------------------------
	// General
	// -----------------------------------------------------------------------

	@Test
	void create_nonPositiveCapacity_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {

			// When / Then
			assertThatThrownBy(() -> ReadBatch.create(db, 0)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> ReadBatch.create(db, -1)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void capacity_returnsConfiguredValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 7)) {

			// When
			int capacity = batch.capacity();

			// Then
			assertThat(capacity).isEqualTo(7);
		}
	}

	@Test
	void close_isIdempotent(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			var batch = ReadBatch.create(db, 2);
			batch.close();

			// When / Then
			assertThatCode(batch::close).doesNotThrowAnyException();
		}
	}

	@Test
	void get_afterClose_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     Arena arena = Arena.ofConfined()) {
			var batch = ReadBatch.create(db, 2);
			List<MemorySegment> keys = List.of(nativeKey(arena, "a"));
			batch.close();

			// When / Then
			assertThatThrownBy(() -> batch.get(keys, ReadBatchTest::decode))
					.isInstanceOf(IllegalStateException.class);
		}
	}

	// -----------------------------------------------------------------------
	// MemorySegment tier (Mapper)
	// -----------------------------------------------------------------------

	@Test
	void get_returnsValuesInOrderWithEmptyForNotFound(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     Arena arena = Arena.ofConfined();
		     var batch = ReadBatch.create(db, 4)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			// When
			List<String> result = batch.get(
					List.of(nativeKey(arena, "a"), nativeKey(arena, "missing"), nativeKey(arena, "b")),
					ReadBatchTest::decode);

			// Then
			assertThat(result).containsExactly("1", null, "2");
		}
	}

	@Test
	void get_reusedAcrossCallsWithDifferentKeysAndSizes(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     Arena arena = Arena.ofConfined();
		     var batch = ReadBatch.create(db, 3)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			// When
			List<String> first = batch.get(
					List.of(nativeKey(arena, "a"), nativeKey(arena, "missing"), nativeKey(arena, "b")),
					ReadBatchTest::decode);

			db.put("c".getBytes(), "3".getBytes());
			List<String> second = batch.get(
					List.of(nativeKey(arena, "c"), nativeKey(arena, "a")), ReadBatchTest::decode);

			// Then
			assertThat(first).containsExactly("1", null, "2");
			assertThat(second).containsExactly("3", "1");
		}
	}

	@Test
	void get_exceedsCapacity_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     Arena arena = Arena.ofConfined();
		     var batch = ReadBatch.create(db, 2)) {

			// When / Then
			assertThatThrownBy(() -> batch.get(
					List.of(nativeKey(arena, "a"), nativeKey(arena, "b"), nativeKey(arena, "c")),
					ReadBatchTest::decode))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void get_emptyKeyList_returnsEmptyList(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 4)) {

			// When
			List<String> result = batch.get(List.<MemorySegment>of(), ReadBatchTest::decode);

			// Then
			assertThat(result).isEmpty();
		}
	}

	@Test
	void get_explicitColumnFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     Arena arena = Arena.ofConfined();
		     var batch = ReadBatch.create(db, cf, 2)) {
			db.put(cf, "x".getBytes(), "9".getBytes());

			// When
			List<String> result = batch.get(
					List.of(nativeKey(arena, "x"), nativeKey(arena, "y")), ReadBatchTest::decode);

			// Then
			assertThat(result).containsExactly("9", null);
		}
	}

	@Test
	void get_explicitReadOptions_seesSnapshotState(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     Arena arena = Arena.ofConfined();
		     var batch = ReadBatch.create(db, 1)) {
			db.put("a".getBytes(), "1".getBytes());

			try (Snapshot snap = db.getSnapshot();
			     ReadOptions readOptions = ReadOptions.newReadOptions().setSnapshot(snap)) {
				db.put("a".getBytes(), "2".getBytes());

				// When
				List<String> result = batch.get(readOptions, List.of(nativeKey(arena, "a")),
						ReadBatchTest::decode);

				// Then — snapshot predates the second write
				assertThat(result).containsExactly("1");
			}
		}
	}

	// -----------------------------------------------------------------------
	// byte[] tier
	// -----------------------------------------------------------------------

	@Test
	void get_byteArray_returnsValuesInOrderWithNullForNotFound(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 3)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			// When
			List<byte[]> result = batch.get(List.of("a".getBytes(), "missing".getBytes(), "b".getBytes()));

			// Then
			assertThat(result).hasSize(3);
			assertThat(result.get(0)).isEqualTo("1".getBytes());
			assertThat(result.get(1)).isNull();
			assertThat(result.get(2)).isEqualTo("2".getBytes());
		}
	}

	@Test
	void get_byteArray_emptyKeyList_returnsEmptyList(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 4)) {

			// When
			List<byte[]> result = batch.get(List.<byte[]>of());

			// Then
			assertThat(result).isEmpty();
		}
	}

	@Test
	void get_byteArray_explicitReadOptions_seesSnapshotState(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 2)) {
			db.put("a".getBytes(), "1".getBytes());

			try (Snapshot snap = db.getSnapshot();
			     ReadOptions readOptions = ReadOptions.newReadOptions().setSnapshot(snap)) {
				db.put("a".getBytes(), "2".getBytes());
				db.put("b".getBytes(), "new".getBytes());

				// When
				List<byte[]> result = batch.get(readOptions, List.of("a".getBytes(), "b".getBytes()));

				// Then — snapshot predates both writes above
				assertThat(result.get(0)).isEqualTo("1".getBytes());
				assertThat(result.get(1)).isNull();
			}
		}
	}

	@Test
	void get_byteArray_explicitColumnFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var batch = ReadBatch.create(db, cf, 2)) {
			db.put(cf, "x".getBytes(), "9".getBytes());

			// When
			List<byte[]> result = batch.get(List.of("x".getBytes(), "y".getBytes()));

			// Then
			assertThat(result.get(0)).isEqualTo("9".getBytes());
			assertThat(result.get(1)).isNull();
		}
	}

	@Test
	void get_byteArray_exceedsCapacity_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 1)) {

			// When / Then
			assertThatThrownBy(() -> batch.get(List.of("a".getBytes(), "b".getBytes())))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// -----------------------------------------------------------------------
	// ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void get_byteBuffer_copiesFoundValuesAndAdvancesPosition(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 2)) {
			db.put("a".getBytes(), "1".getBytes());

			List<ByteBuffer> keys = List.of(directBuffer("a"), directBuffer("missing"));
			ByteBuffer foundValue = ByteBuffer.allocateDirect(16);
			ByteBuffer missingValue = ByteBuffer.allocateDirect(16);
			List<ByteBuffer> values = List.of(foundValue, missingValue);

			// When
			List<CopyResult> result = batch.get(keys, values);

			// Then
			assertThat(result.get(0)).isEqualTo(new CopyResult.Copied());
			assertThat(foundValue.position()).isEqualTo(1);
			assertThat(result.get(1)).isEqualTo(new CopyResult.NotFound());
			assertThat(missingValue.position()).isZero();
		}
	}

	@Test
	void get_byteBuffer_notEnoughCapacity_reportsRequiredLength(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 1)) {
			db.put("a".getBytes(), "toolong".getBytes());

			// When
			List<CopyResult> result = batch.get(List.of(directBuffer("a")), List.of(ByteBuffer.allocateDirect(1)));

			// Then
			assertThat(result.get(0)).isEqualTo(new CopyResult.NotEnoughCapacity(7));
		}
	}

	@Test
	void get_byteBuffer_mismatchedSizes_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = ReadBatch.create(db, 2)) {

			// When / Then
			assertThatThrownBy(() -> batch.get(List.of(directBuffer("a"), directBuffer("b")),
					List.of(ByteBuffer.allocateDirect(1))))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void get_byteBuffer_explicitColumnFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var batch = ReadBatch.create(db, cf, 1)) {
			db.put(cf, "x".getBytes(), "9".getBytes());
			ByteBuffer value = ByteBuffer.allocateDirect(16);

			// When
			List<CopyResult> result = batch.get(List.of(directBuffer("x")), List.of(value));

			// Then
			assertThat(result.get(0)).isEqualTo(new CopyResult.Copied());
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
