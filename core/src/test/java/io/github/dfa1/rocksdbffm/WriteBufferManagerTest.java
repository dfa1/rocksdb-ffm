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

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then — DB opens and accepts writes with the write buffer manager attached
			assertThat(db).isNotNull();
		}
	}

	@Test
	void createWithCache(@TempDir Path dir) {
		// Given
		try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(10));
		     var wbm = WriteBufferManager.createWithCache(MemorySize.ofMB(100), cache, false);
		     var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then — DB opens and accepts writes with the write buffer manager attached
			assertThat(db).isNotNull();
		}
	}

	@Test
	void bufferSize_initialValueMatchesConstructor() {
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

		// Then — verify the setter doesn't throw and buffer size remains unchanged
		assertThat(wbm.bufferSize()).isEqualTo(MemorySize.ofMB(100));
	}

	@Test
	void memoryUsage_growsAfterPuts(@TempDir Path dir) {
		// Given
		var initialUsage = MemorySize.ZERO;
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferManager(wbm);

		// When
		try (var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k1".getBytes(), new byte[1024]);
			db.put("k2".getBytes(), new byte[1024]);
			db.put("k3".getBytes(), new byte[1024]);
			var usage = wbm.memoryUsage();

			// Then — memory usage should have grown after puts
			assertThat(usage.toBytes()).isGreaterThan(initialUsage.toBytes());
		}
	}

	@Test
	void costToCache_returnsFalseForCreateAndTrueForCreateWithCache() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
		var cache = LRUCache.newLRUCache(MemorySize.ofMB(10));
		var wbmWithCache = WriteBufferManager.createWithCache(MemorySize.ofMB(100), cache, false);

		// When / Then
		// costToCache() is true when cache is passed (dummy entries in cache)
		assertThat(wbmWithCache.costToCache()).isTrue();
		assertThat(wbm.costToCache()).isFalse();
	}

	@Test
	void setAllowStall_returnsThisForChaining() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);

		// When
		var result = wbm.setAllowStall(true);

		// Then — setter returns this for chaining
		assertThat(result).isSameAs(wbm);
	}

	@Test
	void setBufferSize_returnsThisForChaining() {
		// Given
		var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);

		// When
		var result = wbm.setBufferSize(MemorySize.ofMB(200));

		// Then — setter returns this for chaining
		assertThat(result).isSameAs(wbm);
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
