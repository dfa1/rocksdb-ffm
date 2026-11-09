package io.github.dfa1.rocksdbffm;

/// Histogram types for RocksDB statistics.
/// Corresponds to `enum Histograms` in `rocksdb/statistics.h`.
public enum HistogramType {
	/// Latency histogram for DB get operations.
	DB_GET(0),
	/// Latency histogram for DB write operations.
	DB_WRITE(1),
	/// Time spent in compaction.
	COMPACTION_TIME(2),
	/// CPU time spent in compaction.
	COMPACTION_CPU_TIME(3),
	/// Time to set up subcompactions.
	SUBCOMPACTION_SETUP_TIME(4),
	/// Microseconds to sync table files.
	TABLE_SYNC_MICROS(5),
	/// Microseconds to sync compaction output files.
	COMPACTION_OUTFILE_SYNC_MICROS(6),
	/// Microseconds to sync the WAL file.
	WAL_FILE_SYNC_MICROS(7),
	/// Microseconds to sync the MANIFEST file.
	MANIFEST_FILE_SYNC_MICROS(8),
	/// Microseconds to open a table file (I/O).
	TABLE_OPEN_IO_MICROS(9),
	/// Latency histogram for DB multiget operations.
	DB_MULTIGET(10),
	/// Microseconds to read a block during compaction.
	READ_BLOCK_COMPACTION_MICROS(11),
	/// Microseconds to read a block during a get.
	READ_BLOCK_GET_MICROS(12),
	/// Microseconds to write a raw block.
	WRITE_RAW_BLOCK_MICROS(13),
	/// Number of files in a single compaction.
	NUM_FILES_IN_SINGLE_COMPACTION(14),
	/// Latency histogram for DB seek operations.
	DB_SEEK(15),
	/// Time spent in write stall.
	WRITE_STALL(16),
	/// Microseconds to read an SST file.
	SST_READ_MICROS(17),
	/// Microseconds to read a file during flush.
	FILE_READ_FLUSH_MICROS(18),
	/// Microseconds to read a file during compaction.
	FILE_READ_COMPACTION_MICROS(19),
	/// Microseconds to read a file during DB open.
	FILE_READ_DB_OPEN_MICROS(20),
	/// Microseconds to read a file during get.
	FILE_READ_GET_MICROS(21),
	/// Microseconds to read a file during multiget.
	FILE_READ_MULTIGET_MICROS(22),
	/// Microseconds to read a file during iterator operations.
	FILE_READ_DB_ITERATOR_MICROS(23),
	/// Microseconds to read a file during DB checksum verification.
	FILE_READ_VERIFY_DB_CHECKSUM_MICROS(24),
	/// Microseconds to read a file during file checksum verification.
	FILE_READ_VERIFY_FILE_CHECKSUMS_MICROS(25),
	/// Microseconds to write an SST file.
	SST_WRITE_MICROS(26),
	/// Microseconds to write a file during flush.
	FILE_WRITE_FLUSH_MICROS(27),
	/// Microseconds to write a file during compaction.
	FILE_WRITE_COMPACTION_MICROS(28),
	/// Microseconds to write a file during DB open.
	FILE_WRITE_DB_OPEN_MICROS(29),
	/// Number of subcompactions scheduled.
	NUM_SUBCOMPACTIONS_SCHEDULED(30),
	/// Bytes read per read operation.
	BYTES_PER_READ(31),
	/// Bytes written per write operation.
	BYTES_PER_WRITE(32),
	/// Bytes read per multiget operation.
	BYTES_PER_MULTIGET(33),
	/// Nanoseconds spent compressing data.
	COMPRESSION_TIMES_NANOS(34),
	/// Nanoseconds spent decompressing data.
	DECOMPRESSION_TIMES_NANOS(35),
	/// Number of merge operands read.
	READ_NUM_MERGE_OPERANDS(36),
	/// Blob DB key sizes.
	BLOB_DB_KEY_SIZE(37),
	/// Blob DB value sizes.
	BLOB_DB_VALUE_SIZE(38),
	/// Microseconds for Blob DB write operations.
	BLOB_DB_WRITE_MICROS(39),
	/// Microseconds for Blob DB get operations.
	BLOB_DB_GET_MICROS(40),
	/// Microseconds for Blob DB multiget operations.
	BLOB_DB_MULTIGET_MICROS(41),
	/// Microseconds for Blob DB seek operations.
	BLOB_DB_SEEK_MICROS(42),
	/// Microseconds for Blob DB next operations.
	BLOB_DB_NEXT_MICROS(43),
	/// Microseconds for Blob DB prev operations.
	BLOB_DB_PREV_MICROS(44),
	/// Microseconds to write to a blob file.
	BLOB_DB_BLOB_FILE_WRITE_MICROS(45),
	/// Microseconds to read from a blob file.
	BLOB_DB_BLOB_FILE_READ_MICROS(46),
	/// Microseconds to sync a blob file.
	BLOB_DB_BLOB_FILE_SYNC_MICROS(47),
	/// Microseconds spent compressing blob data.
	BLOB_DB_COMPRESSION_MICROS(48),
	/// Microseconds spent decompressing blob data.
	BLOB_DB_DECOMPRESSION_MICROS(49),
	/// Time spent flushing memtables.
	FLUSH_TIME(50),
	/// SST batch sizes.
	SST_BATCH_SIZE(51),
	/// I/O batch sizes during multiget.
	MULTIGET_IO_BATCH_SIZE(52),
	/// Number of index and filter blocks read per level.
	NUM_INDEX_AND_FILTER_BLOCKS_READ_PER_LEVEL(53),
	/// Number of SST files read per level.
	NUM_SST_READ_PER_LEVEL(54),
	/// Number of levels read per multiget.
	NUM_LEVEL_READ_PER_MULTIGET(55),
	/// Retry count for auto-resume after error.
	ERROR_HANDLER_AUTORESUME_RETRY_COUNT(56),
	/// Bytes read asynchronously.
	ASYNC_READ_BYTES(57),
	/// Microseconds waiting in poll.
	POLL_WAIT_MICROS(58),
	/// Bytes prefetched during compaction.
	COMPACTION_PREFETCH_BYTES(59),
	/// Prefetched bytes that were discarded.
	PREFETCHED_BYTES_DISCARDED(60),
	/// Microseconds until an async prefetch was aborted.
	ASYNC_PREFETCH_ABORT_MICROS(61),
	/// Bytes read to prefetch the tail of a table during open.
	TABLE_OPEN_PREFETCH_TAIL_READ_BYTES(62),
	/// Number of operations per transaction.
	NUM_OP_PER_TRANSACTION(63),
	/// Number of iterators prepared for multiscan.
	MULTISCAN_PREPARE_ITERATORS(64),
	/// Total Prepare() latency for MultiScan.
	MULTISCAN_PREPARE_MICROS(65),
	/// Distribution of blocks prefetched per MultiScan Prepare().
	MULTISCAN_BLOCKS_PER_PREPARE(66),
	/// Time (microseconds) from IODispatcher async read submission until
	/// completion callback execution.
	IO_DISPATCHER_ASYNC_READ_OBSERVED_COMPLETION_MICROS(67),
	/// Time (microseconds) IODispatcher spends waiting in FileSystem::Poll().
	IO_DISPATCHER_ASYNC_READ_POLL_WAIT_MICROS(68),
	/// Time (microseconds) from async read submission until the block consumer
	/// starts polling for completion.
	IO_DISPATCHER_ASYNC_READ_PREFETCH_LEAD_MICROS(69),
	/// Coefficient of variation of key gaps in blocks, scaled by 10000
	/// (e.g., CV of 0.4532 is recorded as 4532). Currently only used by index
	/// blocks for uniform key distribution tracking.
	BLOCK_KEY_DISTRIBUTION_CV(70),
	/// Time (microseconds) in the "prepare" phase of an IngestExternalFile(s) call
	/// (validation and reading/linking the files; does not block writes). One
	/// sample per successful call; requires stats level > kExceptTimers.
	INGEST_EXTERNAL_FILE_PREPARE_TIME(71),
	/// Time (microseconds) in the "run" phase of an IngestExternalFile(s) call
	/// (work under the DB mutex while writes are blocked). One sample per
	/// successful call; requires stats level > kExceptTimers.
	INGEST_EXTERNAL_FILE_RUN_TIME(72),
	/// Time read requests spent waiting for rate limiter refills.
	RATE_LIMITER_WAIT_MICROS_READ(73),
	/// Time write requests spent waiting for rate limiter refills.
	RATE_LIMITER_WAIT_MICROS_WRITE(74),
	/// Distribution of total memtable memory usage at flush start.
	FLUSH_MEMTABLE_MEMORY_BYTES(75),
	/// Distribution of total memtable data size at flush start.
	FLUSH_MEMTABLE_TOTAL_DATA_SIZE(76),
	/// Distribution of total memtable memory usage for write-buffer-full flushes.
	FLUSH_WRITE_BUFFER_FULL_MEMTABLE_MEMORY_BYTES(77),
	/// Distribution of total memtable memory usage for WBM-triggered flushes.
	FLUSH_WRITE_BUFFER_MANAGER_MEMTABLE_MEMORY_BYTES(78),
	/// Time spent opening the secondary DB inside DB::OpenAndCompact().
	OPEN_AND_COMPACT_DB_OPEN_MICROS(79),
	;

	private final int value;

	HistogramType(int value) {
		this.value = value;
	}

	// don't expose the raw value
	int getValue() {
		return value;
	}
}
