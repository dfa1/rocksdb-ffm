package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalLong;

/// Column-family-scoped counterparts of [RocksDbReadOps]'s read operations.
///
/// Separated from [RocksDbReadOps] rather than folded into it because not every readable
/// instance can obtain a [ColumnFamilyHandle] scoped to itself: [SecondaryDB] has no
/// multi-column-family open path, so passing it any [ColumnFamilyHandle] (necessarily
/// obtained from a different `rocksdb_t*`) is undefined behavior at the C++ level. Only
/// [ReadWriteDB], [TtlDB], [BlobDB] (via [RocksDbWriteOps]) and [ReadOnlyDB] — all of which
/// support opening or creating column families for themselves — implement this interface.
public interface RocksDbCfReadOps extends RocksDbReadOps {

	/// Returns the value for `key` in `cf`, or `null` if not found.
	///
	/// @param cf  column family to read from
	/// @param key key bytes to look up
	/// @return value bytes, or `null` if the key does not exist
	default byte[] get(ColumnFamilyHandle cf, byte[] key) {
		return RocksDB.getCfBytes(dbPtr(), defaultReadOpts().ptr(), cf, key);
	}

	/// Get from `cf` with explicit [ReadOptions]. Returns `null` if not found.
	///
	/// @param cf          column family to read from
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         key bytes to look up
	/// @return value bytes, or `null` if the key does not exist
	default byte[] get(ColumnFamilyHandle cf, ReadOptions readOptions, byte[] key) {
		return RocksDB.getCfBytes(dbPtr(), readOptions.ptr(), cf, key);
	}

	/// Single-copy get from `cf` via `rocksdb_get_into_buffer_cf` + direct output [ByteBuffer].
	/// Copies nothing into `value` when its remaining capacity is too small.
	///
	/// @param cf    column family to read from
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	default CopyResult get(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		return RocksDB.getCfIntoBuffer(dbPtr(), defaultReadOpts().ptr(), cf,
				MemorySegment.ofBuffer(key), key.remaining(), value);
	}

	/// Single-copy get from `cf` into a caller-supplied native segment via
	/// `rocksdb_get_into_buffer_cf`. Copies nothing into `value` when its capacity is too small.
	///
	/// @param cf    column family to read from
	/// @param key   native segment containing the key
	/// @param value native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	default CopyResult get(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		return RocksDB.getCfIntoSegment(dbPtr(), defaultReadOpts().ptr(), cf, key, key.byteSize(), value);
	}

	/// Scoped zero-copy get from `cf`. See [RocksDbReadOps#get(MemorySegment, Mapper)] for
	/// the lifetime contract on the view passed to `fn`.
	///
	/// @param <R> the type produced by `fn`
	/// @param cf  target column family
	/// @param key native segment containing the key
	/// @param fn  callback invoked with a zero-copy view of the pinned value
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`, wrapped in [Optional], or [Optional#empty()] if `key` is absent
	default <R> Optional<R> get(ColumnFamilyHandle cf, MemorySegment key, Mapper<R> fn) {
		return RocksDB.withPinnedCf(dbPtr(), defaultReadOpts().ptr(), cf, key, fn);
	}

	/// Returns `false` if the key definitely does not exist in `cf`; `true` means it _may_ exist.
	///
	/// @param cf  target column family
	/// @param key the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	default boolean keyMayExist(ColumnFamilyHandle cf, byte[] key) {
		return RocksDB.keyMayExistCfBytes(dbPtr(), defaultReadOpts().ptr(), cf, key);
	}

	/// [#keyMayExist(ColumnFamilyHandle, byte\[\])] with explicit [ReadOptions].
	///
	/// @param cf          target column family
	/// @param readOptions read options (e.g. snapshot)
	/// @param key         the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	default boolean keyMayExist(ColumnFamilyHandle cf, ReadOptions readOptions, byte[] key) {
		return RocksDB.keyMayExistCfBytes(dbPtr(), readOptions.ptr(), cf, key);
	}

	/// Zero-copy keyMayExist in `cf` for direct [ByteBuffer]s.
	///
	/// @param cf  target column family
	/// @param key direct [ByteBuffer] containing the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	default boolean keyMayExist(ColumnFamilyHandle cf, ByteBuffer key) {
		return RocksDB.keyMayExistCfSegment(dbPtr(), defaultReadOpts().ptr(), cf,
				MemorySegment.ofBuffer(key), key.remaining());
	}

	/// Zero-copy keyMayExist in `cf` for [MemorySegment]s.
	///
	/// @param cf  target column family
	/// @param key native segment containing the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	default boolean keyMayExist(ColumnFamilyHandle cf, MemorySegment key) {
		return RocksDB.keyMayExistCfSegment(dbPtr(), defaultReadOpts().ptr(), cf, key, key.byteSize());
	}

	/// Returns a new iterator scoped to `cf` using the database's default read options.
	///
	/// @param cf target column family
	/// @return a new [RocksIterator]; caller must close it
	default RocksIterator newIterator(ColumnFamilyHandle cf) {
		return RocksDB.createIteratorCf(dbPtr(), defaultReadOpts().ptr(), cf);
	}

	/// Returns a new iterator scoped to `cf` using the supplied [ReadOptions].
	///
	/// @param cf          target column family
	/// @param readOptions read options, e.g. containing a snapshot
	/// @return a new [RocksIterator]; caller must close it
	default RocksIterator newIterator(ColumnFamilyHandle cf, ReadOptions readOptions) {
		return RocksDB.createIteratorCf(dbPtr(), readOptions.ptr(), cf);
	}

	/// Returns the value of a property scoped to `cf`, or [Optional#empty()] if not supported.
	///
	/// @param cf       column family to query
	/// @param property the property to query
	/// @return the property value, or [Optional#empty()] if not supported
	default Optional<String> getProperty(ColumnFamilyHandle cf, Property property) {
		return RocksDB.getPropertyCf(dbPtr(), cf, property);
	}

	/// Returns the value of a numeric property scoped to `cf`, or [OptionalLong#empty()] if not supported.
	///
	/// @param cf       column family to query
	/// @param property the property to query
	/// @return the numeric property value, or [OptionalLong#empty()] if not supported
	default OptionalLong getLongProperty(ColumnFamilyHandle cf, Property property) {
		return RocksDB.getLongPropertyCf(dbPtr(), cf, property);
	}
}
