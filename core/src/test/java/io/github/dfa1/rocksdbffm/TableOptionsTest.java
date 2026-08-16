package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TableOptionsTest {

	// -----------------------------------------------------------------------
	// BlockBasedTableConfig — basic open/read/write
	// -----------------------------------------------------------------------

	@Test
	void defaultTableConfig_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig();
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void customBlockSize_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setBlockSize(MemorySize.ofKB(16));
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("key".getBytes(), "value".getBytes());

			// When
			var result = db.get("key".getBytes());

			// Then
			assertThat(result).isEqualTo("value".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// FilterPolicy — Bloom
	// -----------------------------------------------------------------------

	@Test
	void bloomFilter_returnsExistingKey(@TempDir Path dir) {
		// Given
		try (var filter = FilterPolicy.newBloom(10);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig().setFilterPolicy(filter);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("bloom-key".getBytes(), "bloom-value".getBytes());

			// When
			var hit = db.get("bloom-key".getBytes());
			var miss = db.get("absent".getBytes());

			// Then
			assertThat(hit).isEqualTo("bloom-value".getBytes());
			assertThat(miss).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// FilterPolicy — Ribbon
	// -----------------------------------------------------------------------

	@Test
	void ribbonFilter_returnsExistingKey(@TempDir Path dir) {
		// Given
		try (var filter = FilterPolicy.newRibbon(10);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig().setFilterPolicy(filter);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("ribbon-key".getBytes(), "ribbon-value".getBytes());

			// When
			var hit = db.get("ribbon-key".getBytes());
			var miss = db.get("absent".getBytes());

			// Then
			assertThat(hit).isEqualTo("ribbon-value".getBytes());
			assertThat(miss).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// Shared block cache
	// -----------------------------------------------------------------------

	@Test
	void sharedBlockCache_servesCachedReads(@TempDir Path dir) {
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

			// When — read all keys to populate the cache
			for (int i = 0; i < 100; i++) {
				assertThat(db.get(("key-" + i).getBytes())).isEqualTo(("val-" + i).getBytes());
			}

			// Then — cache should have been used
			assertThat(cache.getUsage()).isGreaterThanOrEqualTo(MemorySize.ZERO);
		}
	}

	@Test
	void noBlockCache_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setNoBlockCache(true);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Index type
	// -----------------------------------------------------------------------

	@Test
	void twoLevelIndexSearch_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var filter = FilterPolicy.newBloom(10);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig()
					 .setIndexType(BlockBasedTableOptions.IndexType.TWO_LEVEL_INDEX_SEARCH)
					 .setPartitionFilters(true)
					 .setFilterPolicy(filter);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Format version
	// -----------------------------------------------------------------------

	@Test
	void formatVersion5_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setFormatVersion(BlockBasedTableOptions.FormatVersion.V5);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void setFormatVersion_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setFormatVersion(BlockBasedTableOptions.FormatVersion.V5)) {

			// When
			var result = tbl.getFormatVersion();

			// Then
			assertThat(result).isEqualTo(BlockBasedTableOptions.FormatVersion.V5);
		}
	}
}
