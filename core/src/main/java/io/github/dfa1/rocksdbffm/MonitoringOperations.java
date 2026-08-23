package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// Shared read-only inspection operations for every wrapper around a plain `rocksdb_t*`,
/// including [TransactionDB] (reached through its base DB pointer, see
/// [NativeObjectWithBaseDb#dbPtr()]). Implemented directly, alongside [RocksDBReadOperations],
/// by every one of that interface's types (`ReadWriteDB`, `TtlDB`, `BlobDB`, `ReadOnlyDB`,
/// `SecondaryDB`, `OptimisticTransactionDB`); implemented on its own by `TransactionDB`, which
/// implements neither `RocksDBReadOperations` nor `RocksDBWriteOperations`.
///
/// Live SST file metadata ([#getLiveFiles()]) and full database storage inventory
/// ([#getLiveFilesStorageInfo()]) — SST, WAL, MANIFEST, `CURRENT`, `OPTIONS`, blob files, and
/// more, everything needed to reconstruct the database, not just SST.
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

	/// [#getLiveFilesStorageInfo(LiveFilesStorageInfoOptions)] with RocksDB's defaults: no
	/// checksums, and a memtable flush is always forced first.
	///
	/// @return a new [LiveFilesStorageInfo] snapshot; caller must close it
	default LiveFilesStorageInfo getLiveFilesStorageInfo() {
		return LiveFilesStorageInfo.fetch(dbPtr(), MemorySegment.NULL);
	}

	/// Captures every file needed to reconstruct this database — SST, WAL, MANIFEST,
	/// `CURRENT`, `OPTIONS`, blob files, and more — not just the SST-only view
	/// [#getLiveFiles()] gives. Fields are read from native memory lazily, the same as
	/// [#getLiveFiles()].
	///
	/// @param options controls checksum computation and the flush performed before capturing
	/// @return a new [LiveFilesStorageInfo] snapshot; caller must close it
	default LiveFilesStorageInfo getLiveFilesStorageInfo(LiveFilesStorageInfoOptions options) {
		return LiveFilesStorageInfo.fetch(dbPtr(), options.ptr());
	}
}
