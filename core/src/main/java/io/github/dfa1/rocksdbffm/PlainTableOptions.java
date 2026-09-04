package io.github.dfa1.rocksdbffm;

/// Configuration for PlainTable, an SST format backed by an in-memory hash-table index over key
/// prefixes instead of the default binary-searched block index. Good for fixed-size-key,
/// read-heavy, in-memory-resident workloads dominated by point lookups; poor at range scans
/// (falls back to a linear scan) and does not support block compression. Requires
/// [Options#setPrefixExtractor] to build its hash buckets, the same mechanism
/// [Options#setHashSkipListMemTableFactory]/[Options#setHashLinkListMemTableFactory] use for the
/// memtable side of the same idea.
///
/// Unlike [BlockBasedTableOptions]/[CuckooTableOptions], PlainTable has no dedicated opaque type
/// in `rocksdb/c.h` -- `rocksdb_options_set_plain_table_factory` takes its configuration as eight
/// scalar arguments directly, with no `rocksdb_plain_table_options_t*` to create or destroy. This
/// class is a plain, non-native value holder mirroring those scalars: there is nothing to close.
///
/// ```
/// try (var opts = Options.newOptions()
///         .setPrefixExtractor(SliceTransform.newFixedPrefix(4))
///         .setTableFormatConfig(PlainTableOptions.newPlainTableOptions()
///                 .setEncodingType(PlainTableOptions.EncodingType.PREFIX))) {
///     RocksDB db = RocksDB.openReadWrite(opts, path);
/// }
/// ```
public final class PlainTableOptions {

	/// How PlainTable encodes keys when writing a new SST file, per `c.h`'s `char encoding_type`
	/// parameter (backed by C++'s `EncodingType`).
	public enum EncodingType {
		/// Always writes full keys, without any special encoding.
		PLAIN(0),
		/// Shares a common prefix across consecutive rows instead of repeating it, using
		/// [Options#setPrefixExtractor] to determine the prefix. The prefix extractor's name is
		/// stored in the file and bitwise-compared against the one configured when reopening.
		PREFIX(1);

		private final int value;

		EncodingType(int value) {
			this.value = value;
		}

		int getValue() {
			return value;
		}
	}

	/// Sentinel for [#setUserKeyLength]: keys have variable length (the default).
	public static final int VARIABLE_LENGTH = 0;

	private int userKeyLength = VARIABLE_LENGTH;
	private int bloomBitsPerKey = 10;
	private double hashTableRatio = 0.75;
	private long indexSparseness = 16;
	private long hugePageTlbSize = 0;
	private EncodingType encodingType = EncodingType.PLAIN;
	private boolean fullScanMode = false;
	private boolean storeIndexInFile = false;

	private PlainTableOptions() {
	}

	/// Creates [PlainTableOptions] with RocksDB defaults.
	///
	/// @return a new instance with default values
	public static PlainTableOptions newPlainTableOptions() {
		return new PlainTableOptions();
	}

	/// Fixed key length PlainTable optimizes for, or [#VARIABLE_LENGTH] if keys vary in size.
	/// Default: [#VARIABLE_LENGTH].
	///
	/// @param userKeyLength fixed key length, or [#VARIABLE_LENGTH]
	/// @return `this` for chaining
	public PlainTableOptions setUserKeyLength(int userKeyLength) {
		this.userKeyLength = userKeyLength;
		return this;
	}

	/// Returns the configured fixed key length.
	///
	/// @return current fixed key length, or [#VARIABLE_LENGTH]
	public int getUserKeyLength() {
		return userKeyLength;
	}

	/// Number of bits used for the bloom filter per prefix. `0` disables the filter.
	/// Default: 10.
	///
	/// @param bloomBitsPerKey bloom filter bits per prefix, or `0` to disable
	/// @return `this` for chaining
	public PlainTableOptions setBloomBitsPerKey(int bloomBitsPerKey) {
		this.bloomBitsPerKey = bloomBitsPerKey;
		return this;
	}

	/// Returns the configured bloom filter bits per prefix.
	///
	/// @return current bloom filter bits per prefix
	public int getBloomBitsPerKey() {
		return bloomBitsPerKey;
	}

	/// Desired utilization of the hash table used for prefix hashing, expressed as
	/// `number of prefixes / number of buckets`. Default: 0.75.
	///
	/// @param hashTableRatio desired hash table utilization ratio
	/// @return `this` for chaining
	public PlainTableOptions setHashTableRatio(double hashTableRatio) {
		this.hashTableRatio = hashTableRatio;
		return this;
	}

	/// Returns the configured hash table utilization ratio.
	///
	/// @return current hash table utilization ratio
	public double getHashTableRatio() {
		return hashTableRatio;
	}

	/// Number of keys per hash bucket between binary-search index records. Default: 16.
	///
	/// @param indexSparseness keys per hash bucket between index records
	/// @return `this` for chaining
	public PlainTableOptions setIndexSparseness(long indexSparseness) {
		this.indexSparseness = indexSparseness;
		return this;
	}

	/// Returns the configured index sparseness.
	///
	/// @return current keys-per-index-record value
	public long getIndexSparseness() {
		return indexSparseness;
	}

	/// Size of the huge-page TLB to allocate hash indexes and bloom filters from. `0`
	/// allocates from regular `malloc` instead. Requires the OS to have huge pages reserved
	/// (e.g. `sysctl -w vm.nr_hugepages=20` on Linux). Default: 0 (disabled).
	///
	/// @param hugePageTlbSize huge-page TLB byte size, or `0` to disable
	/// @return `this` for chaining
	public PlainTableOptions setHugePageTlbSize(long hugePageTlbSize) {
		this.hugePageTlbSize = hugePageTlbSize;
		return this;
	}

	/// Returns the configured huge-page TLB size.
	///
	/// @return current huge-page TLB byte size, or `0` if disabled
	public long getHugePageTlbSize() {
		return hugePageTlbSize;
	}

	/// How keys are encoded when writing a new SST file. Default: [EncodingType#PLAIN].
	///
	/// @param encodingType the key encoding strategy to use
	/// @return `this` for chaining
	public PlainTableOptions setEncodingType(EncodingType encodingType) {
		this.encodingType = encodingType;
		return this;
	}

	/// Returns the configured key encoding strategy.
	///
	/// @return current key encoding strategy
	public EncodingType getEncodingType() {
		return encodingType;
	}

	/// If `true`, reads scan the whole file record by record instead of using the index.
	/// Default: `false`.
	///
	/// @param fullScanMode `true` to read without using the index
	/// @return `this` for chaining
	public PlainTableOptions setFullScanMode(boolean fullScanMode) {
		this.fullScanMode = fullScanMode;
		return this;
	}

	/// Returns whether full-scan mode is enabled.
	///
	/// @return `true` if reads scan the whole file without using the index
	public boolean isFullScanMode() {
		return fullScanMode;
	}

	/// If `true`, computes the hash index and bloom filter during file building and stores
	/// them in the file, so reads can map them in instead of recomputing. Default: `false`.
	///
	/// @param storeIndexInFile `true` to store the computed index/filter in the file
	/// @return `this` for chaining
	public PlainTableOptions setStoreIndexInFile(boolean storeIndexInFile) {
		this.storeIndexInFile = storeIndexInFile;
		return this;
	}

	/// Returns whether the computed index/filter is stored in the file.
	///
	/// @return `true` if the computed index/filter is stored in the file
	public boolean isStoreIndexInFile() {
		return storeIndexInFile;
	}
}
