package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/// Shared compaction-control operations for every wrapper around a plain `rocksdb_t*`, including
/// [TransactionDB] (reached through its base DB pointer, see
/// [NativeObjectWithBaseDb#dbPtr()]). Implemented alongside [RocksDBWriteOperations] by
/// `ReadWriteDB`/`TtlDB`/`BlobDB`/`OptimisticTransactionDB`.
///
/// A separate interface rather than folded into [RocksDBWriteOperations]: compaction control
/// operates on the plain `rocksdb_t*` the same way tracing and monitoring do, and factoring it
/// out lets [TransactionDB] implement it too via its base-db pointer -- before this interface
/// existed, `TransactionDB` had no compaction control at all, even though nothing in the C API
/// stopped it (`rocksdb_transactiondb_get_base_db()` gives it the same plain `rocksdb_t*` handle
/// `getProperty`/`getLiveFiles()` already call through). See
/// [#131](https://github.com/dfa1/rocksdbffm/issues/131).
///
/// Every method here is a direct, zero-logic forward into the matching package-private
/// [RocksDBCompactionOperationsBindings] helper — implementors only need to supply the native pointer.
public interface RocksDBCompactionOperations {

	/// Returns the native `rocksdb_t*` pointer to operate on.
	///
	/// @return the native database pointer
	MemorySegment dbPtr();

	/// Manually triggers compaction over the entire key space.
	default void compactRange() {
		RocksDBCompactionOperationsBindings.compactRangeBytes(this, null, null);
	}

	/// Manually triggers compaction over `[startKey, endKey]`.
	/// Pass `null` for either bound to indicate the beginning/end of the key space.
	///
	/// @param startKey inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   inclusive upper bound, or `null` for the end of the key space
	default void compactRange(byte[] startKey, byte[] endKey) {
		RocksDBCompactionOperationsBindings.compactRangeBytes(this, startKey, endKey);
	}

	/// [ByteBuffer] overload of [#compactRange(byte\[\], byte\[\])].
	///
	/// @param startKey direct [ByteBuffer] with inclusive lower bound
	/// @param endKey   direct [ByteBuffer] with inclusive upper bound
	default void compactRange(ByteBuffer startKey, ByteBuffer endKey) {
		RocksDBCompactionOperationsBindings.compactRangeBuffer(this, startKey, endKey);
	}

	/// [MemorySegment] overload of [#compactRange(byte\[\], byte\[\])].
	///
	/// @param startKey native segment with inclusive lower bound
	/// @param endKey   native segment with inclusive upper bound
	default void compactRange(MemorySegment startKey, MemorySegment endKey) {
		RocksDBCompactionOperationsBindings.compactRangeSegment(this, startKey, endKey);
	}

	/// Compaction with explicit options.
	///
	/// @param opts     compaction options
	/// @param startKey inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   inclusive upper bound, or `null` for the end of the key space
	default void compactRange(CompactOptions opts, byte[] startKey, byte[] endKey) {
		RocksDBCompactionOperationsBindings.compactRangeOptBytes(this, opts, startKey, endKey);
	}

	/// Deletes whole SST files that fall entirely within `[startKey, endKey]` from the default
	/// column family. Unlike [#compactRange(byte\[\], byte\[\])], nothing is rewritten: a file that
	/// only partly overlaps the range is kept. A fast way to drop a large key range (e.g. a tenant)
	/// when exactness isn't required. Pass `null` for either bound to indicate the beginning/end of
	/// the key space.
	///
	/// @param startKey inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   inclusive upper bound, or `null` for the end of the key space
	/// @throws RocksDBException on I/O error
	default void deleteFilesInRange(byte[] startKey, byte[] endKey) {
		RocksDBCompactionOperationsBindings.deleteFileInRangeBytes(this, startKey, endKey);
	}

	/// [ByteBuffer] overload of [#deleteFilesInRange(byte\[\], byte\[\])].
	///
	/// @param startKey direct [ByteBuffer] with inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   direct [ByteBuffer] with inclusive upper bound, or `null` for the end of the key space
	/// @throws RocksDBException on I/O error
	default void deleteFilesInRange(ByteBuffer startKey, ByteBuffer endKey) {
		RocksDBCompactionOperationsBindings.deleteFileInRangeBuffer(this, startKey, endKey);
	}

