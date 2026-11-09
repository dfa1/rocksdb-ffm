package io.github.dfa1.rocksdbffm;

/// Ticker types for RocksDB statistics.
/// Corresponds to `enum Tickers` in `rocksdb/statistics.h`.
///
/// *NB*: beware this must be regenerated every time a new version of rocksdb is imported (!)
public enum TickerType {
	/// Number of block cache misses.
	BLOCK_CACHE_MISS(0),
	/// Number of block cache hits.
	BLOCK_CACHE_HIT(1),
	/// Number of blocks added to the block cache.
	BLOCK_CACHE_ADD(2),
	/// Number of failures when adding a block to the block cache.
	BLOCK_CACHE_ADD_FAILURES(3),
	/// Number of index block misses in the block cache.
	BLOCK_CACHE_INDEX_MISS(4),
	/// Number of index block hits in the block cache.
	BLOCK_CACHE_INDEX_HIT(5),
	/// Number of index blocks added to the block cache.
	BLOCK_CACHE_INDEX_ADD(6),
	/// Bytes of index blocks inserted into the block cache.
	BLOCK_CACHE_INDEX_BYTES_INSERT(7),
	/// Number of filter block misses in the block cache.
	BLOCK_CACHE_FILTER_MISS(8),
	/// Number of filter block hits in the block cache.
	BLOCK_CACHE_FILTER_HIT(9),
	/// Number of filter blocks added to the block cache.
	BLOCK_CACHE_FILTER_ADD(10),
	/// Bytes of filter blocks inserted into the block cache.
	BLOCK_CACHE_FILTER_BYTES_INSERT(11),
	/// Number of data block misses in the block cache.
	BLOCK_CACHE_DATA_MISS(12),
	/// Number of data block hits in the block cache.
	BLOCK_CACHE_DATA_HIT(13),
	/// Number of data blocks added to the block cache.
	BLOCK_CACHE_DATA_ADD(14),
	/// Bytes of data blocks inserted into the block cache.
	BLOCK_CACHE_DATA_BYTES_INSERT(15),
	/// Bytes read from the block cache.
	BLOCK_CACHE_BYTES_READ(16),
	/// Bytes written to the block cache.
	BLOCK_CACHE_BYTES_WRITE(17),
	/// Number of compression dictionary block misses in the block cache.
	BLOCK_CACHE_COMPRESSION_DICT_MISS(18),
	/// Number of compression dictionary block hits in the block cache.
	BLOCK_CACHE_COMPRESSION_DICT_HIT(19),
	/// Number of compression dictionary blocks added to the block cache.
	BLOCK_CACHE_COMPRESSION_DICT_ADD(20),
	/// Bytes of compression dictionary blocks inserted into the block cache.
	BLOCK_CACHE_COMPRESSION_DICT_BYTES_INSERT(21),
	/// Number of redundant block additions to the block cache.
	BLOCK_CACHE_ADD_REDUNDANT(22),
	/// Number of redundant index block additions.
	BLOCK_CACHE_INDEX_ADD_REDUNDANT(23),
	/// Number of redundant filter block additions.
	BLOCK_CACHE_FILTER_ADD_REDUNDANT(24),
	/// Number of redundant data block additions.
	BLOCK_CACHE_DATA_ADD_REDUNDANT(25),
	/// Number of redundant compression dictionary block additions.
	BLOCK_CACHE_COMPRESSION_DICT_ADD_REDUNDANT(26),
	/// Number of hits served from the secondary cache.
	SECONDARY_CACHE_HITS(27),
	/// Number of filter block hits from the secondary cache.
	SECONDARY_CACHE_FILTER_HITS(28),
	/// Number of index block hits from the secondary cache.
	SECONDARY_CACHE_INDEX_HITS(29),
	/// Number of data block hits from the secondary cache.
	SECONDARY_CACHE_DATA_HITS(30),
	/// Number of dummy hits in the compressed secondary cache.
	COMPRESSED_SECONDARY_CACHE_DUMMY_HITS(31),
	/// Number of hits in the compressed secondary cache.
	COMPRESSED_SECONDARY_CACHE_HITS(32),
	/// Number of promotions from the compressed secondary cache.
	COMPRESSED_SECONDARY_CACHE_PROMOTIONS(33),
	/// Number of skipped promotions from the compressed secondary cache.
	COMPRESSED_SECONDARY_CACHE_PROMOTION_SKIPS(34),
	/// Number of times a Bloom filter was useful (avoided a read).
	BLOOM_FILTER_USEFUL(35),
	/// Number of full Bloom filter positive matches.
	BLOOM_FILTER_FULL_POSITIVE(36),
	/// Number of full Bloom filter true positive matches.
	BLOOM_FILTER_FULL_TRUE_POSITIVE(37),
	/// Number of prefix Bloom filter checks.
	BLOOM_FILTER_PREFIX_CHECKED(38),
	/// Number of prefix Bloom filter useful (avoided a read).
	BLOOM_FILTER_PREFIX_USEFUL(39),
	/// Number of prefix Bloom filter true positive matches.
	BLOOM_FILTER_PREFIX_TRUE_POSITIVE(40),
	/// Number of persistent cache hits.
	PERSISTENT_CACHE_HIT(41),
	/// Number of persistent cache misses.
	PERSISTENT_CACHE_MISS(42),
	/// Number of simulated block cache hits.
	SIM_BLOCK_CACHE_HIT(43),
	/// Number of simulated block cache misses.
	SIM_BLOCK_CACHE_MISS(44),
	/// Number of memtable hits.
	MEMTABLE_HIT(45),
	/// Number of memtable misses.
	MEMTABLE_MISS(46),
	/// Number of Get hits at level 0.
	GET_HIT_L0(47),
	/// Number of Get hits at level 1.
	GET_HIT_L1(48),
	/// Number of Get hits at level 2 and above.
	GET_HIT_L2_AND_UP(49),
	/// Keys dropped during compaction because a newer entry exists.
	COMPACTION_KEY_DROP_NEWER_ENTRY(50),
	/// Keys dropped during compaction because they are obsolete.
	COMPACTION_KEY_DROP_OBSOLETE(51),
	/// Keys dropped during compaction by range tombstones.
	COMPACTION_KEY_DROP_RANGE_DEL(52),
	/// Keys dropped during compaction by user compaction filter.
	COMPACTION_KEY_DROP_USER(53),
	/// Range deletions dropped during compaction as obsolete.
	COMPACTION_RANGE_DEL_DROP_OBSOLETE(54),
	/// Deletions optimized away during compaction.
	COMPACTION_OPTIMIZED_DEL_DROP_OBSOLETE(55),
	/// Number of compactions cancelled due to full output level.
	COMPACTION_CANCELLED(56),
	/// Number of compactions aborted.
	COMPACTION_ABORTED(57),
	/// Total number of keys written.
	NUMBER_KEYS_WRITTEN(58),
	/// Total number of keys read.
	NUMBER_KEYS_READ(59),
	/// Total number of keys updated (merge operations).
	NUMBER_KEYS_UPDATED(60),
	/// Total bytes written to the DB.
	BYTES_WRITTEN(61),
	/// Total bytes read from the DB.
	BYTES_READ(62),
	/// Total number of DB Seek iterator calls.
	NUMBER_DB_SEEK(63),
	/// Total number of DB Next iterator calls.
	NUMBER_DB_NEXT(64),
	/// Total number of DB Prev iterator calls.
	NUMBER_DB_PREV(65),
	/// Number of DB Seek calls that found a valid key.
	NUMBER_DB_SEEK_FOUND(66),
	/// Number of DB Next calls that found a valid key.
	NUMBER_DB_NEXT_FOUND(67),
	/// Number of DB Prev calls that found a valid key.
	NUMBER_DB_PREV_FOUND(68),
	/// Bytes read during iterator operations.
	ITER_BYTES_READ(69),
	/// Number of internal keys skipped during iteration.
	NUMBER_ITER_SKIP(70),
	/// Number of re-seeks performed during iteration.
	NUMBER_OF_RESEEKS_IN_ITERATION(71),
	/// Total number of iterators created.
	NO_ITERATOR_CREATED(72),
	/// Total number of iterators deleted.
	NO_ITERATOR_DELETED(73),
	/// Number of file opens.
	NO_FILE_OPENS(74),
	/// Number of file errors.
	NO_FILE_ERRORS(75),
	/// Total microseconds spent in write stalls.
	STALL_MICROS(76),
	/// Total microseconds spent waiting for the DB mutex.
	DB_MUTEX_WAIT_MICROS(77),
	/// Total number of MultiGet calls.
	NUMBER_MULTIGET_CALLS(78),
	/// Total number of keys read via MultiGet.
	NUMBER_MULTIGET_KEYS_READ(79),
	/// Total bytes read via MultiGet.
	NUMBER_MULTIGET_BYTES_READ(80),
	/// Total number of keys found via MultiGet.
	NUMBER_MULTIGET_KEYS_FOUND(81),
	/// Number of merge operation failures.
	NUMBER_MERGE_FAILURES(82),
	/// Number of GetUpdatesSince calls.
	GET_UPDATES_SINCE_CALLS(83),
	/// Number of WAL file syncs.
	WAL_FILE_SYNCED(84),
	/// Total bytes written to WAL files.
	WAL_FILE_BYTES(85),
	/// Number of WAL rotations that consumed an async precreated WAL.
	WAL_PRECREATE_HIT(86),
	/// Number of WAL rotations that found no async precreated WAL to consume.
	WAL_PRECREATE_MISS(87),
	/// Number of WAL rotations that waited for an in-flight WAL precreation.
	WAL_PRECREATE_WAITED(88),
	/// Total foreground wait time for in-flight WAL precreation.
	WAL_PRECREATE_WAIT_MICROS(89),
	/// Number of async WAL precreation attempts that failed.
	WAL_PRECREATE_FAILED(90),
	/// Number of writes done by the writing thread itself.
	WRITE_DONE_BY_SELF(91),
	/// Number of writes done by another thread in a group commit.
	WRITE_DONE_BY_OTHER(92),
	/// Number of writes that included a WAL write.
	WRITE_WITH_WAL(93),
	/// Bytes read during compaction.
	COMPACT_READ_BYTES(94),
	/// Bytes written during compaction.
	COMPACT_WRITE_BYTES(95),
	/// Bytes written during flush.
	FLUSH_WRITE_BYTES(96),
	/// Compaction bytes read for explicitly marked files.
	COMPACT_READ_BYTES_MARKED(97),
	/// Compaction bytes read for periodic compaction.
	COMPACT_READ_BYTES_PERIODIC(98),
	/// Compaction bytes read for TTL compaction.
	COMPACT_READ_BYTES_TTL(99),
	/// Compaction bytes written for explicitly marked files.
	COMPACT_WRITE_BYTES_MARKED(100),
	/// Compaction bytes written for periodic compaction.
	COMPACT_WRITE_BYTES_PERIODIC(101),
	/// Compaction bytes written for TTL compaction.
	COMPACT_WRITE_BYTES_TTL(102),
	/// Number of times table properties were loaded directly.
	NUMBER_DIRECT_LOAD_TABLE_PROPERTIES(103),
	/// Number of superversion acquisitions.
	NUMBER_SUPERVERSION_ACQUIRES(104),
	/// Number of superversion releases.
	NUMBER_SUPERVERSION_RELEASES(105),
	/// Number of superversion cleanups.
	NUMBER_SUPERVERSION_CLEANUPS(106),
	/// Number of blocks compressed.
	NUMBER_BLOCK_COMPRESSED(107),
	/// Number of blocks decompressed.
	NUMBER_BLOCK_DECOMPRESSED(108),
	/// Bytes before compression.
	BYTES_COMPRESSED_FROM(109),
	/// Bytes after compression.
	BYTES_COMPRESSED_TO(110),
	/// Bytes bypassed by compression (stored uncompressed).
	BYTES_COMPRESSION_BYPASSED(111),
	/// Bytes rejected by compression filter.
	BYTES_COMPRESSION_REJECTED(112),
	/// Number of blocks that bypassed compression.
	NUMBER_BLOCK_COMPRESSION_BYPASSED(113),
	/// Number of blocks rejected by compression filter.
	NUMBER_BLOCK_COMPRESSION_REJECTED(114),
	/// Bytes before decompression.
	BYTES_DECOMPRESSED_FROM(115),
	/// Bytes after decompression.
	BYTES_DECOMPRESSED_TO(116),
	/// Total time spent in merge operations, in microseconds.
	MERGE_OPERATION_TOTAL_TIME(117),
	/// Total time spent in filter operations, in microseconds.
	FILTER_OPERATION_TOTAL_TIME(118),
	/// Total CPU time spent in compaction, in microseconds.
	COMPACTION_CPU_TOTAL_TIME(119),
	/// Number of row cache hits.
	ROW_CACHE_HIT(120),
	/// Number of row cache misses.
	ROW_CACHE_MISS(121),
	/// Estimate of useful bytes read (read amplification numerator).
	READ_AMP_ESTIMATE_USEFUL_BYTES(122),
	/// Total bytes read including amplification (read amplification denominator).
	READ_AMP_TOTAL_READ_BYTES(123),
	/// Number of rate limiter drains.
	NUMBER_RATE_LIMITER_DRAINS(124),
	/// Bytes granted by the rate limiter for read requests.
	RATE_LIMITER_BYTES_READ(125),
	/// Bytes granted by the rate limiter for write requests.
	RATE_LIMITER_BYTES_WRITE(126),
	/// Number of read requests granted by the rate limiter.
	RATE_LIMITER_REQUESTS_READ(127),
	/// Number of write requests granted by the rate limiter.
	RATE_LIMITER_REQUESTS_WRITE(128),
	/// Number of read requests that waited for a future rate limiter refill.
	RATE_LIMITER_DELAYED_REQUESTS_READ(129),
	/// Number of write requests that waited for a future rate limiter refill.
	RATE_LIMITER_DELAYED_REQUESTS_WRITE(130),
	/// Total time read requests spent waiting for rate limiter refills.
	RATE_LIMITER_TOTAL_WAIT_MICROS_READ(131),
	/// Total time write requests spent waiting for rate limiter refills.
	RATE_LIMITER_TOTAL_WAIT_MICROS_WRITE(132),
	/// Number of blob DB Put operations.
	BLOB_DB_NUM_PUT(133),
	/// Number of blob DB Write operations.
	BLOB_DB_NUM_WRITE(134),
	/// Number of blob DB Get operations.
	BLOB_DB_NUM_GET(135),
	/// Number of blob DB MultiGet operations.
	BLOB_DB_NUM_MULTIGET(136),
	/// Number of blob DB Seek operations.
	BLOB_DB_NUM_SEEK(137),
	/// Number of blob DB Next operations.
	BLOB_DB_NUM_NEXT(138),
	/// Number of blob DB Prev operations.
	BLOB_DB_NUM_PREV(139),
	/// Number of keys written to blob DB.
	BLOB_DB_NUM_KEYS_WRITTEN(140),
	/// Number of keys read from blob DB.
	BLOB_DB_NUM_KEYS_READ(141),
	/// Bytes written to blob DB.
	BLOB_DB_BYTES_WRITTEN(142),
	/// Bytes read from blob DB.
	BLOB_DB_BYTES_READ(143),
	/// Deprecated: number of inlined blob DB writes.
	BLOB_DB_WRITE_INLINED_DEPRECATED(144),
	/// Deprecated: number of inlined TTL blob DB writes.
	BLOB_DB_WRITE_INLINED_TTL_DEPRECATED(145),
	/// Number of values written as blobs.
	BLOB_DB_WRITE_BLOB(146),
	/// Number of values written as TTL blobs.
	BLOB_DB_WRITE_BLOB_TTL(147),
	/// Bytes written to blob files.
	BLOB_DB_BLOB_FILE_BYTES_WRITTEN(148),
	/// Bytes read from blob files.
	BLOB_DB_BLOB_FILE_BYTES_READ(149),
	/// Number of blob file syncs.
	BLOB_DB_BLOB_FILE_SYNCED(150),
	/// Number of expired blob index entries.
	BLOB_DB_BLOB_INDEX_EXPIRED_COUNT(151),
	/// Size of expired blob index entries.
	BLOB_DB_BLOB_INDEX_EXPIRED_SIZE(152),
	/// Number of evicted blob index entries.
	BLOB_DB_BLOB_INDEX_EVICTED_COUNT(153),
	/// Size of evicted blob index entries.
	BLOB_DB_BLOB_INDEX_EVICTED_SIZE(154),
	/// Number of blob files considered for GC.
	BLOB_DB_GC_NUM_FILES(155),
	/// Number of new blob files created during GC.
	BLOB_DB_GC_NUM_NEW_FILES(156),
	/// Number of blob GC failures.
	BLOB_DB_GC_FAILURES(157),
	/// Number of keys relocated during blob GC.
	BLOB_DB_GC_NUM_KEYS_RELOCATED(158),
	/// Bytes relocated during blob GC.
	BLOB_DB_GC_BYTES_RELOCATED(159),
	/// Number of files evicted by FIFO compaction.
	BLOB_DB_FIFO_NUM_FILES_EVICTED(160),
	/// Number of keys evicted by FIFO compaction.
	BLOB_DB_FIFO_NUM_KEYS_EVICTED(161),
	/// Bytes evicted by FIFO compaction.
	BLOB_DB_FIFO_BYTES_EVICTED(162),
	/// Number of blob cache misses.
	BLOB_DB_CACHE_MISS(163),
	/// Number of blob cache hits.
	BLOB_DB_CACHE_HIT(164),
	/// Number of blob cache adds.
	BLOB_DB_CACHE_ADD(165),
	/// Number of blob cache add failures.
	BLOB_DB_CACHE_ADD_FAILURES(166),
	/// Bytes read from the blob cache.
	BLOB_DB_CACHE_BYTES_READ(167),
	/// Bytes written to the blob cache.
	BLOB_DB_CACHE_BYTES_WRITE(168),
	/// Overhead from transaction prepare mutex.
	TXN_PREPARE_MUTEX_OVERHEAD(169),
	/// Overhead from transaction old commit map mutex.
	TXN_OLD_COMMIT_MAP_MUTEX_OVERHEAD(170),
	/// Overhead from duplicate key checks in transactions.
	TXN_DUPLICATE_KEY_OVERHEAD(171),
	/// Overhead from transaction snapshot mutex.
	TXN_SNAPSHOT_MUTEX_OVERHEAD(172),
	/// Number of transaction Get try-again retries.
	TXN_GET_TRY_AGAIN(173),
	/// Number of files marked as trash for background deletion.
	FILES_MARKED_TRASH(174),
	/// Number of files deleted from the trash queue.
	FILES_DELETED_FROM_TRASH_QUEUE(175),
	/// Number of files deleted immediately (not via trash queue).
	FILES_DELETED_IMMEDIATELY(176),
	/// Number of background errors handled by the error handler.
	ERROR_HANDLER_BG_ERROR_COUNT(177),
	/// Number of background I/O errors handled by the error handler.
	ERROR_HANDLER_BG_IO_ERROR_COUNT(178),
	/// Number of retryable background I/O errors.
	ERROR_HANDLER_BG_RETRYABLE_IO_ERROR_COUNT(179),
	/// Number of automatic error resume attempts.
	ERROR_HANDLER_AUTORESUME_COUNT(180),
	/// Total number of auto-resume retry attempts.
	ERROR_HANDLER_AUTORESUME_RETRY_TOTAL_COUNT(181),
	/// Number of successful auto-resume operations.
	ERROR_HANDLER_AUTORESUME_SUCCESS_COUNT(182),
	/// Bytes of useful payload in memtable at flush.
	MEMTABLE_PAYLOAD_BYTES_AT_FLUSH(183),
	/// Bytes of garbage in memtable at flush.
	MEMTABLE_GARBAGE_BYTES_AT_FLUSH(184),
	/// Bytes read during checksum verification.
	VERIFY_CHECKSUM_READ_BYTES(185),
	/// Bytes read during backup.
	BACKUP_READ_BYTES(186),
	/// Bytes written during backup.
	BACKUP_WRITE_BYTES(187),
	/// Bytes read during remote compaction.
	REMOTE_COMPACT_READ_BYTES(188),
	/// Bytes written during remote compaction.
	REMOTE_COMPACT_WRITE_BYTES(189),
	/// Bytes resumed from previous remote compaction.
	REMOTE_COMPACT_RESUMED_BYTES(190),
	/// Bytes read from hot-temperature files.
	HOT_FILE_READ_BYTES(191),
	/// Bytes read from warm-temperature files.
	WARM_FILE_READ_BYTES(192),
	/// Bytes read from cool-temperature files.
	COOL_FILE_READ_BYTES(193),
	/// Bytes read from cold-temperature files.
	COLD_FILE_READ_BYTES(194),
	/// Bytes read from ice-temperature files.
	ICE_FILE_READ_BYTES(195),
	/// Number of reads from hot-temperature files.
	HOT_FILE_READ_COUNT(196),
	/// Number of reads from warm-temperature files.
	WARM_FILE_READ_COUNT(197),
	/// Number of reads from cool-temperature files.
	COOL_FILE_READ_COUNT(198),
	/// Number of reads from cold-temperature files.
	COLD_FILE_READ_COUNT(199),
	/// Number of reads from ice-temperature files.
	ICE_FILE_READ_COUNT(200),
	/// Bytes read from the last LSM level.
	LAST_LEVEL_READ_BYTES(201),
	/// Number of reads from the last LSM level.
	LAST_LEVEL_READ_COUNT(202),
	/// Bytes read from non-last LSM levels.
	NON_LAST_LEVEL_READ_BYTES(203),
	/// Number of reads from non-last LSM levels.
	NON_LAST_LEVEL_READ_COUNT(204),
	/// Number of seeks on the last level filtered by Bloom/index.
	LAST_LEVEL_SEEK_FILTERED(205),
	/// Number of seeks on the last level that matched after Bloom/index filter.
	LAST_LEVEL_SEEK_FILTER_MATCH(206),
	/// Number of seeks on the last level that read data.
	LAST_LEVEL_SEEK_DATA(207),
	/// Seeks on the last level reading useful data, no filter.
	LAST_LEVEL_SEEK_DATA_USEFUL_NO_FILTER(208),
	/// Seeks on the last level reading useful data with a filter match.
	LAST_LEVEL_SEEK_DATA_USEFUL_FILTER_MATCH(209),
	/// Number of seeks on non-last levels filtered by Bloom/index.
	NON_LAST_LEVEL_SEEK_FILTERED(210),
	/// Number of seeks on non-last levels matching after Bloom/index filter.
	NON_LAST_LEVEL_SEEK_FILTER_MATCH(211),
	/// Number of seeks on non-last levels reading data.
	NON_LAST_LEVEL_SEEK_DATA(212),
	/// Seeks on non-last levels reading useful data, no filter.
	NON_LAST_LEVEL_SEEK_DATA_USEFUL_NO_FILTER(213),
	/// Seeks on non-last levels reading useful data with filter match.
	NON_LAST_LEVEL_SEEK_DATA_USEFUL_FILTER_MATCH(214),
	/// Number of block checksum computations.
	BLOCK_CHECKSUM_COMPUTE_COUNT(215),
	/// Number of block checksum mismatches detected.
	BLOCK_CHECKSUM_MISMATCH_COUNT(216),
	/// Number of MultiGet coroutines invoked.
	MULTIGET_COROUTINE_COUNT(217),
	/// Microseconds spent in async reads.
	READ_ASYNC_MICROS(218),
	/// Number of errors during async reads.
	ASYNC_READ_ERROR_COUNT(219),
	/// Number of table open prefetch tail misses.
	TABLE_OPEN_PREFETCH_TAIL_MISS(220),
	/// Number of table open prefetch tail hits.
	TABLE_OPEN_PREFETCH_TAIL_HIT(221),
	/// Number of tables checked by the timestamp filter.
	TIMESTAMP_FILTER_TABLE_CHECKED(222),
	/// Number of tables filtered out by the timestamp filter.
	TIMESTAMP_FILTER_TABLE_FILTERED(223),
	/// Number of readahead trims.
	READAHEAD_TRIMMED(224),
	/// Number of FIFO compactions triggered by max size.
	FIFO_MAX_SIZE_COMPACTIONS(225),
	/// Number of FIFO compactions triggered by TTL.
	FIFO_TTL_COMPACTIONS(226),
	/// Number of FIFO compactions triggered by temperature changes.
	FIFO_CHANGE_TEMPERATURE_COMPACTIONS(227),
	/// Total bytes prefetched.
	PREFETCH_BYTES(228),
	/// Useful bytes from prefetching (actually consumed).
	PREFETCH_BYTES_USEFUL(229),
	/// Number of prefetch hits.
	PREFETCH_HITS(230),
	/// Number of SST file footer corruption events.
	SST_FOOTER_CORRUPTION_COUNT(231),
	/// Number of file read corruption retry attempts.
	FILE_READ_CORRUPTION_RETRY_COUNT(232),
	/// Number of successful file read corruption retries.
	FILE_READ_CORRUPTION_RETRY_SUCCESS_COUNT(233),
	/// Number of WBWI ingest operations.
	NUMBER_WBWI_INGEST(234),
	/// Number of failures loading user-defined SST index.
	SST_USER_DEFINED_INDEX_LOAD_FAIL_COUNT(235),
	/// Number of multiscan prepare calls.
	MULTISCAN_PREPARE_CALLS(236),
	/// Number of multiscan prepare errors.
	MULTISCAN_PREPARE_ERRORS(237),
	/// Number of blocks prefetched during multiscan.
	MULTISCAN_BLOCKS_PREFETCHED(238),
	/// Number of blocks served from cache during multiscan.
	MULTISCAN_BLOCKS_FROM_CACHE(239),
	/// Bytes prefetched during multiscan.
	MULTISCAN_PREFETCH_BYTES(240),
	/// Prefetched blocks wasted during multiscan.
	MULTISCAN_PREFETCH_BLOCKS_WASTED(241),
	/// Number of I/O requests during multiscan.
	MULTISCAN_IO_REQUESTS(242),
	/// Number of coalesced non-adjacent I/O requests during multiscan.
	MULTISCAN_IO_COALESCED_NONADJACENT(243),
	/// Number of seek errors during multiscan.
	MULTISCAN_SEEK_ERRORS(244),
	/// Bytes granted for prefetch memory.
	PREFETCH_MEMORY_BYTES_GRANTED(245),
	/// Bytes released from prefetch memory.
	PREFETCH_MEMORY_BYTES_RELEASED(246),
	/// Number of prefetch memory requests blocked.
	PREFETCH_MEMORY_REQUESTS_BLOCKED(247),
	/// Number of range tombstones inserted by read-path conversion from contiguous
	/// point tombstones.
	READ_PATH_RANGE_TOMBSTONES_INSERTED(248),
	/// Number of range tombstones not inserted. Reasons include:
	/// - iterator's snapshot predates the memtable
	/// - tombstones may be uncommitted (transactions)
	/// - memtable already has a covering range tombstone
	/// - memtable switched to immutable state.
	READ_PATH_RANGE_TOMBSTONES_DISCARDED(249),
	/// Number of times file open metadata was retrieved from the file system
	/// via GetFileOpenMetadata() (when fast_sst_open is enabled).
	FILE_OPEN_METADATA_RETRIEVED(250),
	/// Number of times file open metadata was passed to NewRandomAccessFile()
	/// via FileOptions::file_metadata (on DB open / table cache miss).
	FILE_OPEN_METADATA_PASSED(251),
	/// Number of times MANIFEST content validation detected corruption on DB close.
	MANIFEST_VALIDATION_FAILURE_COUNT(252),
	/// Number of flushes triggered because the memtable reached write_buffer_size.
	FLUSH_REASON_WRITE_BUFFER_FULL(253),
	/// Number of flushes triggered by WriteBufferManager memory pressure.
	FLUSH_REASON_WRITE_BUFFER_MANAGER(254),
	/// Number of flushes triggered because the memtable reached
	/// memtable_max_range_deletions.
	FLUSH_REASON_MEMTABLE_MAX_RANGE_DELETIONS(255),
	/// Number of atomic flush requests triggered because a memtable reached
	/// write_buffer_size.
	ATOMIC_FLUSH_REQUEST_REASON_WRITE_BUFFER_FULL(256),
	/// Number of atomic flush requests triggered by WriteBufferManager memory
	/// pressure.
	ATOMIC_FLUSH_REQUEST_REASON_WRITE_BUFFER_MANAGER(257),
	/// Number of atomic flush requests triggered because a memtable reached
	/// memtable_max_range_deletions.
	ATOMIC_FLUSH_REQUEST_REASON_MEMTABLE_MAX_RANGE_DELETIONS(258),
	/// Number of atomic flush requests triggered for reasons that do not have a
	/// dedicated atomic flush request reason ticker.
	ATOMIC_FLUSH_REQUEST_REASON_OTHER(259),
	/// Bytes read while creating checkpoints.
	CHECKPOINT_READ_BYTES(260),
	/// Bytes written while creating checkpoints.
	CHECKPOINT_WRITE_BYTES(261),
	/// Number of SubmitReadAsync calls that fell back to a synchronous read.
	FILE_SUBMIT_ASYNC_READ_FALLBACK(262),
	/// Number of storage reads issued by lazy wide-column resolution
	/// (DB::GetEntityLazy / DB::MultiGetEntityLazy), both whole-column and partial
	/// byte-range reads. Cache hits, which do no storage read, are not counted.
	BLOB_DB_LAZY_READ_COUNT(263),
	/// Bytes read from storage by lazy resolution (on-disk record bytes for
	/// whole-column reads; the requested sub-range length for partial reads).
	BLOB_DB_LAZY_READ_BYTES(264),
	/// Lazy-resolution reads that were partial (byte-range) reads: only
	/// a requested sub-range of an uncompressed blob's value was read (skipping
	/// the whole-column read). A whole-column read or a force_verify read is not
	/// counted here.
	BLOB_DB_LAZY_PARTIAL_READ_COUNT(265),
	/// Bytes not read thanks to partial lazy-resolution reads: for each, the column's
	/// logical value size minus the bytes actually read.
	BLOB_DB_LAZY_PARTIAL_BYTES_SAVED(266),
	;

	private final int value;

	TickerType(int value) {
		this.value = value;
	}

	// don't expose this
	int getValue() {
		return value;
	}
}
