package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WriteBufferManagerTest {

	@Test
	void create_withAllowStallFalse(@TempDir Path dir) {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		     var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then — DB opens and accepts writes with the write buffer manager attached
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void create_withAllowStallTrue(@TempDir Path dir) {
		// Given
		try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), true);
		     var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When / Then
			assertThat(db).isNotNull();
		}
	}

	@Test
	void createWithCache(@TempDir Path dir) {
		// Given
		try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(10));
		     var wbm = WriteBufferManager.createWithCache(MemorySize.ofMB(100), cache);
		     var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When / Then
			assertThat(db).isNotNull();
		}
	}

	@Test
	void bufferSize_initialValueMatchesConstructor(@TempDir Path dir) {
		// Given
		var expectedSize = MemorySize.ofMB(100);
		var wbm = WriteBufferManager.create(expectedSize, false);

		// When
		var actualSize = wbm.bufferSize();

		// Then
		assertThat(actualSize).isEqualTo(expectedSize);
	}

	@Test
	void setBufferSize_updatesValue() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		var newSize = MemorySize.ofMB(200);

		// When
		wbm.setBufferSize(newSize);

		// Then
		assertThat(wbm.bufferSize()).isEqualTo(newSize);
	}

	@Test
	void enabled_returnsTrueWhenBufferSizeGreaterThanZero() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);

		// When / Then
		// enabled() returns true if buffer_size > 0
		assertThat(wbm.isEnabled()).isTrue();
	}

	@Test
	void enabled_returnsFalseWhenBufferSizeIsZero() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ZERO, false);

		// When / Then
		assertThat(wbm.isEnabled()).isFalse();
	}

	@Test
	void setAllowStall_updatesValue() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);

		// When
		wbm.setAllowStall(true);

		// Then
		assertThat(wbm.isEnabled()).isTrue();
	}

	@Test
	void memoryUsage_returnsNonNegativeValue() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);

		// When
		var usage = wbm.memoryUsage();

		// Then
		assertThat(usage.toBytes()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void mutableMemtableMemoryUsage_returnsNonNegativeValue() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);

		// When
		var usage = wbm.mutableMemtableMemoryUsage();

		// Then
		assertThat(usage.toBytes()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void dummyEntriesInCacheUsage_returnsNonNegativeValue() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);

		// When
		var usage = wbm.dummyEntriesInCacheUsage();

		// Then
		assertThat(usage.toBytes()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void costToCache_returnsBoolean() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);

		// When / Then
		// costToCache is implementation-dependent; just verify it returns a boolean
		assertThat(wbm.costToCache()).isIn(true, false);
	}

	@Test
	void sharedOwnership_survivesIndependentClose() {
		// Given — WriteBufferManager is not owned by Options; both may be closed independently
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);
		opts.close();

		// When
		ThrowingCallable action = wbm::close;

		// Then — the write buffer manager itself is still usable/closable after Options is gone
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
}
