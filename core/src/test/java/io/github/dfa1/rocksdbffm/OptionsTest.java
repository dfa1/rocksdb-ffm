package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptionsTest {

	@Test
	void newOptions_hasRocksDBLsmDefaults() {
		// Given

		// When
		try (var opts = Options.newOptions()) {

			// Then
			assertThat(opts.getWriteBufferSize()).isEqualTo(MemorySize.ofMB(64));
			assertThat(opts.getNumLevels()).isEqualTo(7);
			assertThat(opts.getLevel0FileNumCompactionTrigger()).isEqualTo(4);
			assertThat(opts.getTargetFileSizeBase()).isEqualTo(MemorySize.ofMB(64));
			assertThat(opts.getMaxBytesForLevelBase()).isEqualTo(MemorySize.ofMB(256));
			assertThat(opts.getLevel0SlowdownWritesTrigger()).isEqualTo(20);
			assertThat(opts.getLevel0StopWritesTrigger()).isEqualTo(36);
			assertThat(opts.getDisableAutoCompactions()).isFalse();
		}
	}

	@Test
	void setWriteBufferSize_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setWriteBufferSize(MemorySize.ofKB(64))) {

			// When
			var result = opts.getWriteBufferSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofKB(64));
		}
	}

	@Test
	void setNumLevels_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setNumLevels(3)) {

			// When
			var result = opts.getNumLevels();

			// Then
			assertThat(result).isEqualTo(3);
		}
	}

	@Test
	void setLevel0FileNumCompactionTrigger_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setLevel0FileNumCompactionTrigger(2)) {

			// When
			var result = opts.getLevel0FileNumCompactionTrigger();

			// Then
			assertThat(result).isEqualTo(2);
		}
	}

	@Test
	void setTargetFileSizeBase_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setTargetFileSizeBase(MemorySize.ofMB(2))) {

			// When
			var result = opts.getTargetFileSizeBase();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(2));
		}
	}

	@Test
	void setMaxBytesForLevelBase_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMaxBytesForLevelBase(MemorySize.ofMB(8))) {

			// When
			var result = opts.getMaxBytesForLevelBase();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(8));
		}
	}

	@Test
	void setLevel0SlowdownWritesTrigger_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setLevel0SlowdownWritesTrigger(5)) {

			// When
			var result = opts.getLevel0SlowdownWritesTrigger();

			// Then
			assertThat(result).isEqualTo(5);
		}
	}

	@Test
	void setLevel0StopWritesTrigger_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setLevel0StopWritesTrigger(10)) {

			// When
			var result = opts.getLevel0StopWritesTrigger();

			// Then
			assertThat(result).isEqualTo(10);
		}
	}

	@Test
	void setDisableAutoCompactions_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setDisableAutoCompactions(true)) {

			// When
			var result = opts.getDisableAutoCompactions();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setBlobCache_doesNotThrowAndCallerRetainsOwnership() {
		// Given -- ownership is shared, like RateLimiter/SstFileManager, so cache stays usable
		try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(8));
		     var opts = Options.newOptions().setBlobCache(cache)) {

			// When
			var stillOpen = cache.ptr();

			// Then
			assertThat(stillOpen).isNotNull();
		}
	}

	// -----------------------------------------------------------------------
	// Background jobs and file handles
	// -----------------------------------------------------------------------

	@Test
	void setMaxBackgroundJobs_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMaxBackgroundJobs(4)) {

			// When
			var result = opts.getMaxBackgroundJobs();

			// Then
			assertThat(result).isEqualTo(4);
		}
	}

	@Test
	void setMaxOpenFiles_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMaxOpenFiles(100)) {

			// When
			var result = opts.getMaxOpenFiles();

			// Then
			assertThat(result).isEqualTo(100);
		}
	}

	@Test
	void setMaxFileOpeningThreads_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMaxFileOpeningThreads(8)) {

			// When
			var result = opts.getMaxFileOpeningThreads();

			// Then
			assertThat(result).isEqualTo(8);
		}
	}

	@Test
	void setAdviseRandomOnOpen_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setAdviseRandomOnOpen(false)) {

			// When
			var result = opts.getAdviseRandomOnOpen();

			// Then
			assertThat(result).isFalse();
		}
	}

	@Test
	void setSkipStatsUpdateOnDbOpen_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setSkipStatsUpdateOnDbOpen(true)) {

			// When
			var result = opts.getSkipStatsUpdateOnDbOpen();

			// Then
			assertThat(result).isTrue();
		}
	}

	// -----------------------------------------------------------------------
	// Write-path tuning
	// -----------------------------------------------------------------------

	@Test
	void increaseParallelism_doesNotThrowAndReturnsThisForChaining() {
		// Given

		// When
		try (var opts = Options.newOptions().increaseParallelism(4)) {

			// Then
			assertThat(opts).isNotNull();
		}
	}

	@Test
	void setUnorderedWrite_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setUnorderedWrite(true)) {

			// When
			var result = opts.getUnorderedWrite();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setBytesPerSync_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setBytesPerSync(MemorySize.ofMB(1))) {

			// When
			var result = opts.getBytesPerSync();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(1));
		}
	}

	@Test
	void setUseDirectReads_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setUseDirectReads(true)) {

			// When
			var result = opts.getUseDirectReads();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setUseDirectIoForFlushAndCompaction_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setUseDirectIoForFlushAndCompaction(true)) {

			// When
			var result = opts.getUseDirectIoForFlushAndCompaction();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setCompactionPriority_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setCompactionPriority(Options.CompactionPriority.ROUND_ROBIN)) {

			// When
			var result = opts.getCompactionPriority();

			// Then
			assertThat(result).isEqualTo(Options.CompactionPriority.ROUND_ROBIN);
		}
	}

	@Test
	void setBottommostCompressionType_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setBottommostCompressionType(CompressionType.NO_COMPRESSION)) {

			// When
			var result = opts.getBottommostCompressionType();

			// Then
			assertThat(result).isEqualTo(CompressionType.NO_COMPRESSION);
		}
	}

	@Test
	void setMaxWriteBufferNumber_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMaxWriteBufferNumber(4)) {

			// When
			var result = opts.getMaxWriteBufferNumber();

			// Then
			assertThat(result).isEqualTo(4);
		}
	}

	// -----------------------------------------------------------------------
	// Memtable tuning
	// -----------------------------------------------------------------------

	@Test
	void setMemtablePrefixBloomSizeRatio_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMemtablePrefixBloomSizeRatio(0.1)) {

			// When
			var result = opts.getMemtablePrefixBloomSizeRatio();

			// Then
			assertThat(result).isEqualTo(0.1);
		}
	}

	@Test
	void setMemtableWholeKeyFiltering_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMemtableWholeKeyFiltering(true)) {

			// When
			var result = opts.getMemtableWholeKeyFiltering();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setMemtableHugePageSize_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setMemtableHugePageSize(MemorySize.ofMB(2))) {

			// When
			var result = opts.getMemtableHugePageSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(2));
		}
	}

	@Test
	void setBloomLocality_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setBloomLocality(1)) {

			// When
			var result = opts.getBloomLocality();

			// Then
			assertThat(result).isEqualTo(1);
		}
	}

	// -----------------------------------------------------------------------
	// Memtable factory
	// -----------------------------------------------------------------------

	@Test
	void setHashSkipListMemTableFactory_doesNotThrowAndReturnsThisForChaining() {
		// Given

		// When
		try (var opts = Options.newOptions()
				.setPrefixExtractor(SliceTransform.newFixedPrefix(4))
				.setHashSkipListMemTableFactory(1000, 4, 4)) {

			// Then
			assertThat(opts).isNotNull();
		}
	}

	@Test
	void setHashLinkListMemTableFactory_doesNotThrowAndReturnsThisForChaining() {
		// Given

		// When
		try (var opts = Options.newOptions()
				.setPrefixExtractor(SliceTransform.newFixedPrefix(4))
				.setHashLinkListMemTableFactory(1000)) {

			// Then
			assertThat(opts).isNotNull();
		}
	}

	@Test
	void setVectorMemTableFactory_doesNotThrowAndReturnsThisForChaining() {
		// Given

		// When
		try (var opts = Options.newOptions().setVectorMemTableFactory()) {

			// Then
			assertThat(opts).isNotNull();
		}
	}
}
