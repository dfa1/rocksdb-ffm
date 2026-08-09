package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyDBTest {

	// -----------------------------------------------------------------------
	// get — byte[] tier
	// -----------------------------------------------------------------------

	@Test
	void get_returnsStoredValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("k".getBytes(), "v".getBytes());
		}

		// When
		try (var ro = RocksDB.openReadOnly(dir)) {
			var result = ro.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void get_returnsNull_whenKeyAbsent(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("seed".getBytes(), "val".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir)) {

			// When
			var result = ro.get("nonexistent".getBytes());

			// Then
			assertThat(result).isNull();
		}
	}

	@Test
	void get_withReadOptions(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("k".getBytes(), "v".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir);
		     var readOpts = ReadOptions.newReadOptions()) {

			// When
			var result = ro.get(readOpts, "k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// get — ByteBuffer tier
	// -----------------------------------------------------------------------

	@Test
	void get_byteBuffer_returnsValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("key".getBytes(), "value".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir)) {
			var key = ByteBuffer.allocateDirect(3);
			key.put("key".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(32);

			// When
			CopyResult result = ro.get(key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.Copied());
			out.flip();
			var bytes = new byte[out.remaining()];
			out.get(bytes);
			assertThat(bytes).isEqualTo("value".getBytes());
		}
	}

	@Test
	void get_byteBuffer_returnsNotFound_whenKeyAbsent(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("seed".getBytes(), "val".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir)) {
			var key = ByteBuffer.allocateDirect(7);
			key.put("missing".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(32);

			// When
			CopyResult result = ro.get(key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.NotFound());
		}
	}

	@Test
	void get_byteBuffer_returnsNotEnoughCapacity_whenValueDoesNotFit(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("key".getBytes(), "value".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir)) {
			var key = ByteBuffer.allocateDirect(3);
			key.put("key".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(2);

			// When
			CopyResult result = ro.get(key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.NotEnoughCapacity(5));
			assertThat(out.position()).isZero();
		}
	}

	// -----------------------------------------------------------------------
	// get — MemorySegment tier
	// -----------------------------------------------------------------------

	@Test
	void get_memorySegment_returnsValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("key".getBytes(), "value".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir);
		     Arena arena = Arena.ofConfined()) {
			var key = arena.allocateFrom("key");
			var value = arena.allocate(32);

			// When
			CopyResult result = ro.get(key.asSlice(0, 3), value);

			// Then
			assertThat(result).isEqualTo(new CopyResult.Copied());
			assertThat(value.asSlice(0, 5).toArray(ValueLayout.JAVA_BYTE))
					.isEqualTo("value".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// get — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void get_columnFamily_returnsStoredValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			var cf = rw.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
			rw.put(cf, "k".getBytes(), "v".getBytes());
			cf.close();
		}

		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles)) {
			var cf = handles.get(1);

			// When
			var result = ro.get(cf, "k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void get_columnFamily_withReadOptions(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			var cf = rw.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
			rw.put(cf, "k".getBytes(), "v".getBytes());
			cf.close();
		}

		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles);
		     var readOpts = ReadOptions.newReadOptions()) {
			var cf = handles.get(1);

			// When
			var result = ro.get(cf, readOpts, "k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void get_columnFamily_byteBuffer_returnsValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			var cf = rw.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
			rw.put(cf, "key".getBytes(), "value".getBytes());
			cf.close();
		}

		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles)) {
			var cf = handles.get(1);
			var key = ByteBuffer.allocateDirect(3);
			key.put("key".getBytes()).flip();
			var out = ByteBuffer.allocateDirect(32);

			// When
			CopyResult result = ro.get(cf, key, out);

			// Then
			assertThat(result).isEqualTo(new CopyResult.Copied());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void get_columnFamily_memorySegment_returnsValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			var cf = rw.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
			rw.put(cf, "key".getBytes(), "value".getBytes());
			cf.close();
		}

		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles);
		     Arena arena = Arena.ofConfined()) {
			var cf = handles.get(1);
			var key = arena.allocateFrom("key");
			var value = arena.allocate(32);

			// When
			CopyResult result = ro.get(cf, key.asSlice(0, 3), value);

			// Then
			assertThat(result).isEqualTo(new CopyResult.Copied());
			assertThat(value.asSlice(0, 5).toArray(ValueLayout.JAVA_BYTE))
					.isEqualTo("value".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	// -----------------------------------------------------------------------
	// Iterator — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void newIterator_columnFamily_scansKeys(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			var cf = rw.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
			rw.put(cf, "a".getBytes(), "1".getBytes());
			rw.put(cf, "b".getBytes(), "2".getBytes());
			cf.close();
		}

		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles)) {
			var cf = handles.get(1);

			// When
			try (var it = ro.newIterator(cf)) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("a".getBytes());
			}
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void newIterator_columnFamily_withReadOptions(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			var cf = rw.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
			rw.put(cf, "x".getBytes(), "y".getBytes());
			cf.close();
		}

		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles);
		     var readOpts = ReadOptions.newReadOptions()) {
			var cf = handles.get(1);

			// When
			try (var it = ro.newIterator(cf, readOpts)) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("x".getBytes());
			}
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	// -----------------------------------------------------------------------
	// DB Properties — column family overloads
	// -----------------------------------------------------------------------

	@Test
	void getProperty_columnFamily_returnsValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			var cf = rw.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
			rw.put(cf, "k".getBytes(), "v".getBytes());
			cf.close();
		}

		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles)) {
			var cf = handles.get(1);

			// When
			var result = ro.getProperty(cf, Property.STATS);

			// Then
			assertThat(result).isPresent();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void getLongProperty_columnFamily_returnsValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			var cf = rw.createColumnFamily(ColumnFamilyDescriptor.of("cf1"));
			rw.put(cf, "k".getBytes(), "v".getBytes());
			cf.close();
		}

		List<ColumnFamilyHandle> handles = new ArrayList<>();
		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir,
				     List.of(ColumnFamilyDescriptor.of("default"), ColumnFamilyDescriptor.of("cf1")),
				     handles)) {
			var cf = handles.get(1);

			// When
			var result = ro.getLongProperty(cf, Property.ESTIMATE_NUM_KEYS);

			// Then
			assertThat(result).isPresent();
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	// -----------------------------------------------------------------------
	// Iterator
	// -----------------------------------------------------------------------

	@Test
	void newIterator_iteratesData(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("a".getBytes(), "1".getBytes());
			rw.put("b".getBytes(), "2".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir)) {

			// When
			try (var it = ro.newIterator()) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("a".getBytes());
				it.next();
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("b".getBytes());
			}
		}
	}

	@Test
	void newIterator_withReadOptions(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("x".getBytes(), "y".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir);
		     var readOpts = ReadOptions.newReadOptions()) {

			// When
			try (var it = ro.newIterator(readOpts)) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("x".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// Snapshot
	// -----------------------------------------------------------------------

	@Test
	void getSnapshot_allowsSnapshotRead(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("k".getBytes(), "v".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir)) {

			// When
			try (var snap = ro.getSnapshot();
			     var readOpts = ReadOptions.newReadOptions().setSnapshot(snap)) {

				// Then
				assertThat(ro.get(readOpts, "k".getBytes())).isEqualTo("v".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// DB Properties
	// -----------------------------------------------------------------------

	@Test
	void getLongProperty_returnsValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("k".getBytes(), "v".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir)) {

			// When
			var result = ro.getLongProperty(Property.ESTIMATE_NUM_KEYS);

			// Then
			assertThat(result).isPresent();
		}
	}

	@Test
	void getProperty_returnsValue(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("k".getBytes(), "v".getBytes());
		}

		try (var ro = RocksDB.openReadOnly(dir)) {

			// When
			var result = ro.getProperty(Property.STATS);

			// Then
			assertThat(result).isPresent();
		}
	}
}
