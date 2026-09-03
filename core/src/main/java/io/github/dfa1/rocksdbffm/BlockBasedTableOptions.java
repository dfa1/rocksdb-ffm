package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/// FFM wrapper for `rocksdb_block_based_table_options_t`.
///
/// Configure and pass to [Options#setTableFormatConfig(BlockBasedTableOptions)].
/// `BlockBasedTableOptions` may be closed once the options have been applied —
/// RocksDB internally copies everything it needs.
///
/// ```
/// try (LRUCache cache = LRUCache.newLRUCache(MemorySize.ofMB(64));
///      BlockBasedTableOptions tbl = BlockBasedTableOptions.newBlockBasedConfig()
///          .setBlockSize(MemorySize.ofKB(16))
///          .setFilterPolicy(FilterPolicy.newBloom(10))
///          .setBlockCache(cache)
///          .setCacheIndexAndFilterBlocks(true);
///      Options opts = Options.newOptions()
///          .setCreateIfMissing(true)
///          .setTableFormatConfig(tbl)) {
///     ...
/// }
/// ```
/// ## Filter policy ownership
///
/// Calling [#setFilterPolicy(FilterPolicy)] transfers native ownership to this
/// config object. The `FilterPolicy` must not be used after that call.
public final class BlockBasedTableOptions extends NativeObject {

	// -----------------------------------------------------------------------
	// Index type constants (mirrors rocksdb_block_based_table_index_type_*)
	// -----------------------------------------------------------------------

	/// Index type used by the block-based table format.
	public enum IndexType {
		/// Standard binary-search index.
		BINARY_SEARCH(0),
		/// Hash-based index, requires prefix extractor.
		HASH_SEARCH(1),
		/// Two-level partitioned index (better for large SSTs).
		TWO_LEVEL_INDEX_SEARCH(2);

		final int value;

		IndexType(int v) {
			this.value = v;
		}
	}

	/// SST format version, per `table.h`. Higher versions enable newer on-disk features at
	/// the cost of read compatibility with older RocksDB releases; the information is read
	/// from the file footer, so this only affects newly written tables. Versions 0 and 1 are
	/// no longer supported (RocksDB errors reading such files) and are intentionally not
	/// represented here.
	public enum FormatVersion {
		/// Since RocksDB 3.10. Changes how blocks compressed with LZ4/BZip2/Zlib are encoded.
		V2(2),
		/// Since RocksDB 5.15. Changes how keys are encoded in index blocks.
		V3(3),
		/// Since RocksDB 5.16. Changes how values are encoded in index blocks; reduces index
		/// size when `indexBlockRestartInterval > 1`.
		V4(4),
		/// Since RocksDB 6.6.0. Full/partitioned filters use a faster, more accurate Bloom
		/// filter implementation with a different schema.
		V5(5),
		/// Since RocksDB 8.6.0. Changes the file footer and checksum matching so misplaced
		/// SST data is as likely to fail checksum verification as random corruption; also
		/// checksum-protects the footer itself.
		V6(6),
		/// Since RocksDB 10.4.0. Supports custom compression algorithms via a
		/// `CompressionManager` with a non-built-in name; changes the `TableProperties`
		/// `compression_name` field format. Default.
		V7(7);

		final int value;

		FormatVersion(int value) {
			this.value = value;
		}

		static FormatVersion fromValue(int value) {
			return switch (value) {
				case 2 -> V2;
				case 3 -> V3;
				case 4 -> V4;
				case 5 -> V5;
				case 6 -> V6;
				case 7 -> V7;
				default -> throw new IllegalArgumentException("Unknown FormatVersion value: " + value);
			};
		}
	}

	/// Which tier of block-based tables a metadata-block cache-pinning setting affects
	/// ([#setTopLevelIndexPinningTier], [#setPartitionPinningTier],
	/// [#setUnpartitionedPinningTier]), per `table.h`'s `PinningTier`.
	public enum PinningTier {
		/// Falls back to the deprecated [#setPinL0FilterAndIndexBlocksInCache] /
		/// [#setPinTopLevelIndexAndFilter] booleans instead of a tier-based rule.
		FALLBACK(0),
		/// No block-based tables in this tier are pinned.
		NONE(1),
		/// Tables that may have originated from a memtable flush -- includes L0 tables smaller
		/// than 1.5x the current write buffer size, so also intra-L0 compaction outputs and
		/// ingested files not abnormally large compared to flushed L0 files.
		FLUSHED_AND_SIMILAR(2),
		/// Every block-based table in this tier is pinned.
		ALL(3);

		final int value;

		PinningTier(int value) {
			this.value = value;
		}
	}

	/// How much of an index key is retained, per `table.h`'s `IndexShorteningMode`. Shortening
	/// replaces a full key with the shortest separator that still distinguishes two adjacent
	/// blocks, trading a smaller index for a very small chance of extra block reads on range
	/// scans near a shortened boundary.
	public enum IndexShorteningMode {
		/// Every index key is stored in full. Required by RocksDB's `kBinarySearchWithFirstKey`
		/// index type (not currently exposed by [IndexType]) -- shortening would defeat that
		/// mode's whole point.
		NO_SHORTENING(0),
		/// Shortens keys between blocks, but keeps the last index key (the file's upper bound) in
		/// full. Default.
		SHORTEN_SEPARATORS(1),
		/// Also shortens the last index key. Slightly smaller index, at the cost of the file's
		/// upper bound sometimes being overestimated, which can cause an extra unnecessary block
		/// read on the last data block per file on some seeks.
		SHORTEN_SEPARATORS_AND_SUCCESSOR(2);

		final int value;

		IndexShorteningMode(int value) {
			this.value = value;
		}

		static IndexShorteningMode fromValue(int value) {
			return switch (value) {
				case 0 -> NO_SHORTENING;
				case 1 -> SHORTEN_SEPARATORS;
				case 2 -> SHORTEN_SEPARATORS_AND_SUCCESSOR;
				default -> throw new IllegalArgumentException("Unknown IndexShorteningMode value: " + value);
			};
		}
	}

	/// Search algorithm used within an index block at read time, per `table.h`'s
	/// `BlockSearchType`. Compatible with any index block, regardless of which mode wrote it.
	public enum IndexSearchType {
		/// Standard binary search. Default.
		BINARY(0),
		/// Interpolation search; can outperform binary search for uniformly distributed keys
		/// under the default byte-wise comparator. Avoid combining with
		/// [IndexShorteningMode#SHORTEN_SEPARATORS_AND_SUCCESSOR], which skews the estimated
		/// upper bound in a way that hurts interpolation search specifically.
		INTERPOLATION(1),
		/// Uses interpolation search for blocks flagged "uniform" at write time (see
		/// [#setUniformCvThreshold(double)]), binary search otherwise. Blocks from files written
		/// before that flag existed, or with uniformity detection disabled, are never flagged
		/// uniform and always fall back to binary search.
		AUTO(2);

		final int value;

		IndexSearchType(int value) {
			this.value = value;
		}

		static IndexSearchType fromValue(int value) {
			return switch (value) {
				case 0 -> BINARY;
				case 1 -> INTERPOLATION;
				case 2 -> AUTO;
				default -> throw new IllegalArgumentException("Unknown IndexSearchType value: " + value);
			};
		}
	}

	/// Index format for a single data block's own key index, per `table.h`'s `DataBlockIndexType`
	/// -- distinct from [IndexType], which selects the format of the SST-level index over blocks.
	public enum DataBlockIndexType {
		/// Traditional binary-search block index. Default.
		BINARY_SEARCH(0),
		/// Binary search plus an additional hash index for point lookups, tuned by
		/// [#setDataBlockHashTableUtilRatio(double)].
		BINARY_AND_HASH(1);

		final int value;

		DataBlockIndexType(int value) {
			this.value = value;
		}

