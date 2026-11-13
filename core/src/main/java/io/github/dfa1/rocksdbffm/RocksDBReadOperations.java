package io.github.dfa1.rocksdbffm;

import javax.annotation.CheckReturnValue;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalLong;

/// Shared read operations, including column-family-scoped overloads, for every wrapper
/// around a plain `rocksdb_t*`: read-write, read-only, TTL, blob, secondary, and
/// optimistic-transaction instances all expose this identical surface and all support
/// opening or creating column families for themselves.
///
/// Every method here is a direct, zero-logic forward into the matching package-private
/// [RocksDBReadOperationsBindings] helper — implementors only need to supply the native pointer.
///
/// Not implemented by [TransactionDB]: its direct (non-transactional) operations bind their
/// own `MethodHandle`s instead of sharing these helpers (`rocksdb_transactiondb_put` etc. are
/// genuinely different native symbols than the ones these defaults call), per the project
/// convention that a `MethodHandle` must never be routed through a shared call site (it
/// defeats `invokeExact`'s compile-time constant folding). [TransactionDB] implements
/// [RocksDBMonitoringOperations] separately, though — `getLiveFiles()` has no such per-type native
/// symbol to duplicate, so nothing stops it sharing that one surface even though it can't share
/// this one. [OptimisticTransactionDB] has no such dedicated C API for direct ops — it always
/// goes through the base `rocksdb_t*` — so it implements this interface directly instead.
public interface RocksDBReadOperations extends RocksDBOperations {

	// -----------------------------------------------------------------------
	// Get
	// -----------------------------------------------------------------------

	/// Returns the value for `key`, or `null` if the key does not exist.
	/// Uses PinnableSlice to avoid an intermediate copy from the block cache.
	///
	/// @param key key bytes to look up
	/// @return value bytes, or `null` if the key does not exist
	default byte[] get(byte[] key) {
		return RocksDBReadOperationsBindings.getBytes(this, ReadOptions.DEFAULTS, key);
	}

	/// Get with explicit [ReadOptions], e.g. for snapshot-pinned reads. Returns `null` if not found.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         key bytes to look up
	/// @return value bytes, or `null` if the key does not exist
	default byte[] get(ReadOptions readOptions, byte[] key) {
		return RocksDBReadOperationsBindings.getBytes(this, readOptions, key);
	}

	/// Single-copy get via `rocksdb_get_into_buffer` + direct output [ByteBuffer].
	/// Copies nothing into `value` when its remaining capacity is too small.
	///
	/// See [CopyResult]'s class doc for the fill-mode contract on `value`: on success only its
	/// position advances, so call `value.flip()` before reading — a required extra step for
	/// callers migrating from `rocksdbjni`, whose equivalent flips for you.
	///
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
    @CheckReturnValue
	default CopyResult get(ByteBuffer key, ByteBuffer value) {
		return RocksDBReadOperationsBindings.getIntoBuffer(this, ReadOptions.DEFAULTS,
				MemorySegment.ofBuffer(key), value);
	}

	/// [#get(ByteBuffer, ByteBuffer)] with explicit [ReadOptions].
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         direct [ByteBuffer] containing the key
	/// @param value       direct [ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	@CheckReturnValue
	default CopyResult get(ReadOptions readOptions, ByteBuffer key, ByteBuffer value) {
		return RocksDBReadOperationsBindings.getIntoBuffer(this, readOptions,
				MemorySegment.ofBuffer(key), value);
	}

	/// Single-copy get into a caller-supplied native segment via `rocksdb_get_into_buffer`.
	/// Copies nothing into `value` when its capacity is too small.
	///
	/// @param key   native segment containing the key
	/// @param value native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	@CheckReturnValue
	default CopyResult get(MemorySegment key, MemorySegment value) {
		return RocksDBReadOperationsBindings.getIntoSegment(this, ReadOptions.DEFAULTS, key, value);
	}

	/// [#get(MemorySegment, MemorySegment)] with explicit [ReadOptions].
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         native segment containing the key
	/// @param value       native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	@CheckReturnValue
	default CopyResult get(ReadOptions readOptions, MemorySegment key, MemorySegment value) {
		return RocksDBReadOperationsBindings.getIntoSegment(this, readOptions, key, value);
	}

