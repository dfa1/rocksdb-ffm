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
/// [RocksDBWriteOperationsBindings] helper — implementors only need to supply the native pointer.
///
/// Not implemented by [TransactionDB] — see [RocksDBReadOperations] for why.
/// [OptimisticTransactionDB] implements it directly.
///
/// Compaction control ([RocksDBCompactionOperations#compactRange()]/`suggestCompactRange`/
/// `waitForCompact`/file-deletion and manual-compaction toggles) lives on the separate
/// [RocksDBCompactionOperations] interface instead of
/// here — every current implementor of this interface implements that one too, but factoring it
/// out lets [TransactionDB] implement compaction control as well, something it couldn't do while
/// these methods lived only on this write-only interface it doesn't implement. See
/// [#131](https://github.com/dfa1/rocksdbffm/issues/131).
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
		RocksDBWriteOperationsBindings.putBytes(this, NativeCalls.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// [#put(byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to store
	/// @param value        the value to associate with the key
	default void put(WriteOptions writeOptions, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.putBytes(this, writeOptions, key, value);
	}

	/// Stores `value` under `key` using the caller's [Arena] for native allocation.
	///
	/// @param arena arena used for temporary native allocations
	/// @param key   the key to store
	/// @param value the value to associate with the key
	default void put(Arena arena, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.putBytes(arena, this, NativeCalls.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// [#put(Arena, byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param arena        arena used for temporary native allocations
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to store
	/// @param value        the value to associate with the key
	default void put(Arena arena, WriteOptions writeOptions, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.putBytes(arena, this, writeOptions, key, value);
	}

	/// Zero-copy put: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the value
	default void put(ByteBuffer key, ByteBuffer value) {
		RocksDBWriteOperationsBindings.putSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// [#put(ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [ByteBuffer] containing the key
	/// @param value        direct [ByteBuffer] containing the value
	default void put(WriteOptions writeOptions, ByteBuffer key, ByteBuffer value) {
		RocksDBWriteOperationsBindings.putSegment(this, writeOptions,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// Zero-copy put: caller supplies pre-allocated native segments.
	///
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	default void put(MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.putSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// [#put(MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the value
	default void put(WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.putSegment(this, writeOptions, key, value);
	}

	/// Zero-copy put using the caller's [Arena].
	///
	/// @param arena arena used for temporary native allocations
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	default void put(Arena arena, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.putSegment(arena, this, NativeCalls.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// [#put(Arena, MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param arena        arena used for temporary native allocations
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the value
	default void put(Arena arena, WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.putSegment(arena, this, writeOptions, key, value);
	}

	/// Stores `value` under `key` in `cf`. Slow path: copies key/value into native memory.
	///
	/// @param cf    target column family
	/// @param key   the key to store
	/// @param value the value to associate with the key
	default void put(ColumnFamilyHandle cf, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.putCfBytes(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, key, value);
	}

	/// [#put(ColumnFamilyHandle, byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to store
	/// @param value        the value to associate with the key
	default void put(ColumnFamilyHandle cf, WriteOptions writeOptions, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.putCfBytes(this, writeOptions, cf, key, value);
	}

	/// Zero-copy put into `cf`: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param cf    target column family
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the value
	default void put(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		RocksDBWriteOperationsBindings.putCfSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// [#put(ColumnFamilyHandle, ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [ByteBuffer] containing the key
	/// @param value        direct [ByteBuffer] containing the value
	default void put(ColumnFamilyHandle cf, WriteOptions writeOptions, ByteBuffer key, ByteBuffer value) {
		RocksDBWriteOperationsBindings.putCfSegment(this, writeOptions, cf,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// Zero-copy put into `cf`: caller supplies pre-allocated native segments.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	default void put(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.putCfSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, key, value);
	}

	/// [#put(ColumnFamilyHandle, MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the value
	default void put(ColumnFamilyHandle cf, WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.putCfSegment(this, writeOptions, cf, key, value);
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
		RocksDBWriteOperationsBindings.mergeBytes(this, NativeCalls.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// [#merge(byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to merge into
	/// @param value        the merge operand
	default void merge(WriteOptions writeOptions, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.mergeBytes(this, writeOptions, key, value);
	}

	/// Merges `value` into `key` using the caller's [Arena] for native allocation.
	///
	/// @param arena arena used for temporary native allocations
	/// @param key   the key to merge into
	/// @param value the merge operand
	default void merge(Arena arena, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.mergeBytes(arena, this, NativeCalls.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// [#merge(Arena, byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param arena        arena used for temporary native allocations
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to merge into
	/// @param value        the merge operand
	default void merge(Arena arena, WriteOptions writeOptions, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.mergeBytes(arena, this, writeOptions, key, value);
	}

	/// Zero-copy merge: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the merge operand
	default void merge(ByteBuffer key, ByteBuffer value) {
		RocksDBWriteOperationsBindings.mergeSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// [#merge(ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [ByteBuffer] containing the key
	/// @param value        direct [ByteBuffer] containing the merge operand
	default void merge(WriteOptions writeOptions, ByteBuffer key, ByteBuffer value) {
		RocksDBWriteOperationsBindings.mergeSegment(this, writeOptions,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// Zero-copy merge: caller supplies pre-allocated native segments.
	///
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	default void merge(MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.mergeSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// [#merge(MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the merge operand
	default void merge(WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.mergeSegment(this, writeOptions, key, value);
	}

	/// Zero-copy merge using the caller's [Arena].
	///
	/// @param arena arena used for temporary native allocations
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	default void merge(Arena arena, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.mergeSegment(arena, this, NativeCalls.DEFAULT_WRITE_OPTIONS, key, value);
	}

	/// [#merge(Arena, MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param arena        arena used for temporary native allocations
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the merge operand
	default void merge(Arena arena, WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.mergeSegment(arena, this, writeOptions, key, value);
	}

	/// Merges `value` into `key` in `cf`. Slow path: copies key/value into native memory.
	///
	/// @param cf    target column family
	/// @param key   the key to merge into
	/// @param value the merge operand
	default void merge(ColumnFamilyHandle cf, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.mergeCfBytes(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, key, value);
	}

	/// [#merge(ColumnFamilyHandle, byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to merge into
	/// @param value        the merge operand
	default void merge(ColumnFamilyHandle cf, WriteOptions writeOptions, byte[] key, byte[] value) {
		RocksDBWriteOperationsBindings.mergeCfBytes(this, writeOptions, cf, key, value);
	}

	/// Zero-copy merge into `cf`: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param cf    target column family
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the merge operand
	default void merge(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		RocksDBWriteOperationsBindings.mergeCfSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// [#merge(ColumnFamilyHandle, ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [ByteBuffer] containing the key
	/// @param value        direct [ByteBuffer] containing the merge operand
	default void merge(ColumnFamilyHandle cf, WriteOptions writeOptions, ByteBuffer key, ByteBuffer value) {
		RocksDBWriteOperationsBindings.mergeCfSegment(this, writeOptions, cf,
				MemorySegment.ofBuffer(key),
				MemorySegment.ofBuffer(value));
	}

	/// Zero-copy merge into `cf`: caller supplies pre-allocated native segments.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	default void merge(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.mergeCfSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, key, value);
	}

	/// [#merge(ColumnFamilyHandle, MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key
	/// @param value        native segment containing the merge operand
	default void merge(ColumnFamilyHandle cf, WriteOptions writeOptions, MemorySegment key, MemorySegment value) {
		RocksDBWriteOperationsBindings.mergeCfSegment(this, writeOptions, cf, key, value);
	}

	// -----------------------------------------------------------------------
	// Delete
	// -----------------------------------------------------------------------

	/// Removes `key` from the database. Slow path: copies the key into native memory.
	///
	/// @param key the key to remove
	default void delete(byte[] key) {
		RocksDBWriteOperationsBindings.deleteBytes(this, NativeCalls.DEFAULT_WRITE_OPTIONS, key);
	}

	/// [#delete(byte\[\])] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to remove
	default void delete(WriteOptions writeOptions, byte[] key) {
		RocksDBWriteOperationsBindings.deleteBytes(this, writeOptions, key);
	}

	/// Zero-copy for direct [ByteBuffer]s.
	///
	/// @param key direct [ByteBuffer] containing the key to remove
	default void delete(ByteBuffer key) {
		RocksDBWriteOperationsBindings.deleteSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, MemorySegment.ofBuffer(key));
	}

	/// [#delete(ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [ByteBuffer] containing the key to remove
	default void delete(WriteOptions writeOptions, ByteBuffer key) {
		RocksDBWriteOperationsBindings.deleteSegment(this, writeOptions, MemorySegment.ofBuffer(key));
	}

	/// Zero-copy native-first path.
	///
	/// @param key native segment containing the key to remove
	default void delete(MemorySegment key) {
		RocksDBWriteOperationsBindings.deleteSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, key);
	}

	/// [#delete(MemorySegment)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key to remove
	default void delete(WriteOptions writeOptions, MemorySegment key) {
		RocksDBWriteOperationsBindings.deleteSegment(this, writeOptions, key);
	}

	/// Removes `key` from `cf`. Slow path: copies the key into native memory.
	///
	/// @param cf  target column family
	/// @param key the key to remove
	default void delete(ColumnFamilyHandle cf, byte[] key) {
		RocksDBWriteOperationsBindings.deleteCfBytes(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, key);
	}

	/// [#delete(ColumnFamilyHandle, byte\[\])] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          the key to remove
	default void delete(ColumnFamilyHandle cf, WriteOptions writeOptions, byte[] key) {
		RocksDBWriteOperationsBindings.deleteCfBytes(this, writeOptions, cf, key);
	}

	/// Zero-copy delete from `cf` for direct [ByteBuffer]s.
	///
	/// @param cf  target column family
	/// @param key direct [ByteBuffer] containing the key to remove
	default void delete(ColumnFamilyHandle cf, ByteBuffer key) {
		RocksDBWriteOperationsBindings.deleteCfSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf,
				MemorySegment.ofBuffer(key));
	}

	/// [#delete(ColumnFamilyHandle, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          direct [ByteBuffer] containing the key to remove
	default void delete(ColumnFamilyHandle cf, WriteOptions writeOptions, ByteBuffer key) {
		RocksDBWriteOperationsBindings.deleteCfSegment(this, writeOptions, cf,
				MemorySegment.ofBuffer(key));
	}

	/// Zero-copy delete from `cf` for [MemorySegment]s.
	///
	/// @param cf  target column family
	/// @param key native segment containing the key to remove
	default void delete(ColumnFamilyHandle cf, MemorySegment key) {
		RocksDBWriteOperationsBindings.deleteCfSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, key);
	}

	/// [#delete(ColumnFamilyHandle, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param key          native segment containing the key to remove
	default void delete(ColumnFamilyHandle cf, WriteOptions writeOptions, MemorySegment key) {
		RocksDBWriteOperationsBindings.deleteCfSegment(this, writeOptions, cf, key);
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
		RocksDBWriteOperationsBindings.deleteRangeCfBytes(this, NativeCalls.DEFAULT_WRITE_OPTIONS, startKey, endKey);
	}

	/// [#deleteRange(byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param startKey     inclusive lower bound
	/// @param endKey       exclusive upper bound
	default void deleteRange(WriteOptions writeOptions, byte[] startKey, byte[] endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfBytes(this, writeOptions, startKey, endKey);
	}

	/// Zero-copy for direct [ByteBuffer]s.
	///
	/// @param startKey direct [ByteBuffer] with inclusive lower bound
	/// @param endKey   direct [ByteBuffer] with exclusive upper bound
	default void deleteRange(ByteBuffer startKey, ByteBuffer endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfBuffer(this, NativeCalls.DEFAULT_WRITE_OPTIONS, startKey, endKey);
	}

	/// [#deleteRange(ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param startKey     direct [ByteBuffer] with inclusive lower bound
	/// @param endKey       direct [ByteBuffer] with exclusive upper bound
	default void deleteRange(WriteOptions writeOptions, ByteBuffer startKey, ByteBuffer endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfBuffer(this, writeOptions, startKey, endKey);
	}

	/// Zero-copy native-first path.
	///
	/// @param startKey native segment with inclusive lower bound
	/// @param endKey   native segment with exclusive upper bound
	default void deleteRange(MemorySegment startKey, MemorySegment endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfSegment(this, NativeCalls.DEFAULT_WRITE_OPTIONS, startKey, endKey);
	}

	/// [#deleteRange(MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param startKey     native segment with inclusive lower bound
	/// @param endKey       native segment with exclusive upper bound
	default void deleteRange(WriteOptions writeOptions, MemorySegment startKey, MemorySegment endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfSegment(this, writeOptions, startKey, endKey);
	}

	/// Deletes all keys in the half-open range [`startKey`, `endKey`) from `cf`. Slow path.
	///
	/// @param cf       target column family
	/// @param startKey inclusive lower bound
	/// @param endKey   exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, byte[] startKey, byte[] endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfBytesExplicit(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, startKey, endKey);
	}

	/// [#deleteRange(ColumnFamilyHandle, byte\[\], byte\[\])] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param startKey     inclusive lower bound
	/// @param endKey       exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, WriteOptions writeOptions, byte[] startKey, byte[] endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfBytesExplicit(this, writeOptions, cf, startKey, endKey);
	}

	/// Zero-copy deleteRange from `cf` for direct [ByteBuffer]s.
	///
	/// @param cf       target column family
	/// @param startKey direct [ByteBuffer] with inclusive lower bound
	/// @param endKey   direct [ByteBuffer] with exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, ByteBuffer startKey, ByteBuffer endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfBufferExplicit(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, startKey, endKey);
	}

	/// [#deleteRange(ColumnFamilyHandle, ByteBuffer, ByteBuffer)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param startKey     direct [ByteBuffer] with inclusive lower bound
	/// @param endKey       direct [ByteBuffer] with exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, WriteOptions writeOptions, ByteBuffer startKey, ByteBuffer endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfBufferExplicit(this, writeOptions, cf, startKey, endKey);
	}

	/// Zero-copy deleteRange from `cf` for [MemorySegment]s.
	///
	/// @param cf       target column family
	/// @param startKey native segment with inclusive lower bound
	/// @param endKey   native segment with exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, MemorySegment startKey, MemorySegment endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfSegmentExplicit(this, NativeCalls.DEFAULT_WRITE_OPTIONS, cf, startKey, endKey);
	}

	/// [#deleteRange(ColumnFamilyHandle, MemorySegment, MemorySegment)] with explicit [WriteOptions].
	///
	/// @param cf           target column family
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param startKey     native segment with inclusive lower bound
	/// @param endKey       native segment with exclusive upper bound
	default void deleteRange(ColumnFamilyHandle cf, WriteOptions writeOptions,
	                          MemorySegment startKey, MemorySegment endKey) {
		RocksDBWriteOperationsBindings.deleteRangeCfSegmentExplicit(this, writeOptions, cf, startKey, endKey);
	}

	// -----------------------------------------------------------------------
	// Write (batch)
	// -----------------------------------------------------------------------

	/// Applies all mutations in `batch` atomically to the database.
	///
	/// @param batch the write batch to apply
	default void write(WriteBatch batch) {
		RocksDBWriteOperationsBindings.writeBatch(this, NativeCalls.DEFAULT_WRITE_OPTIONS, batch);
	}

	/// [#write(WriteBatch)] with explicit [WriteOptions].
	///
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param batch        the write batch to apply
	default void write(WriteOptions writeOptions, WriteBatch batch) {
		RocksDBWriteOperationsBindings.writeBatch(this, writeOptions, batch);
	}

	/// Applies all mutations in `batch` atomically, using the caller's [Arena] for native allocation.
	///
	/// @param arena arena used for temporary native allocations
	/// @param batch the write batch to apply
	default void write(Arena arena, WriteBatch batch) {
		RocksDBWriteOperationsBindings.writeBatch(arena, this, NativeCalls.DEFAULT_WRITE_OPTIONS, batch);
	}

	/// [#write(Arena, WriteBatch)] with explicit [WriteOptions].
	///
	/// @param arena        arena used for temporary native allocations
	/// @param writeOptions write options, e.g. to disable the WAL for this write
	/// @param batch        the write batch to apply
	default void write(Arena arena, WriteOptions writeOptions, WriteBatch batch) {
		RocksDBWriteOperationsBindings.writeBatch(arena, this, writeOptions, batch);
	}

	// -----------------------------------------------------------------------
	// Background jobs
	// -----------------------------------------------------------------------

	/// Cancels all background work (compaction, flush, etc.).
	///
	/// @param wait if `true`, blocks until all running jobs have finished
	default void cancelAllBackgroundWork(boolean wait) {
		RocksDBWriteOperationsBindings.cancelAllBackgroundWork(this, wait);
	}

	// -----------------------------------------------------------------------
	// WAL iteration
	// -----------------------------------------------------------------------

	/// Returns the sequence number of the most recent committed transaction.
	///
	/// @return current sequence number
	default SequenceNumber getLatestSequenceNumber() {
		return RocksDBWriteOperationsBindings.getLatestSequenceNumber(this);
	}

	/// Returns a [WalIterator] positioned at the first [WriteBatch] with a sequence number
	/// greater than or equal to `sequenceNumber`.
	///
	/// The caller must close the iterator after use.
	///
	/// @param sequenceNumber starting sequence number (inclusive)
	/// @return a new [WalIterator]; caller must close it
	default WalIterator getUpdatesSince(SequenceNumber sequenceNumber) {
		return RocksDBWriteOperationsBindings.getUpdatesSince(this, sequenceNumber);
	}

	// -----------------------------------------------------------------------
	// Flush
	// -----------------------------------------------------------------------

	/// Flushes all memtable data to SST files. Blocks when [FlushOptions#isWait()] is `true`.
	///
	/// @param flushOptions options controlling flush behavior
	default void flush(FlushOptions flushOptions) {
		RocksDBWriteOperationsBindings.flush(this, flushOptions);
	}

	/// Flushes the WAL to disk.
	///
	/// @param sync if `true`, performs an `fsync` after writing
	default void flushWal(boolean sync) {
		RocksDBWriteOperationsBindings.flushWal(this, sync);
	}

	/// Flushes the memtable for `cf` to SST files.
	///
	/// @param cf           target column family
	/// @param flushOptions options controlling flush behavior
	default void flush(ColumnFamilyHandle cf, FlushOptions flushOptions) {
		RocksDBWriteOperationsBindings.flushCf(this, flushOptions, cf);
	}

	// -----------------------------------------------------------------------
	// SST File Ingest
	// -----------------------------------------------------------------------

	/// Ingests SST files produced by [SstFileWriter] into the database.
	///
	/// @param files   list of SST file paths to ingest
	/// @param options ingest options controlling move vs copy, error handling, etc.
	default void ingestExternalFile(List<Path> files, IngestExternalFileOptions options) {
		RocksDBWriteOperationsBindings.ingestExternalFile(this, files, options);
	}

	/// Ingests `files` using default [IngestExternalFileOptions].
	///
	/// @param files list of SST file paths to ingest
	default void ingestExternalFile(List<Path> files) {
		RocksDBWriteOperationsBindings.ingestExternalFileWithDefaults(this, files);
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
		return RocksDBWriteOperationsBindings.createCf(this, descriptor);
	}

	/// Drops the column family identified by `handle`.
	/// The handle should be closed after this call; it is no longer valid for reads/writes.
	///
	/// @param handle handle of the column family to drop
	default void dropColumnFamily(ColumnFamilyHandle handle) {
		RocksDBWriteOperationsBindings.dropCf(dbPtr(), handle);
	}
}
