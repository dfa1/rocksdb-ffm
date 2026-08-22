package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

/// Shared write/administrative operations, including column-family-scoped overloads, for
/// every wrapper around a mutable plain `rocksdb_t*`: read-write, TTL, blob, and
/// optimistic-transaction instances all expose the identical surface and all support opening
/// or creating column families for themselves.
///
/// Deliberately does not extend [RocksDBReadOperations] — a class that is both readable
/// and writable (every current implementor) implements both interfaces directly, rather
/// than the write interface inheriting the read one. This keeps the two independently
/// reusable: [ReadOnlyDB] and [SecondaryDB] need a read interface with no write methods
/// at all.
///
/// Every method here is a direct, zero-logic forward into the matching package-private
/// `RocksDB` helper — implementors only need to supply the native pointer.
///
/// Not implemented by [TransactionDB] — see [RocksDBReadOperations] for why.
/// [OptimisticTransactionDB] implements it directly.
public interface RocksDBWriteOperations {

	/// Returns the native `rocksdb_t*` pointer to operate on. Redeclared from
	/// [RocksDBReadOperations#dbPtr()] since this interface does not extend it; every
	/// implementor also implements [RocksDBReadOperations] and provides a single override
	/// satisfying both.
	///
	/// @return the native database pointer
	MemorySegment dbPtr();

	// -----------------------------------------------------------------------
	// Put
	// -----------------------------------------------------------------------

