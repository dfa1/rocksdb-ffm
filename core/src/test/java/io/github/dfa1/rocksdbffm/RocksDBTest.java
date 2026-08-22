package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocksDBTest {

	// -----------------------------------------------------------------------
	// put / get / delete
	// -----------------------------------------------------------------------

	@Test
	void get_returnsStoredValue(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("hello".getBytes(), "world".getBytes());

			// When
			var result = db.get("hello".getBytes());

			// Then
			assertThat(result).isEqualTo("world".getBytes());
		}
	}

	@Test
	void get_returnsNull_whenKeyAbsent(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {

			// When
			var result = db.get("nonexistent".getBytes());

			// Then
			assertThat(result).isNull();
		}
	}

	@Test
	void get_returnsNull_afterDelete(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.delete("k".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isNull();
		}
	}

	@Test
	void put_overwritesExistingKey(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("k".getBytes(), "v1".getBytes());

			// When
			db.put("k".getBytes(), "v2".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	@Test
	void put_handlesArbitraryBinaryKeys(@TempDir Path dir) {
		// Given
		var key = new byte[]{0x00, 0x01, (byte) 0xFF};
		var value = new byte[]{0x42, 0x00, 0x43};

		try (var db = RocksDB.openReadWrite(dir)) {
			// When
			db.put(key, value);

			// Then
			assertThat(db.get(key)).isEqualTo(value);
		}
	}

	// -----------------------------------------------------------------------
	// WriteBatch
	// -----------------------------------------------------------------------

	@Test
	void write_commitsBatchedPuts(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create()) {

			batch.put("k1".getBytes(), "v1".getBytes());
			batch.put("k2".getBytes(), "v2".getBytes());
			batch.put("k3".getBytes(), "v3".getBytes());

			// When
			db.write(batch);

			// Then
			assertThat(db.get("k1".getBytes())).isEqualTo("v1".getBytes());
			assertThat(db.get("k2".getBytes())).isEqualTo("v2".getBytes());
			assertThat(db.get("k3".getBytes())).isEqualTo("v3".getBytes());
		}
	}

	@Test
	void write_commitsBatchedDeletes(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create()) {

			db.put("k1".getBytes(), "v1".getBytes());
			db.put("k2".getBytes(), "v2".getBytes());
			batch.delete("k1".getBytes());
			batch.delete("k2".getBytes());

			// When
			db.write(batch);

			// Then
			assertThat(db.get("k1".getBytes())).isNull();
			assertThat(db.get("k2".getBytes())).isNull();
		}
	}

	@Test
	void write_commitsBatchedPuts_byteBuffer(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create()) {

			var key = ByteBuffer.allocateDirect(2).put("k1".getBytes()).flip();
			var value = ByteBuffer.allocateDirect(2).put("v1".getBytes()).flip();
			batch.put(key, value);

			// When
			db.write(batch);

			// Then
			assertThat(db.get("k1".getBytes())).isEqualTo("v1".getBytes());
		}
	}

	@Test
	void write_commitsBatchedPuts_memorySegment(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create();
		     var arena = Arena.ofConfined()) {

			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k1".getBytes());
			var value = arena.allocateFrom(ValueLayout.JAVA_BYTE, "v1".getBytes());
			batch.put(key, value);

			// When
			db.write(batch);

			// Then
			assertThat(db.get("k1".getBytes())).isEqualTo("v1".getBytes());
		}
	}

	@Test
	void write_commitsBatchedDeletes_byteBuffer(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create()) {

			db.put("k1".getBytes(), "v1".getBytes());
			var key = ByteBuffer.allocateDirect(2).put("k1".getBytes()).flip();
			batch.delete(key);

			// When
			db.write(batch);

			// Then
			assertThat(db.get("k1".getBytes())).isNull();
		}
	}

	@Test
	void write_commitsBatchedDeletes_memorySegment(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create();
		     var arena = Arena.ofConfined()) {

			db.put("k1".getBytes(), "v1".getBytes());
			var key = arena.allocateFrom(ValueLayout.JAVA_BYTE, "k1".getBytes());
			batch.delete(key);

			// When
			db.write(batch);

			// Then
			assertThat(db.get("k1".getBytes())).isNull();
		}
	}

	@Test
	void writeBatch_countReflectsQueuedOperations(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var batch = WriteBatch.create()) {

			for (int i = 0; i < 50; i++) {
				batch.put(("key-" + i).getBytes(), ("val-" + i).getBytes());
			}

			// When
			var count = batch.count();

			// Then — count is observable before commit
			assertThat(count).isEqualTo(50);

			// When
			db.write(batch);

			// Then
			for (int i = 0; i < 50; i++) {
				assertThat(db.get(("key-" + i).getBytes())).isEqualTo(("val-" + i).getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// Options — createIfMissing
	// -----------------------------------------------------------------------

	@Test
	void open_fails_whenDbAbsentAndCreateIfMissingFalse(@TempDir Path dir) {
		// Given
		var dbPath = dir.resolve("nonexistent");

		try (var opts = Options.newOptions().setCreateIfMissing(false)) {
			// When
			var thrown = assertThatThrownBy(() -> RocksDB.openReadWrite(opts, dbPath));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void open_createsDb_whenCreateIfMissingTrue(@TempDir Path dir) {
		// Given
		var dbPath = dir.resolve("newdb");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbPath)) {

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void options_createIfMissing_roundTrips() {
		// Given
		try (var opts = Options.newOptions()) {
			assertThat(opts.getCreateIfMissing()).isFalse();

			// When
			opts.setCreateIfMissing(true);
			// Then
			assertThat(opts.getCreateIfMissing()).isTrue();

			// When
			opts.setCreateIfMissing(false);
			// Then
			assertThat(opts.getCreateIfMissing()).isFalse();
		}
	}

	// -----------------------------------------------------------------------
	// Options — blobCompactionReadaheadSize
	// -----------------------------------------------------------------------

	@Test
	void options_blobCompactionReadaheadSize_roundTrips() {
		// Given
		try (var opts = Options.newOptions()) {
			assertThat(opts.getBlobCompactionReadaheadSize()).isEqualTo(MemorySize.ZERO);

			// When
			opts.setBlobCompactionReadaheadSize(MemorySize.ofMB(2));

			// Then
			assertThat(opts.getBlobCompactionReadaheadSize()).isEqualTo(MemorySize.ofMB(2));
		}
	}

	// -----------------------------------------------------------------------
	// readOnly
	// -----------------------------------------------------------------------

	@Test
	void openReadOnly_allowsReads(@TempDir Path dir) {
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
	void openReadOnly_withExplicitOptions(@TempDir Path dir) {
		// Given
		try (var rw = RocksDB.openReadWrite(dir)) {
			rw.put("hello".getBytes(), "world".getBytes());
		}

		try (var opts = Options.newOptions();
		     var ro = RocksDB.openReadOnly(opts, dir)) {

			// When
			var result = ro.get("hello".getBytes());

			// Then
			assertThat(result).isEqualTo("world".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// wrapInvokeFailure — shared catch-block plumbing behind every `invokeExact` call site,
	// tested directly here since the call sites themselves can't force a MethodHandle to throw
	// without sabotaging it (see ADR 0004: a correctly configured downcall handle should never
	// reach its own catch block).
	// -----------------------------------------------------------------------

	@Test
	void wrapInvokeFailure_rethrowsARuntimeExceptionUnwrapped() {
		// Given
		var original = new IllegalStateException("boom");

		// When
		var thrown = assertThatThrownBy(() -> RocksDB.wrapInvokeFailure("op failed", original));

		// Then
		thrown.isSameAs(original);
	}

	@Test
	void wrapInvokeFailure_wrapsAnIOExceptionAsUnchecked() {
		// Given
		var original = new IOException("disk full");

		// When
		var thrown = assertThatThrownBy(() -> RocksDB.wrapInvokeFailure("op failed", original));

		// Then
		thrown.isInstanceOf(UncheckedIOException.class)
				.hasMessage("op failed")
				.hasCause(original);
	}

	@Test
	void wrapInvokeFailure_wrapsAnythingElseAsAnAssertionError() {
		// Given
		var original = new OutOfMemoryError("native alloc failed");

		// When
		var thrown = assertThatThrownBy(() -> RocksDB.wrapInvokeFailure("op failed", original));

		// Then
		thrown.isInstanceOf(AssertionError.class)
				.hasMessage("op failed")
				.hasCause(original);
	}
}
