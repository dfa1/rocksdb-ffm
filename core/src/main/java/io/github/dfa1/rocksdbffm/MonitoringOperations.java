package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// Shared read-only inspection operations for every wrapper around a plain `rocksdb_t*`,
/// including [TransactionDB] (reached through its base DB pointer, see
/// [NativeObjectWithBaseDb#dbPtr()]). Implemented directly, alongside [RocksDBReadOperations],
/// by every one of that interface's types (`ReadWriteDB`, `TtlDB`, `BlobDB`, `ReadOnlyDB`,
/// `SecondaryDB`, `OptimisticTransactionDB`); implemented on its own by `TransactionDB`, which
/// implements neither `RocksDBReadOperations` nor `RocksDBWriteOperations`.
///
/// Currently just live SST file metadata ([#getLiveFiles()]) — the home for future inspection
/// surfaces that only need `rocksdb_t*`, such as a `rocksdb_get_livefiles_storage_info`-based
/// wrapper covering WAL/MANIFEST/CURRENT files too, not just SST.
public interface MonitoringOperations {

	/// Returns the native `rocksdb_t*` pointer to operate on.
	///
	/// @return the native database pointer
	MemorySegment dbPtr();

	/// Captures metadata for every live SST file currently belonging to this database —
	/// column family, level, size, key range, sequence number range, and entry/deletion
	/// counts. Fields are read from native memory lazily, one native call per field actually
	/// accessed, so scanning a single field across many files does not pay for the rest.
	///
	/// @return a new [LiveFiles] snapshot; caller must close it
	default LiveFiles getLiveFiles() {
		return LiveFiles.fetch(dbPtr());
	}
}