	/// Stores `value` under `key`. Slow path: copies key/value into native memory.
	///
	/// @param key   the key to store
	/// @param value the value to associate with the key
	default void put(byte[] key, byte[] value) {
		RocksDB.putBytes(this, RocksDB.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// Stores `value` under `key` using the caller's [Arena] for native allocation.
	///
	/// @param arena arena used for temporary native allocations
	/// @param key   the key to store
	/// @param value the value to associate with the key
	default void put(Arena arena, byte[] key, byte[] value) {
		RocksDB.putBytes(arena, this, RocksDB.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// Zero-copy put: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the value
	default void put(ByteBuffer key, ByteBuffer value) {
		RocksDB.putSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// Zero-copy put: caller supplies pre-allocated native segments.
	///
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	default void put(MemorySegment key, MemorySegment value) {
		RocksDB.putSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// Zero-copy put using the caller's [Arena].
	///
	/// @param arena arena used for temporary native allocations
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	default void put(Arena arena, MemorySegment key, MemorySegment value) {
		RocksDB.putSegment(arena, this, RocksDB.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// Stores `value` under `key` in `cf`. Slow path: copies key/value into native memory.
	///
	/// @param cf    target column family
	/// @param key   the key to store
	/// @param value the value to associate with the key
	default void put(ColumnFamilyHandle cf, byte[] key, byte[] value) {
		RocksDB.putCfBytes(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, key, value);
	}

	/// Zero-copy put into `cf`: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param cf    target column family
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the value
	default void put(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		RocksDB.putCfSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// Zero-copy put into `cf`: caller supplies pre-allocated native segments.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	default void put(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		RocksDB.putCfSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, key, value);
	}

	// -----------------------------------------------------------------------
	// Merge
	// -----------------------------------------------------------------------

	/// Merges `value` into `key` via the configured merge operator. Slow path: copies key/value into
	/// native memory.
	///
	/// @param key   the key to merge into
	/// @param value the merge operand
	default void merge(byte[] key, byte[] value) {
		RocksDB.mergeBytes(this, RocksDB.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// Merges `value` into `key` using the caller's [Arena] for native allocation.
	///
	/// @param arena arena used for temporary native allocations
	/// @param key   the key to merge into
	/// @param value the merge operand
	default void merge(Arena arena, byte[] key, byte[] value) {
		RocksDB.mergeBytes(arena, this, RocksDB.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// Zero-copy merge: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the merge operand
	default void merge(ByteBuffer key, ByteBuffer value) {
		RocksDB.mergeSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// Zero-copy merge: caller supplies pre-allocated native segments.
	///
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	default void merge(MemorySegment key, MemorySegment value) {
		RocksDB.mergeSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// Zero-copy merge using the caller's [Arena].
	///
	/// @param arena arena used for temporary native allocations
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	default void merge(Arena arena, MemorySegment key, MemorySegment value) {
		RocksDB.mergeSegment(arena, this, RocksDB.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// Merges `value` into `key` in `cf`. Slow path: copies key/value into native memory.
	///
	/// @param cf    target column family
	/// @param key   the key to merge into
	/// @param value the merge operand
	default void merge(ColumnFamilyHandle cf, byte[] key, byte[] value) {
		RocksDB.mergeCfBytes(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, key, value);
	}

	/// Zero-copy merge into `cf`: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param cf    target column family
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the merge operand
	default void merge(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		RocksDB.mergeCfSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// Zero-copy merge into `cf`: caller supplies pre-allocated native segments.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	default void merge(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		RocksDB.mergeCfSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, key, value);
	}

	// -----------------------------------------------------------------------
	// Delete
	// -----------------------------------------------------------------------

	/// Removes `key` from the database. Slow path: copies the key into native memory.
	///
	/// @param key the key to remove
	default void delete(byte[] key) {
		RocksDB.deleteBytes(this, RocksDB.DEFAULT_WRITE_OPTIONS, key);
	}

	/// Zero-copy for direct [ByteBuffer]s.
	///
	/// @param key direct [ByteBuffer] containing the key to remove
	default void delete(ByteBuffer key) {
		RocksDB.deleteSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, MemorySegment.ofBuffer(key));
	}

	/// Zero-copy native-first path.
	///
	/// @param key native segment containing the key to remove
	default void delete(MemorySegment key) {
		RocksDB.deleteSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, key);
	}

	/// Removes `key` from `cf`. Slow path: copies the key into native memory.
	///
	/// @param cf  target column family
	/// @param key the key to remove
	default void delete(ColumnFamilyHandle cf, byte[] key) {
		RocksDB.deleteCfBytes(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, key);
	}

	/// Zero-copy delete from `cf` for direct [ByteBuffer]s.
	///
	/// @param cf  target column family
	/// @param key direct [ByteBuffer] containing the key to remove
	default void delete(ColumnFamilyHandle cf, ByteBuffer key) {
		RocksDB.deleteCfSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf,
				MemorySegment.ofBuffer(key));
	}

	/// Zero-copy delete from `cf` for [MemorySegment]s.
	///
	/// @param cf  target column family
	/// @param key native segment containing the key to remove
	default void delete(ColumnFamilyHandle cf, MemorySegment key) {
		RocksDB.deleteCfSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, key);
	}

	// -----------------------------------------------------------------------
	// DeleteRange
	// -----------------------------------------------------------------------

	/// Deletes all keys in the half-open range [`startKey`, `endKey`).
	/// Slow path: copies keys into native memory.
	///
	/// @param startKey inclusive lower bound
	/// @param endKey   exclusive upper bound
	default void deleteRange(byte[] startKey, byte[] endKey) {
		RocksDB.deleteRangeCfBytes(this, RocksDB.DEFAULT_WRITE_OPTIONS, startKey, endKey);
	}

	/// Zero-copy for direct [ByteBuffer]s.
	///
	/// @param startKey direct [ByteBuffer] with inclusive lower bound
	/// @param endKey   direct [ByteBuffer] with exclusive upper bound
	default void deleteRange(ByteBuffer startKey, ByteBuffer endKey) {
		RocksDB.deleteRangeCfBuffer(this, RocksDB.DEFAULT_WRITE_OPTIONS, startKey, endKey);
	}

	/// Zero-copy native-first path.
	///
	/// @param startKey native segment with inclusive lower bound
	/// @param endKey   native segment with exclusive upper bound
	default void deleteRange(MemorySegment startKey, MemorySegment endKey) {
		RocksDB.deleteRangeCfSegment(this, RocksDB.DEFAULT_WRITE_OPTIONS, startKey, endKey);
	}

	/// Deletes all keys in the half-open range [`startKey`, `endKey`) from `cf`. Slow path.
	///
	/// @param cf       target column family
	/// @param startKey inclusive lower bound
	/// @param endKey   exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, byte[] startKey, byte[] endKey) {
		RocksDB.deleteRangeCfBytesExplicit(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, startKey, endKey);
	}

	/// Zero-copy deleteRange from `cf` for direct [ByteBuffer]s.
	///
	/// @param cf       target column family
	/// @param startKey direct [ByteBuffer] with inclusive lower bound
	/// @param endKey   direct [ByteBuffer] with exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, ByteBuffer startKey, ByteBuffer endKey) {
		RocksDB.deleteRangeCfBufferExplicit(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, startKey, endKey);
	}

	/// Zero-copy deleteRange from `cf` for [MemorySegment]s.
	///
	/// @param cf       target column family
	/// @param startKey native segment with inclusive lower bound
	/// @param endKey   native segment with exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, MemorySegment startKey, MemorySegment endKey) {
		RocksDB.deleteRangeCfSegmentExplicit(this, RocksDB.DEFAULT_WRITE_OPTIONS, cf, startKey, endKey);
	}

	// -----------------------------------------------------------------------
	// Write (batch)
	// -----------------------------------------------------------------------

	/// Applies all mutations in `batch` atomically to the database.
	///
	/// @param batch the write batch to apply
	default void write(WriteBatch batch) {
		RocksDB.writeBatch(this, RocksDB.DEFAULT_WRITE_OPTIONS, batch);
	}

	/// Applies all mutations in `batch` atomically, using the caller's [Arena] for native allocation.
	///
	/// @param arena arena used for temporary native allocations
	/// @param batch the write batch to apply
	default void write(Arena arena, WriteBatch batch) {
		RocksDB.writeBatch(arena, this, RocksDB.DEFAULT_WRITE_OPTIONS, batch);
	}

	// -----------------------------------------------------------------------
	// Background jobs
	// -----------------------------------------------------------------------

	/// Cancels all background work (compaction, flush, etc.).
	///
	/// @param wait if `true`, blocks until all running jobs have finished
	default void cancelAllBackgroundWork(boolean wait) {
		RocksDB.cancelAllBackgroundWork(this, wait);
	}

	/// Prevents new manual compactions from starting.
	/// In-progress manual compactions are not affected.
	/// Call [#enableManualCompaction()] to reverse.
	default void disableManualCompaction() {
		RocksDB.disableManualCompaction(this);
	}

	/// Re-enables manual compactions after [#disableManualCompaction()].
	default void enableManualCompaction() {
		RocksDB.enableManualCompaction(this);
	}

	/// Blocks until all current compactions finish, subject to the given [WaitForCompactOptions].
	///
	/// @param options  options controlling wait behavior (e.g. abort-on-pause)
	/// @throws RocksDBException on I/O error or if [WaitForCompactOptions#isAbortOnPause()] is
	///                          `true` and background work is paused
	default void waitForCompact(WaitForCompactOptions options) {
		RocksDB.waitForCompact(this, options);
	}

	// -----------------------------------------------------------------------
	// WAL iteration
	// -----------------------------------------------------------------------

	/// Returns the sequence number of the most recent committed transaction.
	///
	/// @return current sequence number
	default SequenceNumber getLatestSequenceNumber() {
		return RocksDB.getLatestSequenceNumber(this);
	}

	/// Returns a [WalIterator] positioned at the first [WriteBatch] with a sequence number
	/// greater than or equal to `sequenceNumber`.
	///
	/// The caller must close the iterator after use.
	///
	/// @param sequenceNumber starting sequence number (inclusive)
	/// @return a new [WalIterator]; caller must close it
	default WalIterator getUpdatesSince(SequenceNumber sequenceNumber) {
		return RocksDB.getUpdatesSince(this, sequenceNumber);
	}

	// -----------------------------------------------------------------------
	// Flush
	// -----------------------------------------------------------------------

	/// Flushes all memtable data to SST files. Blocks when [FlushOptions#isWait()] is `true`.
	///
	/// @param flushOptions options controlling flush behavior
	default void flush(FlushOptions flushOptions) {
		RocksDB.flush(this, flushOptions);
	}

	/// Flushes the WAL to disk.
	///
	/// @param sync if `true`, performs an `fsync` after writing
	default void flushWal(boolean sync) {
		RocksDB.flushWal(this, sync);
	}

	/// Flushes the memtable for `cf` to SST files.
	///
	/// @param cf           target column family
	/// @param flushOptions options controlling flush behavior
	default void flush(ColumnFamilyHandle cf, FlushOptions flushOptions) {
		RocksDB.flushCf(this, flushOptions, cf);
	}

	// -----------------------------------------------------------------------
	// Compaction
	// -----------------------------------------------------------------------

	/// Manually triggers compaction over the entire key space.
	default void compactRange() {
		RocksDB.compactRangeBytes(this, null, null);
	}

	/// Manually triggers compaction over `[startKey, endKey]`.
	/// Pass `null` for either bound to indicate the beginning/end of the key space.
	///
	/// @param startKey inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   inclusive upper bound, or `null` for the end of the key space
	default void compactRange(byte[] startKey, byte[] endKey) {
		RocksDB.compactRangeBytes(this, startKey, endKey);
	}

	/// [ByteBuffer] overload of [#compactRange(byte\[\], byte\[\])].
	///
	/// @param startKey direct [ByteBuffer] with inclusive lower bound
	/// @param endKey   direct [ByteBuffer] with inclusive upper bound
	default void compactRange(ByteBuffer startKey, ByteBuffer endKey) {
		RocksDB.compactRangeBuffer(this, startKey, endKey);
	}

	/// [MemorySegment] overload of [#compactRange(byte\[\], byte\[\])].
	///
	/// @param startKey native segment with inclusive lower bound
	/// @param endKey   native segment with inclusive upper bound
	default void compactRange(MemorySegment startKey, MemorySegment endKey) {
		RocksDB.compactRangeSegment(this, startKey, endKey);
	}

	/// Compaction with explicit options.
	///
	/// @param opts     compaction options
	/// @param startKey inclusive lower bound, or `null` for the start of the key space
	/// @param endKey   inclusive upper bound, or `null` for the end of the key space
	default void compactRange(CompactOptions opts, byte[] startKey, byte[] endKey) {
		RocksDB.compactRangeOptBytes(this, opts, startKey, endKey);
	}

	/// Hints that `[startKey, endKey]` may benefit from compaction, but does not block.
	///
	/// @param startKey inclusive lower bound
	/// @param endKey   inclusive upper bound
	default void suggestCompactRange(byte[] startKey, byte[] endKey) {
		RocksDB.suggestCompactRangeBytes(this, startKey, endKey);
	}

	// -----------------------------------------------------------------------
	// File deletions
	// -----------------------------------------------------------------------

	/// Prevents new SST files from being deleted. Must be paired with [#enableFileDeletions()].
	default void disableFileDeletions() {
		RocksDB.disableFileDeletions(this);
	}

	/// Re-enables SST file deletions after [#disableFileDeletions()].
	default void enableFileDeletions() {
		RocksDB.enableFileDeletions(this);
	}

	// -----------------------------------------------------------------------
	// SST File Ingest
	// -----------------------------------------------------------------------

	/// Ingests SST files produced by [SstFileWriter] into the database.
	///
	/// @param files   list of SST file paths to ingest
	/// @param options ingest options controlling move vs copy, error handling, etc.
	default void ingestExternalFile(List<Path> files, IngestExternalFileOptions options) {
		RocksDB.ingestExternalFile(this, files, options);
	}

	/// Ingests `files` using default [IngestExternalFileOptions].
	///
	/// @param files list of SST file paths to ingest
	default void ingestExternalFile(List<Path> files) {
		RocksDB.ingestExternalFileWithDefaults(this, files);
	}

	/// Convenience overload for ingesting a single file with explicit options.
	///
	/// @param file    SST file path to ingest
	/// @param options ingest options controlling move vs copy, error handling, etc.
	default void ingestExternalFile(Path file, IngestExternalFileOptions options) {
		ingestExternalFile(List.of(file), options);
	}

	/// Convenience overload for ingesting a single file with default options.
	///
	/// @param file SST file path to ingest
	default void ingestExternalFile(Path file) {
		ingestExternalFile(List.of(file));
	}

	// -----------------------------------------------------------------------
	// Column family management
	// -----------------------------------------------------------------------

	/// Creates a new column family described by `descriptor` and returns its handle.
	/// The caller must close the returned handle when done.
	///
	/// @param descriptor name and options for the new column family
	/// @return handle to the newly created column family; caller must close it
	default ColumnFamilyHandle createColumnFamily(ColumnFamilyDescriptor descriptor) {
		return RocksDB.createCf(this, descriptor);
	}

	/// Drops the column family identified by `handle`.
	/// The handle should be closed after this call; it is no longer valid for reads/writes.
	///
	/// @param handle handle of the column family to drop
	default void dropColumnFamily(ColumnFamilyHandle handle) {
		RocksDB.dropCf(dbPtr(), handle);
	}
}