	/// Scoped zero-copy get: reads `key` via a `rocksdb_pinnable_handle_t` and passes a
	/// read-only view of the value directly to `fn`, with no intermediate copy.
	///
	/// The view passed to `fn` is bound to an arena that is closed the moment `fn`
	/// returns, so it must not be retained beyond the call — doing so throws
	/// `IllegalStateException` (used after this call returns) or `WrongThreadException`
	/// (used from another thread) rather than reading freed memory.
	///
	/// @param <R> the type produced by `fn`
	/// @param key native segment containing the key
	/// @param fn  callback invoked with a zero-copy view of the pinned value
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`, or `null` if `key` is absent
	default <R> R get(MemorySegment key, Mapper<R> fn) {
		return RocksDBReadOperationsBindings.withPinned(this, ReadOptions.DEFAULTS, key, fn);
	}

	/// [#get(MemorySegment, Mapper)] with explicit [ReadOptions].
	///
	/// @param <R> the type produced by `fn`
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         native segment containing the key
	/// @param fn          callback invoked with a zero-copy view of the pinned value
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`, or `null` if `key` is absent
	default <R> R get(ReadOptions readOptions, MemorySegment key, Mapper<R> fn) {
		return RocksDBReadOperationsBindings.withPinned(this, readOptions, key, fn);
	}

	/// Returns the value for `key` in `cf`, or `null` if not found.
	///
	/// @param cf  column family to read from
	/// @param key key bytes to look up
	/// @return value bytes, or `null` if the key does not exist
	default byte[] get(ColumnFamilyHandle cf, byte[] key) {
		return RocksDBReadOperationsBindings.getCfBytes(this, ReadOptions.DEFAULTS, cf, key);
	}

	/// Get from `cf` with explicit [ReadOptions]. Returns `null` if not found.
	///
	/// @param cf          column family to read from
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         key bytes to look up
	/// @return value bytes, or `null` if the key does not exist
	default byte[] get(ColumnFamilyHandle cf, ReadOptions readOptions, byte[] key) {
		return RocksDBReadOperationsBindings.getCfBytes(this, readOptions, cf, key);
	}

	/// Single-copy get from `cf` via `rocksdb_get_into_buffer_cf` + direct output [ByteBuffer].
	/// Copies nothing into `value` when its remaining capacity is too small.
	///
	/// See [CopyResult]'s class doc for the fill-mode contract on `value`: on success only its
	/// position advances, so call `value.flip()` before reading — a required extra step for
	/// callers migrating from `rocksdbjni`, whose equivalent flips for you.
	///
	/// @param cf    column family to read from
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	@CheckReturnValue
	default CopyResult get(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		return RocksDBReadOperationsBindings.getCfIntoBuffer(this, ReadOptions.DEFAULTS, cf,
				MemorySegment.ofBuffer(key), value);
	}

	/// [#get(ColumnFamilyHandle, ByteBuffer, ByteBuffer)] with explicit [ReadOptions].
	///
	/// @param cf          column family to read from
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         direct [ByteBuffer] containing the key
	/// @param value       direct [ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	@CheckReturnValue
	default CopyResult get(ColumnFamilyHandle cf, ReadOptions readOptions, ByteBuffer key, ByteBuffer value) {
		return RocksDBReadOperationsBindings.getCfIntoBuffer(this, readOptions, cf,
				MemorySegment.ofBuffer(key), value);
	}

	/// Single-copy get from `cf` into a caller-supplied native segment via
	/// `rocksdb_get_into_buffer_cf`. Copies nothing into `value` when its capacity is too small.
	///
	/// @param cf    column family to read from
	/// @param key   native segment containing the key
	/// @param value native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	@CheckReturnValue
	default CopyResult get(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		return RocksDBReadOperationsBindings.getCfIntoSegment(this, ReadOptions.DEFAULTS, cf, key, value);
	}

