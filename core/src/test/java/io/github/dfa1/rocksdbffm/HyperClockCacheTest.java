package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HyperClockCacheTest {

	@Test
	void newHyperClockCache_reportsConfiguredCapacity() {
		// Given / When
		try (var sut = HyperClockCache.newHyperClockCache(MemorySize.ofMB(32), MemorySize.ofKB(8))) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(32));
		}
	}

	@Test
	void newHyperClockCache_withZeroEstimatedEntryCharge_letsRocksDbPickDefault() {
		// Given / When
		try (var sut = HyperClockCache.newHyperClockCache(MemorySize.ofMB(32), MemorySize.ZERO)) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(32));
		}
	}

	@Test
	void newHyperClockCache_withExplicitShardBits_reportsConfiguredCapacity() {
		// Given / When
		try (var sut = HyperClockCache.newHyperClockCache(
				MemorySize.ofMB(32), MemorySize.ofKB(8), 4)) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(32));
		}
	}

	@Test
	void newHyperClockCache_withAutoShardBits_reportsConfiguredCapacity() {
		// Given / When
		try (var sut = HyperClockCache.newHyperClockCache(
				MemorySize.ofMB(32), MemorySize.ofKB(8), -1)) {

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(32));
		}
	}

	@Test
	void setCapacity_updatesCapacity() {
		// Given
		try (var sut = HyperClockCache.newHyperClockCache(MemorySize.ofMB(8), MemorySize.ofKB(8))) {

			// When
			sut.setCapacity(MemorySize.ofMB(32));

			// Then
			assertThat(sut.getCapacity()).isEqualTo(MemorySize.ofMB(32));
		}
	}

	@Test
	void freshCache_pinnedUsageIsZero() {
		// Given / When
		try (var sut = HyperClockCache.newHyperClockCache(MemorySize.ofMB(32), MemorySize.ofKB(8))) {

			// Then — unlike LRUCache, HyperClockCache pre-allocates a fixed-size slot table on
			// creation, so usage() is already non-zero before anything is cached; pinnedUsage()
			// (nothing pinned yet) still is.
			assertThat(sut.getPinnedUsage()).isEqualTo(MemorySize.ZERO);
			assertThat(sut.getUsage()).isLessThan(sut.getCapacity());
		}
	}

	@Test
	void usage_increasesAfterPopulatingBlockCache(@TempDir Path dir) {
		// Given
		try (var cache = HyperClockCache.newHyperClockCache(MemorySize.ofMB(64), MemorySize.ofKB(8));
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
		}
	}
}
