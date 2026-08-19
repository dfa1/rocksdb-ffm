package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/// Covers column-family support on [BlobDB] — mirrors a subset of [ColumnFamilyTest], which
/// exercises the same `RocksDB.*Cf*` static helpers via [ReadWriteDB] instead.
class BlobDBColumnFamilyTest {

	@Test
	void openBlob_withDescriptors_seesFamilyCreatedInAnEarlierOpen(@TempDir Path dir) {
		// Given — create a blob DB with a non-default CF and write data
		try (var db = RocksDB.openBlob(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "key".getBytes(), "value".getBytes());
		}

		// When — reopen with both CFs listed explicitly
		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions().setCreateIfMissing(false).setEnableBlobFiles(true);
		     var db = RocksDB.openBlob(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles)) {
			// Then
			assertThat(handles).hasSize(2);
			assertThat(db.get(handles.get(1), "key".getBytes())).isEqualTo("value".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void createColumnFamily_isolatedFromDefaultFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openBlob(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put("key".getBytes(), "default-value".getBytes());
			db.put(cf, "key".getBytes(), "cf-value".getBytes());

			// When
			var defaultResult = db.get("key".getBytes());
			var cfResult = db.get(cf, "key".getBytes());

			// Then
			assertThat(defaultResult).isEqualTo("default-value".getBytes());
			assertThat(cfResult).isEqualTo("cf-value".getBytes());
		}
	}

	@Test
	void dropColumnFamily_removesFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openBlob(dir)) {
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("to-drop"));
			db.put(cf, "key".getBytes(), "value".getBytes());

			// When
			db.dropColumnFamily(cf);
			cf.close();

			// Then
			try (var opts = Options.newOptions()) {
				List<byte[]> families = RocksDB.listColumnFamilies(opts, dir);
				assertThat(families).hasSize(1);
			}
		}
	}

	@Test
	void put_byteBuffer_and_memorySegment_inColumnFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openBlob(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			ByteBuffer key = ByteBuffer.allocateDirect(3).put("bbk".getBytes()).flip();
			ByteBuffer value = ByteBuffer.allocateDirect(3).put("bbv".getBytes()).flip();

			// When
			db.put(cf, key, value);

			// Then
			assertThat(db.get(cf, "bbk".getBytes())).isEqualTo("bbv".getBytes());
		}
	}

	@Test
	void merge_withUint64Add_sumsOperandsInColumnFamily(@TempDir Path dir) {
		// Given — the merge operator is per-column-family, not inherited from the DB-open Options,
		// so it must be set on the new CF's own descriptor options.
		try (var db = RocksDB.openBlob(dir);
		     var cfOpts = Options.newOptions().setMergeOperator(MergeOperator.uint64Add());
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("counters", cfOpts))) {
			byte[] one = ByteBuffer.allocate(Long.BYTES).putLong(1).array();

			// When
			db.merge(cf, "views".getBytes(), one);
			db.merge(cf, "views".getBytes(), one);

			// Then
			long total = ByteBuffer.wrap(db.get(cf, "views".getBytes())).getLong();
			assertThat(total).isEqualTo(2);
		}
	}

	@Test
	void delete_removesKeyFromColumnFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openBlob(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "key".getBytes(), "value".getBytes());

			// When
			db.delete(cf, "key".getBytes());

			// Then
			assertThat(db.get(cf, "key".getBytes())).isNull();
		}
	}

	@Test
	void newIterator_doesNotSeeKeysFromOtherFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openBlob(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put("default-key".getBytes(), "x".getBytes());
			db.put(cf, "cf-key".getBytes(), "y".getBytes());

			// When
			List<String> keys = new ArrayList<>();
			try (var it = db.newIterator(cf)) {
				for (it.seekToFirst(); it.isValid(); it.next()) {
					keys.add(new String(it.key(), java.nio.charset.StandardCharsets.UTF_8));
				}
			}

			// Then
			assertThat(keys).containsExactly("cf-key");
		}
	}

	@Test
	void flush_columnFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openBlob(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
		     var flushOpts = FlushOptions.newFlushOptions().setWait(true)) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			ThrowingCallable action = () -> db.flush(cf, flushOpts);

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	@Test
	void getProperty_columnFamily(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openBlob(dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.getProperty(cf, Property.NUM_ENTRIES_ACTIVE_MEM_TABLE);

			// Then
			assertThat(result).isPresent();
		}
	}
}