		static DataBlockIndexType fromValue(int value) {
			return switch (value) {
				case 0 -> BINARY_SEARCH;
				case 1 -> BINARY_AND_HASH;
				default -> throw new IllegalArgumentException("Unknown DataBlockIndexType value: " + value);
			};
		}
	}

	/// Per-block checksum algorithm, per `table.h`'s `ChecksumType`. Only affects newly written
	/// blocks -- files with a different checksum type remain readable.
	public enum ChecksumType {
		/// No checksum protection.
		NO_CHECKSUM(0),
		/// CRC32C.
		CRC32C(1),
		/// xxHash.
		XX_HASH(2),
		/// xxHash64.
		XX_HASH64(3),
		/// Default since RocksDB 6.27.
		XXH3(4);

		final int value;

		ChecksumType(int value) {
			this.value = value;
		}

		static ChecksumType fromValue(int value) {
			return switch (value) {
				case 0 -> NO_CHECKSUM;
				case 1 -> CRC32C;
				case 2 -> XX_HASH;
				case 3 -> XX_HASH64;
				case 4 -> XXH3;
				default -> throw new IllegalArgumentException("Unknown ChecksumType value: " + value);
			};
		}
	}

	/// Whether to eagerly warm the block cache with blocks this process just wrote, per
	/// `table.h`'s `PrepopulateBlockCache`.
	public enum PrepopulateBlockCache {
		/// No eager warming. Default.
		DISABLE(0),
		/// Warms the cache with blocks written by a memtable flush only.
		FLUSH_ONLY(1),
		/// Warms the cache with blocks written by flush and by compaction. Compaction output is
		/// typically much larger than a flush and often not all of it is hot, so
		/// compaction-warmed blocks are inserted at a lower cache priority than flush-warmed
		/// ones and are evicted first under pressure; recommended mainly when most or all of the
		/// database is expected to stay resident in cache.
		FLUSH_AND_COMPACTION(2);

		final int value;

		PrepopulateBlockCache(int value) {
			this.value = value;
		}

		static PrepopulateBlockCache fromValue(int value) {
			return switch (value) {
				case 0 -> DISABLE;
				case 1 -> FLUSH_ONLY;
				case 2 -> FLUSH_AND_COMPACTION;
				default -> throw new IllegalArgumentException("Unknown PrepopulateBlockCache value: " + value);
			};
		}
	}

	// -----------------------------------------------------------------------
	// Method handles
	// -----------------------------------------------------------------------