	/// [#get(ColumnFamilyHandle, MemorySegment, MemorySegment)] with explicit [ReadOptions].
	///
	/// @param cf          column family to read from
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         native segment containing the key
	/// @param value       native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	@CheckReturnValue
	default CopyResult get(ColumnFamilyHandle cf, ReadOptions readOptions, MemorySegment key, MemorySegment value) {
		return RocksDBReadOperationsBindings.getCfIntoSegment(this, readOptions, cf, key, value);
	}

	/// Scoped zero-copy get from `cf`. See [#get(MemorySegment, Mapper)] for
	/// the lifetime contract on the view passed to `fn`.
	///
	/// @param <R> the type produced by `fn`
	/// @param cf  target column family
	/// @param key native segment containing the key
	/// @param fn  callback invoked with a zero-copy view of the pinned value
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`, or `null` if `key` is absent
	default <R> R get(ColumnFamilyHandle cf, MemorySegment key, Mapper<R> fn) {
		return RocksDBReadOperationsBindings.withPinnedCf(this, ReadOptions.DEFAULTS, cf, key, fn);
	}

	/// [#get(ColumnFamilyHandle, MemorySegment, Mapper)] with explicit [ReadOptions].
	///
	/// @param <R> the type produced by `fn`
	/// @param cf          target column family
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         native segment containing the key
	/// @param fn          callback invoked with a zero-copy view of the pinned value
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`, or `null` if `key` is absent
	default <R> R get(ColumnFamilyHandle cf, ReadOptions readOptions, MemorySegment key, Mapper<R> fn) {
		return RocksDBReadOperationsBindings.withPinnedCf(this, readOptions, cf, key, fn);
	}

	// -----------------------------------------------------------------------
	// KeyMayExist (Bloom filter check)
	// -----------------------------------------------------------------------

	/// Returns `false` if the key definitely does not exist; `true` means it _may_ exist.
	/// Slow path: copies the key into native memory.
	///
	/// @param key the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	@CheckReturnValue
	default boolean keyMayExist(byte[] key) {
		return RocksDBReadOperationsBindings.keyMayExistBytes(this, ReadOptions.DEFAULTS, key);
	}

	/// [#keyMayExist(byte\[\])] with explicit [ReadOptions].
	///
	/// @param readOptions read options (e.g. snapshot)
	/// @param key         the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	@CheckReturnValue
	default boolean keyMayExist(ReadOptions readOptions, byte[] key) {
		return RocksDBReadOperationsBindings.keyMayExistBytes(this, readOptions, key);
	}

	/// Zero-copy for direct [ByteBuffer]s.
	///
	/// @param key direct [ByteBuffer] containing the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	@CheckReturnValue
	default boolean keyMayExist(ByteBuffer key) {
		return RocksDBReadOperationsBindings.keyMayExistSegment(this, ReadOptions.DEFAULTS, MemorySegment.ofBuffer(key));
	}

	/// Zero-copy for [MemorySegment]s.
	///
	/// @param key native segment containing the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	@CheckReturnValue
	default boolean keyMayExist(MemorySegment key) {
		return RocksDBReadOperationsBindings.keyMayExistSegment(this, ReadOptions.DEFAULTS, key);
	}

	/// Returns `false` if the key definitely does not exist in `cf`; `true` means it _may_ exist.
	///
	/// @param cf  target column family
	/// @param key the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	@CheckReturnValue
	default boolean keyMayExist(ColumnFamilyHandle cf, byte[] key) {
		return RocksDBReadOperationsBindings.keyMayExistCfBytes(this, ReadOptions.DEFAULTS, cf, key);
	}

	/// [#keyMayExist(ColumnFamilyHandle, byte\[\])] with explicit [ReadOptions].
	///
	/// @param cf          target column family
	/// @param readOptions read options (e.g. snapshot)
	/// @param key         the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	@CheckReturnValue
	default boolean keyMayExist(ColumnFamilyHandle cf, ReadOptions readOptions, byte[] key) {
		return RocksDBReadOperationsBindings.keyMayExistCfBytes(this, readOptions, cf, key);
	}

	/// Zero-copy keyMayExist in `cf` for direct [ByteBuffer]s.
	///
	/// @param cf  target column family
	/// @param key direct [ByteBuffer] containing the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	@CheckReturnValue
	default boolean keyMayExist(ColumnFamilyHandle cf, ByteBuffer key) {
		return RocksDBReadOperationsBindings.keyMayExistCfSegment(this, ReadOptions.DEFAULTS, cf,
				MemorySegment.ofBuffer(key));
	}

