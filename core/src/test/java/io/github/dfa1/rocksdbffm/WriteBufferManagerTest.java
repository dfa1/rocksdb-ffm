package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

class WriteBufferManagerTest {

	/// Large enough to need fresh arena blocks with a 4 MB write buffer (512 KB blocks).
	private static final byte[] VALUE = new byte[64 * 1024];

	@Test
	void bufferSize_initialValueMatchesConstructor() {
		// Given
		var expectedSize = MemorySize.ofMB(100);
		try (var wbm = WriteBufferManager.create(expectedSize, false)) {

			// When
			var actualSize = wbm.bufferSize();

			// Then
			assertThat(actualSize).isEqualTo(expectedSize);
		}
	}

	@Test
	void setBufferSize_updatesValue() {
		// Given
		var newSize = MemorySize.ofMB(200);
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false)) {

			// When
			var result = wbm.setBufferSize(newSize).bufferSize();

			// Then
			assertThat(result).isEqualTo(newSize);
		}
	}

	@Test
	void setBufferSize_rejectsZero() {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false)) {

			// When
			var thrown = catchThrowable(() -> wbm.setBufferSize(MemorySize.ZERO));

			// Then
			assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
			assertThat(wbm.bufferSize()).isEqualTo(MemorySize.ofMB(100));
		}
	}

	@Test
	void isEnabled_trueWhenBufferSizeGreaterThanZero() {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false)) {

			// When
			var enabled = wbm.isEnabled();

			// Then
			assertThat(enabled).isTrue();
		}
	}

	@Test
	void isEnabled_falseWhenBufferSizeIsZero() {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ZERO, false)) {

			// When
			var enabled = wbm.isEnabled();

			// Then
			assertThat(enabled).isFalse();
		}
	}

	@Test
	void costToCache_falseWithoutCache() {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false)) {

			// When
			var costToCache = wbm.costToCache();

			// Then
			assertThat(costToCache).isFalse();
		}
	}

	@Test
	void costToCache_trueWithCache() {
		// Given
		try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(10));
		     var wbm = WriteBufferManager.createWithCache(MemorySize.ofMB(100), cache, false)) {

			// When
			var costToCache = wbm.costToCache();

			// Then
			assertThat(costToCache).isTrue();
		}
	}

	@Test
	void createWithCache_rejectsNullCache() {
		// Given — no cache

		// When
		var thrown = catchThrowable(() -> WriteBufferManager.createWithCache(MemorySize.ofMB(100), null, false));

		// Then
		assertThat(thrown).isInstanceOf(NullPointerException.class).hasMessageContaining("cache");
	}

	@Test
	void memoryUsage_growsAfterPuts(@TempDir Path dir) {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		     var opts = Options.newOptions().setCreateIfMissing(true)
				     .setWriteBufferSize(MemorySize.ofMB(4)).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			var before = wbm.memoryUsage();

			// When
			putValues(db, "k", 16);
			var after = wbm.memoryUsage();

			// Then
			assertThat(after.toBytes()).isGreaterThan(before.toBytes());
		}
	}

	@Test
	void mutableMemtableMemoryUsage_growsAfterPuts(@TempDir Path dir) {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		     var opts = Options.newOptions().setCreateIfMissing(true)
				     .setWriteBufferSize(MemorySize.ofMB(4)).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			var before = wbm.mutableMemtableMemoryUsage();

			// When
			putValues(db, "k", 16);
			var after = wbm.mutableMemtableMemoryUsage();

			// Then
			assertThat(after.toBytes()).isGreaterThan(before.toBytes());
		}
	}

	@Test
	void memoryUsage_isSharedAcrossDatabases(@TempDir Path dir) {
		// Given — one manager attached to two databases
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		     var opts = Options.newOptions().setCreateIfMissing(true)
				     .setWriteBufferSize(MemorySize.ofMB(4)).setWriteBufferManager(wbm);
		     var db1 = RocksDB.openReadWrite(opts, dir.resolve("db1"));
		     var db2 = RocksDB.openReadWrite(opts, dir.resolve("db2"))) {
			putValues(db1, "k", 16);
			var afterFirst = wbm.memoryUsage();

			// When
			putValues(db2, "k", 16);
			var afterSecond = wbm.memoryUsage();

			// Then
			assertThat(afterSecond.toBytes()).isGreaterThan(afterFirst.toBytes());
		}
	}

	@Test
	void dummyEntriesInCacheUsage_growsAfterPutsWithCache(@TempDir Path dir) {
		// Given
		try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(64));
		     var wbm = WriteBufferManager.createWithCache(MemorySize.ofMB(100), cache, false);
		     var opts = Options.newOptions().setCreateIfMissing(true)
				     .setWriteBufferSize(MemorySize.ofMB(4)).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			putValues(db, "k", 16);
			var usage = wbm.dummyEntriesInCacheUsage();

			// Then
			assertThat(usage.toBytes()).isPositive();
		}
	}

	@Test
	void allowStall_false_acceptsWritesOverBudget(@TempDir Path dir) {
		// Given — a budget the first put already exceeds
		try (var wbm = WriteBufferManager.create(MemorySize.ofKB(1), false);
		     var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var noSlowdown = WriteOptions.newWriteOptions().setNoSlowdown(true)) {
			db.put(noSlowdown, "k1".getBytes(), VALUE);

			// When
			ThrowingCallable secondPut = () -> db.put(noSlowdown, "k2".getBytes(), VALUE);

			// Then
			assertThatCode(secondPut).doesNotThrowAnyException();
		}
	}

	@Test
	void allowStall_true_rejectsNoSlowdownWritesOverBudget(@TempDir Path dir) {
		// Given — a budget the freshly opened memtable already exceeds
		try (var wbm = WriteBufferManager.create(MemorySize.ofKB(1), true);
		     var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var noSlowdown = WriteOptions.newWriteOptions().setNoSlowdown(true)) {

			// When
			var thrown = catchThrowable(() -> db.put(noSlowdown, "k".getBytes(), VALUE));

			// Then
			assertThat(thrown).isInstanceOf(RocksDBException.class).hasMessageContaining("Write stall");
		}
	}

	@Test
	void setAllowStall_enablesStalling(@TempDir Path dir) {
		// Given — created without stalling, then switched on
		try (var wbm = WriteBufferManager.create(MemorySize.ofKB(1), false);
		     var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var noSlowdown = WriteOptions.newWriteOptions().setNoSlowdown(true)) {
			db.put(noSlowdown, "k1".getBytes(), VALUE);

			// When
			wbm.setAllowStall(true);
			var thrown = catchThrowable(() -> db.put(noSlowdown, "k2".getBytes(), VALUE));

			// Then
			assertThat(thrown).isInstanceOf(RocksDBException.class).hasMessageContaining("Write stall");
		}
	}

	@Test
	void setAllowStall_returnsThisForChaining() {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false)) {

			// When
			var result = wbm.setAllowStall(true);

			// Then
			assertThat(result).isSameAs(wbm);
		}
	}

	@Test
	void sharedOwnership_survivesIndependentClose() {
		// Given — WriteBufferManager is not owned by Options; both may be closed independently
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		Options.newOptions().setWriteBufferManager(wbm).close();

		// When
		ThrowingCallable action = wbm::close;

		// Then — the write buffer manager itself is still closable after Options is gone
		assertThatCode(action).doesNotThrowAnyException();
	}

	@Test
	void close_isIdempotent() {
		// Given — an already-closed instance
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		wbm.close();

		// When
		ThrowingCallable secondClose = wbm::close;

		// Then — closing twice must not crash the JVM
		assertThatCode(secondClose).doesNotThrowAnyException();
	}

	private static void putValues(ReadWriteDB db, String prefix, int count) {
		for (int i = 0; i < count; i++) {
			db.put((prefix + i).getBytes(), VALUE);
		}
	}
}