	/// `rocksdb_block_based_table_options_t* rocksdb_block_based_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_block_based_options_destroy(rocksdb_block_based_table_options_t* options);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_block_based_options_set_block_size(rocksdb_block_based_table_options_t* options, size_t block_size);`
	private static final MethodHandle MH_SET_BLOCK_SIZE;
	/// `void rocksdb_block_based_options_set_filter_policy(rocksdb_block_based_table_options_t* options, rocksdb_filterpolicy_t* filter_policy);`
	private static final MethodHandle MH_SET_FILTER_POLICY;
	/// `void rocksdb_block_based_options_set_no_block_cache(rocksdb_block_based_table_options_t* options, unsigned char no_block_cache);`
	private static final MethodHandle MH_SET_NO_BLOCK_CACHE;
	/// `void rocksdb_block_based_options_set_block_cache(rocksdb_block_based_table_options_t* options, rocksdb_cache_t* block_cache);`
	private static final MethodHandle MH_SET_BLOCK_CACHE;
	/// `void rocksdb_block_based_options_set_cache_index_and_filter_blocks(rocksdb_block_based_table_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_CACHE_INDEX_AND_FILTER_BLOCKS;
	/// `void rocksdb_block_based_options_set_index_type(rocksdb_block_based_table_options_t*, int);`
	private static final MethodHandle MH_SET_INDEX_TYPE;
	/// `void rocksdb_block_based_options_set_format_version(rocksdb_block_based_table_options_t*, int);`
	private static final MethodHandle MH_SET_FORMAT_VERSION;
	/// `uint32_t rocksdb_block_based_options_get_format_version(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_FORMAT_VERSION;
	/// `void rocksdb_block_based_options_set_whole_key_filtering(rocksdb_block_based_table_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_WHOLE_KEY_FILTERING;
	/// `void rocksdb_block_based_options_set_partition_filters(rocksdb_block_based_table_options_t* options, unsigned char partition_filters);`
	private static final MethodHandle MH_SET_PARTITION_FILTERS;
	/// `void rocksdb_block_based_options_set_max_auto_readahead_size(rocksdb_block_based_table_options_t* opt, size_t v);`
	private static final MethodHandle MH_SET_MAX_AUTO_READAHEAD_SIZE;
	/// `size_t rocksdb_block_based_options_get_max_auto_readahead_size(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_MAX_AUTO_READAHEAD_SIZE;
	/// `void rocksdb_block_based_options_set_initial_auto_readahead_size(rocksdb_block_based_table_options_t* opt, size_t v);`
	private static final MethodHandle MH_SET_INITIAL_AUTO_READAHEAD_SIZE;
	/// `size_t rocksdb_block_based_options_get_initial_auto_readahead_size(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_INITIAL_AUTO_READAHEAD_SIZE;
	/// `void rocksdb_block_based_options_set_num_file_reads_for_auto_readahead(rocksdb_block_based_table_options_t* opt, uint64_t v);`
	private static final MethodHandle MH_SET_NUM_FILE_READS_FOR_AUTO_READAHEAD;
	/// `uint64_t rocksdb_block_based_options_get_num_file_reads_for_auto_readahead(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_NUM_FILE_READS_FOR_AUTO_READAHEAD;
	/// `void rocksdb_block_based_options_set_cache_index_and_filter_blocks_with_high_priority(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_CACHE_INDEX_AND_FILTER_BLOCKS_WITH_HIGH_PRIORITY;
	/// `unsigned char rocksdb_block_based_options_get_cache_index_and_filter_blocks_with_high_priority(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_CACHE_INDEX_AND_FILTER_BLOCKS_WITH_HIGH_PRIORITY;
	/// `void rocksdb_block_based_options_set_pin_l0_filter_and_index_blocks_in_cache(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE;
	/// `unsigned char rocksdb_block_based_options_get_pin_l0_filter_and_index_blocks_in_cache(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE;
	/// `void rocksdb_block_based_options_set_pin_top_level_index_and_filter(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_PIN_TOP_LEVEL_INDEX_AND_FILTER;
	/// `unsigned char rocksdb_block_based_options_get_pin_top_level_index_and_filter(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_PIN_TOP_LEVEL_INDEX_AND_FILTER;
	/// `void rocksdb_block_based_options_set_top_level_index_pinning_tier(rocksdb_block_based_table_options_t* options, int v);`
	private static final MethodHandle MH_SET_TOP_LEVEL_INDEX_PINNING_TIER;
	/// `void rocksdb_block_based_options_set_partition_pinning_tier(rocksdb_block_based_table_options_t* options, int v);`
	private static final MethodHandle MH_SET_PARTITION_PINNING_TIER;
	/// `void rocksdb_block_based_options_set_unpartitioned_pinning_tier(rocksdb_block_based_table_options_t* options, int v);`
	private static final MethodHandle MH_SET_UNPARTITIONED_PINNING_TIER;
	/// `void rocksdb_block_based_options_set_block_restart_interval(rocksdb_block_based_table_options_t* opt, int v);`
	private static final MethodHandle MH_SET_BLOCK_RESTART_INTERVAL;
	/// `int rocksdb_block_based_options_get_block_restart_interval(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_BLOCK_RESTART_INTERVAL;
	/// `void rocksdb_block_based_options_set_index_block_restart_interval(rocksdb_block_based_table_options_t* opt, int v);`
	private static final MethodHandle MH_SET_INDEX_BLOCK_RESTART_INTERVAL;
	/// `int rocksdb_block_based_options_get_index_block_restart_interval(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_INDEX_BLOCK_RESTART_INTERVAL;
	/// `void rocksdb_block_based_options_set_metadata_block_size(rocksdb_block_based_table_options_t* opt, uint64_t v);`
	private static final MethodHandle MH_SET_METADATA_BLOCK_SIZE;
	/// `uint64_t rocksdb_block_based_options_get_metadata_block_size(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_METADATA_BLOCK_SIZE;
	/// `void rocksdb_block_based_options_set_block_size_deviation(rocksdb_block_based_table_options_t* opt, int v);`
	private static final MethodHandle MH_SET_BLOCK_SIZE_DEVIATION;
	/// `int rocksdb_block_based_options_get_block_size_deviation(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_BLOCK_SIZE_DEVIATION;
	/// `void rocksdb_block_based_options_set_use_delta_encoding(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_USE_DELTA_ENCODING;
	/// `unsigned char rocksdb_block_based_options_get_use_delta_encoding(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_USE_DELTA_ENCODING;
	/// `void rocksdb_block_based_options_set_separate_key_value_in_data_block(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_SEPARATE_KEY_VALUE_IN_DATA_BLOCK;
	/// `unsigned char rocksdb_block_based_options_get_separate_key_value_in_data_block(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_SEPARATE_KEY_VALUE_IN_DATA_BLOCK;
	/// `void rocksdb_block_based_options_set_optimize_filters_for_memory(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_OPTIMIZE_FILTERS_FOR_MEMORY;
	/// `unsigned char rocksdb_block_based_options_get_optimize_filters_for_memory(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_OPTIMIZE_FILTERS_FOR_MEMORY;
	/// `void rocksdb_block_based_options_set_decouple_partitioned_filters(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_DECOUPLE_PARTITIONED_FILTERS;
	/// `unsigned char rocksdb_block_based_options_get_decouple_partitioned_filters(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_DECOUPLE_PARTITIONED_FILTERS;
	/// `void rocksdb_block_based_options_set_data_block_hash_table_util_ratio(rocksdb_block_based_table_options_t* opt, double v);`
	private static final MethodHandle MH_SET_DATA_BLOCK_HASH_TABLE_UTIL_RATIO;
	/// `double rocksdb_block_based_options_get_data_block_hash_table_util_ratio(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_DATA_BLOCK_HASH_TABLE_UTIL_RATIO;
	/// `void rocksdb_block_based_options_set_index_shortening(rocksdb_block_based_table_options_t* opt, int v);`
	private static final MethodHandle MH_SET_INDEX_SHORTENING;
	/// `int rocksdb_block_based_options_get_index_shortening(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_INDEX_SHORTENING;
	/// `void rocksdb_block_based_options_set_data_block_index_type(rocksdb_block_based_table_options_t* opt, int v);`
	private static final MethodHandle MH_SET_DATA_BLOCK_INDEX_TYPE;
	/// `int rocksdb_block_based_options_get_data_block_index_type(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_DATA_BLOCK_INDEX_TYPE;
	/// `void rocksdb_block_based_options_set_index_block_search_type(rocksdb_block_based_table_options_t* opt, int v);`
	private static final MethodHandle MH_SET_INDEX_BLOCK_SEARCH_TYPE;
	/// `int rocksdb_block_based_options_get_index_block_search_type(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_INDEX_BLOCK_SEARCH_TYPE;
	/// `void rocksdb_block_based_options_set_enable_index_compression(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_ENABLE_INDEX_COMPRESSION;
	/// `unsigned char rocksdb_block_based_options_get_enable_index_compression(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_ENABLE_INDEX_COMPRESSION;
	/// `void rocksdb_block_based_options_set_uniform_cv_threshold(rocksdb_block_based_table_options_t* opt, double v);`
	private static final MethodHandle MH_SET_UNIFORM_CV_THRESHOLD;
	/// `double rocksdb_block_based_options_get_uniform_cv_threshold(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_UNIFORM_CV_THRESHOLD;
	/// `void rocksdb_block_based_options_set_checksum(rocksdb_block_based_table_options_t* opt, char v);`
	private static final MethodHandle MH_SET_CHECKSUM;
	/// `int rocksdb_block_based_options_get_checksum(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_CHECKSUM;
	/// `void rocksdb_block_based_options_set_verify_compression(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_VERIFY_COMPRESSION;
	/// `unsigned char rocksdb_block_based_options_get_verify_compression(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_VERIFY_COMPRESSION;
	/// `void rocksdb_block_based_options_set_detect_filter_construct_corruption(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_DETECT_FILTER_CONSTRUCT_CORRUPTION;
	/// `unsigned char rocksdb_block_based_options_get_detect_filter_construct_corruption(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_DETECT_FILTER_CONSTRUCT_CORRUPTION;
	/// `void rocksdb_block_based_options_set_read_amp_bytes_per_bit(rocksdb_block_based_table_options_t* opt, uint32_t v);`
	private static final MethodHandle MH_SET_READ_AMP_BYTES_PER_BIT;
	/// `uint32_t rocksdb_block_based_options_get_read_amp_bytes_per_bit(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_READ_AMP_BYTES_PER_BIT;
	/// `void rocksdb_block_based_options_set_block_align(rocksdb_block_based_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_BLOCK_ALIGN;
	/// `unsigned char rocksdb_block_based_options_get_block_align(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_BLOCK_ALIGN;
	/// `void rocksdb_block_based_options_set_super_block_alignment_size(rocksdb_block_based_table_options_t* opt, size_t v);`
	private static final MethodHandle MH_SET_SUPER_BLOCK_ALIGNMENT_SIZE;
	/// `size_t rocksdb_block_based_options_get_super_block_alignment_size(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_SUPER_BLOCK_ALIGNMENT_SIZE;
	/// `void rocksdb_block_based_options_set_super_block_alignment_space_overhead_ratio(rocksdb_block_based_table_options_t* opt, size_t v);`
	private static final MethodHandle MH_SET_SUPER_BLOCK_ALIGNMENT_SPACE_OVERHEAD_RATIO;
	/// `size_t rocksdb_block_based_options_get_super_block_alignment_space_overhead_ratio(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_SUPER_BLOCK_ALIGNMENT_SPACE_OVERHEAD_RATIO;
	/// `void rocksdb_block_based_options_set_prepopulate_block_cache(rocksdb_block_based_table_options_t* opt, int v);`
	private static final MethodHandle MH_SET_PREPOPULATE_BLOCK_CACHE;
	/// `int rocksdb_block_based_options_get_prepopulate_block_cache(rocksdb_block_based_table_options_t* opt);`
	private static final MethodHandle MH_GET_PREPOPULATE_BLOCK_CACHE;
	/// `void rocksdb_block_based_options_set_user_defined_index_factory_from_string(rocksdb_block_based_table_options_t* options, const char* value, size_t value_len, char** errptr);`
	private static final MethodHandle MH_SET_UDI_FACTORY_FROM_STRING;
	/// `void rocksdb_block_based_options_clear_user_defined_index_factory(rocksdb_block_based_table_options_t* options);`
	private static final MethodHandle MH_CLEAR_UDI_FACTORY;
	/// `const char* rocksdb_block_based_options_get_user_defined_index_factory_name(const rocksdb_block_based_table_options_t* options, size_t* name_len);`
	private static final MethodHandle MH_GET_UDI_FACTORY_NAME;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_block_based_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_block_based_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_BLOCK_SIZE = NativeLibrary.lookup("rocksdb_block_based_options_set_block_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_SET_FILTER_POLICY = NativeLibrary.lookup("rocksdb_block_based_options_set_filter_policy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_NO_BLOCK_CACHE = NativeLibrary.lookup("rocksdb_block_based_options_set_no_block_cache",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_BLOCK_CACHE = NativeLibrary.lookup("rocksdb_block_based_options_set_block_cache",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_CACHE_INDEX_AND_FILTER_BLOCKS = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_cache_index_and_filter_blocks",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_INDEX_TYPE = NativeLibrary.lookup("rocksdb_block_based_options_set_index_type",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_SET_FORMAT_VERSION = NativeLibrary.lookup("rocksdb_block_based_options_set_format_version",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_FORMAT_VERSION = NativeLibrary.lookup("rocksdb_block_based_options_get_format_version",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_WHOLE_KEY_FILTERING = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_whole_key_filtering",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_PARTITION_FILTERS = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_partition_filters",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_MAX_AUTO_READAHEAD_SIZE = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_max_auto_readahead_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_AUTO_READAHEAD_SIZE = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_max_auto_readahead_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_INITIAL_AUTO_READAHEAD_SIZE = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_initial_auto_readahead_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_INITIAL_AUTO_READAHEAD_SIZE = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_initial_auto_readahead_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_NUM_FILE_READS_FOR_AUTO_READAHEAD = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_num_file_reads_for_auto_readahead",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_NUM_FILE_READS_FOR_AUTO_READAHEAD = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_num_file_reads_for_auto_readahead",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_CACHE_INDEX_AND_FILTER_BLOCKS_WITH_HIGH_PRIORITY = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_cache_index_and_filter_blocks_with_high_priority",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_CACHE_INDEX_AND_FILTER_BLOCKS_WITH_HIGH_PRIORITY = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_cache_index_and_filter_blocks_with_high_priority",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_pin_l0_filter_and_index_blocks_in_cache",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_pin_l0_filter_and_index_blocks_in_cache",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_PIN_TOP_LEVEL_INDEX_AND_FILTER = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_pin_top_level_index_and_filter",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_PIN_TOP_LEVEL_INDEX_AND_FILTER = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_pin_top_level_index_and_filter",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_TOP_LEVEL_INDEX_PINNING_TIER = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_top_level_index_pinning_tier",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_SET_PARTITION_PINNING_TIER = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_partition_pinning_tier",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_SET_UNPARTITIONED_PINNING_TIER = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_unpartitioned_pinning_tier",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_SET_BLOCK_RESTART_INTERVAL = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_block_restart_interval",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_BLOCK_RESTART_INTERVAL = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_block_restart_interval",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_INDEX_BLOCK_RESTART_INTERVAL = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_index_block_restart_interval",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_INDEX_BLOCK_RESTART_INTERVAL = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_index_block_restart_interval",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_METADATA_BLOCK_SIZE = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_metadata_block_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_METADATA_BLOCK_SIZE = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_metadata_block_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_BLOCK_SIZE_DEVIATION = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_block_size_deviation",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_BLOCK_SIZE_DEVIATION = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_block_size_deviation",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_USE_DELTA_ENCODING = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_use_delta_encoding",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_DELTA_ENCODING = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_use_delta_encoding",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_SEPARATE_KEY_VALUE_IN_DATA_BLOCK = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_separate_key_value_in_data_block",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_SEPARATE_KEY_VALUE_IN_DATA_BLOCK = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_separate_key_value_in_data_block",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_OPTIMIZE_FILTERS_FOR_MEMORY = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_optimize_filters_for_memory",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_OPTIMIZE_FILTERS_FOR_MEMORY = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_optimize_filters_for_memory",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_DECOUPLE_PARTITIONED_FILTERS = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_decouple_partitioned_filters",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_DECOUPLE_PARTITIONED_FILTERS = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_decouple_partitioned_filters",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_DATA_BLOCK_HASH_TABLE_UTIL_RATIO = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_data_block_hash_table_util_ratio",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_DATA_BLOCK_HASH_TABLE_UTIL_RATIO = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_data_block_hash_table_util_ratio",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

		MH_SET_INDEX_SHORTENING = NativeLibrary.lookup("rocksdb_block_based_options_set_index_shortening",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_INDEX_SHORTENING = NativeLibrary.lookup("rocksdb_block_based_options_get_index_shortening",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_DATA_BLOCK_INDEX_TYPE = NativeLibrary.lookup("rocksdb_block_based_options_set_data_block_index_type",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_DATA_BLOCK_INDEX_TYPE = NativeLibrary.lookup("rocksdb_block_based_options_get_data_block_index_type",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_INDEX_BLOCK_SEARCH_TYPE = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_index_block_search_type",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_INDEX_BLOCK_SEARCH_TYPE = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_index_block_search_type",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_ENABLE_INDEX_COMPRESSION = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_enable_index_compression",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ENABLE_INDEX_COMPRESSION = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_enable_index_compression",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_UNIFORM_CV_THRESHOLD = NativeLibrary.lookup("rocksdb_block_based_options_set_uniform_cv_threshold",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_UNIFORM_CV_THRESHOLD = NativeLibrary.lookup("rocksdb_block_based_options_get_uniform_cv_threshold",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

		MH_SET_CHECKSUM = NativeLibrary.lookup("rocksdb_block_based_options_set_checksum",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_CHECKSUM = NativeLibrary.lookup("rocksdb_block_based_options_get_checksum",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_VERIFY_COMPRESSION = NativeLibrary.lookup("rocksdb_block_based_options_set_verify_compression",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_VERIFY_COMPRESSION = NativeLibrary.lookup("rocksdb_block_based_options_get_verify_compression",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_DETECT_FILTER_CONSTRUCT_CORRUPTION = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_detect_filter_construct_corruption",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_DETECT_FILTER_CONSTRUCT_CORRUPTION = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_detect_filter_construct_corruption",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_READ_AMP_BYTES_PER_BIT = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_read_amp_bytes_per_bit",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_READ_AMP_BYTES_PER_BIT = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_read_amp_bytes_per_bit",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_BLOCK_ALIGN = NativeLibrary.lookup("rocksdb_block_based_options_set_block_align",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_BLOCK_ALIGN = NativeLibrary.lookup("rocksdb_block_based_options_get_block_align",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_SUPER_BLOCK_ALIGNMENT_SIZE = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_super_block_alignment_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_SUPER_BLOCK_ALIGNMENT_SIZE = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_super_block_alignment_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_SUPER_BLOCK_ALIGNMENT_SPACE_OVERHEAD_RATIO = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_super_block_alignment_space_overhead_ratio",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_SUPER_BLOCK_ALIGNMENT_SPACE_OVERHEAD_RATIO = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_super_block_alignment_space_overhead_ratio",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_PREPOPULATE_BLOCK_CACHE = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_prepopulate_block_cache",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_PREPOPULATE_BLOCK_CACHE = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_prepopulate_block_cache",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_UDI_FACTORY_FROM_STRING = NativeLibrary.lookup(
				"rocksdb_block_based_options_set_user_defined_index_factory_from_string",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_CLEAR_UDI_FACTORY = NativeLibrary.lookup(
				"rocksdb_block_based_options_clear_user_defined_index_factory",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_GET_UDI_FACTORY_NAME = NativeLibrary.lookup(
				"rocksdb_block_based_options_get_user_defined_index_factory_name",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private BlockBasedTableOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a new [BlockBasedTableOptions] with RocksDB defaults.
	///
	/// @return a new instance; caller must close it
	public static BlockBasedTableOptions newBlockBasedConfig() {
		try {
			return new BlockBasedTableOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable e) {
			throw RocksDB.wrapInvokeFailure("new block based config", e);
		}
	}

	// -----------------------------------------------------------------------
	// Setters
	// -----------------------------------------------------------------------

	/// Size of each data block. Default: 4 KB.
	/// Larger blocks improve compression but increase read amplification.
	///
	/// @param blockSize desired block size
	/// @return `this` for chaining
	public BlockBasedTableOptions setBlockSize(MemorySize blockSize) {
		NativeFields.setMemorySize(MH_SET_BLOCK_SIZE, ptr(), blockSize);
		return this;
	}

	/// Sets the filter policy. Transfers ownership of `policy` to this config; do not close it afterwards.
	///
	/// @param policy filter policy to use; ownership is transferred
	/// @return `this` for chaining
	public BlockBasedTableOptions setFilterPolicy(FilterPolicy policy) {
		try {
			MH_SET_FILTER_POLICY.invokeExact(ptr(), policy.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setFilterPolicy failed", t);
		}
		// BlockBasedTableConfig will take care of the freeing the policy
		policy.transferOwnership();
		return this;
	}

	/// If true, no block cache is used for this table. Default: false.
	/// Use when all data fits in memory or when block cache would be counter-productive.
	///
	/// @param noBlockCache `true` to disable block cache for this table
	/// @return `this` for chaining
	public BlockBasedTableOptions setNoBlockCache(boolean noBlockCache) {
		NativeFields.setBoolean(MH_SET_NO_BLOCK_CACHE, ptr(), noBlockCache);
		return this;
	}

	/// Sets a custom block cache. The `cache` object remains owned by the caller
	/// and can be shared across multiple table configs.
	///
	/// @param cache block cache to use; caller retains ownership
	/// @return `this` for chaining
	public BlockBasedTableOptions setBlockCache(Cache cache) {
		try {
			MH_SET_BLOCK_CACHE.invokeExact(ptr(), cache.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setBlockCache failed", t);
		}
		return this;
	}

	/// If true, index and filter blocks are stored in the block cache (subject to
	/// eviction). Default: false (index/filter are pinned in memory).
	///
	/// @param value `true` to store index/filter blocks in the block cache
	/// @return `this` for chaining
	public BlockBasedTableOptions setCacheIndexAndFilterBlocks(boolean value) {
		NativeFields.setBoolean(MH_SET_CACHE_INDEX_AND_FILTER_BLOCKS, ptr(), value);
		return this;
	}

	/// Sets the index type. Default: [IndexType#BINARY_SEARCH].
	/// Use [IndexType#TWO_LEVEL_INDEX_SEARCH] for very large SSTs.
	///
	/// @param indexType index type to use
	/// @return `this` for chaining
	public BlockBasedTableOptions setIndexType(IndexType indexType) {
		NativeFields.setInt(MH_SET_INDEX_TYPE, ptr(), indexType.value);
		return this;
	}

	/// Sets the SST format version. Higher versions enable newer features but
	/// reduce backward compatibility. Default: [FormatVersion#V7].
	///
	/// @param formatVersion SST format version to use
	/// @return `this` for chaining
	public BlockBasedTableOptions setFormatVersion(FormatVersion formatVersion) {
		NativeFields.setInt(MH_SET_FORMAT_VERSION, ptr(), formatVersion.value);
		return this;
	}

	/// Returns the configured SST format version.
	///
	/// @return current SST format version
	public FormatVersion getFormatVersion() {
		return FormatVersion.fromValue(NativeFields.getInt(MH_GET_FORMAT_VERSION, ptr()));
	}

	/// If true, a whole-key Bloom filter is built in addition to any prefix filter.
	/// Default: true. Set to false when only a prefix filter is desired.
	///
	/// @param value `true` to enable whole-key filtering
	/// @return `this` for chaining
	public BlockBasedTableOptions setWholeKeyFiltering(boolean value) {
		NativeFields.setBoolean(MH_SET_WHOLE_KEY_FILTERING, ptr(), value);
		return this;
	}

	/// If true, use partitioned Bloom filters (one small filter per index partition).
	/// Requires [IndexType#TWO_LEVEL_INDEX_SEARCH].
	///
	/// @param value `true` to enable partitioned filters
	/// @return `this` for chaining
	public BlockBasedTableOptions setPartitionFilters(boolean value) {
		NativeFields.setBoolean(MH_SET_PARTITION_FILTERS, ptr(), value);
		return this;
	}

	// -----------------------------------------------------------------------
	// Auto-readahead tuning
	// -----------------------------------------------------------------------

	/// Upper bound on the read-ahead size RocksDB grows to for sequential scans of this
	/// table. Auto-readahead starts at [#setInitialAutoReadaheadSize] and doubles after every
	/// [#setNumFileReadsForAutoReadahead] sequential reads, capped at this value. Default: 256 KB.
	///
	/// @param size maximum auto-readahead size
	/// @return `this` for chaining
	public BlockBasedTableOptions setMaxAutoReadaheadSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_MAX_AUTO_READAHEAD_SIZE, ptr(), size);
		return this;
	}

	/// Returns the configured maximum auto-readahead size.
	///
	/// @return current maximum auto-readahead size
	public MemorySize getMaxAutoReadaheadSize() {
		return NativeFields.getMemorySize(MH_GET_MAX_AUTO_READAHEAD_SIZE, ptr());
	}

	/// Read-ahead size RocksDB starts with for a new sequential scan of this table, before it
	/// grows toward [#setMaxAutoReadaheadSize]. Default: 8 KB.
	///
	/// @param size initial auto-readahead size
	/// @return `this` for chaining
	public BlockBasedTableOptions setInitialAutoReadaheadSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_INITIAL_AUTO_READAHEAD_SIZE, ptr(), size);
		return this;
	}

	/// Returns the configured initial auto-readahead size.
	///
	/// @return current initial auto-readahead size
	public MemorySize getInitialAutoReadaheadSize() {
		return NativeFields.getMemorySize(MH_GET_INITIAL_AUTO_READAHEAD_SIZE, ptr());
	}

	/// Number of sequential file reads that must be observed before RocksDB doubles the
	/// auto-readahead size (up to [#setMaxAutoReadaheadSize]). Default: 2.
	///
	/// @param count number of sequential reads that triggers doubling the readahead size
	/// @return `this` for chaining
	public BlockBasedTableOptions setNumFileReadsForAutoReadahead(long count) {
		NativeFields.setLong(MH_SET_NUM_FILE_READS_FOR_AUTO_READAHEAD, ptr(), count);
		return this;
	}

	/// Returns the configured number of sequential reads that triggers doubling the
	/// auto-readahead size.
	///
	/// @return current number of sequential reads that triggers doubling the readahead size
	public long getNumFileReadsForAutoReadahead() {
		return NativeFields.getLong(MH_GET_NUM_FILE_READS_FOR_AUTO_READAHEAD, ptr());
	}

	// -----------------------------------------------------------------------
	// Cache pinning and priority
	// -----------------------------------------------------------------------

	/// If true, index and filter blocks are inserted into the block cache with high priority,
	/// making them less likely to be evicted than normal-priority data blocks. Only takes
	/// effect when [#setCacheIndexAndFilterBlocks] is `true`. Default: false.
	///
	/// @param value `true` to insert index/filter blocks at high cache priority
	/// @return `this` for chaining
	public BlockBasedTableOptions setCacheIndexAndFilterBlocksWithHighPriority(boolean value) {
		NativeFields.setBoolean(MH_SET_CACHE_INDEX_AND_FILTER_BLOCKS_WITH_HIGH_PRIORITY, ptr(), value);
		return this;
	}

	/// Returns whether index/filter blocks are inserted into the block cache at high priority.
	///
	/// @return `true` if index/filter blocks are inserted at high cache priority
	public boolean getCacheIndexAndFilterBlocksWithHighPriority() {
		return NativeFields.getBoolean(MH_GET_CACHE_INDEX_AND_FILTER_BLOCKS_WITH_HIGH_PRIORITY, ptr());
	}

	/// Deprecated pinning control, superseded by [#setUnpartitionedPinningTier] and
	/// [#setPartitionPinningTier] (used only when those are left at
	/// [PinningTier#FALLBACK]). If true, pins L0 filter and index blocks in the block cache.
	/// Default: false.
	///
	/// @param value `true` to pin L0 filter and index blocks in the block cache
	/// @return `this` for chaining
	public BlockBasedTableOptions setPinL0FilterAndIndexBlocksInCache(boolean value) {
		NativeFields.setBoolean(MH_SET_PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE, ptr(), value);
		return this;
	}

	/// Returns whether L0 filter and index blocks are pinned in the block cache.
	///
	/// @return `true` if L0 filter and index blocks are pinned in the block cache
	public boolean getPinL0FilterAndIndexBlocksInCache() {
		return NativeFields.getBoolean(MH_GET_PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE, ptr());
	}

	/// Deprecated pinning control, superseded by [#setTopLevelIndexPinningTier] (used only
	/// when that is left at [PinningTier#FALLBACK]). If true, pins the top-level index and
	/// filter on partitioned index/filter tables. Default: false.
	///
	/// @param value `true` to pin the top-level index and filter
	/// @return `this` for chaining
	public BlockBasedTableOptions setPinTopLevelIndexAndFilter(boolean value) {
		NativeFields.setBoolean(MH_SET_PIN_TOP_LEVEL_INDEX_AND_FILTER, ptr(), value);
		return this;
	}

	/// Returns whether the top-level index and filter are pinned.
	///
	/// @return `true` if the top-level index and filter are pinned
	public boolean getPinTopLevelIndexAndFilter() {
		return NativeFields.getBoolean(MH_GET_PIN_TOP_LEVEL_INDEX_AND_FILTER, ptr());
	}

	/// The tier of block-based tables whose top-level index into metadata partitions will be
	/// pinned in the block cache. Requires [#setCacheIndexAndFilterBlocks] to be `true` to have
	/// any effect. Default: [PinningTier#FALLBACK].
	///
	/// @param tier the pinning tier to apply
	/// @return `this` for chaining
	public BlockBasedTableOptions setTopLevelIndexPinningTier(PinningTier tier) {
		NativeFields.setInt(MH_SET_TOP_LEVEL_INDEX_PINNING_TIER, ptr(), tier.value);
		return this;
	}

	/// The tier of block-based tables whose metadata partitions (index and filter) will be
	/// pinned in the block cache. Default: [PinningTier#FALLBACK].
	///
	/// @param tier the pinning tier to apply
	/// @return `this` for chaining
	public BlockBasedTableOptions setPartitionPinningTier(PinningTier tier) {
		NativeFields.setInt(MH_SET_PARTITION_PINNING_TIER, ptr(), tier.value);
		return this;
	}

	/// The tier of block-based tables whose unpartitioned metadata blocks will be pinned in
	/// the block cache. Requires [#setCacheIndexAndFilterBlocks] to be `true` to have any
	/// effect. Default: [PinningTier#FALLBACK].
	///
	/// @param tier the pinning tier to apply
	/// @return `this` for chaining
	public BlockBasedTableOptions setUnpartitionedPinningTier(PinningTier tier) {
		NativeFields.setInt(MH_SET_UNPARTITIONED_PINNING_TIER, ptr(), tier.value);
		return this;
	}

	// -----------------------------------------------------------------------
	// Block layout
	// -----------------------------------------------------------------------

	/// Number of keys between restart points in a data block. Every restart point stores its
	/// key in full; keys in between are delta-encoded against the previous key (subject to
	/// [#setUseDeltaEncoding]). Smaller values speed up random reads within a block at the
	/// cost of space; larger values save space at the cost of a longer linear scan per lookup.
	/// Default: 16.
	///
	/// @param interval number of keys between restart points
	/// @return `this` for chaining
	public BlockBasedTableOptions setBlockRestartInterval(int interval) {
		NativeFields.setInt(MH_SET_BLOCK_RESTART_INTERVAL, ptr(), interval);
		return this;
	}

	/// Returns the configured number of keys between restart points in a data block.
	///
	/// @return current block restart interval
	public int getBlockRestartInterval() {
		return NativeFields.getInt(MH_GET_BLOCK_RESTART_INTERVAL, ptr());
	}

	/// Same as [#setBlockRestartInterval], but for the index block instead of data blocks.
	/// Values greater than 1 reduce index block size (per format version 4+) at the cost of
	/// a longer linear scan per index lookup. Default: 1 (no delta encoding of index entries).
	///
	/// @param interval number of index entries between restart points
	/// @return `this` for chaining
	public BlockBasedTableOptions setIndexBlockRestartInterval(int interval) {
		NativeFields.setInt(MH_SET_INDEX_BLOCK_RESTART_INTERVAL, ptr(), interval);
		return this;
	}

	/// Returns the configured number of index entries between restart points in the index block.
	///
	/// @return current index block restart interval
	public int getIndexBlockRestartInterval() {
		return NativeFields.getInt(MH_GET_INDEX_BLOCK_RESTART_INTERVAL, ptr());
	}

	/// Approximate size of partitioned metadata blocks (index/filter partitions). Only takes
	/// effect with [IndexType#TWO_LEVEL_INDEX_SEARCH] and [#setPartitionFilters]. Default: 4 KB.
	///
	/// @param size approximate metadata block size
	/// @return `this` for chaining
	public BlockBasedTableOptions setMetadataBlockSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_METADATA_BLOCK_SIZE, ptr(), size);
		return this;
	}

	/// Returns the configured approximate metadata block size.
	///
	/// @return current metadata block size
	public MemorySize getMetadataBlockSize() {
		return NativeFields.getMemorySize(MH_GET_METADATA_BLOCK_SIZE, ptr());
	}

	/// Percentage that a data block may exceed [#setBlockSize] before RocksDB starts a new
	/// block instead of packing in one more key -- e.g. 10 allows blocks up to 1.1x the
	/// configured block size. Default: 10.
	///
	/// @param percent allowed overshoot past the configured block size, as a percentage
	/// @return `this` for chaining
	public BlockBasedTableOptions setBlockSizeDeviation(int percent) {
		NativeFields.setInt(MH_SET_BLOCK_SIZE_DEVIATION, ptr(), percent);
		return this;
	}

	/// Returns the configured block size deviation percentage.
	///
	/// @return current block size deviation, as a percentage
	public int getBlockSizeDeviation() {
		return NativeFields.getInt(MH_GET_BLOCK_SIZE_DEVIATION, ptr());
	}

	/// If true, keys between restart points within a data block are delta-encoded against the
	/// previous key instead of stored in full. Default: true. Disabling trades a smaller CPU
	/// cost per read for larger data blocks.
	///
	/// @param value `true` to delta-encode keys between restart points
	/// @return `this` for chaining
	public BlockBasedTableOptions setUseDeltaEncoding(boolean value) {
		NativeFields.setBoolean(MH_SET_USE_DELTA_ENCODING, ptr(), value);
		return this;
	}

	/// Returns whether keys between restart points are delta-encoded.
	///
	/// @return `true` if keys between restart points are delta-encoded
	public boolean getUseDeltaEncoding() {
		return NativeFields.getBoolean(MH_GET_USE_DELTA_ENCODING, ptr());
	}

	/// If true, keys and values within a data block are stored in separate areas instead of
	/// interleaved, which can improve compression and point-lookup performance for some
	/// workloads at the cost of range-scan performance. Default: false.
	///
	/// @param value `true` to store keys and values in separate areas of a data block
	/// @return `this` for chaining
	public BlockBasedTableOptions setSeparateKeyValueInDataBlock(boolean value) {
		NativeFields.setBoolean(MH_SET_SEPARATE_KEY_VALUE_IN_DATA_BLOCK, ptr(), value);
		return this;
	}

	/// Returns whether keys and values are stored in separate areas of a data block.
	///
	/// @return `true` if keys and values are stored in separate areas of a data block
	public boolean getSeparateKeyValueInDataBlock() {
		return NativeFields.getBoolean(MH_GET_SEPARATE_KEY_VALUE_IN_DATA_BLOCK, ptr());
	}

	// -----------------------------------------------------------------------
	// Filter tuning
	// -----------------------------------------------------------------------

	/// If true, avoids allocating a full-precision cache reservation charge for the Bloom/Ribbon
	/// filter's own memory, using a lower-memory (but less accurate) estimate instead. Takes
	/// effect only when [#setCacheIndexAndFilterBlocks] is `true` with a cache that reserves
	/// memory for filters. Default: false.
	///
	/// @param value `true` to optimize filter memory tracking for lower overhead
	/// @return `this` for chaining
	public BlockBasedTableOptions setOptimizeFiltersForMemory(boolean value) {
		NativeFields.setBoolean(MH_SET_OPTIMIZE_FILTERS_FOR_MEMORY, ptr(), value);
		return this;
	}

	/// Returns whether filter memory tracking is optimized for lower overhead.
	///
	/// @return `true` if filter memory tracking is optimized for lower overhead
	public boolean getOptimizeFiltersForMemory() {
		return NativeFields.getBoolean(MH_GET_OPTIMIZE_FILTERS_FOR_MEMORY, ptr());
	}

	/// If true, partitioned filters are stored in their own separate blocks rather than
	/// alongside the index partitions they'd otherwise share storage with. Only takes effect
	/// with [#setPartitionFilters]. Default: false.
	///
	/// @param value `true` to store partitioned filters in their own blocks
	/// @return `this` for chaining
	public BlockBasedTableOptions setDecouplePartitionedFilters(boolean value) {
		NativeFields.setBoolean(MH_SET_DECOUPLE_PARTITIONED_FILTERS, ptr(), value);
		return this;
	}

	/// Returns whether partitioned filters are stored in their own blocks.
	///
	/// @return `true` if partitioned filters are stored in their own blocks
	public boolean getDecouplePartitionedFilters() {
		return NativeFields.getBoolean(MH_GET_DECOUPLE_PARTITIONED_FILTERS, ptr());
	}

	/// Target entries-to-buckets ratio for a data block's hash index, valid only when
	/// [DataBlockIndexType#BINARY_AND_HASH] is set via [#setDataBlockIndexType]. Default: `0.75`.
	///
	/// @param ratio target entries/buckets ratio
	/// @return `this` for chaining
	public BlockBasedTableOptions setDataBlockHashTableUtilRatio(double ratio) {
		NativeFields.setDouble(MH_SET_DATA_BLOCK_HASH_TABLE_UTIL_RATIO, ptr(), ratio);
		return this;
	}

	/// Returns the configured data block hash table utilization ratio.
	///
	/// @return current data block hash table utilization ratio
	public double getDataBlockHashTableUtilRatio() {
		return NativeFields.getDouble(MH_GET_DATA_BLOCK_HASH_TABLE_UTIL_RATIO, ptr());
	}

	// -----------------------------------------------------------------------
	// Index tuning
	// -----------------------------------------------------------------------

	/// Controls how much of each index key is retained. Default: [IndexShorteningMode#SHORTEN_SEPARATORS].
	///
	/// @param mode index shortening mode to use
	/// @return `this` for chaining
	public BlockBasedTableOptions setIndexShortening(IndexShorteningMode mode) {
		NativeFields.setInt(MH_SET_INDEX_SHORTENING, ptr(), mode.value);
		return this;
	}

	/// Returns the configured index shortening mode.
	///
	/// @return current index shortening mode
	public IndexShorteningMode getIndexShortening() {
		return IndexShorteningMode.fromValue(NativeFields.getInt(MH_GET_INDEX_SHORTENING, ptr()));
	}

	/// Sets the search algorithm used within an index block at read time. Default:
	/// [IndexSearchType#BINARY].
	///
	/// @param searchType index block search algorithm to use
	/// @return `this` for chaining
	public BlockBasedTableOptions setIndexBlockSearchType(IndexSearchType searchType) {
		NativeFields.setInt(MH_SET_INDEX_BLOCK_SEARCH_TYPE, ptr(), searchType.value);
		return this;
	}

	/// Returns the configured index block search algorithm.
	///
	/// @return current index block search algorithm
	public IndexSearchType getIndexBlockSearchType() {
		return IndexSearchType.fromValue(NativeFields.getInt(MH_GET_INDEX_BLOCK_SEARCH_TYPE, ptr()));
	}

	/// Sets the index format used for each data block's own key index. Default:
	/// [DataBlockIndexType#BINARY_SEARCH].
	///
	/// @param indexType data block index type to use
	/// @return `this` for chaining
	public BlockBasedTableOptions setDataBlockIndexType(DataBlockIndexType indexType) {
		NativeFields.setInt(MH_SET_DATA_BLOCK_INDEX_TYPE, ptr(), indexType.value);
		return this;
	}

	/// Returns the configured data block index type.
	///
	/// @return current data block index type
	public DataBlockIndexType getDataBlockIndexType() {
		return DataBlockIndexType.fromValue(NativeFields.getInt(MH_GET_DATA_BLOCK_INDEX_TYPE, ptr()));
	}

	/// If true, index blocks are compressed like data blocks are, subject to
	/// [Options#setCompression]. Default: true.
	///
	/// @param value `true` to compress index blocks
	/// @return `this` for chaining
	public BlockBasedTableOptions setEnableIndexCompression(boolean value) {
		NativeFields.setBoolean(MH_SET_ENABLE_INDEX_COMPRESSION, ptr(), value);
		return this;
	}

	/// Returns whether index blocks are compressed.
	///
	/// @return `true` if index blocks are compressed
	public boolean getEnableIndexCompression() {
		return NativeFields.getBoolean(MH_GET_ENABLE_INDEX_COMPRESSION, ptr());
	}

	/// Coefficient-of-variation threshold below which a data block's key spacing is flagged
	/// "uniform" in its footer at write time, letting [IndexSearchType#AUTO] use interpolation
	/// search on it at read time. A negative value (the default, `-1.0`) disables uniformity
	/// detection entirely, so blocks are never flagged and `AUTO` always falls back to binary
	/// search.
	///
	/// @param threshold coefficient-of-variation threshold, or a negative value to disable
	/// @return `this` for chaining
	public BlockBasedTableOptions setUniformCvThreshold(double threshold) {
		NativeFields.setDouble(MH_SET_UNIFORM_CV_THRESHOLD, ptr(), threshold);
		return this;
	}

	/// Returns the configured coefficient-of-variation threshold.
	///
	/// @return current coefficient-of-variation threshold
	public double getUniformCvThreshold() {
		return NativeFields.getDouble(MH_GET_UNIFORM_CV_THRESHOLD, ptr());
	}

	// -----------------------------------------------------------------------
	// Corruption and integrity
	// -----------------------------------------------------------------------

	/// Sets the per-block checksum algorithm for newly written blocks. Existing files with a
	/// different checksum type remain readable. Default: [ChecksumType#XXH3].
	///
	/// @param checksumType checksum algorithm to use for newly written blocks
	/// @return `this` for chaining
	public BlockBasedTableOptions setChecksumType(ChecksumType checksumType) {
		try {
			MH_SET_CHECKSUM.invokeExact(ptr(), (byte) checksumType.value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setChecksumType failed", t);
		}
		return this;
	}

	/// Returns the configured checksum algorithm.
	///
	/// @return current checksum algorithm
	public ChecksumType getChecksumType() {
		return ChecksumType.fromValue(NativeFields.getInt(MH_GET_CHECKSUM, ptr()));
	}

	/// If true, re-decompresses each compressed block immediately after compressing it during
	/// write and compares the result byte-for-byte, catching a broken compression library before
	/// corrupted data reaches disk. Adds meaningful CPU and latency to writes. Default: false.
	///
	/// @param value `true` to verify compression round-trips correctly on every write
	/// @return `this` for chaining
	public BlockBasedTableOptions setVerifyCompression(boolean value) {
		NativeFields.setBoolean(MH_SET_VERIFY_COMPRESSION, ptr(), value);
		return this;
	}

	/// Returns whether compression is verified on every write.
	///
	/// @return `true` if compression is verified on every write
	public boolean getVerifyCompression() {
		return NativeFields.getBoolean(MH_GET_VERIFY_COMPRESSION, ptr());
	}

	/// If true, computes and checks a checksum over each constructed filter (Bloom/Ribbon)
	/// immediately after building it, so a filter corrupted during construction is caught
	/// before the SST file is finalized rather than surfacing later as a false-negative lookup.
	/// Default: false.
	///
	/// @param value `true` to checksum-verify each filter right after it's built
	/// @return `this` for chaining
	public BlockBasedTableOptions setDetectFilterConstructCorruption(boolean value) {
		NativeFields.setBoolean(MH_SET_DETECT_FILTER_CONSTRUCT_CORRUPTION, ptr(), value);
		return this;
	}

	/// Returns whether newly constructed filters are checksum-verified immediately.
	///
	/// @return `true` if newly constructed filters are checksum-verified immediately
	public boolean getDetectFilterConstructCorruption() {
		return NativeFields.getBoolean(MH_GET_DETECT_FILTER_CONSTRUCT_CORRUPTION, ptr());
	}

	/// Enables read amplification tracking (the `rocksdb.read-amp-estimate-useful-bytes` and
	/// `rocksdb.read-amp-estimate-total-read-bytes` properties) by tagging every `bytesPerBit`
	/// bytes of each data block with a coverage bit, set the first time that byte range is
	/// actually read. Default: `0` (disabled) -- tracking adds bookkeeping overhead per block
	/// read, so enable only while investigating read amplification.
	///
	/// @param bytesPerBit bytes of a data block covered by each tracking bit, or `0` to disable
	/// @return `this` for chaining
	public BlockBasedTableOptions setReadAmpBytesPerBit(int bytesPerBit) {
		NativeFields.setInt(MH_SET_READ_AMP_BYTES_PER_BIT, ptr(), bytesPerBit);
		return this;
	}

	/// Returns the configured read amplification tracking granularity.
	///
	/// @return current read amplification tracking granularity, in bytes per bit
	public int getReadAmpBytesPerBit() {
		return NativeFields.getInt(MH_GET_READ_AMP_BYTES_PER_BIT, ptr());
	}

	// -----------------------------------------------------------------------
	// Block alignment
	// -----------------------------------------------------------------------

	/// If true, data blocks are padded to the filesystem's block size so each one starts on a
	/// physical block boundary, avoiding an extra physical read for a logical block that would
	/// otherwise straddle two disk blocks. Wastes some space to padding; requires
	/// [#setPrepopulateBlockCache] left at [PrepopulateBlockCache#DISABLE] and compression to be
	/// disabled via [Options#setCompression]. Default: false.
	///
	/// @param value `true` to align data blocks to filesystem block boundaries
	/// @return `this` for chaining
	public BlockBasedTableOptions setBlockAlign(boolean value) {
		NativeFields.setBoolean(MH_SET_BLOCK_ALIGN, ptr(), value);
		return this;
	}

	/// Returns whether data blocks are aligned to filesystem block boundaries.
	///
	/// @return `true` if data blocks are aligned to filesystem block boundaries
	public boolean getBlockAlign() {
		return NativeFields.getBoolean(MH_GET_BLOCK_ALIGN, ptr());
	}

	/// Alignment size, in bytes, for the coarser "super block" grouping of data blocks -- a
	/// second, larger-grained alignment layered on top of [#setBlockAlign]'s per-block
	/// alignment. Default: `0` (disabled).
	///
	/// @param size super block alignment size
	/// @return `this` for chaining
	public BlockBasedTableOptions setSuperBlockAlignmentSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_SUPER_BLOCK_ALIGNMENT_SIZE, ptr(), size);
		return this;
	}

	/// Returns the configured super block alignment size.
	///
	/// @return current super block alignment size
	public MemorySize getSuperBlockAlignmentSize() {
		return NativeFields.getMemorySize(MH_GET_SUPER_BLOCK_ALIGNMENT_SIZE, ptr());
	}

	/// Divisor used to cap the padding [#setSuperBlockAlignmentSize] is allowed to introduce: the
	/// maximum padding is `superBlockAlignmentSize / ratio` -- e.g. a 2 MB alignment size with
	/// the default ratio of `128` allows at most 16 KB of padding per super block. Ignored while
	/// [#setSuperBlockAlignmentSize] is `0` (alignment disabled). Default: `128`.
	///
	/// @param ratio divisor applied to the alignment size to cap padding overhead
	/// @return `this` for chaining
	public BlockBasedTableOptions setSuperBlockAlignmentSpaceOverheadRatio(long ratio) {
		NativeFields.setLong(MH_SET_SUPER_BLOCK_ALIGNMENT_SPACE_OVERHEAD_RATIO, ptr(), ratio);
		return this;
	}

	/// Returns the configured super block alignment space overhead ratio.
	///
	/// @return current super block alignment space overhead ratio
	public long getSuperBlockAlignmentSpaceOverheadRatio() {
		return NativeFields.getLong(MH_GET_SUPER_BLOCK_ALIGNMENT_SPACE_OVERHEAD_RATIO, ptr());
	}

	// -----------------------------------------------------------------------
	// Block cache prepopulation
	// -----------------------------------------------------------------------

	/// Controls whether blocks are eagerly inserted into the block cache as soon as this
	/// process writes them, instead of waiting for a later read to pull them in. Default:
	/// [PrepopulateBlockCache#DISABLE].
	///
	/// @param mode when to eagerly warm the block cache
	/// @return `this` for chaining
	public BlockBasedTableOptions setPrepopulateBlockCache(PrepopulateBlockCache mode) {
		NativeFields.setInt(MH_SET_PREPOPULATE_BLOCK_CACHE, ptr(), mode.value);
		return this;
	}

	/// Returns the configured block cache prepopulation mode.
	///
	/// @return current block cache prepopulation mode
	public PrepopulateBlockCache getPrepopulateBlockCache() {
		return PrepopulateBlockCache.fromValue(NativeFields.getInt(MH_GET_PREPOPULATE_BLOCK_CACHE, ptr()));
	}

	// -----------------------------------------------------------------------
	// User-defined index (UDI) activation
	// -----------------------------------------------------------------------

	/// Activates a `UserDefinedIndexFactory` already registered and self-linked into the loaded
	/// native library, by name -- e.g. RocksDB's built-in `TrieIndexFactory`, if the build
	/// includes it. This does not let Java code supply its own index implementation (the C API
	/// has no hook for that, only for activating one that already exists in C++); see
	/// `docs/c-api-gaps.md` for the Type B gap tracking the missing callback-based factory
	/// constructor.
	///
	/// @param name registered factory name to activate
	/// @return `this` for chaining
	/// @throws RocksDBException if no factory with `name` is registered in the loaded library
	public BlockBasedTableOptions setUserDefinedIndexFactoryFromString(String name) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
			MemorySegment nameSeg = RocksDB.toNative(arena, nameBytes);
			MH_SET_UDI_FACTORY_FROM_STRING.invokeExact(ptr(), nameSeg, (long) nameBytes.length, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setUserDefinedIndexFactoryFromString failed", t);
		}
		return this;
	}

	/// Clears any user-defined index factory activated via [#setUserDefinedIndexFactoryFromString],
	/// reverting to the standard index formats controlled by [#setIndexType].
	///
	/// @return `this` for chaining
	public BlockBasedTableOptions clearUserDefinedIndexFactory() {
		try {
			MH_CLEAR_UDI_FACTORY.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("clearUserDefinedIndexFactory failed", t);
		}
		return this;
	}

	/// Returns the name of the currently activated user-defined index factory.
	///
	/// @return the activated factory's name, or [Optional#empty()] if none is activated
	public Optional<String> getUserDefinedIndexFactoryName() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment namePtr = (MemorySegment) MH_GET_UDI_FACTORY_NAME.invokeExact(ptr(), lenSeg);
			if (MemorySegment.NULL.equals(namePtr)) {
				return Optional.empty();
			}
			long len = lenSeg.get(ValueLayout.JAVA_LONG, 0);
			return Optional.of(new String(RocksDB.toByteArray(namePtr, len), StandardCharsets.UTF_8));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getUserDefinedIndexFactoryName failed", t);
		}
	}

	@Override
	public void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
