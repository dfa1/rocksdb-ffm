package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.util.List;

/// Shared read-only inspection operations for every wrapper around a plain `rocksdb_t*`,
/// including [TransactionDB] (reached through its base DB pointer, see
/// [NativeObjectWithBaseDb#dbPtr()]). Implemented directly, alongside [RocksDBReadOperations],
/// by every one of that interface's types (`ReadWriteDB`, `TtlDB`, `BlobDB`, `ReadOnlyDB`,
/// `SecondaryDB`, `OptimisticTransactionDB`); implemented on its own by `TransactionDB`, which
/// implements neither `RocksDBReadOperations` nor `RocksDBWriteOperations`.
///
/// Live SST file metadata ([#getLiveFiles()]) and full database storage inventory
/// ([#getLiveFilesStorageInfo()]) — SST, WAL, MANIFEST, `CURRENT`, `OPTIONS`, blob files, and
/// more, everything needed to reconstruct the database, not just SST. [#getApproximateSizes(List)]
/// estimates the on-disk footprint of one or more key ranges without scanning them.
public interface RocksDBMonitoringOperations {

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

	/// Estimates the on-disk size, in bytes, of each range in `ranges`, on the default column
	/// family, without scanning the data. RocksDB defaults apply: on-disk SST data only
	/// (memtables and blob files excluded), computed exactly rather than with an error margin.
	///
	/// @param ranges key ranges to estimate; one entry per range
	/// @return estimated size in bytes for each range, in the same order as `ranges`
	default long[] getApproximateSizes(List<Range> ranges) {
		return RocksDB.approximateSizes(dbPtr(), ranges);
	}

	/// [#getApproximateSizes(List)] with explicit [SizeApproximationOptions] controlling what
	/// data counts toward the estimate and how precise it needs to be.
	///
	/// @param options controls which data is counted and the allowed error margin
	/// @param ranges  key ranges to estimate; one entry per range
	/// @return estimated size in bytes for each range, in the same order as `ranges`
	default long[] getApproximateSizes(SizeApproximationOptions options, List<Range> ranges) {
		return RocksDB.approximateSizesWithOptions(dbPtr(), options, ranges);
	}

	/// [#getApproximateSizes(List)] scoped to a specific column family instead of the default one.
	///
	/// @param cf     target column family
	/// @param ranges key ranges to estimate; one entry per range
	/// @return estimated size in bytes for each range, in the same order as `ranges`
	default long[] getApproximateSizes(ColumnFamilyHandle cf, List<Range> ranges) {
		return RocksDB.approximateSizesCf(dbPtr(), cf, ranges);
	}

	/// [#getApproximateSizes(ColumnFamilyHandle, List)] with explicit [SizeApproximationOptions].
	///
	/// @param cf      target column family
	/// @param options controls which data is counted and the allowed error margin
	/// @param ranges  key ranges to estimate; one entry per range
	/// @return estimated size in bytes for each range, in the same order as `ranges`
	default long[] getApproximateSizes(ColumnFamilyHandle cf, SizeApproximationOptions options, List<Range> ranges) {
		return RocksDB.approximateSizesCfWithOptions(dbPtr(), cf, options, ranges);
	}
}
