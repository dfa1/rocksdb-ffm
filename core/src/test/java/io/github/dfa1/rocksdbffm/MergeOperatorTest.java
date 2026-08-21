package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers `merge()` once a [MergeOperator] is actually configured — the counterpart to
/// `MergeTest`, which documents the no-operator-configured (always throws) default.
class MergeOperatorTest {

	// -----------------------------------------------------------------------
	// uint64Add
	// -----------------------------------------------------------------------

	@Test
	void uint64Add_sumsOperandsIntoAFreshKey(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.merge("views".getBytes(), encodeUint64(1));
			db.merge("views".getBytes(), encodeUint64(1));
			db.merge("views".getBytes(), encodeUint64(3));

			// When
			long total = decodeUint64(db.get("views".getBytes()));

			// Then
			assertThat(total).isEqualTo(5);
		}
	}

	@Test
	void uint64Add_addsOnTopOfAnExistingPutValue(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("views".getBytes(), encodeUint64(10));
			db.merge("views".getBytes(), encodeUint64(5));

			// When
			long total = decodeUint64(db.get("views".getBytes()));

			// Then
			assertThat(total).isEqualTo(15);
		}
	}

	// -----------------------------------------------------------------------
	// merge() tiers — RocksDBWriteOperations exposes 8 overloads (byte[], Arena+byte[],
	// ByteBuffer, MemorySegment, Arena+MemorySegment, and the 3 column-family variants); only
	// the plain byte[] overload is exercised above. These confirm the rest actually merge
	// through to the same result, not just that they compile/queue.
	// -----------------------------------------------------------------------

	@Test
	void uint64Add_viaByteBuffer_sumsOperands(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir)) {
			ByteBuffer key = ByteBuffer.allocateDirect(5).put("views".getBytes()).flip();
			db.merge(key.duplicate(), directBuffer(encodeUint64(1)));
			db.merge(key.duplicate(), directBuffer(encodeUint64(4)));

			// When
			long total = decodeUint64(db.get("views".getBytes()));

			// Then
			assertThat(total).isEqualTo(5);
		}
	}

	@Test
	void uint64Add_viaMemorySegment_sumsOperands(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir);
		     var arena = Arena.ofConfined()) {
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "views".getBytes());
			db.merge(key, arena.allocateFrom(ValueLayout.JAVA_BYTE, encodeUint64(1)));
			db.merge(key, arena.allocateFrom(ValueLayout.JAVA_BYTE, encodeUint64(4)));

			// When
			long total = decodeUint64(db.get("views".getBytes()));

			// Then
			assertThat(total).isEqualTo(5);
		}
	}

	@Test
	void uint64Add_viaCallerArena_sumsOperands(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir);
		     var arena = Arena.ofConfined()) {
			db.merge(arena, "views".getBytes(), encodeUint64(1));
			db.merge(arena, "views".getBytes(), encodeUint64(4));

			// When
			long total = decodeUint64(db.get("views".getBytes()));

			// Then
			assertThat(total).isEqualTo(5);
		}
	}

	@Test
	void uint64Add_viaCallerArenaMemorySegment_sumsOperands(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir);
		     var arena = Arena.ofConfined()) {
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "views".getBytes());
			db.merge(arena, key, arena.allocateFrom(ValueLayout.JAVA_BYTE, encodeUint64(1)));
			db.merge(arena, key, arena.allocateFrom(ValueLayout.JAVA_BYTE, encodeUint64(4)));

			// When
			long total = decodeUint64(db.get("views".getBytes()));

			// Then
			assertThat(total).isEqualTo(5);
		}
	}

	@Test
	void uint64Add_columnFamily_bytes_sumsOperands(@TempDir Path dir) throws RocksDBException {
		// Given — the merge operator is per-column-family, not inherited from the DB-open
		// Options, so it must also be set on the new CF's own descriptor options.
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir);
		     var cfOpts = Options.newOptions().setMergeOperator(MergeOperator.uint64Add());
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1", cfOpts))) {
			db.merge(cf, "views".getBytes(), encodeUint64(1));
			db.merge(cf, "views".getBytes(), encodeUint64(4));

			// When
			long total = decodeUint64(db.get(cf, "views".getBytes()));

			// Then
			assertThat(total).isEqualTo(5);
		}
	}

	@Test
	void uint64Add_columnFamily_byteBuffer_sumsOperands(@TempDir Path dir) throws RocksDBException {
		// Given — the merge operator is per-column-family, not inherited from the DB-open
		// Options, so it must also be set on the new CF's own descriptor options.
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir);
		     var cfOpts = Options.newOptions().setMergeOperator(MergeOperator.uint64Add());
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1", cfOpts))) {
			ByteBuffer key = ByteBuffer.allocateDirect(5).put("views".getBytes()).flip();
			db.merge(cf, key.duplicate(), directBuffer(encodeUint64(1)));
			db.merge(cf, key.duplicate(), directBuffer(encodeUint64(4)));

			// When
			long total = decodeUint64(db.get(cf, "views".getBytes()));

			// Then
			assertThat(total).isEqualTo(5);
		}
	}

	@Test
	void uint64Add_columnFamily_memorySegment_sumsOperands(@TempDir Path dir) throws RocksDBException {
		// Given — the merge operator is per-column-family, not inherited from the DB-open
		// Options, so it must also be set on the new CF's own descriptor options.
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.uint64Add());
		     var db = RocksDB.openReadWrite(opts, dir);
		     var cfOpts = Options.newOptions().setMergeOperator(MergeOperator.uint64Add());
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1", cfOpts));
		     var arena = Arena.ofConfined()) {
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "views".getBytes());
			db.merge(cf, key, arena.allocateFrom(ValueLayout.JAVA_BYTE, encodeUint64(1)));
			db.merge(cf, key, arena.allocateFrom(ValueLayout.JAVA_BYTE, encodeUint64(4)));

			// When
			long total = decodeUint64(db.get(cf, "views".getBytes()));

			// Then
			assertThat(total).isEqualTo(5);
		}
	}

	private static ByteBuffer directBuffer(byte[] bytes) {
		return (ByteBuffer) ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
	}

	private static byte[] encodeUint64(long value) {
		return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
	}

	private static long decodeUint64(byte[] bytes) {
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
	}

	// -----------------------------------------------------------------------
	// custom — StringMax / StringMin, built on MergeOperator.custom(...); fn receives
	// zero-copy MemorySegment views, so these convert to byte[] only where the assertions need it.
	// -----------------------------------------------------------------------

	private static final MergeOperator.FullMergeFn STRING_MAX = (key, existingValue, operands) -> {
		byte[] max = existingValue == null ? null : existingValue.toArray(ValueLayout.JAVA_BYTE);
		for (var operand : operands) {
			byte[] candidate = operand.toArray(ValueLayout.JAVA_BYTE);
			if (max == null || Arrays.compare(candidate, max) > 0) {
				max = candidate;
			}
		}
		return max;
	};

	private static final MergeOperator.FullMergeFn STRING_MIN = (key, existingValue, operands) -> {
		byte[] min = existingValue == null ? null : existingValue.toArray(ValueLayout.JAVA_BYTE);
		for (var operand : operands) {
			byte[] candidate = operand.toArray(ValueLayout.JAVA_BYTE);
			if (min == null || Arrays.compare(candidate, min) < 0) {
				min = candidate;
			}
		}
		return min;
	};

	@Test
	void custom_stringMax_keepsTheLargestOperand(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.custom("string-max", STRING_MAX));
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.merge("high-score".getBytes(), "b".getBytes());
			db.merge("high-score".getBytes(), "z".getBytes());
			db.merge("high-score".getBytes(), "m".getBytes());

			// When
			String result = new String(db.get("high-score".getBytes()), StandardCharsets.UTF_8);

			// Then
			assertThat(result).isEqualTo("z");
		}
	}

	@Test
	void custom_stringMin_keepsTheSmallestOperand(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.custom("string-min", STRING_MIN));
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.merge("low-score".getBytes(), "b".getBytes());
			db.merge("low-score".getBytes(), "z".getBytes());
			db.merge("low-score".getBytes(), "m".getBytes());

			// When
			String result = new String(db.get("low-score".getBytes()), StandardCharsets.UTF_8);

			// Then
			assertThat(result).isEqualTo("b");
		}
	}

	@Test
	void custom_fullMergeFn_seesTheExistingPutValue(@TempDir Path dir) throws RocksDBException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.custom("string-max", STRING_MAX));
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("high-score".getBytes(), "z".getBytes());
			db.merge("high-score".getBytes(), "a".getBytes());

			// When
			String result = new String(db.get("high-score".getBytes()), StandardCharsets.UTF_8);

			// Then
			assertThat(result).isEqualTo("z");
		}
	}

	@Test
	void custom_fullMergeFn_seesOperandsOldestFirst(@TempDir Path dir) throws RocksDBException {
		// Given
		var seen = new java.util.concurrent.atomic.AtomicReference<List<byte[]>>();
		MergeOperator.FullMergeFn capturing = (key, existingValue, operands) -> {
			seen.set(operands.stream().map(o -> o.toArray(ValueLayout.JAVA_BYTE)).toList());
			return operands.get(operands.size() - 1).toArray(ValueLayout.JAVA_BYTE);
		};
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.custom("capturing", capturing));
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.merge("k".getBytes(), "1".getBytes());
			db.merge("k".getBytes(), "2".getBytes());
			db.merge("k".getBytes(), "3".getBytes());

			// When
			db.get("k".getBytes());

			// Then
			assertThat(seen.get()).extracting(b -> new String(b, StandardCharsets.UTF_8))
					.containsExactly("1", "2", "3");
		}
	}

	@Test
	void custom_fullMergeFn_thatThrows_surfacesAsRocksDBException(@TempDir Path dir) {
		// Given
		MergeOperator.FullMergeFn throwing = (key, existingValue, operands) -> {
			throw new RuntimeException("boom");
		};
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setMergeOperator(MergeOperator.custom("throwing", throwing));
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.merge("k".getBytes(), "v".getBytes());

			// When
			var thrown = assertThatThrownBy(() -> db.get("k".getBytes()));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}
}
