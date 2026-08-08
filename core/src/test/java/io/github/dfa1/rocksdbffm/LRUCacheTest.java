package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
	void usage_increasesAfterPopulatingBlockCache(@TempDir Path dir) {
		// Given
		try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(64));
		     var filter = FilterPolicy.newBloom(10);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig()
					 .setBlockCache(cache)
					 .setCacheIndexAndFilterBlocks(true)
					 .setFilterPolicy(filter);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.open(opts, dir)) {
			for (int i = 0; i < 100; i++) {
				db.put(("key-" + i).getBytes(), ("val-" + i).getBytes());
			}

			// When — read all keys so their blocks land in the shared cache
			for (int i = 0; i < 100; i++) {
				db.get(("key-" + i).getBytes());
			}

			// Then
			assertThat(cache.getUsage()).isGreaterThan(MemorySize.ZERO);
		}
	}
}
