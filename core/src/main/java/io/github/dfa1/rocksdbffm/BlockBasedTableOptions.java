package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

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
		try {
			MH_SET_BLOCK_SIZE.invokeExact(ptr(), blockSize.toBytes());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setBlockSize failed", t);
		}
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
		try {
			MH_SET_NO_BLOCK_CACHE.invokeExact(ptr(), RocksDB.toByte(noBlockCache));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setNoBlockCache failed", t);
		}
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
		try {
			MH_SET_CACHE_INDEX_AND_FILTER_BLOCKS.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setCacheIndexAndFilterBlocks failed", t);
		}
		return this;
	}

	/// Sets the index type. Default: [IndexType#BINARY_SEARCH].
	/// Use [IndexType#TWO_LEVEL_INDEX_SEARCH] for very large SSTs.
	///
	/// @param indexType index type to use
	/// @return `this` for chaining
	public BlockBasedTableOptions setIndexType(IndexType indexType) {
		try {
			MH_SET_INDEX_TYPE.invokeExact(ptr(), indexType.value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIndexType failed", t);
		}
		return this;
	}

	/// Sets the SST format version. Higher versions enable newer features but
	/// reduce backward compatibility. Default: [FormatVersion#V7].
	///
	/// @param formatVersion SST format version to use
	/// @return `this` for chaining
	public BlockBasedTableOptions setFormatVersion(FormatVersion formatVersion) {
		try {
			MH_SET_FORMAT_VERSION.invokeExact(ptr(), formatVersion.value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setFormatVersion failed", t);
		}
		return this;
	}

	/// Returns the configured SST format version.
	///
	/// @return current SST format version
	public FormatVersion getFormatVersion() {
		try {
			return FormatVersion.fromValue((int) MH_GET_FORMAT_VERSION.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getFormatVersion failed", t);
		}
	}

	/// If true, a whole-key Bloom filter is built in addition to any prefix filter.
	/// Default: true. Set to false when only a prefix filter is desired.
	///
	/// @param value `true` to enable whole-key filtering
	/// @return `this` for chaining
	public BlockBasedTableOptions setWholeKeyFiltering(boolean value) {
		try {
			MH_SET_WHOLE_KEY_FILTERING.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setWholeKeyFiltering failed", t);
		}
		return this;
	}

	/// If true, use partitioned Bloom filters (one small filter per index partition).
	/// Requires [IndexType#TWO_LEVEL_INDEX_SEARCH].
	///
	/// @param value `true` to enable partitioned filters
	/// @return `this` for chaining
	public BlockBasedTableOptions setPartitionFilters(boolean value) {
		try {
			MH_SET_PARTITION_FILTERS.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setPartitionFilters failed", t);
		}
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
		try {
			MH_SET_MAX_AUTO_READAHEAD_SIZE.invokeExact(ptr(), size.toBytes());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setMaxAutoReadaheadSize failed", t);
		}
		return this;
	}

	/// Returns the configured maximum auto-readahead size.
	///
	/// @return current maximum auto-readahead size
	public MemorySize getMaxAutoReadaheadSize() {
		try {
			return MemorySize.ofBytes((long) MH_GET_MAX_AUTO_READAHEAD_SIZE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getMaxAutoReadaheadSize failed", t);
		}
	}

	/// Read-ahead size RocksDB starts with for a new sequential scan of this table, before it
	/// grows toward [#setMaxAutoReadaheadSize]. Default: 8 KB.
	///
	/// @param size initial auto-readahead size
	/// @return `this` for chaining
	public BlockBasedTableOptions setInitialAutoReadaheadSize(MemorySize size) {
		try {
			MH_SET_INITIAL_AUTO_READAHEAD_SIZE.invokeExact(ptr(), size.toBytes());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setInitialAutoReadaheadSize failed", t);
		}
		return this;
	}

	/// Returns the configured initial auto-readahead size.
	///
	/// @return current initial auto-readahead size
	public MemorySize getInitialAutoReadaheadSize() {
		try {
			return MemorySize.ofBytes((long) MH_GET_INITIAL_AUTO_READAHEAD_SIZE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getInitialAutoReadaheadSize failed", t);
		}
	}

	/// Number of sequential file reads that must be observed before RocksDB doubles the
	/// auto-readahead size (up to [#setMaxAutoReadaheadSize]). Default: 2.
	///
	/// @param count number of sequential reads that triggers doubling the readahead size
	/// @return `this` for chaining
	public BlockBasedTableOptions setNumFileReadsForAutoReadahead(long count) {
		try {
			MH_SET_NUM_FILE_READS_FOR_AUTO_READAHEAD.invokeExact(ptr(), count);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setNumFileReadsForAutoReadahead failed", t);
		}
		return this;
	}

	/// Returns the configured number of sequential reads that triggers doubling the
	/// auto-readahead size.
	///
	/// @return current number of sequential reads that triggers doubling the readahead size
	public long getNumFileReadsForAutoReadahead() {
		try {
			return (long) MH_GET_NUM_FILE_READS_FOR_AUTO_READAHEAD.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getNumFileReadsForAutoReadahead failed", t);
		}
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
		try {
			MH_SET_CACHE_INDEX_AND_FILTER_BLOCKS_WITH_HIGH_PRIORITY.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setCacheIndexAndFilterBlocksWithHighPriority failed", t);
		}
		return this;
	}

	/// Returns whether index/filter blocks are inserted into the block cache at high priority.
	///
	/// @return `true` if index/filter blocks are inserted at high cache priority
	public boolean getCacheIndexAndFilterBlocksWithHighPriority() {
		try {
			return RocksDB.fromByte((byte) MH_GET_CACHE_INDEX_AND_FILTER_BLOCKS_WITH_HIGH_PRIORITY.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getCacheIndexAndFilterBlocksWithHighPriority failed", t);
		}
	}

	/// Deprecated pinning control, superseded by [#setUnpartitionedPinningTier] and
	/// [#setPartitionPinningTier] (used only when those are left at
	/// [PinningTier#FALLBACK]). If true, pins L0 filter and index blocks in the block cache.
	/// Default: false.
	///
	/// @param value `true` to pin L0 filter and index blocks in the block cache
	/// @return `this` for chaining
	public BlockBasedTableOptions setPinL0FilterAndIndexBlocksInCache(boolean value) {
		try {
			MH_SET_PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setPinL0FilterAndIndexBlocksInCache failed", t);
		}
		return this;
	}

	/// Returns whether L0 filter and index blocks are pinned in the block cache.
	///
	/// @return `true` if L0 filter and index blocks are pinned in the block cache
	public boolean getPinL0FilterAndIndexBlocksInCache() {
		try {
			return RocksDB.fromByte((byte) MH_GET_PIN_L0_FILTER_AND_INDEX_BLOCKS_IN_CACHE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getPinL0FilterAndIndexBlocksInCache failed", t);
		}
	}

	/// Deprecated pinning control, superseded by [#setTopLevelIndexPinningTier] (used only
	/// when that is left at [PinningTier#FALLBACK]). If true, pins the top-level index and
	/// filter on partitioned index/filter tables. Default: false.
	///
	/// @param value `true` to pin the top-level index and filter
	/// @return `this` for chaining
	public BlockBasedTableOptions setPinTopLevelIndexAndFilter(boolean value) {
		try {
			MH_SET_PIN_TOP_LEVEL_INDEX_AND_FILTER.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setPinTopLevelIndexAndFilter failed", t);
		}
		return this;
	}

	/// Returns whether the top-level index and filter are pinned.
	///
	/// @return `true` if the top-level index and filter are pinned
	public boolean getPinTopLevelIndexAndFilter() {
		try {
			return RocksDB.fromByte((byte) MH_GET_PIN_TOP_LEVEL_INDEX_AND_FILTER.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getPinTopLevelIndexAndFilter failed", t);
		}
	}

	/// The tier of block-based tables whose top-level index into metadata partitions will be
	/// pinned in the block cache. Requires [#setCacheIndexAndFilterBlocks] to be `true` to have
	/// any effect. Default: [PinningTier#FALLBACK].
	///
	/// @param tier the pinning tier to apply
	/// @return `this` for chaining
	public BlockBasedTableOptions setTopLevelIndexPinningTier(PinningTier tier) {
		try {
			MH_SET_TOP_LEVEL_INDEX_PINNING_TIER.invokeExact(ptr(), tier.value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setTopLevelIndexPinningTier failed", t);
		}
		return this;
	}

	/// The tier of block-based tables whose metadata partitions (index and filter) will be
	/// pinned in the block cache. Default: [PinningTier#FALLBACK].
	///
	/// @param tier the pinning tier to apply
	/// @return `this` for chaining
	public BlockBasedTableOptions setPartitionPinningTier(PinningTier tier) {
		try {
			MH_SET_PARTITION_PINNING_TIER.invokeExact(ptr(), tier.value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setPartitionPinningTier failed", t);
		}
		return this;
	}

	/// The tier of block-based tables whose unpartitioned metadata blocks will be pinned in
	/// the block cache. Requires [#setCacheIndexAndFilterBlocks] to be `true` to have any
	/// effect. Default: [PinningTier#FALLBACK].
	///
	/// @param tier the pinning tier to apply
	/// @return `this` for chaining
	public BlockBasedTableOptions setUnpartitionedPinningTier(PinningTier tier) {
		try {
			MH_SET_UNPARTITIONED_PINNING_TIER.invokeExact(ptr(), tier.value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setUnpartitionedPinningTier failed", t);
		}
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
		try {
			MH_SET_BLOCK_RESTART_INTERVAL.invokeExact(ptr(), interval);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setBlockRestartInterval failed", t);
		}
		return this;
	}

	/// Returns the configured number of keys between restart points in a data block.
	///
	/// @return current block restart interval
	public int getBlockRestartInterval() {
		try {
			return (int) MH_GET_BLOCK_RESTART_INTERVAL.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getBlockRestartInterval failed", t);
		}
	}

	/// Same as [#setBlockRestartInterval], but for the index block instead of data blocks.
	/// Values greater than 1 reduce index block size (per format version 4+) at the cost of
	/// a longer linear scan per index lookup. Default: 1 (no delta encoding of index entries).
	///
	/// @param interval number of index entries between restart points
	/// @return `this` for chaining
	public BlockBasedTableOptions setIndexBlockRestartInterval(int interval) {
		try {
			MH_SET_INDEX_BLOCK_RESTART_INTERVAL.invokeExact(ptr(), interval);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIndexBlockRestartInterval failed", t);
		}
		return this;
	}

	/// Returns the configured number of index entries between restart points in the index block.
	///
	/// @return current index block restart interval
	public int getIndexBlockRestartInterval() {
		try {
			return (int) MH_GET_INDEX_BLOCK_RESTART_INTERVAL.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getIndexBlockRestartInterval failed", t);
		}
	}

	/// Approximate size of partitioned metadata blocks (index/filter partitions). Only takes
	/// effect with [IndexType#TWO_LEVEL_INDEX_SEARCH] and [#setPartitionFilters]. Default: 4 KB.
	///
	/// @param size approximate metadata block size
	/// @return `this` for chaining
	public BlockBasedTableOptions setMetadataBlockSize(MemorySize size) {
		try {
			MH_SET_METADATA_BLOCK_SIZE.invokeExact(ptr(), size.toBytes());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setMetadataBlockSize failed", t);
		}
		return this;
	}

	/// Returns the configured approximate metadata block size.
	///
	/// @return current metadata block size
	public MemorySize getMetadataBlockSize() {
		try {
			return MemorySize.ofBytes((long) MH_GET_METADATA_BLOCK_SIZE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getMetadataBlockSize failed", t);
		}
	}

	/// Percentage that a data block may exceed [#setBlockSize] before RocksDB starts a new
	/// block instead of packing in one more key -- e.g. 10 allows blocks up to 1.1x the
	/// configured block size. Default: 10.
	///
	/// @param percent allowed overshoot past the configured block size, as a percentage
	/// @return `this` for chaining
	public BlockBasedTableOptions setBlockSizeDeviation(int percent) {
		try {
			MH_SET_BLOCK_SIZE_DEVIATION.invokeExact(ptr(), percent);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setBlockSizeDeviation failed", t);
		}
		return this;
	}

	/// Returns the configured block size deviation percentage.
	///
	/// @return current block size deviation, as a percentage
	public int getBlockSizeDeviation() {
		try {
			return (int) MH_GET_BLOCK_SIZE_DEVIATION.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getBlockSizeDeviation failed", t);
		}
	}

	/// If true, keys between restart points within a data block are delta-encoded against the
	/// previous key instead of stored in full. Default: true. Disabling trades a smaller CPU
	/// cost per read for larger data blocks.
	///
	/// @param value `true` to delta-encode keys between restart points
	/// @return `this` for chaining
	public BlockBasedTableOptions setUseDeltaEncoding(boolean value) {
		try {
			MH_SET_USE_DELTA_ENCODING.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setUseDeltaEncoding failed", t);
		}
		return this;
	}

	/// Returns whether keys between restart points are delta-encoded.
	///
	/// @return `true` if keys between restart points are delta-encoded
	public boolean getUseDeltaEncoding() {
		try {
			return RocksDB.fromByte((byte) MH_GET_USE_DELTA_ENCODING.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getUseDeltaEncoding failed", t);
		}
	}

	/// If true, keys and values within a data block are stored in separate areas instead of
	/// interleaved, which can improve compression and point-lookup performance for some
	/// workloads at the cost of range-scan performance. Default: false.
	///
	/// @param value `true` to store keys and values in separate areas of a data block
	/// @return `this` for chaining
	public BlockBasedTableOptions setSeparateKeyValueInDataBlock(boolean value) {
		try {
			MH_SET_SEPARATE_KEY_VALUE_IN_DATA_BLOCK.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setSeparateKeyValueInDataBlock failed", t);
		}
		return this;
	}

	/// Returns whether keys and values are stored in separate areas of a data block.
	///
	/// @return `true` if keys and values are stored in separate areas of a data block
	public boolean getSeparateKeyValueInDataBlock() {
		try {
			return RocksDB.fromByte((byte) MH_GET_SEPARATE_KEY_VALUE_IN_DATA_BLOCK.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getSeparateKeyValueInDataBlock failed", t);
		}
	}

	@Override
	public void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