	/// Zero-copy keyMayExist in `cf` for [MemorySegment]s.
	///
	/// @param cf  target column family
	/// @param key native segment containing the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	@CheckReturnValue
	default boolean keyMayExist(ColumnFamilyHandle cf, MemorySegment key) {
		return RocksDBReadOperationsBindings.keyMayExistCfSegment(this, ReadOptions.DEFAULTS, cf, key);
	}

	// -----------------------------------------------------------------------
	// Iterator
	// -----------------------------------------------------------------------

	/// Returns a new iterator using the database's default read options.
	///
	/// @return a new [RocksIterator]; caller must close it
	default RocksIterator newIterator() {
		return RocksIterator.create(this, ReadOptions.DEFAULTS);
	}

	/// Returns a new iterator using the supplied [ReadOptions].
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @return a new [RocksIterator]; caller must close it
	default RocksIterator newIterator(ReadOptions readOptions) {
		return RocksIterator.create(this, readOptions);
	}

	/// Returns a new iterator scoped to `cf` using the database's default read options.
	///
	/// @param cf target column family
	/// @return a new [RocksIterator]; caller must close it
	default RocksIterator newIterator(ColumnFamilyHandle cf) {
		return RocksDBReadOperationsBindings.createIteratorCf(this, ReadOptions.DEFAULTS, cf);
	}

	/// Returns a new iterator scoped to `cf` using the supplied [ReadOptions].
	///
	/// @param cf          target column family
	/// @param readOptions read options, e.g. containing a snapshot
	/// @return a new [RocksIterator]; caller must close it
	default RocksIterator newIterator(ColumnFamilyHandle cf, ReadOptions readOptions) {
		return RocksDBReadOperationsBindings.createIteratorCf(this, readOptions, cf);
	}

	// -----------------------------------------------------------------------
	// Snapshot
	// -----------------------------------------------------------------------

	/// Creates a snapshot of the current DB state. Must be closed after use.
	///
	/// @return a new [Snapshot]; caller must close it
	default Snapshot getSnapshot() {
		// Every implementor extends NativeObjectWithChildren (see #dbPtr()); passed through so
		// the returned Snapshot registers itself and is released automatically if this DB
		// closes first, rather than dangling.
		return RocksDBReadOperationsBindings.createSnapshot((NativeObjectWithChildren) this, dbPtr());
	}

	// -----------------------------------------------------------------------
	// DB Properties
	// -----------------------------------------------------------------------

	/// Returns the value of a DB property as a string, or [Optional#empty()] if not supported.
	///
	/// @param property the property to query
	/// @return the property value, or [Optional#empty()] if not supported
	default Optional<String> getProperty(Property property) {
		return RocksDBReadOperationsBindings.getProperty(this, property);
	}

	/// Returns the value of a numeric DB property, or [OptionalLong#empty()] if not supported.
	///
	/// @param property the property to query
	/// @return the numeric property value, or [OptionalLong#empty()] if not supported
	default OptionalLong getLongProperty(Property property) {
		return RocksDBReadOperationsBindings.getLongProperty(this, property);
	}

	/// Returns the value of a property scoped to `cf`, or [Optional#empty()] if not supported.
	///
	/// @param cf       column family to query
	/// @param property the property to query
	/// @return the property value, or [Optional#empty()] if not supported
	default Optional<String> getProperty(ColumnFamilyHandle cf, Property property) {
		return RocksDBReadOperationsBindings.getPropertyCf(dbPtr(), cf, property);
	}

	/// Returns the value of a numeric property scoped to `cf`, or [OptionalLong#empty()] if not supported.
	///
	/// @param cf       column family to query
	/// @param property the property to query
	/// @return the numeric property value, or [OptionalLong#empty()] if not supported
	default OptionalLong getLongProperty(ColumnFamilyHandle cf, Property property) {
		return RocksDBReadOperationsBindings.getLongPropertyCf(dbPtr(), cf, property);
	}
}
