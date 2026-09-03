package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LRUCacheTest {

	@Test
	void newLRUCache_reportsConfiguredCapacity() {
		// Given / When
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(16))) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(16));
		}
	}

	@Test
	void setCapacity_updatesCapacity() {
		// Given
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(8))) {

			// When
			sut.setCapacity(MemorySize.ofMB(32));

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(32));
		}
	}

	@Test
	void freshCache_usageAndPinnedUsageAreZero() {
		// Given / When
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(16))) {

			// Then
			assertThat(sut.getUsage()).isEqualTo(MemorySize.ZERO);
			assertThat(sut.getPinnedUsage()).isEqualTo(MemorySize.ZERO);
		}
	}

	@Test
	void newLRUCache_withStrictCapacityLimit_reportsConfiguredCapacity() {
		// Given / When
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(16), true)) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(16));
		}
	}

	@Test
	void newLRUCache_withoutStrictCapacityLimit_reportsConfiguredCapacity() {
		// Given / When
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(16), false)) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(16));
		}
	}

	@Test
	void newLRUCache_withExplicitShardBits_reportsConfiguredCapacity() {
		// Given / When
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(16), 4)) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(16));
		}
	}

	@Test
	void newLRUCache_withAutoShardBits_reportsConfiguredCapacity() {
		// Given / When
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(16), -1)) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(16));
		}
	}

	@Test
	void freshCache_occupancyCountIsZero() {
		// Given / When
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(16))) {

			// Then
			assertThat(sut.getOccupancyCount()).isZero();
		}
	}

	@Test
	void freshCache_tableAddressCountIsNotNegative() {
		// Given / When
		try (var sut = LRUCache.newLRUCache(MemorySize.ofMB(16))) {

			// Then — 0 means "not supported"; LRUCache supports it, but assert loosely to avoid
			// coupling the test to RocksDB's internal pre-sizing
			assertThat(sut.getTableAddressCount()).isGreaterThanOrEqualTo(0);
		}
	}

	@Test
	void disownData_afterClosingDb_doesNotThrow(@TempDir Path dir) {
		// Given — a cache actually used by a (now-closed) DB, per disownData()'s own
		// precondition that every database using it is closed first
		var cache = LRUCache.newLRUCache(MemorySize.ofMB(16));
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.setTableFormatConfig(BlockBasedTableOptions.newBlockBasedConfig().setBlockCache(cache));
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());
			db.get("k".getBytes());
		}

		// When
		cache.disownData();

		// Then — the cache object itself can still be closed normally afterward
		assertThatCode(cache::close).doesNotThrowAnyException();
	}

	@Test
	void usageAndOccupancyCount_increaseAfterPopulatingBlockCache(@TempDir Path dir) {
		// Given
		try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(64));
		     var filter = FilterPolicy.newBloom(10);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig()
					 .setBlockCache(cache)
					 .setCacheIndexAndFilterBlocks(true)
					 .setFilterPolicy(filter);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			for (int i = 0; i < 100; i++) {
				db.put(("key-" + i).getBytes(), ("val-" + i).getBytes());
			}

			// When — read all keys so their blocks land in the shared cache
			for (int i = 0; i < 100; i++) {
				db.get(("key-" + i).getBytes());
			}

			// Then
			assertThat(cache.getUsage()).isGreaterThan(MemorySize.ZERO);
			assertThat(cache.getOccupancyCount()).isPositive();
		}
	}
}
