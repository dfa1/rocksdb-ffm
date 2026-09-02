package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApproximateSizesTest {

	private static final byte[] FULL_RANGE_START = new byte[0];
	private static final byte[] FULL_RANGE_END = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

	private static void writeSizableData(RocksDBWriteOperations db, int keyCount) {
		byte[] value = new byte[4096];
		for (int i = 0; i < keyCount; i++) {
			db.put(String.format("key-%08d", i).getBytes(), value);
		}
	}

	@Test
	void getApproximateSizes_afterFlush_returnsPositiveSizeForFullRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			writeSizableData(db, 200);
			db.flush(fo);

			// When
			var sizes = db.getApproximateSizes(List.of(Range.of(FULL_RANGE_START, FULL_RANGE_END)));

			// Then
			assertThat(sizes).hasSize(1);
			assertThat(sizes[0]).isPositive();
		}
	}

	@Test
	void getApproximateSizes_beforeFlush_defaultsToZero(@TempDir Path dir) {
		// Given -- default options exclude memtables and nothing has been flushed to SST yet
		try (var db = RocksDB.openReadWrite(dir)) {
			writeSizableData(db, 200);

			// When
			var sizes = db.getApproximateSizes(List.of(Range.of(FULL_RANGE_START, FULL_RANGE_END)));

			// Then
			assertThat(sizes).containsExactly(0L);
		}
	}

	@Test
	void getApproximateSizes_withIncludeMemtables_countsUnflushedData(@TempDir Path dir) {
		// Given -- same unflushed data as above, but this time asking to count memtables
		try (var db = RocksDB.openReadWrite(dir);
		     var opts = SizeApproximationOptions.create().setIncludeMemtables(true)) {
			writeSizableData(db, 200);

			// When
			var sizes = db.getApproximateSizes(opts, List.of(Range.of(FULL_RANGE_START, FULL_RANGE_END)));

			// Then
			assertThat(sizes).hasSize(1);
			assertThat(sizes[0]).isPositive();
		}
	}

	@Test
	void getApproximateSizes_multipleRanges_returnsOneSizePerRange(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			writeSizableData(db, 200);
			db.flush(fo);

			// When
			var sizes = db.getApproximateSizes(List.of(
					Range.of(FULL_RANGE_START, "key-00000100".getBytes()),
					Range.of("key-00000100".getBytes(), FULL_RANGE_END)));

			// Then
			assertThat(sizes).hasSize(2);
			assertThat(sizes[0]).isPositive();
			assertThat(sizes[1]).isPositive();
		}
	}

	@Test
	void getApproximateSizes_columnFamilyScoped_isolatesFromDefaultCf(@TempDir Path dir) {
		// Given -- data written only to a non-default column family
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var fo = FlushOptions.newFlushOptions()) {
			byte[] value = new byte[4096];
			for (int i = 0; i < 200; i++) {
				db.put(cf, String.format("key-%08d", i).getBytes(), value);
			}
			db.flush(cf, fo);

			// When
			var defaultCfSizes = db.getApproximateSizes(List.of(Range.of(FULL_RANGE_START, FULL_RANGE_END)));
			var cf1Sizes = db.getApproximateSizes(cf, List.of(Range.of(FULL_RANGE_START, FULL_RANGE_END)));

			// Then
			assertThat(defaultCfSizes).containsExactly(0L);
			assertThat(cf1Sizes[0]).isPositive();
		}
	}

	@Test
	void getApproximateSizes_columnFamilyScopedWithOptions_countsUnflushedData(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var opts = SizeApproximationOptions.create().setIncludeMemtables(true)) {
			byte[] value = new byte[4096];
			for (int i = 0; i < 200; i++) {
				db.put(cf, String.format("key-%08d", i).getBytes(), value);
			}

			// When
			var sizes = db.getApproximateSizes(cf, opts, List.of(Range.of(FULL_RANGE_START, FULL_RANGE_END)));

			// Then
			assertThat(sizes[0]).isPositive();
		}
	}
}
