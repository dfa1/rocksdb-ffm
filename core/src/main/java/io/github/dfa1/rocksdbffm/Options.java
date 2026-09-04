package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_options_t`.
///
/// Usage:
///
/// ```
/// try (Options opts = Options.newOptions().setCreateIfMissing(true)) {
///     RocksDB db = RocksDB.openReadWrite(opts, path);
/// }
/// ```
///
/// Note: the Options object must remain open until after RocksDB.openReadWrite() returns;
/// it can be closed immediately after that call.
public final class Options extends NativeObject {

	/// `rocksdb_options_t* rocksdb_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_options_destroy(rocksdb_options_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_options_set_create_if_missing(rocksdb_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_CREATE_IF_MISSING;
	/// `unsigned char rocksdb_options_get_create_if_missing(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_CREATE_IF_MISSING;
	/// `void rocksdb_options_set_block_based_table_factory(rocksdb_options_t* opt, rocksdb_block_based_table_options_t* table_options);`
	private static final MethodHandle MH_SET_BLOCK_BASED_TABLE_FACTORY;
	/// `void rocksdb_options_set_cuckoo_table_factory(rocksdb_options_t* opt, rocksdb_cuckoo_table_options_t* table_options);`
	private static final MethodHandle MH_SET_CUCKOO_TABLE_FACTORY;
	/// `void rocksdb_options_set_plain_table_factory(rocksdb_options_t*, uint32_t, int, double, size_t, size_t, char, unsigned char, unsigned char);`
	private static final MethodHandle MH_SET_PLAIN_TABLE_FACTORY;
	/// `void rocksdb_options_enable_statistics(rocksdb_options_t*);`
	private static final MethodHandle MH_ENABLE_STATISTICS;
	/// `void rocksdb_options_set_statistics_level(rocksdb_options_t*, int level);`
	private static final MethodHandle MH_SET_STATISTICS_LEVEL;
	/// `int rocksdb_options_get_statistics_level(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_STATISTICS_LEVEL;
	/// `char* rocksdb_options_statistics_get_string(rocksdb_options_t* opt);`
	private static final MethodHandle MH_STATISTICS_GET_STRING;
	/// `uint64_t rocksdb_options_statistics_get_ticker_count(rocksdb_options_t* opt, uint32_t ticker_type);`
	private static final MethodHandle MH_STATISTICS_GET_TICKER_COUNT;
	/// `void rocksdb_options_statistics_get_histogram_data(rocksdb_options_t* opt, uint32_t histogram_type, rocksdb_statistics_histogram_data_t* const data);`
	private static final MethodHandle MH_STATISTICS_GET_HISTOGRAM_DATA;
	/// `void rocksdb_options_set_compression(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_COMPRESSION;
	/// `int rocksdb_options_get_compression(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_COMPRESSION;
	/// `void rocksdb_options_set_compaction_style(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_COMPACTION_STYLE;
	/// `int rocksdb_options_get_compaction_style(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_COMPACTION_STYLE;
	/// `void rocksdb_options_set_fifo_compaction_options(rocksdb_options_t* opt, rocksdb_fifo_compaction_options_t* fifo);`
	private static final MethodHandle MH_SET_FIFO_COMPACTION_OPTIONS;
	/// `void rocksdb_options_set_universal_compaction_options(rocksdb_options_t*, rocksdb_universal_compaction_options_t*);`
	private static final MethodHandle MH_SET_UNIVERSAL_COMPACTION_OPTIONS;
	/// `void rocksdb_options_set_enable_blob_files(rocksdb_options_t* opt, unsigned char val);`
	private static final MethodHandle MH_SET_ENABLE_BLOB_FILES;
	/// `unsigned char rocksdb_options_get_enable_blob_files(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_ENABLE_BLOB_FILES;
	/// `void rocksdb_options_set_min_blob_size(rocksdb_options_t* opt, uint64_t val);`
	private static final MethodHandle MH_SET_MIN_BLOB_SIZE;
	/// `uint64_t rocksdb_options_get_min_blob_size(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_MIN_BLOB_SIZE;
	/// `void rocksdb_options_set_blob_file_size(rocksdb_options_t* opt, uint64_t val);`
	private static final MethodHandle MH_SET_BLOB_FILE_SIZE;
	/// `uint64_t rocksdb_options_get_blob_file_size(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_BLOB_FILE_SIZE;
	/// `void rocksdb_options_set_blob_compression_type(rocksdb_options_t* opt, int val);`
	private static final MethodHandle MH_SET_BLOB_COMPRESSION_TYPE;
	/// `int rocksdb_options_get_blob_compression_type(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_BLOB_COMPRESSION_TYPE;
	/// `void rocksdb_options_set_enable_blob_gc(rocksdb_options_t* opt, unsigned char val);`
	private static final MethodHandle MH_SET_ENABLE_BLOB_GC;
	/// `unsigned char rocksdb_options_get_enable_blob_gc(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_ENABLE_BLOB_GC;
	/// `void rocksdb_options_set_blob_gc_age_cutoff(rocksdb_options_t* opt, double val);`
	private static final MethodHandle MH_SET_BLOB_GC_AGE_CUTOFF;
	/// `double rocksdb_options_get_blob_gc_age_cutoff(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_BLOB_GC_AGE_CUTOFF;
	/// `void rocksdb_options_set_blob_gc_force_threshold(rocksdb_options_t* opt, double val);`
	private static final MethodHandle MH_SET_BLOB_GC_FORCE_THRESHOLD;
	/// `double rocksdb_options_get_blob_gc_force_threshold(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_BLOB_GC_FORCE_THRESHOLD;
	/// `void rocksdb_options_set_blob_compaction_readahead_size(rocksdb_options_t* opt, uint64_t val);`
	private static final MethodHandle MH_SET_BLOB_COMPACTION_READAHEAD_SIZE;
	/// `uint64_t rocksdb_options_get_blob_compaction_readahead_size(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_BLOB_COMPACTION_READAHEAD_SIZE;
	/// `void rocksdb_options_set_blob_file_starting_level(rocksdb_options_t* opt, int val);`
	private static final MethodHandle MH_SET_BLOB_FILE_STARTING_LEVEL;
	/// `int rocksdb_options_get_blob_file_starting_level(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_BLOB_FILE_STARTING_LEVEL;
	/// `void rocksdb_options_set_blob_cache(rocksdb_options_t* opt, rocksdb_cache_t* blob_cache);`
	private static final MethodHandle MH_SET_BLOB_CACHE;
	/// `void rocksdb_options_set_prepopulate_blob_cache(rocksdb_options_t* opt, int val);`
	private static final MethodHandle MH_SET_PREPOPULATE_BLOB_CACHE;
	/// `int rocksdb_options_get_prepopulate_blob_cache(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_PREPOPULATE_BLOB_CACHE;
	/// `void rocksdb_options_set_info_log(rocksdb_options_t*, rocksdb_logger_t*);`
	private static final MethodHandle MH_SET_INFO_LOG;
	/// `void rocksdb_options_set_info_log_level(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_INFO_LOG_LEVEL;
	/// `int rocksdb_options_get_info_log_level(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_INFO_LOG_LEVEL;
	/// `void rocksdb_options_set_ratelimiter(rocksdb_options_t* opt, rocksdb_ratelimiter_t* limiter);`
	private static final MethodHandle MH_SET_RATELIMITER;
	/// `void rocksdb_options_set_env(rocksdb_options_t*, rocksdb_env_t*);`
	private static final MethodHandle MH_SET_ENV;
	/// `void rocksdb_options_set_sst_file_manager(rocksdb_options_t* opt, rocksdb_sst_file_manager_t* sfm);`
	private static final MethodHandle MH_SET_SST_FILE_MANAGER;
	/// `void rocksdb_options_set_sst_partitioner_factory(rocksdb_options_t*, rocksdb_sst_partitioner_factory_t*);`
	private static final MethodHandle MH_SET_SST_PARTITIONER_FACTORY;
	/// `void rocksdb_options_set_prefix_extractor(rocksdb_options_t*, rocksdb_slicetransform_t*);`
	private static final MethodHandle MH_SET_PREFIX_EXTRACTOR;
	/// `void rocksdb_options_set_metadata_write_temperature(rocksdb_options_t* opt, int v);`
	private static final MethodHandle MH_SET_METADATA_WRITE_TEMPERATURE;
	/// `int rocksdb_options_get_metadata_write_temperature(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_METADATA_WRITE_TEMPERATURE;
	/// `void rocksdb_options_set_wal_write_temperature(rocksdb_options_t* opt, int v);`
	private static final MethodHandle MH_SET_WAL_WRITE_TEMPERATURE;
	/// `int rocksdb_options_get_wal_write_temperature(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_WAL_WRITE_TEMPERATURE;
	/// `void rocksdb_options_set_last_level_temperature(rocksdb_options_t* opt, int v);`
	private static final MethodHandle MH_SET_LAST_LEVEL_TEMPERATURE;
	/// `int rocksdb_options_get_last_level_temperature(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_LAST_LEVEL_TEMPERATURE;
	/// `void rocksdb_options_set_default_write_temperature(rocksdb_options_t* opt, int v);`
	private static final MethodHandle MH_SET_DEFAULT_WRITE_TEMPERATURE;
	/// `int rocksdb_options_get_default_write_temperature(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_DEFAULT_WRITE_TEMPERATURE;
	/// `void rocksdb_options_set_default_temperature(rocksdb_options_t* opt, int v);`
	private static final MethodHandle MH_SET_DEFAULT_TEMPERATURE;
	/// `int rocksdb_options_get_default_temperature(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_DEFAULT_TEMPERATURE;
	/// `void rocksdb_options_set_write_buffer_size(rocksdb_options_t*, size_t);`
	private static final MethodHandle MH_SET_WRITE_BUFFER_SIZE;
	/// `size_t rocksdb_options_get_write_buffer_size(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_WRITE_BUFFER_SIZE;
	/// `void rocksdb_options_set_num_levels(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_NUM_LEVELS;
	/// `int rocksdb_options_get_num_levels(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_NUM_LEVELS;
	/// `void rocksdb_options_set_max_write_buffer_number(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_MAX_WRITE_BUFFER_NUMBER;
	/// `int rocksdb_options_get_max_write_buffer_number(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_MAX_WRITE_BUFFER_NUMBER;
	/// `void rocksdb_options_set_hash_skip_list_rep(rocksdb_options_t*, size_t, int32_t, int32_t);`
	private static final MethodHandle MH_SET_HASH_SKIP_LIST_REP;
	/// `void rocksdb_options_set_hash_link_list_rep(rocksdb_options_t*, size_t);`
	private static final MethodHandle MH_SET_HASH_LINK_LIST_REP;
	/// `void rocksdb_options_set_memtable_vector_rep(rocksdb_options_t*);`
	private static final MethodHandle MH_SET_MEMTABLE_VECTOR_REP;
	/// `void rocksdb_options_set_level0_file_num_compaction_trigger(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_LEVEL0_FILE_NUM_COMPACTION_TRIGGER;
	/// `int rocksdb_options_get_level0_file_num_compaction_trigger(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_LEVEL0_FILE_NUM_COMPACTION_TRIGGER;
	/// `void rocksdb_options_set_level0_slowdown_writes_trigger(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_LEVEL0_SLOWDOWN_WRITES_TRIGGER;
	/// `int rocksdb_options_get_level0_slowdown_writes_trigger(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_LEVEL0_SLOWDOWN_WRITES_TRIGGER;
	/// `void rocksdb_options_set_level0_stop_writes_trigger(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_LEVEL0_STOP_WRITES_TRIGGER;
	/// `int rocksdb_options_get_level0_stop_writes_trigger(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_LEVEL0_STOP_WRITES_TRIGGER;
	/// `void rocksdb_options_set_disable_auto_compactions(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_DISABLE_AUTO_COMPACTIONS;
	/// `unsigned char rocksdb_options_get_disable_auto_compactions(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_DISABLE_AUTO_COMPACTIONS;
	/// `void rocksdb_options_set_target_file_size_base(rocksdb_options_t*, uint64_t);`
	private static final MethodHandle MH_SET_TARGET_FILE_SIZE_BASE;
	/// `uint64_t rocksdb_options_get_target_file_size_base(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_TARGET_FILE_SIZE_BASE;
	/// `void rocksdb_options_set_max_bytes_for_level_base(rocksdb_options_t*, uint64_t);`
	private static final MethodHandle MH_SET_MAX_BYTES_FOR_LEVEL_BASE;
	/// `uint64_t rocksdb_options_get_max_bytes_for_level_base(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_MAX_BYTES_FOR_LEVEL_BASE;
	/// `void rocksdb_options_set_max_background_jobs(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_MAX_BACKGROUND_JOBS;
	/// `int rocksdb_options_get_max_background_jobs(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_MAX_BACKGROUND_JOBS;
	/// `void rocksdb_options_set_max_open_files(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_MAX_OPEN_FILES;
	/// `int rocksdb_options_get_max_open_files(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_MAX_OPEN_FILES;
	/// `void rocksdb_options_set_max_file_opening_threads(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_MAX_FILE_OPENING_THREADS;
	/// `int rocksdb_options_get_max_file_opening_threads(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_MAX_FILE_OPENING_THREADS;
	/// `void rocksdb_options_set_advise_random_on_open(rocksdb_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_ADVISE_RANDOM_ON_OPEN;
	/// `unsigned char rocksdb_options_get_advise_random_on_open(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_ADVISE_RANDOM_ON_OPEN;
	/// `void rocksdb_options_set_skip_stats_update_on_db_open(rocksdb_options_t* opt, unsigned char val);`
	private static final MethodHandle MH_SET_SKIP_STATS_UPDATE_ON_DB_OPEN;
	/// `unsigned char rocksdb_options_get_skip_stats_update_on_db_open(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_SKIP_STATS_UPDATE_ON_DB_OPEN;
	/// `void rocksdb_options_increase_parallelism(rocksdb_options_t* opt, int total_threads);`
	private static final MethodHandle MH_INCREASE_PARALLELISM;
	/// `void rocksdb_options_set_unordered_write(rocksdb_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_UNORDERED_WRITE;
	/// `unsigned char rocksdb_options_get_unordered_write(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_UNORDERED_WRITE;
	/// `void rocksdb_options_set_bytes_per_sync(rocksdb_options_t*, uint64_t);`
	private static final MethodHandle MH_SET_BYTES_PER_SYNC;
	/// `uint64_t rocksdb_options_get_bytes_per_sync(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_BYTES_PER_SYNC;
	/// `void rocksdb_options_set_use_direct_reads(rocksdb_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_USE_DIRECT_READS;
	/// `unsigned char rocksdb_options_get_use_direct_reads(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_USE_DIRECT_READS;
	/// `void rocksdb_options_set_use_direct_io_for_flush_and_compaction(rocksdb_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION;
	/// `unsigned char rocksdb_options_get_use_direct_io_for_flush_and_compaction(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION;
	/// `void rocksdb_options_set_compaction_pri(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_COMPACTION_PRI;
	/// `int rocksdb_options_get_compaction_pri(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_COMPACTION_PRI;
	/// `void rocksdb_options_set_bottommost_compression(rocksdb_options_t*, int);`
	private static final MethodHandle MH_SET_BOTTOMMOST_COMPRESSION;
	/// `int rocksdb_options_get_bottommost_compression(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_BOTTOMMOST_COMPRESSION;
	/// `void rocksdb_options_set_memtable_prefix_bloom_size_ratio(rocksdb_options_t*, double);`
	private static final MethodHandle MH_SET_MEMTABLE_PREFIX_BLOOM_SIZE_RATIO;
	/// `double rocksdb_options_get_memtable_prefix_bloom_size_ratio(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_MEMTABLE_PREFIX_BLOOM_SIZE_RATIO;
	/// `void rocksdb_options_set_memtable_whole_key_filtering(rocksdb_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_MEMTABLE_WHOLE_KEY_FILTERING;
	/// `unsigned char rocksdb_options_get_memtable_whole_key_filtering(rocksdb_options_t* opt);`
	private static final MethodHandle MH_GET_MEMTABLE_WHOLE_KEY_FILTERING;
	/// `void rocksdb_options_set_memtable_huge_page_size(rocksdb_options_t*, size_t);`
	private static final MethodHandle MH_SET_MEMTABLE_HUGE_PAGE_SIZE;
	/// `size_t rocksdb_options_get_memtable_huge_page_size(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_MEMTABLE_HUGE_PAGE_SIZE;
	/// `void rocksdb_options_set_bloom_locality(rocksdb_options_t*, uint32_t);`
	private static final MethodHandle MH_SET_BLOOM_LOCALITY;
	/// `uint32_t rocksdb_options_get_bloom_locality(rocksdb_options_t*);`
	private static final MethodHandle MH_GET_BLOOM_LOCALITY;
	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_CREATE_IF_MISSING = NativeLibrary.lookup("rocksdb_options_set_create_if_missing",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_CREATE_IF_MISSING = NativeLibrary.lookup("rocksdb_options_get_create_if_missing",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_BLOCK_BASED_TABLE_FACTORY = NativeLibrary.lookup(
				"rocksdb_options_set_block_based_table_factory",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_CUCKOO_TABLE_FACTORY = NativeLibrary.lookup(
				"rocksdb_options_set_cuckoo_table_factory",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_PLAIN_TABLE_FACTORY = NativeLibrary.lookup(
				"rocksdb_options_set_plain_table_factory",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
						ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
						ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_BYTE));

		MH_ENABLE_STATISTICS = NativeLibrary.lookup("rocksdb_options_enable_statistics",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_STATISTICS_LEVEL = NativeLibrary.lookup("rocksdb_options_set_statistics_level",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_STATISTICS_LEVEL = NativeLibrary.lookup("rocksdb_options_get_statistics_level",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_STATISTICS_GET_STRING = NativeLibrary.lookup("rocksdb_options_statistics_get_string",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_STATISTICS_GET_TICKER_COUNT = NativeLibrary.lookup("rocksdb_options_statistics_get_ticker_count",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_STATISTICS_GET_HISTOGRAM_DATA = NativeLibrary.lookup("rocksdb_options_statistics_get_histogram_data",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_COMPRESSION = NativeLibrary.lookup("rocksdb_options_set_compression",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_COMPRESSION = NativeLibrary.lookup("rocksdb_options_get_compression",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_COMPACTION_STYLE = NativeLibrary.lookup("rocksdb_options_set_compaction_style",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_COMPACTION_STYLE = NativeLibrary.lookup("rocksdb_options_get_compaction_style",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_FIFO_COMPACTION_OPTIONS = NativeLibrary.lookup("rocksdb_options_set_fifo_compaction_options",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_UNIVERSAL_COMPACTION_OPTIONS = NativeLibrary.lookup(
				"rocksdb_options_set_universal_compaction_options",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_ENABLE_BLOB_FILES = NativeLibrary.lookup("rocksdb_options_set_enable_blob_files",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ENABLE_BLOB_FILES = NativeLibrary.lookup("rocksdb_options_get_enable_blob_files",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_MIN_BLOB_SIZE = NativeLibrary.lookup("rocksdb_options_set_min_blob_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MIN_BLOB_SIZE = NativeLibrary.lookup("rocksdb_options_get_min_blob_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_BLOB_FILE_SIZE = NativeLibrary.lookup("rocksdb_options_set_blob_file_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_BLOB_FILE_SIZE = NativeLibrary.lookup("rocksdb_options_get_blob_file_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_BLOB_COMPRESSION_TYPE = NativeLibrary.lookup("rocksdb_options_set_blob_compression_type",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_BLOB_COMPRESSION_TYPE = NativeLibrary.lookup("rocksdb_options_get_blob_compression_type",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_ENABLE_BLOB_GC = NativeLibrary.lookup("rocksdb_options_set_enable_blob_gc",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ENABLE_BLOB_GC = NativeLibrary.lookup("rocksdb_options_get_enable_blob_gc",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_BLOB_GC_AGE_CUTOFF = NativeLibrary.lookup("rocksdb_options_set_blob_gc_age_cutoff",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_BLOB_GC_AGE_CUTOFF = NativeLibrary.lookup("rocksdb_options_get_blob_gc_age_cutoff",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

		MH_SET_BLOB_GC_FORCE_THRESHOLD = NativeLibrary.lookup("rocksdb_options_set_blob_gc_force_threshold",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_BLOB_GC_FORCE_THRESHOLD = NativeLibrary.lookup("rocksdb_options_get_blob_gc_force_threshold",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

		MH_SET_BLOB_COMPACTION_READAHEAD_SIZE = NativeLibrary.lookup(
				"rocksdb_options_set_blob_compaction_readahead_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_BLOB_COMPACTION_READAHEAD_SIZE = NativeLibrary.lookup(
				"rocksdb_options_get_blob_compaction_readahead_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_BLOB_FILE_STARTING_LEVEL = NativeLibrary.lookup("rocksdb_options_set_blob_file_starting_level",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_BLOB_FILE_STARTING_LEVEL = NativeLibrary.lookup("rocksdb_options_get_blob_file_starting_level",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_BLOB_CACHE = NativeLibrary.lookup("rocksdb_options_set_blob_cache",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_PREPOPULATE_BLOB_CACHE = NativeLibrary.lookup("rocksdb_options_set_prepopulate_blob_cache",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_PREPOPULATE_BLOB_CACHE = NativeLibrary.lookup("rocksdb_options_get_prepopulate_blob_cache",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_INFO_LOG = NativeLibrary.lookup("rocksdb_options_set_info_log",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_INFO_LOG_LEVEL = NativeLibrary.lookup("rocksdb_options_set_info_log_level",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_INFO_LOG_LEVEL = NativeLibrary.lookup("rocksdb_options_get_info_log_level",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_RATELIMITER = NativeLibrary.lookup("rocksdb_options_set_ratelimiter",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_ENV = NativeLibrary.lookup("rocksdb_options_set_env",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_SST_FILE_MANAGER = NativeLibrary.lookup("rocksdb_options_set_sst_file_manager",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_SST_PARTITIONER_FACTORY = NativeLibrary.lookup("rocksdb_options_set_sst_partitioner_factory",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_PREFIX_EXTRACTOR = NativeLibrary.lookup("rocksdb_options_set_prefix_extractor",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_METADATA_WRITE_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_set_metadata_write_temperature",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_METADATA_WRITE_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_get_metadata_write_temperature",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_WAL_WRITE_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_set_wal_write_temperature",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_WAL_WRITE_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_get_wal_write_temperature",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_LAST_LEVEL_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_set_last_level_temperature",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_LAST_LEVEL_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_get_last_level_temperature",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_DEFAULT_WRITE_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_set_default_write_temperature",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_DEFAULT_WRITE_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_get_default_write_temperature",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_DEFAULT_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_set_default_temperature",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_DEFAULT_TEMPERATURE = NativeLibrary.lookup("rocksdb_options_get_default_temperature",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_WRITE_BUFFER_SIZE = NativeLibrary.lookup("rocksdb_options_set_write_buffer_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_WRITE_BUFFER_SIZE = NativeLibrary.lookup("rocksdb_options_get_write_buffer_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_NUM_LEVELS = NativeLibrary.lookup("rocksdb_options_set_num_levels",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_NUM_LEVELS = NativeLibrary.lookup("rocksdb_options_get_num_levels",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_MAX_WRITE_BUFFER_NUMBER = NativeLibrary.lookup("rocksdb_options_set_max_write_buffer_number",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MAX_WRITE_BUFFER_NUMBER = NativeLibrary.lookup("rocksdb_options_get_max_write_buffer_number",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_HASH_SKIP_LIST_REP = NativeLibrary.lookup("rocksdb_options_set_hash_skip_list_rep",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

		MH_SET_HASH_LINK_LIST_REP = NativeLibrary.lookup("rocksdb_options_set_hash_link_list_rep",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_SET_MEMTABLE_VECTOR_REP = NativeLibrary.lookup("rocksdb_options_set_memtable_vector_rep",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_LEVEL0_FILE_NUM_COMPACTION_TRIGGER = NativeLibrary.lookup(
				"rocksdb_options_set_level0_file_num_compaction_trigger",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_LEVEL0_FILE_NUM_COMPACTION_TRIGGER = NativeLibrary.lookup(
				"rocksdb_options_get_level0_file_num_compaction_trigger",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_LEVEL0_SLOWDOWN_WRITES_TRIGGER = NativeLibrary.lookup(
				"rocksdb_options_set_level0_slowdown_writes_trigger",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_LEVEL0_SLOWDOWN_WRITES_TRIGGER = NativeLibrary.lookup(
				"rocksdb_options_get_level0_slowdown_writes_trigger",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_LEVEL0_STOP_WRITES_TRIGGER = NativeLibrary.lookup(
				"rocksdb_options_set_level0_stop_writes_trigger",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_LEVEL0_STOP_WRITES_TRIGGER = NativeLibrary.lookup(
				"rocksdb_options_get_level0_stop_writes_trigger",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_DISABLE_AUTO_COMPACTIONS = NativeLibrary.lookup(
				"rocksdb_options_set_disable_auto_compactions",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_DISABLE_AUTO_COMPACTIONS = NativeLibrary.lookup(
				"rocksdb_options_get_disable_auto_compactions",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_TARGET_FILE_SIZE_BASE = NativeLibrary.lookup("rocksdb_options_set_target_file_size_base",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_TARGET_FILE_SIZE_BASE = NativeLibrary.lookup("rocksdb_options_get_target_file_size_base",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_MAX_BYTES_FOR_LEVEL_BASE = NativeLibrary.lookup("rocksdb_options_set_max_bytes_for_level_base",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_BYTES_FOR_LEVEL_BASE = NativeLibrary.lookup("rocksdb_options_get_max_bytes_for_level_base",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_MAX_BACKGROUND_JOBS = NativeLibrary.lookup("rocksdb_options_set_max_background_jobs",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MAX_BACKGROUND_JOBS = NativeLibrary.lookup("rocksdb_options_get_max_background_jobs",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_MAX_OPEN_FILES = NativeLibrary.lookup("rocksdb_options_set_max_open_files",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MAX_OPEN_FILES = NativeLibrary.lookup("rocksdb_options_get_max_open_files",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_MAX_FILE_OPENING_THREADS = NativeLibrary.lookup("rocksdb_options_set_max_file_opening_threads",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MAX_FILE_OPENING_THREADS = NativeLibrary.lookup("rocksdb_options_get_max_file_opening_threads",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_ADVISE_RANDOM_ON_OPEN = NativeLibrary.lookup("rocksdb_options_set_advise_random_on_open",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ADVISE_RANDOM_ON_OPEN = NativeLibrary.lookup("rocksdb_options_get_advise_random_on_open",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_SKIP_STATS_UPDATE_ON_DB_OPEN = NativeLibrary.lookup(
				"rocksdb_options_set_skip_stats_update_on_db_open",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_SKIP_STATS_UPDATE_ON_DB_OPEN = NativeLibrary.lookup(
				"rocksdb_options_get_skip_stats_update_on_db_open",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_INCREASE_PARALLELISM = NativeLibrary.lookup("rocksdb_options_increase_parallelism",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_SET_UNORDERED_WRITE = NativeLibrary.lookup("rocksdb_options_set_unordered_write",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_UNORDERED_WRITE = NativeLibrary.lookup("rocksdb_options_get_unordered_write",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_BYTES_PER_SYNC = NativeLibrary.lookup("rocksdb_options_set_bytes_per_sync",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_BYTES_PER_SYNC = NativeLibrary.lookup("rocksdb_options_get_bytes_per_sync",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_USE_DIRECT_READS = NativeLibrary.lookup("rocksdb_options_set_use_direct_reads",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_DIRECT_READS = NativeLibrary.lookup("rocksdb_options_get_use_direct_reads",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION = NativeLibrary.lookup(
				"rocksdb_options_set_use_direct_io_for_flush_and_compaction",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION = NativeLibrary.lookup(
				"rocksdb_options_get_use_direct_io_for_flush_and_compaction",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_COMPACTION_PRI = NativeLibrary.lookup("rocksdb_options_set_compaction_pri",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_COMPACTION_PRI = NativeLibrary.lookup("rocksdb_options_get_compaction_pri",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_BOTTOMMOST_COMPRESSION = NativeLibrary.lookup("rocksdb_options_set_bottommost_compression",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_BOTTOMMOST_COMPRESSION = NativeLibrary.lookup("rocksdb_options_get_bottommost_compression",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_MEMTABLE_PREFIX_BLOOM_SIZE_RATIO = NativeLibrary.lookup(
				"rocksdb_options_set_memtable_prefix_bloom_size_ratio",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_MEMTABLE_PREFIX_BLOOM_SIZE_RATIO = NativeLibrary.lookup(
				"rocksdb_options_get_memtable_prefix_bloom_size_ratio",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

		MH_SET_MEMTABLE_WHOLE_KEY_FILTERING = NativeLibrary.lookup(
				"rocksdb_options_set_memtable_whole_key_filtering",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_MEMTABLE_WHOLE_KEY_FILTERING = NativeLibrary.lookup(
				"rocksdb_options_get_memtable_whole_key_filtering",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_MEMTABLE_HUGE_PAGE_SIZE = NativeLibrary.lookup("rocksdb_options_set_memtable_huge_page_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MEMTABLE_HUGE_PAGE_SIZE = NativeLibrary.lookup("rocksdb_options_get_memtable_huge_page_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_BLOOM_LOCALITY = NativeLibrary.lookup("rocksdb_options_set_bloom_locality",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_BLOOM_LOCALITY = NativeLibrary.lookup("rocksdb_options_get_bloom_locality",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	}

	private Options(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates [Options] with RocksDB defaults.
	///
	/// @return a new instance; caller must close it
	public static Options newOptions() {
		try {
			return new Options((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("options create failed", t);
		}
	}

	/// If true, the database will be created if it does not already exist.
	/// Default: false (same as RocksDB C++ default).
	///
	/// @param value `true` to create the DB if absent
	/// @return `this` for chaining
	public Options setCreateIfMissing(boolean value) {
		NativeFields.setBoolean(MH_SET_CREATE_IF_MISSING, ptr(), value);
		return this;
	}

	/// Returns whether the DB is created if it does not already exist.
	///
	/// @return `true` if the DB is created on open when absent
	public boolean getCreateIfMissing() {
		return NativeFields.getBoolean(MH_GET_CREATE_IF_MISSING, ptr());
	}

	/// Enables statistics gathering for this DB.
	///
	/// @return `this` for chaining
	public Options enableStatistics() {
		try {
			MH_ENABLE_STATISTICS.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("enableStatistics failed", t);
		}
		return this;
	}

	/// Sets the statistics collection level. Only effective after [#enableStatistics] is called.
	///
	/// @param level the desired statistics collection level
	/// @return `this` for chaining
	public Options setStatisticsLevel(StatsLevel level) {
		NativeFields.setInt(MH_SET_STATISTICS_LEVEL, ptr(), level.getValue());
		return this;
	}

	/// Returns the current statistics collection level.
	///
	/// @return the active [StatsLevel]
	public StatsLevel getStatisticsLevel() {
		return StatsLevel.fromValue(NativeFields.getInt(MH_GET_STATISTICS_LEVEL, ptr()));
	}

	/// Returns a human-readable statistics summary, or `null` if statistics are not enabled.
	///
	/// @return formatted statistics string, or `null` if not available
	public String getStatisticsString() {
		try {
			MemorySegment strPtr = (MemorySegment) MH_STATISTICS_GET_STRING.invokeExact(ptr());
			if (MemorySegment.NULL.equals(strPtr)) {
				return null;
			}
			return RocksDB.toJavaString(strPtr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getStatisticsString failed", t);
		}
	}

	/// Returns the accumulated count for a ticker statistic.
	///
	/// @param ticker the ticker to read
	/// @return accumulated count since the DB was opened
	public long getTickerCount(TickerType ticker) {
		try {
			return (long) MH_STATISTICS_GET_TICKER_COUNT.invokeExact(ptr(), ticker.getValue());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getTickerCount failed", t);
		}
	}

	/// Populates `data` with histogram statistics for `histogram`.
	///
	/// @param histogram the histogram to read
	/// @param data      output object to populate with the histogram values
	public void getHistogramData(HistogramType histogram, StatisticsHistogramData data) {
		try {
			MH_STATISTICS_GET_HISTOGRAM_DATA.invokeExact(ptr(), histogram.getValue(), data.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getHistogramData failed", t);
		}
	}

	/// Sets the compression algorithm for all levels.
	///
	/// @param type the compression algorithm to use
	/// @return `this` for chaining
	/// @throws UnsupportedOperationException if `type` isn't linked into the bundled native library
	public Options setCompression(CompressionType type) {
		if (!type.isSupported()) {
			throw new UnsupportedOperationException(type + " compression is not linked into the bundled native library");
		}
		NativeFields.setInt(MH_SET_COMPRESSION, ptr(), type.getValue());
		return this;
	}

	/// Returns the compression algorithm configured for this Options.
	///
	/// @return the active compression type
	public CompressionType getCompression() {
		return CompressionType.fromValue(NativeFields.getInt(MH_GET_COMPRESSION, ptr()));
	}

	/// Which strategy RocksDB uses to pick which SST files to compact and when, per `c.h`'s
	/// anonymous `rocksdb_*_compaction` enum (backed by C++'s `CompactionStyle`).
	public enum CompactionStyle {
		/// The default: organizes SSTs into levels of exponentially increasing size: good
		/// general-purpose read/write/space balance.
		LEVEL(0),
		/// Merges files of similar size, minimizing write amplification at the cost of higher
		/// read/space amplification and periodic large compactions. Configure further via
		/// [#setUniversalCompactionOptions].
		UNIVERSAL(1),
		/// First-in-first-out: never compacts, just drops (or, with
		/// [FifoCompactionOptions#setAllowCompaction], compacts) the oldest SST once a size
		/// bound is exceeded. For TTL-like or ring-buffer workloads. Configure further via
		/// [#setFifoCompactionOptions].
		FIFO(2);

		final int value;

		CompactionStyle(int value) {
			this.value = value;
		}

		static CompactionStyle fromValue(int value) {
			return switch (value) {
				case 0 -> LEVEL;
				case 1 -> UNIVERSAL;
				case 2 -> FIFO;
				default -> throw new IllegalArgumentException("Unknown CompactionStyle value: " + value);
			};
		}
	}

	/// Sets the compaction style. Default: [CompactionStyle#LEVEL].
	///
	/// @param style the compaction style to use
	/// @return `this` for chaining
	public Options setCompactionStyle(CompactionStyle style) {
		NativeFields.setInt(MH_SET_COMPACTION_STYLE, ptr(), style.value);
		return this;
	}

	/// Returns the configured compaction style.
	///
	/// @return current compaction style
	public CompactionStyle getCompactionStyle() {
		return CompactionStyle.fromValue(NativeFields.getInt(MH_GET_COMPACTION_STYLE, ptr()));
	}

	/// Configures FIFO compaction. Only takes effect when [#setCompactionStyle] is
	/// [CompactionStyle#FIFO]. RocksDB copies the config internally; `fifoOptions` may be
	/// closed after this call.
	///
	/// @param fifoOptions the FIFO compaction options to apply
	/// @return `this` for chaining
	public Options setFifoCompactionOptions(FifoCompactionOptions fifoOptions) {
		try {
			MH_SET_FIFO_COMPACTION_OPTIONS.invokeExact(ptr(), fifoOptions.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setFifoCompactionOptions failed", t);
		}
		return this;
	}

	/// Configures universal compaction. Only takes effect when [#setCompactionStyle] is
	/// [CompactionStyle#UNIVERSAL]. RocksDB copies the config internally; `universalOptions`
	/// may be closed after this call.
	///
	/// @param universalOptions the universal compaction options to apply
	/// @return `this` for chaining
	public Options setUniversalCompactionOptions(UniversalCompactionOptions universalOptions) {
		try {
			MH_SET_UNIVERSAL_COMPACTION_OPTIONS.invokeExact(ptr(), universalOptions.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setUniversalCompactionOptions failed", t);
		}
		return this;
	}

	/// Configures block-based table format for this DB.
	/// RocksDB copies the config internally; `tableConfig` may be closed after this call.
	///
	/// @param tableConfig the block-based table options to apply
	/// @return `this` for chaining
	public Options setTableFormatConfig(BlockBasedTableOptions tableConfig) {
		try {
			MH_SET_BLOCK_BASED_TABLE_FACTORY.invokeExact(ptr(), tableConfig.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setTableFormatConfig failed", t);
		}
		return this;
	}

	/// Configures Cuckoo Table format for this DB -- a hash-based SST format optimized for
	/// fixed-size keys and point lookups (no range scans).
	/// RocksDB copies the config internally; `tableConfig` may be closed after this call.
	///
	/// @param tableConfig the Cuckoo table options to apply
	/// @return `this` for chaining
	public Options setTableFormatConfig(CuckooTableOptions tableConfig) {
		try {
			MH_SET_CUCKOO_TABLE_FACTORY.invokeExact(ptr(), tableConfig.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setTableFormatConfig failed", t);
		}
		return this;
	}

	/// Configures PlainTable format for this DB -- an in-memory hash-indexed SST format
	/// optimized for fixed-size keys and read-heavy point-lookup workloads. Unlike the other
	/// `setTableFormatConfig` overloads, `tableConfig` is a plain value holder with no native
	/// counterpart to transfer ownership of or close -- see [PlainTableOptions]'s class doc.
	/// Pair with [#setPrefixExtractor] to build PlainTable's hash buckets.
	///
	/// @param tableConfig the PlainTable options to apply
	/// @return `this` for chaining
	public Options setTableFormatConfig(PlainTableOptions tableConfig) {
		try {
			MH_SET_PLAIN_TABLE_FACTORY.invokeExact(ptr(),
					tableConfig.getUserKeyLength(),
					tableConfig.getBloomBitsPerKey(),
					tableConfig.getHashTableRatio(),
					tableConfig.getIndexSparseness(),
					tableConfig.getHugePageTlbSize(),
					(byte) tableConfig.getEncodingType().getValue(),
					RocksDB.toByte(tableConfig.isFullScanMode()),
					RocksDB.toByte(tableConfig.isStoreIndexInFile()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setTableFormatConfig failed", t);
		}
		return this;
	}

	// -----------------------------------------------------------------------
	// LSM shape and compaction triggers
	// -----------------------------------------------------------------------

	/// Amount of data to accumulate in a memtable before it is flushed to an SST file.
	/// Larger values reduce write amplification and the number of files produced, at the cost
	/// of more memory per column family and a larger recovery replay window. Default: 64 MiB.
	///
	/// @param size memtable size threshold that triggers a flush
	/// @return `this` for chaining
	public Options setWriteBufferSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_WRITE_BUFFER_SIZE, ptr(), size);
		return this;
	}

	/// Returns the configured memtable flush threshold.
	///
	/// @return current memtable size threshold that triggers a flush
	public MemorySize getWriteBufferSize() {
		return NativeFields.getMemorySize(MH_GET_WRITE_BUFFER_SIZE, ptr());
	}

	/// Number of levels in the LSM tree for this column family. Default: 7.
	///
	/// @param numLevels number of levels
	/// @return `this` for chaining
	public Options setNumLevels(int numLevels) {
		NativeFields.setInt(MH_SET_NUM_LEVELS, ptr(), numLevels);
		return this;
	}

	/// Returns the configured number of LSM tree levels.
	///
	/// @return current number of levels
	public int getNumLevels() {
		return NativeFields.getInt(MH_GET_NUM_LEVELS, ptr());
	}

	/// Maximum number of memtables, both active and immutable-pending-flush, held in memory
	/// at once. Values above 1 let writes continue into a fresh memtable while an older one is
	/// still being flushed, at the cost of more memory. Default: 2.
	///
	/// @param number maximum number of memtables held in memory at once
	/// @return `this` for chaining
	public Options setMaxWriteBufferNumber(int number) {
		NativeFields.setInt(MH_SET_MAX_WRITE_BUFFER_NUMBER, ptr(), number);
		return this;
	}

	/// Returns the configured maximum number of memtables held in memory at once.
	///
	/// @return current maximum number of memtables held in memory at once
	public int getMaxWriteBufferNumber() {
		return NativeFields.getInt(MH_GET_MAX_WRITE_BUFFER_NUMBER, ptr());
	}

	/// Once the number of level-0 SST files reaches this count, RocksDB triggers a compaction
	/// of level 0 into level 1. Lower values trigger compaction sooner (useful for tests that
	/// need to observe real automatic compactions without writing a large volume of data);
	/// higher values tolerate more read amplification from level 0 before compacting.
	/// Default: 4.
	///
	/// @param numFiles number of level-0 files that triggers compaction
	/// @return `this` for chaining
	public Options setLevel0FileNumCompactionTrigger(int numFiles) {
		NativeFields.setInt(MH_SET_LEVEL0_FILE_NUM_COMPACTION_TRIGGER, ptr(), numFiles);
		return this;
	}

	/// Returns the configured level-0 file count that triggers compaction.
	///
	/// @return current level-0 file count that triggers compaction
	public int getLevel0FileNumCompactionTrigger() {
		return NativeFields.getInt(MH_GET_LEVEL0_FILE_NUM_COMPACTION_TRIGGER, ptr());
	}

	/// Once the number of level-0 SST files reaches this count, RocksDB slows writes down (an
	/// artificial per-write delay) until compaction brings the count back down -- the
	/// write-stall "delayed" condition reported via [EventNotifier#onStallConditionsChanged].
	/// Must be less than or equal to [#setLevel0StopWritesTrigger]; RocksDB silently raises this
	/// to match the stop trigger otherwise. Default: 20.
	///
	/// @param numFiles number of level-0 files that triggers write slowdown
	/// @return `this` for chaining
	public Options setLevel0SlowdownWritesTrigger(int numFiles) {
		NativeFields.setInt(MH_SET_LEVEL0_SLOWDOWN_WRITES_TRIGGER, ptr(), numFiles);
		return this;
	}

	/// Returns the configured level-0 file count that triggers write slowdown.
	///
	/// @return current level-0 file count that triggers write slowdown
	public int getLevel0SlowdownWritesTrigger() {
		return NativeFields.getInt(MH_GET_LEVEL0_SLOWDOWN_WRITES_TRIGGER, ptr());
	}

	/// Once the number of level-0 SST files reaches this count, RocksDB stops accepting writes
	/// entirely until compaction brings the count back down -- the write-stall "stop" condition
	/// reported via [EventNotifier#onStallConditionsChanged]. Default: 36.
	///
	/// @param numFiles number of level-0 files that stops writes
	/// @return `this` for chaining
	public Options setLevel0StopWritesTrigger(int numFiles) {
		NativeFields.setInt(MH_SET_LEVEL0_STOP_WRITES_TRIGGER, ptr(), numFiles);
		return this;
	}

	/// Returns the configured level-0 file count that stops writes.
	///
	/// @return current level-0 file count that stops writes
	public int getLevel0StopWritesTrigger() {
		return NativeFields.getInt(MH_GET_LEVEL0_STOP_WRITES_TRIGGER, ptr());
	}

	/// If `true`, disables automatic compaction entirely -- only a manually triggered
	/// `compactRange()` will run. Default: `false`.
	///
	/// @param value `true` to disable automatic compaction
	/// @return `this` for chaining
	public Options setDisableAutoCompactions(boolean value) {
		NativeFields.setInt(MH_SET_DISABLE_AUTO_COMPACTIONS, ptr(), RocksDB.toByte(value));
		return this;
	}

	/// Returns whether automatic compaction is disabled.
	///
	/// @return `true` if automatic compaction is disabled
	public boolean getDisableAutoCompactions() {
		return NativeFields.getBoolean(MH_GET_DISABLE_AUTO_COMPACTIONS, ptr());
	}

	/// Target size for SST files at level 1; higher levels scale up from this by
	/// `target_file_size_multiplier` (not currently exposed by this library). Default: 64 MiB.
	///
	/// @param size target SST file size at level 1
	/// @return `this` for chaining
	public Options setTargetFileSizeBase(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_TARGET_FILE_SIZE_BASE, ptr(), size);
		return this;
	}

	/// Returns the configured target SST file size at level 1.
	///
	/// @return current target SST file size at level 1
	public MemorySize getTargetFileSizeBase() {
		return NativeFields.getMemorySize(MH_GET_TARGET_FILE_SIZE_BASE, ptr());
	}

	/// Target total size for level 1; higher levels scale up from this by
	/// `max_bytes_for_level_multiplier` (not currently exposed by this library). Default: 256 MiB.
	///
	/// @param size target total size for level 1
	/// @return `this` for chaining
	public Options setMaxBytesForLevelBase(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_MAX_BYTES_FOR_LEVEL_BASE, ptr(), size);
		return this;
	}

	/// Returns the configured target total size for level 1.
	///
	/// @return current target total size for level 1
	public MemorySize getMaxBytesForLevelBase() {
		return NativeFields.getMemorySize(MH_GET_MAX_BYTES_FOR_LEVEL_BASE, ptr());
	}

	// -----------------------------------------------------------------------
	// Blob file options
	// -----------------------------------------------------------------------

	/// Enables storing large values in separate blob files instead of inline in SSTs.
	/// When enabled, values ≥ [#setMinBlobSize] are written to blob files.
	/// Default: `false`.
	///
	/// @param value `true` to enable blob file storage
	/// @return `this` for chaining
	public Options setEnableBlobFiles(boolean value) {
		NativeFields.setBoolean(MH_SET_ENABLE_BLOB_FILES, ptr(), value);
		return this;
	}

	/// Returns whether blob file storage is enabled.
	///
	/// @return `true` if large values are stored in separate blob files
	public boolean getEnableBlobFiles() {
		return NativeFields.getBoolean(MH_GET_ENABLE_BLOB_FILES, ptr());
	}

	/// Values strictly smaller than this size are stored inline; larger values go to blob files.
	/// Only effective when [#setEnableBlobFiles] is `true`. Default: 0 (all values externalized).
	///
	/// @param size minimum value size to externalize into a blob file
	/// @return `this` for chaining
	public Options setMinBlobSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_MIN_BLOB_SIZE, ptr(), size);
		return this;
	}

	/// Returns the minimum value size that is stored in a blob file.
	///
	/// @return minimum blob size threshold
	public MemorySize getMinBlobSize() {
		return NativeFields.getMemorySize(MH_GET_MIN_BLOB_SIZE, ptr());
	}

	/// Target size for individual blob files. RocksDB rolls to a new file when this is exceeded.
	/// Default: 256 MiB.
	///
	/// @param size target size per blob file
	/// @return `this` for chaining
	public Options setBlobFileSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_BLOB_FILE_SIZE, ptr(), size);
		return this;
	}

	/// Returns the target size for individual blob files.
	///
	/// @return target blob file size
	public MemorySize getBlobFileSize() {
		return NativeFields.getMemorySize(MH_GET_BLOB_FILE_SIZE, ptr());
	}

	/// Compression algorithm applied to blob file values. Independent of SST compression.
	/// Default: [CompressionType#NO_COMPRESSION].
	///
	/// @param type the compression algorithm for blob values
	/// @return `this` for chaining
	/// @throws UnsupportedOperationException if `type` isn't linked into the bundled native library
	public Options setBlobCompressionType(CompressionType type) {
		if (!type.isSupported()) {
			throw new UnsupportedOperationException(type + " compression is not linked into the bundled native library");
		}
		NativeFields.setInt(MH_SET_BLOB_COMPRESSION_TYPE, ptr(), type.getValue());
		return this;
	}

	/// Returns the compression algorithm applied to blob file values.
	///
	/// @return compression type for blob values
	public CompressionType getBlobCompressionType() {
		return CompressionType.fromValue(NativeFields.getInt(MH_GET_BLOB_COMPRESSION_TYPE, ptr()));
	}

	/// Enables garbage collection of obsolete blob files during compaction.
	/// Default: `false`.
	///
	/// @param value `true` to enable blob GC during compaction
	/// @return `this` for chaining
	public Options setEnableBlobGc(boolean value) {
		NativeFields.setBoolean(MH_SET_ENABLE_BLOB_GC, ptr(), value);
		return this;
	}

	/// Returns whether blob garbage collection during compaction is enabled.
	///
	/// @return `true` if blob GC is enabled
	public boolean getEnableBlobGc() {
		return NativeFields.getBoolean(MH_GET_ENABLE_BLOB_GC, ptr());
	}

	/// Blob files whose age is older than this fraction of the oldest snapshot are
	/// unconditionally GC'd, regardless of garbage ratio.
	/// Default: 0.5.
	///
	/// @param value age cutoff fraction
	/// @return `this` for chaining
	public Options setBlobGcAgeCutoff(Ratio value) {
		NativeFields.setDouble(MH_SET_BLOB_GC_AGE_CUTOFF, ptr(), value.toDouble());
		return this;
	}

	/// Returns the blob GC age cutoff fraction.
	///
	/// @return age cutoff fraction
	public Ratio getBlobGcAgeCutoff() {
		return Ratio.of(NativeFields.getDouble(MH_GET_BLOB_GC_AGE_CUTOFF, ptr()));
	}

	/// Blob files whose garbage ratio exceeds this threshold are force-compacted.
	/// Default: 1.0 (disabled).
	///
	/// @param value force-GC garbage ratio threshold
	/// @return `this` for chaining
	public Options setBlobGcForceThreshold(Ratio value) {
		NativeFields.setDouble(MH_SET_BLOB_GC_FORCE_THRESHOLD, ptr(), value.toDouble());
		return this;
	}

	/// Returns the blob GC force-compaction garbage ratio threshold.
	///
	/// @return force-GC threshold
	public Ratio getBlobGcForceThreshold() {
		return Ratio.of(NativeFields.getDouble(MH_GET_BLOB_GC_FORCE_THRESHOLD, ptr()));
	}

	/// Read-ahead size when reading blob files during compaction.
	/// `0` disables read-ahead. Default: 0.
	///
	/// @param size read-ahead buffer size; `MemorySize.ofBytes(0)` disables it
	/// @return `this` for chaining
	public Options setBlobCompactionReadaheadSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_BLOB_COMPACTION_READAHEAD_SIZE, ptr(), size);
		return this;
	}

	/// Returns the read-ahead size used when reading blob files during compaction.
	///
	/// @return read-ahead size; [MemorySize#ZERO] means disabled
	public MemorySize getBlobCompactionReadaheadSize() {
		return NativeFields.getMemorySize(MH_GET_BLOB_COMPACTION_READAHEAD_SIZE, ptr());
	}

	/// LSM level at which blob file separation begins. Keys in levels below this
	/// threshold are stored inline. Default: 0 (all levels externalize blobs).
	///
	/// @param level first LSM level where blobs are externalized (0 = all levels)
	/// @return `this` for chaining
	public Options setBlobFileStartingLevel(int level) {
		NativeFields.setInt(MH_SET_BLOB_FILE_STARTING_LEVEL, ptr(), level);
		return this;
	}

	/// Returns the LSM level at which blob file separation begins.
	///
	/// @return first level where blobs are externalized (0 = all levels)
	public int getBlobFileStartingLevel() {
		return NativeFields.getInt(MH_GET_BLOB_FILE_STARTING_LEVEL, ptr());
	}

	/// Attaches a dedicated cache for blob values.
	/// Ownership of the cache is shared; the cache must outlive this Options object.
	///
	/// @param cache the blob cache to attach
	/// @return `this` for chaining
	public Options setBlobCache(Cache cache) {
		try {
			MH_SET_BLOB_CACHE.invokeExact(ptr(), cache.ptr());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setBlobCache failed", t);
		}
	}

	/// Controls whether blob values are pre-populated into the blob cache on write.
	/// Default: [PrepopulateBlobCache#DISABLE].
	///
	/// @param mode the pre-population strategy
	/// @return `this` for chaining
	public Options setPrepopulateBlobCache(PrepopulateBlobCache mode) {
		NativeFields.setInt(MH_SET_PREPOPULATE_BLOB_CACHE, ptr(), mode.value);
		return this;
	}

	/// Returns the blob cache pre-population strategy.
	///
	/// @return the current [PrepopulateBlobCache] mode
	public PrepopulateBlobCache getPrepopulateBlobCache() {
		return PrepopulateBlobCache.fromValue(NativeFields.getInt(MH_GET_PREPOPULATE_BLOB_CACHE, ptr()));
	}

	// -----------------------------------------------------------------------
	// Logging options
	// -----------------------------------------------------------------------

	/// Sets the logger for this DB. RocksDB holds a shared reference; it is safe
	/// to close [Logger] after this call.
	///
	/// @param logger the logger instance to attach
	/// @return `this` for chaining
	public Options setInfoLog(Logger logger) {
		try {
			MH_SET_INFO_LOG.invokeExact(ptr(), logger.ptr());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setInfoLog failed", t);
		}
	}

	/// Sets the minimum log level. Messages below this level are suppressed.
	///
	/// @param level the minimum log level to emit
	/// @return `this` for chaining
	public Options setInfoLogLevel(LogLevel level) {
		NativeFields.setInt(MH_SET_INFO_LOG_LEVEL, ptr(), level.value);
		return this;
	}

	/// Returns the minimum log level currently configured.
	///
	/// @return the active minimum [LogLevel]
	public LogLevel getInfoLogLevel() {
		return LogLevel.fromValue(NativeFields.getInt(MH_GET_INFO_LOG_LEVEL, ptr()));
	}

	/// Sets the [Env] used for all file-system and threading operations.
	///
	/// The [Env] must remain open for the lifetime of the database.
	/// No ownership transfer: both objects may be closed independently.
	///
	/// @param env the environment to use
	/// @return `this` for chaining
	public Options setEnv(Env env) {
		try {
			MH_SET_ENV.invokeExact(ptr(), env.ptr());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setEnv failed", t);
		}
	}

	/// Attaches an [SstFileManager] to track SST files and enforce disk-space limits.
	///
	/// No ownership transfer: both objects may be closed independently.
	///
	/// @param sfm the SST file manager to attach
	/// @return `this` for chaining
	public Options setSstFileManager(SstFileManager sfm) {
		try {
			MH_SET_SST_FILE_MANAGER.invokeExact(ptr(), sfm.ptr());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setSstFileManager failed", t);
		}
	}

	/// Attaches an [SstPartitionerFactory] so compaction splits output SST files at partition
	/// boundaries instead of only by size.
	///
	/// No ownership transfer: both objects may be closed independently.
	///
	/// @param factory the SST partitioner factory to attach
	/// @return `this` for chaining
	public Options setSstPartitionerFactory(SstPartitionerFactory factory) {
		try {
			MH_SET_SST_PARTITIONER_FACTORY.invokeExact(ptr(), factory.ptr());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setSstPartitionerFactory failed", t);
		}
	}

	/// Attaches a [SliceTransform] as the prefix extractor, enabling prefix-based Bloom filters
	/// (attach one via [BlockBasedTableOptions#setFilterPolicy(FilterPolicy)]) and prefix
	/// iteration.
	///
	/// Changes the default seek behavior: with a prefix extractor set, iterators/seeks using a
	/// default [ReadOptions] may only return keys sharing the seek key's prefix instead of doing
	/// a full-order scan. Use [ReadOptions#setTotalOrderSeek(boolean)] to opt back into full
	/// ordering, or [ReadOptions#setPrefixSameAsStart(boolean)] to explicitly bound iteration to
	/// the seek key's prefix.
	///
	/// Transfers ownership of `transform` to this Options; do not close it afterwards.
	///
	/// @param transform the prefix extractor to attach; ownership is transferred
	/// @return `this` for chaining
	public Options setPrefixExtractor(SliceTransform transform) {
		try {
			MH_SET_PREFIX_EXTRACTOR.invokeExact(ptr(), transform.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setPrefixExtractor failed", t);
		}
		transform.transferOwnership();
		return this;
	}

	/// Attaches a [RateLimiter] to throttle compaction and flush I/O.
	///
	/// The rate limiter uses shared ownership: this call does not transfer
	/// ownership — both objects may be closed independently.
	///
	/// @param rateLimiter the rate limiter to attach
	/// @return `this` for chaining
	public Options setRateLimiter(RateLimiter rateLimiter) {
		try {
			MH_SET_RATELIMITER.invokeExact(ptr(), rateLimiter.ptr());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setRateLimiter failed", t);
		}
	}

	/// Attaches a [MergeOperator] so `merge()` calls have a defined semantics for this column
	/// family. Without one configured, every `merge()` call fails with [RocksDBException].
	///
	/// A [MergeOperator.Custom] transfers ownership: its `close()` becomes a no-op afterward.
	/// [MergeOperator#uint64Add()] holds no native handle and needs no ownership transfer.
	///
	/// @param mergeOperator the merge operator to attach
	/// @return `this` for chaining
	public Options setMergeOperator(MergeOperator mergeOperator) {
		switch (mergeOperator) {
			case MergeOperator.Uint64Add u -> u.applyTo(ptr());
			case MergeOperator.Custom c -> c.applyTo(ptr());
		}
		return this;
	}

	/// Attaches an [EventNotifier] that receives RocksDB's internal lifecycle events (flushes,
	/// compactions, external file ingestion, background errors, write stalls, memtable seals).
	///
	/// May be called more than once; every attached notifier is dispatched to independently.
	///
	/// @param notifier the callback to attach
	/// @return `this` for chaining
	public Options addEventListener(EventNotifier notifier) {
		EventNotifierBridge.attach(ptr(), notifier);
		return this;
	}

	// -----------------------------------------------------------------------
	// Temperature options
	// -----------------------------------------------------------------------

	/// Sets the temperature hint for metadata block-based tables (index, filter, etc.).
	/// EXPERIMENTAL. Default: [Temperature#UNKNOWN].
	///
	/// @param temperature the temperature hint to use for metadata files
	/// @return `this` for chaining
	public Options setMetadataWriteTemperature(Temperature temperature) {
		NativeFields.setInt(MH_SET_METADATA_WRITE_TEMPERATURE, ptr(), temperature.getValue());
		return this;
	}

	/// Returns the temperature hint configured for metadata block-based tables.
	///
	/// @return the active [Temperature] hint for metadata files
	public Temperature getMetadataWriteTemperature() {
		return Temperature.fromValue(NativeFields.getInt(MH_GET_METADATA_WRITE_TEMPERATURE, ptr()));
	}

	/// Sets the temperature hint for WAL files.
	/// EXPERIMENTAL. Default: [Temperature#UNKNOWN].
	///
	/// @param temperature the temperature hint to use for WAL files
	/// @return `this` for chaining
	public Options setWalWriteTemperature(Temperature temperature) {
		NativeFields.setInt(MH_SET_WAL_WRITE_TEMPERATURE, ptr(), temperature.getValue());
		return this;
	}

	/// Returns the temperature hint configured for WAL files.
	///
	/// @return the active [Temperature] hint for WAL files
	public Temperature getWalWriteTemperature() {
		return Temperature.fromValue(NativeFields.getInt(MH_GET_WAL_WRITE_TEMPERATURE, ptr()));
	}

	/// Sets the temperature hint for SST files placed on the last level.
	/// EXPERIMENTAL. Default: [Temperature#UNKNOWN].
	///
	/// @param temperature the temperature hint to use for last-level files
	/// @return `this` for chaining
	public Options setLastLevelTemperature(Temperature temperature) {
		NativeFields.setInt(MH_SET_LAST_LEVEL_TEMPERATURE, ptr(), temperature.getValue());
		return this;
	}

	/// Returns the temperature hint configured for SST files on the last level.
	///
	/// @return the active [Temperature] hint for last-level files
	public Temperature getLastLevelTemperature() {
		return Temperature.fromValue(NativeFields.getInt(MH_GET_LAST_LEVEL_TEMPERATURE, ptr()));
	}

	/// Sets the temperature hint used when a new SST file is written, for levels
	/// that don't otherwise have an explicit temperature configured.
	/// EXPERIMENTAL. Default: [Temperature#UNKNOWN].
	///
	/// @param temperature the temperature hint to use for newly written files
	/// @return `this` for chaining
	public Options setDefaultWriteTemperature(Temperature temperature) {
		NativeFields.setInt(MH_SET_DEFAULT_WRITE_TEMPERATURE, ptr(), temperature.getValue());
		return this;
	}

	/// Returns the temperature hint configured for newly written SST files.
	///
	/// @return the active default write [Temperature] hint
	public Temperature getDefaultWriteTemperature() {
		return Temperature.fromValue(NativeFields.getInt(MH_GET_DEFAULT_WRITE_TEMPERATURE, ptr()));
	}

	/// Sets the temperature hint assumed for existing SST files that have no
	/// temperature recorded in their metadata (e.g. files created before this
	/// option existed).
	/// EXPERIMENTAL. Default: [Temperature#UNKNOWN].
	///
	/// @param temperature the fallback temperature hint for files without a recorded temperature
	/// @return `this` for chaining
	public Options setDefaultTemperature(Temperature temperature) {
		NativeFields.setInt(MH_SET_DEFAULT_TEMPERATURE, ptr(), temperature.getValue());
		return this;
	}

	/// Returns the fallback temperature hint for files without a recorded temperature.
	///
	/// @return the active default [Temperature] hint
	public Temperature getDefaultTemperature() {
		return Temperature.fromValue(NativeFields.getInt(MH_GET_DEFAULT_TEMPERATURE, ptr()));
	}

	// -----------------------------------------------------------------------
	// Background jobs and file handles
	// -----------------------------------------------------------------------

	/// Maximum number of concurrent background flush and compaction jobs. Default: 2.
	///
	/// @param jobs maximum number of concurrent background jobs
	/// @return `this` for chaining
	public Options setMaxBackgroundJobs(int jobs) {
		NativeFields.setInt(MH_SET_MAX_BACKGROUND_JOBS, ptr(), jobs);
		return this;
	}

	/// Returns the configured maximum number of concurrent background jobs.
	///
	/// @return current maximum number of concurrent background jobs
	public int getMaxBackgroundJobs() {
		return NativeFields.getInt(MH_GET_MAX_BACKGROUND_JOBS, ptr());
	}

	/// Maximum number of open file handles RocksDB may keep cached across all SST files;
	/// `-1` means always keep every file open. Lowering this bounds file-descriptor usage at
	/// the cost of extra open/close syscalls under a large working set. Default: `-1`.
	///
	/// @param files maximum number of open files, or `-1` for unlimited
	/// @return `this` for chaining
	public Options setMaxOpenFiles(int files) {
		NativeFields.setInt(MH_SET_MAX_OPEN_FILES, ptr(), files);
		return this;
	}

	/// Returns the configured maximum number of open file handles.
	///
	/// @return current maximum number of open files, or `-1` if unlimited
	public int getMaxOpenFiles() {
		return NativeFields.getInt(MH_GET_MAX_OPEN_FILES, ptr());
	}

	/// Maximum number of threads used to open SST files in parallel when opening the DB.
	/// Higher values speed up startup with a large number of files at the cost of more
	/// concurrent I/O. Default: 16.
	///
	/// @param threads maximum number of file-opening threads
	/// @return `this` for chaining
	public Options setMaxFileOpeningThreads(int threads) {
		NativeFields.setInt(MH_SET_MAX_FILE_OPENING_THREADS, ptr(), threads);
		return this;
	}

	/// Returns the configured maximum number of file-opening threads.
	///
	/// @return current maximum number of file-opening threads
	public int getMaxFileOpeningThreads() {
		return NativeFields.getInt(MH_GET_MAX_FILE_OPENING_THREADS, ptr());
	}

	/// If `true`, hints the OS that file reads are random access (`POSIX_FADV_RANDOM`),
	/// disabling readahead for table files. Default: `true`.
	///
	/// @param value `true` to advise the OS that reads are random access
	/// @return `this` for chaining
	public Options setAdviseRandomOnOpen(boolean value) {
		NativeFields.setBoolean(MH_SET_ADVISE_RANDOM_ON_OPEN, ptr(), value);
		return this;
	}

	/// Returns whether the OS is advised that reads are random access.
	///
	/// @return `true` if the OS is advised that reads are random access
	public boolean getAdviseRandomOnOpen() {
		return NativeFields.getBoolean(MH_GET_ADVISE_RANDOM_ON_OPEN, ptr());
	}

	/// If `true`, skips updating per-column-family stats (e.g. number of files, levels) when
	/// opening the DB, shaving time off startup at the cost of stale stats until the first
	/// flush or compaction refreshes them. Default: `false`.
	///
	/// @param value `true` to skip the stats update on open
	/// @return `this` for chaining
	public Options setSkipStatsUpdateOnDbOpen(boolean value) {
		NativeFields.setBoolean(MH_SET_SKIP_STATS_UPDATE_ON_DB_OPEN, ptr(), value);
		return this;
	}

	/// Returns whether the stats update on open is skipped.
	///
	/// @return `true` if the stats update on open is skipped
	public boolean getSkipStatsUpdateOnDbOpen() {
		return NativeFields.getBoolean(MH_GET_SKIP_STATS_UPDATE_ON_DB_OPEN, ptr());
	}

	// -----------------------------------------------------------------------
	// Write-path tuning
	// -----------------------------------------------------------------------

	/// Which strategy RocksDB uses to prioritize which SST files to compact first within a
	/// level, per `c.h`'s anonymous `rocksdb_k_*_compaction_pri` enum (backed by C++'s
	/// `CompactionPri`).
	public enum CompactionPriority {
		/// The default: slightly prioritizes larger files, weighted down by their number of
		/// pending deletes.
		BY_COMPENSATED_SIZE(0),
		/// Compacts the file whose data was written longest ago first. Reduces write
		/// amplification.
		OLDEST_LARGEST_SEQ_FIRST(1),
		/// Compacts the file whose key range has gone longest without being pushed to the
		/// next level first. Reduces read amplification.
		OLDEST_SMALLEST_SEQ_FIRST(2),
		/// Compacts the file with the smallest ratio of overlapping-next-level-size to
		/// own-size first. Minimizes write amplification; a good default for most workloads.
		MIN_OVERLAPPING_RATIO(3),
		/// Keeps a per-level cursor and always compacts the file next to it, cycling through
		/// files in order rather than by a size/overlap heuristic.
		ROUND_ROBIN(4);

		final int value;

		CompactionPriority(int value) {
			this.value = value;
		}

		static CompactionPriority fromValue(int value) {
			return switch (value) {
				case 0 -> BY_COMPENSATED_SIZE;
				case 1 -> OLDEST_LARGEST_SEQ_FIRST;
				case 2 -> OLDEST_SMALLEST_SEQ_FIRST;
				case 3 -> MIN_OVERLAPPING_RATIO;
				case 4 -> ROUND_ROBIN;
				default -> throw new IllegalArgumentException("Unknown CompactionPriority value: " + value);
			};
		}
	}

	/// Applies a one-shot heuristic that derives several parallelism-related settings (e.g.
	/// background jobs, subcompactions, write-buffer count) from `totalThreads`, matching the
	/// number of cores available for RocksDB's own background work. Unlike the rest of this
	/// section, this has no matching getter: it's a write-only helper that fans out into other
	/// options rather than a single stored value.
	///
	/// @param totalThreads number of threads to size background parallelism for
	/// @return `this` for chaining
	public Options increaseParallelism(int totalThreads) {
		try {
			MH_INCREASE_PARALLELISM.invokeExact(ptr(), totalThreads);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("increaseParallelism failed", t);
		}
		return this;
	}

	/// If `true`, allows out-of-order writes when there's no dependency on the sequence number
	/// order (e.g. no user-defined timestamps, no merge operator sensitive to write order).
	/// Improves write throughput on multi-core machines at the cost of that ordering guarantee.
	/// Default: `false`.
	///
	/// @param value `true` to allow unordered writes
	/// @return `this` for chaining
	public Options setUnorderedWrite(boolean value) {
		NativeFields.setBoolean(MH_SET_UNORDERED_WRITE, ptr(), value);
		return this;
	}

	/// Returns whether unordered writes are allowed.
	///
	/// @return `true` if unordered writes are allowed
	public boolean getUnorderedWrite() {
		return NativeFields.getBoolean(MH_GET_UNORDERED_WRITE, ptr());
	}

	/// Threshold of bytes written before RocksDB forces an `fsync` on the currently active SST
	/// file, smoothing out I/O instead of a single large sync at file close. `0` disables this
	/// smoothing. Default: 0.
	///
	/// @param size byte threshold that triggers an intermediate `fsync`, or a zero-byte
	///             [MemorySize] to disable
	/// @return `this` for chaining
	public Options setBytesPerSync(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_BYTES_PER_SYNC, ptr(), size);
		return this;
	}

	/// Returns the configured `fsync` byte threshold.
	///
	/// @return current byte threshold that triggers an intermediate `fsync`
	public MemorySize getBytesPerSync() {
		return NativeFields.getMemorySize(MH_GET_BYTES_PER_SYNC, ptr());
	}

	/// If `true`, uses `O_DIRECT` for user reads, bypassing the OS page cache. Default: `false`.
	///
	/// @param value `true` to use direct I/O for reads
	/// @return `this` for chaining
	public Options setUseDirectReads(boolean value) {
		NativeFields.setBoolean(MH_SET_USE_DIRECT_READS, ptr(), value);
		return this;
	}

	/// Returns whether direct I/O is used for reads.
	///
	/// @return `true` if direct I/O is used for reads
	public boolean getUseDirectReads() {
		return NativeFields.getBoolean(MH_GET_USE_DIRECT_READS, ptr());
	}

	/// If `true`, uses `O_DIRECT` for flush and compaction output, bypassing the OS page cache.
	/// Default: `false`.
	///
	/// @param value `true` to use direct I/O for flush and compaction
	/// @return `this` for chaining
	public Options setUseDirectIoForFlushAndCompaction(boolean value) {
		NativeFields.setBoolean(MH_SET_USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION, ptr(), value);
		return this;
	}

	/// Returns whether direct I/O is used for flush and compaction output.
	///
	/// @return `true` if direct I/O is used for flush and compaction output
	public boolean getUseDirectIoForFlushAndCompaction() {
		return NativeFields.getBoolean(MH_GET_USE_DIRECT_IO_FOR_FLUSH_AND_COMPACTION, ptr());
	}

	/// Sets which strategy RocksDB uses to prioritize files for compaction within a level.
	/// Default: [CompactionPriority#BY_COMPENSATED_SIZE].
	///
	/// @param priority the compaction priority strategy to use
	/// @return `this` for chaining
	public Options setCompactionPriority(CompactionPriority priority) {
		NativeFields.setInt(MH_SET_COMPACTION_PRI, ptr(), priority.value);
		return this;
	}

	/// Returns the configured compaction priority strategy.
	///
	/// @return current compaction priority strategy
	public CompactionPriority getCompactionPriority() {
		return CompactionPriority.fromValue(NativeFields.getInt(MH_GET_COMPACTION_PRI, ptr()));
	}

	/// Sets the compression algorithm for the bottommost level only, overriding
	/// [#setCompression] there -- typically a stronger/slower algorithm (e.g.
	/// [CompressionType#ZSTD]) since the bottommost level holds the most data and is
	/// compacted least often.
	///
	/// @param type the compression algorithm to use for the bottommost level
	/// @return `this` for chaining
	/// @throws UnsupportedOperationException if `type` isn't linked into the bundled native library
	public Options setBottommostCompressionType(CompressionType type) {
		if (!type.isSupported()) {
			throw new UnsupportedOperationException(type + " compression is not linked into the bundled native library");
		}
		NativeFields.setInt(MH_SET_BOTTOMMOST_COMPRESSION, ptr(), type.getValue());
		return this;
	}

	/// Returns the compression algorithm configured for the bottommost level.
	///
	/// @return the active bottommost-level compression type
	public CompressionType getBottommostCompressionType() {
		return CompressionType.fromValue(NativeFields.getInt(MH_GET_BOTTOMMOST_COMPRESSION, ptr()));
	}

	// -----------------------------------------------------------------------
	// Memtable tuning
	// -----------------------------------------------------------------------
	//
	// Applies to the memtable's own bloom filter and arena allocation, independent of which
	// memtable factory is selected below (default SkipList, hash-skiplist, or hash-linklist).

	/// Builds a bloom filter inside the memtable itself, sized as this fraction of the write
	/// buffer, keyed by [#setPrefixExtractor]'s prefix -- speeds up prefix `Seek()` against the
	/// still-unflushed memtable, the memtable-side counterpart to what
	/// [#setHashSkipListMemTableFactory]/[#setHashLinkListMemTableFactory] do for the memtable's
	/// own lookup structure. `0` disables it. Requires a prefix extractor to be set. Default: 0.
	///
	/// @param ratio memtable bloom filter size as a fraction of the write buffer, or `0` to disable
	/// @return `this` for chaining
	public Options setMemtablePrefixBloomSizeRatio(double ratio) {
		NativeFields.setDouble(MH_SET_MEMTABLE_PREFIX_BLOOM_SIZE_RATIO, ptr(), ratio);
		return this;
	}

	/// Returns the configured memtable bloom filter size ratio.
	///
	/// @return current memtable bloom filter size ratio, or `0` if disabled
	public double getMemtablePrefixBloomSizeRatio() {
		return NativeFields.getDouble(MH_GET_MEMTABLE_PREFIX_BLOOM_SIZE_RATIO, ptr());
	}

	/// If `true`, [#setMemtablePrefixBloomSizeRatio]'s memtable bloom filter also indexes whole
	/// keys, not just prefixes -- speeds up exact `Get()`s against the memtable in addition to
	/// prefix `Seek()`s. Has no effect unless a memtable prefix bloom filter is configured.
	/// Default: `false`.
	///
	/// @param value `true` to also index whole keys in the memtable bloom filter
	/// @return `this` for chaining
	public Options setMemtableWholeKeyFiltering(boolean value) {
		NativeFields.setBoolean(MH_SET_MEMTABLE_WHOLE_KEY_FILTERING, ptr(), value);
		return this;
	}

	/// Returns whether the memtable bloom filter also indexes whole keys.
	///
	/// @return `true` if the memtable bloom filter also indexes whole keys
	public boolean getMemtableWholeKeyFiltering() {
		return NativeFields.getBoolean(MH_GET_MEMTABLE_WHOLE_KEY_FILTERING, ptr());
	}

	/// Size of the huge-page TLB to allocate the memtable's arena from, independent of which
	/// memtable factory is selected. `0` allocates from regular `malloc` instead. Requires the
	/// OS to have huge pages reserved (e.g. `sysctl -w vm.nr_hugepages=20` on Linux).
	/// Default: 0 (disabled).
	///
	/// @param size huge-page TLB byte size, or a zero-byte [MemorySize] to disable
	/// @return `this` for chaining
	public Options setMemtableHugePageSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_MEMTABLE_HUGE_PAGE_SIZE, ptr(), size);
		return this;
	}

	/// Returns the configured memtable huge-page TLB size.
	///
	/// @return current memtable huge-page TLB byte size, or zero if disabled
	public MemorySize getMemtableHugePageSize() {
		return NativeFields.getMemorySize(MH_GET_MEMTABLE_HUGE_PAGE_SIZE, ptr());
	}

	/// Controls memory locality of the memtable's bloom filter bits: `0` disables locality
	/// grouping; higher values group more of a key's bloom bits into the same cache line, at
	/// the cost of a slightly higher false-positive rate. Default: 0.
	///
	/// @param locality bloom filter memory locality level, or `0` to disable
	/// @return `this` for chaining
	public Options setBloomLocality(int locality) {
		NativeFields.setInt(MH_SET_BLOOM_LOCALITY, ptr(), locality);
		return this;
	}

	/// Returns the configured bloom filter memory locality level.
	///
	/// @return current bloom filter memory locality level, or `0` if disabled
	public int getBloomLocality() {
		return NativeFields.getInt(MH_GET_BLOOM_LOCALITY, ptr());
	}

	// -----------------------------------------------------------------------
	// Memtable factory
	// -----------------------------------------------------------------------
	//
	// Selects the in-memory representation backing the active memtable, replacing the default
	// SkipList. Each factory is a one-shot selection with no matching getter -- like
	// setTableFormatConfig, c.h exposes no way to read back which memtable factory is
	// currently configured.

	/// Selects a hash-table-of-skiplists memtable, indexed by [#setPrefixExtractor]'s prefix.
	/// Speeds up prefix `Seek()` at the cost of full-key iteration order across different
	/// prefixes. Requires a prefix extractor to be set; behaves like the default SkipList
	/// memtable otherwise.
	///
	/// @param bucketCount       number of hash buckets
	/// @param height            skiplist height for each bucket
	/// @param branchingFactor   skiplist branching factor for each bucket
	/// @return `this` for chaining
	public Options setHashSkipListMemTableFactory(long bucketCount, int height, int branchingFactor) {
		try {
			MH_SET_HASH_SKIP_LIST_REP.invokeExact(ptr(), bucketCount, height, branchingFactor);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setHashSkipListMemTableFactory failed", t);
		}
		return this;
	}

	/// Selects a hash-table-of-sorted-linked-lists memtable, indexed by [#setPrefixExtractor]'s
	/// prefix. Lighter-weight than [#setHashSkipListMemTableFactory] per bucket, at the cost of
	/// O(n) insertion within a bucket instead of O(log n). Requires a prefix extractor to be
	/// set; behaves like the default SkipList memtable otherwise.
	///
	/// @param bucketCount number of hash buckets
	/// @return `this` for chaining
	public Options setHashLinkListMemTableFactory(long bucketCount) {
		try {
			MH_SET_HASH_LINK_LIST_REP.invokeExact(ptr(), bucketCount);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setHashLinkListMemTableFactory failed", t);
		}
		return this;
	}

	/// Selects a plain vector memtable: appends writes to an unsorted vector and only sorts on
	/// flush. Useful for bulk-loading workloads with few reads against the active memtable, since
	/// it avoids the SkipList's per-insert ordering cost; degrades to a linear scan for any read
	/// or iteration issued before the next flush.
	///
	/// @return `this` for chaining
	public Options setVectorMemTableFactory() {
		try {
			MH_SET_MEMTABLE_VECTOR_REP.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setVectorMemTableFactory failed", t);
		}
		return this;
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