	/// [MemorySegment] overload of [#deleteFilesInRange(byte\[\], byte\[\])].
	///
	/// @param startKey native segment with inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   native segment with inclusive upper bound, or `null` for the end of the key space
	/// @throws RocksDBException on I/O error
	default void deleteFilesInRange(MemorySegment startKey, MemorySegment endKey) {
		RocksDBCompactionOperationsBindings.deleteFileInRangeSegment(this, startKey, endKey);
	}

	/// Deletes whole SST files that fall entirely within `[startKey, endKey]` from the given column
	/// family. See [#deleteFilesInRange(byte\[\], byte\[\])].
	///
	/// @param cf       target column family
	/// @param startKey inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   inclusive upper bound, or `null` for the end of the key space
	/// @throws RocksDBException on I/O error
	default void deleteFilesInRange(ColumnFamilyHandle cf, byte[] startKey, byte[] endKey) {
		RocksDBCompactionOperationsBindings.deleteFileInRangeCfBytes(this, cf, startKey, endKey);
	}

	/// [ByteBuffer] overload of [#deleteFilesInRange(ColumnFamilyHandle, byte\[\], byte\[\])].
	///
	/// @param cf       target column family
	/// @param startKey direct [ByteBuffer] with inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   direct [ByteBuffer] with inclusive upper bound, or `null` for the end of the key space
	/// @throws RocksDBException on I/O error
	default void deleteFilesInRange(ColumnFamilyHandle cf, ByteBuffer startKey, ByteBuffer endKey) {
		RocksDBCompactionOperationsBindings.deleteFileInRangeCfBuffer(this, cf, startKey, endKey);
	}

	/// [MemorySegment] overload of [#deleteFilesInRange(ColumnFamilyHandle, byte\[\], byte\[\])].
	///
	/// @param cf       target column family
	/// @param startKey native segment with inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   native segment with inclusive upper bound, or `null` for the end of the key space
	/// @throws RocksDBException on I/O error
	default void deleteFilesInRange(ColumnFamilyHandle cf, MemorySegment startKey, MemorySegment endKey) {
		RocksDBCompactionOperationsBindings.deleteFileInRangeCfSegment(this, cf, startKey, endKey);
	}

	/// Hints that `[startKey, endKey]` may benefit from compaction, but does not block.
	///
	/// @param startKey inclusive lower bound
	/// @param endKey   inclusive upper bound
	default void suggestCompactRange(byte[] startKey, byte[] endKey) {
		RocksDBCompactionOperationsBindings.suggestCompactRangeBytes(this, startKey, endKey);
	}

	/// Prevents new SST files from being deleted. Must be paired with [#enableFileDeletions()].
	default void disableFileDeletions() {
		RocksDBCompactionOperationsBindings.disableFileDeletions(this);
	}

	/// Re-enables SST file deletions after [#disableFileDeletions()].
	default void enableFileDeletions() {
		RocksDBCompactionOperationsBindings.enableFileDeletions(this);
	}

	/// Prevents new manual compactions from starting.
	/// In-progress manual compactions are not affected.
	/// Call [#enableManualCompaction()] to reverse.
	default void disableManualCompaction() {
		RocksDBCompactionOperationsBindings.disableManualCompaction(this);
	}

	/// Re-enables manual compactions after [#disableManualCompaction()].
	default void enableManualCompaction() {
		RocksDBCompactionOperationsBindings.enableManualCompaction(this);
	}

	/// Blocks until all current compactions finish, subject to the given [WaitForCompactOptions].
	///
	/// @param options  options controlling wait behavior (e.g. abort-on-pause)
	/// @throws RocksDBException on I/O error or if [WaitForCompactOptions#isAbortOnPause()] is
	///                          `true` and background work is paused
	default void waitForCompact(WaitForCompactOptions options) {
		RocksDBCompactionOperationsBindings.waitForCompact(this, options);
	}
}
