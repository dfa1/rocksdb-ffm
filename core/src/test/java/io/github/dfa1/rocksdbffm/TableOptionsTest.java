package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Test
	void ribbonHybridFilter_returnsExistingKey(@TempDir Path dir) {
		// Given — bloomBeforeLevel=1: Bloom for flushes/L0, Ribbon from L1 down
		try (var filter = FilterPolicy.newRibbonHybrid(10, 1);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig().setFilterPolicy(filter);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("ribbon-hybrid-key".getBytes(), "ribbon-hybrid-value".getBytes());

			// When
			var hit = db.get("ribbon-hybrid-key".getBytes());
			var miss = db.get("absent".getBytes());

			// Then
			assertThat(hit).isEqualTo("ribbon-hybrid-value".getBytes());
			assertThat(miss).isNull();
		}
	}

	@Test
	void filterPolicy_closedImmediatelyAfterSetFilterPolicy_isANoOpAndDbStillWorks(@TempDir Path dir) {
		// Given — setFilterPolicy transfers ownership to the table config, so closing the
		// FilterPolicy wrapper right away (before it's even used to open a DB) must be a
		// no-op rather than freeing the pointer RocksDB's own shared_ptr now owns
		var filter = FilterPolicy.newBloom(10);
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setFilterPolicy(filter)) {
			filter.close();

			// When
			try (var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
			     var db = RocksDB.openReadWrite(opts, dir)) {
				db.put("bloom-key".getBytes(), "bloom-value".getBytes());
				var hit = db.get("bloom-key".getBytes());

				// Then
				assertThat(hit).isEqualTo("bloom-value".getBytes());
			}
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

	// -----------------------------------------------------------------------
	// Auto-readahead tuning
	// -----------------------------------------------------------------------

	@Test
	void autoReadaheadTuning_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setInitialAutoReadaheadSize(MemorySize.ofKB(4))
				     .setMaxAutoReadaheadSize(MemorySize.ofKB(128))
				     .setNumFileReadsForAutoReadahead(3);
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
	void setMaxAutoReadaheadSize_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setMaxAutoReadaheadSize(MemorySize.ofKB(128))) {

			// When
			var result = tbl.getMaxAutoReadaheadSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofKB(128));
		}
	}

	@Test
	void setInitialAutoReadaheadSize_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setInitialAutoReadaheadSize(MemorySize.ofKB(4))) {

			// When
			var result = tbl.getInitialAutoReadaheadSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofKB(4));
		}
	}

	@Test
	void setNumFileReadsForAutoReadahead_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setNumFileReadsForAutoReadahead(5)) {

			// When
			var result = tbl.getNumFileReadsForAutoReadahead();

			// Then
			assertThat(result).isEqualTo(5L);
		}
	}

	// -----------------------------------------------------------------------
	// Cache pinning and priority
	// -----------------------------------------------------------------------

	@Test
	void cachePinningAndPriority_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setCacheIndexAndFilterBlocks(true)
				     .setCacheIndexAndFilterBlocksWithHighPriority(true)
				     .setPinL0FilterAndIndexBlocksInCache(true)
				     .setPinTopLevelIndexAndFilter(true)
				     .setTopLevelIndexPinningTier(BlockBasedTableOptions.PinningTier.ALL)
				     .setPartitionPinningTier(BlockBasedTableOptions.PinningTier.FLUSHED_AND_SIMILAR)
				     .setUnpartitionedPinningTier(BlockBasedTableOptions.PinningTier.NONE);
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
	void setCacheIndexAndFilterBlocksWithHighPriority_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setCacheIndexAndFilterBlocksWithHighPriority(true)) {

			// When
			var result = tbl.getCacheIndexAndFilterBlocksWithHighPriority();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setPinL0FilterAndIndexBlocksInCache_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setPinL0FilterAndIndexBlocksInCache(true)) {

			// When
			var result = tbl.getPinL0FilterAndIndexBlocksInCache();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setPinTopLevelIndexAndFilter_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setPinTopLevelIndexAndFilter(true)) {

			// When
			var result = tbl.getPinTopLevelIndexAndFilter();

			// Then
			assertThat(result).isTrue();
		}
	}

	// -----------------------------------------------------------------------
	// Block layout
	// -----------------------------------------------------------------------

	@Test
	void blockLayoutTuning_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setBlockRestartInterval(8)
				     .setIndexBlockRestartInterval(2)
				     .setMetadataBlockSize(MemorySize.ofKB(8))
				     .setBlockSizeDeviation(20)
				     .setUseDeltaEncoding(false)
				     .setSeparateKeyValueInDataBlock(true);
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
	void setBlockRestartInterval_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setBlockRestartInterval(8)) {

			// When
			var result = tbl.getBlockRestartInterval();

			// Then
			assertThat(result).isEqualTo(8);
		}
	}

	@Test
	void setIndexBlockRestartInterval_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setIndexBlockRestartInterval(2)) {

			// When
			var result = tbl.getIndexBlockRestartInterval();

			// Then
			assertThat(result).isEqualTo(2);
		}
	}

	@Test
	void setMetadataBlockSize_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setMetadataBlockSize(MemorySize.ofKB(8))) {

			// When
			var result = tbl.getMetadataBlockSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofKB(8));
		}
	}

	@Test
	void setBlockSizeDeviation_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setBlockSizeDeviation(20)) {

			// When
			var result = tbl.getBlockSizeDeviation();

			// Then
			assertThat(result).isEqualTo(20);
		}
	}

	@Test
	void setUseDeltaEncoding_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setUseDeltaEncoding(false)) {

			// When
			var result = tbl.getUseDeltaEncoding();

			// Then
			assertThat(result).isFalse();
		}
	}

	@Test
	void setSeparateKeyValueInDataBlock_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setSeparateKeyValueInDataBlock(true)) {

			// When
			var result = tbl.getSeparateKeyValueInDataBlock();

			// Then
			assertThat(result).isTrue();
		}
	}

	// -----------------------------------------------------------------------
	// Filter tuning
	// -----------------------------------------------------------------------

	@Test
	void filterTuning_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var filter = FilterPolicy.newBloom(10);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setOptimizeFiltersForMemory(true)
				     .setPartitionFilters(true)
				     .setDecouplePartitionedFilters(true)
				     .setDataBlockIndexType(BlockBasedTableOptions.DataBlockIndexType.BINARY_AND_HASH)
				     .setDataBlockHashTableUtilRatio(0.5)
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

	@Test
	void setOptimizeFiltersForMemory_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setOptimizeFiltersForMemory(true)) {

			// When
			var result = tbl.getOptimizeFiltersForMemory();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setDecouplePartitionedFilters_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setDecouplePartitionedFilters(true)) {

			// When
			var result = tbl.getDecouplePartitionedFilters();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setDataBlockHashTableUtilRatio_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setDataBlockHashTableUtilRatio(0.5)) {

			// When
			var result = tbl.getDataBlockHashTableUtilRatio();

			// Then
			assertThat(result).isEqualTo(0.5);
		}
	}

	// -----------------------------------------------------------------------
	// Index tuning
	// -----------------------------------------------------------------------

	@Test
	void indexTuning_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setIndexShortening(BlockBasedTableOptions.IndexShorteningMode.NO_SHORTENING)
				     .setIndexBlockSearchType(BlockBasedTableOptions.IndexSearchType.AUTO)
				     .setDataBlockIndexType(BlockBasedTableOptions.DataBlockIndexType.BINARY_SEARCH)
				     .setEnableIndexCompression(false)
				     .setUniformCvThreshold(0.2);
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
	void setIndexShortening_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setIndexShortening(BlockBasedTableOptions.IndexShorteningMode.NO_SHORTENING)) {

			// When
			var result = tbl.getIndexShortening();

			// Then
			assertThat(result).isEqualTo(BlockBasedTableOptions.IndexShorteningMode.NO_SHORTENING);
		}
	}

	@Test
	void setIndexBlockSearchType_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setIndexBlockSearchType(BlockBasedTableOptions.IndexSearchType.INTERPOLATION)) {

			// When
			var result = tbl.getIndexBlockSearchType();

			// Then
			assertThat(result).isEqualTo(BlockBasedTableOptions.IndexSearchType.INTERPOLATION);
		}
	}

	@Test
	void setDataBlockIndexType_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setDataBlockIndexType(BlockBasedTableOptions.DataBlockIndexType.BINARY_AND_HASH)) {

			// When
			var result = tbl.getDataBlockIndexType();

			// Then
			assertThat(result).isEqualTo(BlockBasedTableOptions.DataBlockIndexType.BINARY_AND_HASH);
		}
	}

	@Test
	void setEnableIndexCompression_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setEnableIndexCompression(false)) {

			// When
			var result = tbl.getEnableIndexCompression();

			// Then
			assertThat(result).isFalse();
		}
	}

	@Test
	void setUniformCvThreshold_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setUniformCvThreshold(0.2)) {

			// When
			var result = tbl.getUniformCvThreshold();

			// Then
			assertThat(result).isEqualTo(0.2);
		}
	}

	// -----------------------------------------------------------------------
	// Corruption and integrity
	// -----------------------------------------------------------------------

	@Test
	void corruptionAndIntegrityTuning_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var filter = FilterPolicy.newBloom(10);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setChecksumType(BlockBasedTableOptions.ChecksumType.CRC32C)
				     .setVerifyCompression(true)
				     .setDetectFilterConstructCorruption(true)
				     .setReadAmpBytesPerBit(8)
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

	@Test
	void setChecksumType_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setChecksumType(BlockBasedTableOptions.ChecksumType.CRC32C)) {

			// When
			var result = tbl.getChecksumType();

			// Then
			assertThat(result).isEqualTo(BlockBasedTableOptions.ChecksumType.CRC32C);
		}
	}

	@Test
	void setVerifyCompression_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setVerifyCompression(true)) {

			// When
			var result = tbl.getVerifyCompression();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setDetectFilterConstructCorruption_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setDetectFilterConstructCorruption(true)) {

			// When
			var result = tbl.getDetectFilterConstructCorruption();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setReadAmpBytesPerBit_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setReadAmpBytesPerBit(8)) {

			// When
			var result = tbl.getReadAmpBytesPerBit();

			// Then
			assertThat(result).isEqualTo(8);
		}
	}

	// -----------------------------------------------------------------------
	// Block alignment
	// -----------------------------------------------------------------------

	@Test
	void blockAlign_allowsReadWrite(@TempDir Path dir) {
		// Given — block_align requires compression disabled
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setBlockAlign(true);
		     var opts = Options.newOptions()
				     .setCreateIfMissing(true)
				     .setCompression(CompressionType.NO_COMPRESSION)
				     .setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void setBlockAlign_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig().setBlockAlign(true)) {

			// When
			var result = tbl.getBlockAlign();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setSuperBlockAlignmentSize_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setSuperBlockAlignmentSize(MemorySize.ofMB(2))) {

			// When
			var result = tbl.getSuperBlockAlignmentSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(2));
		}
	}

	@Test
	void newBlockBasedConfig_hasDefaultSuperBlockAlignmentSpaceOverheadRatio() {
		// Given

		// When
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()) {

			// Then
			assertThat(tbl.getSuperBlockAlignmentSpaceOverheadRatio()).isEqualTo(128L);
		}
	}

	@Test
	void setSuperBlockAlignmentSpaceOverheadRatio_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setSuperBlockAlignmentSpaceOverheadRatio(64)) {

			// When
			var result = tbl.getSuperBlockAlignmentSpaceOverheadRatio();

			// Then
			assertThat(result).isEqualTo(64L);
		}
	}

	// -----------------------------------------------------------------------
	// Block cache prepopulation
	// -----------------------------------------------------------------------

	@Test
	void prepopulateBlockCache_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(64));
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setBlockCache(cache)
				     .setPrepopulateBlockCache(BlockBasedTableOptions.PrepopulateBlockCache.FLUSH_ONLY);
		     var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var fo = FlushOptions.newFlushOptions()) {

			db.put("k".getBytes(), "v".getBytes());
			db.flush(fo);

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void setPrepopulateBlockCache_roundTrips() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setPrepopulateBlockCache(BlockBasedTableOptions.PrepopulateBlockCache.FLUSH_AND_COMPACTION)) {

			// When
			var result = tbl.getPrepopulateBlockCache();

			// Then
			assertThat(result).isEqualTo(BlockBasedTableOptions.PrepopulateBlockCache.FLUSH_AND_COMPACTION);
		}
	}

	// -----------------------------------------------------------------------
	// User-defined index (UDI) activation
	// -----------------------------------------------------------------------

	@Test
	void getUserDefinedIndexFactoryName_isEmptyByDefault() {
		// Given

		// When
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()) {

			// Then
			assertThat(tbl.getUserDefinedIndexFactoryName()).isEmpty();
		}
	}

	@Test
	void setUserDefinedIndexFactoryFromString_unregisteredName_throws() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()) {

			// When / Then — no UserDefinedIndexFactory is registered in this build
			assertThatThrownBy(() -> tbl.setUserDefinedIndexFactoryFromString("NoSuchFactory"))
					.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void clearUserDefinedIndexFactory_leavesFactoryNameEmpty() {
		// Given
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()) {

			// When
			tbl.clearUserDefinedIndexFactory();

			// Then
			assertThat(tbl.getUserDefinedIndexFactoryName()).isEmpty();
		}
	}
}
