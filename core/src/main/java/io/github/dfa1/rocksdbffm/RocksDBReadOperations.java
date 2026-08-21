package io.github.dfa1.rocksdbffm;

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
/// `RocksDB` helper — implementors only need to supply the native pointer.
///
/// Not implemented by [TransactionDB]: its direct (non-transactional) operations bind their
/// own `MethodHandle`s instead of sharing these helpers (`rocksdb_transactiondb_put` etc. are
/// genuinely different native symbols than the ones these defaults call), per the project
/// convention that a `MethodHandle` must never be routed through a shared call site (it
/// defeats `invokeExact`'s compile-time constant folding). [OptimisticTransactionDB] has no
/// such dedicated C API for direct ops — it always goes through the base `rocksdb_t*` — so it
/// implements this interface directly instead.
public interface RocksDBReadOperations {

	/// Returns the native `rocksdb_t*` pointer to operate on. Equivalent to the
	/// [NativeObject#ptr()] every implementor already has — a separate accessor only
	/// because an interface cannot require a method from a specific superclass.
	///
	/// @return the native database pointer
	MemorySegment dbPtr();

	/// Returns the [ReadOptions] used when no explicit options are supplied. Every current
	/// implementor shares the same never-closed [RocksDB#DEFAULT_READ_OPTIONS] instance;
	/// override if a future implementor ever needs its own.
	///
	/// @return the default read options
	default ReadOptions defaultReadOpts() {
		return RocksDB.DEFAULT_READ_OPTIONS;
	}

	// -----------------------------------------------------------------------
	// Get
	// -----------------------------------------------------------------------

	/// Returns the value for `key`, or `null` if the key does not exist.
	/// Uses PinnableSlice to avoid an intermediate copy from the block cache.
	///
	/// @param key key bytes to look up
	/// @return value bytes, or `null` if the key does not exist
	default byte[] get(byte[] key) {
		return RocksDB.getBytes(dbPtr(), defaultReadOpts().ptr(), key);
	}

	/// Get with explicit [ReadOptions], e.g. for snapshot-pinned reads. Returns `null` if not found.
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @param key         key bytes to look up
	/// @return value bytes, or `null` if the key does not exist
	default byte[] get(ReadOptions readOptions, byte[] key) {
		return RocksDB.getBytes(dbPtr(), readOptions.ptr(), key);
	}

	/// Single-copy get via `rocksdb_get_into_buffer` + direct output [ByteBuffer].
	/// Copies nothing into `value` when its remaining capacity is too small.
	///
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	default CopyResult get(ByteBuffer key, ByteBuffer value) {
		return RocksDB.getIntoBuffer(dbPtr(), defaultReadOpts().ptr(),
				MemorySegment.ofBuffer(key), key.remaining(), value);
	}

	/// Single-copy get into a caller-supplied native segment via `rocksdb_get_into_buffer`.
	/// Copies nothing into `value` when its capacity is too small.
	///
	/// @param key   native segment containing the key
	/// @param value native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	default CopyResult get(MemorySegment key, MemorySegment value) {
		return RocksDB.getIntoSegment(dbPtr(), defaultReadOpts().ptr(), key, key.byteSize(), value);
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
	/// @return the result of `fn`, wrapped in [Optional], or [Optional#empty()] if `key` is absent
	default <R> Optional<R> get(MemorySegment key, Mapper<R> fn) {
		return RocksDB.withPinned(dbPtr(), defaultReadOpts().ptr(), key, fn);
	}

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

	/// Scoped zero-copy get from `cf`. See [#get(MemorySegment, Mapper)] for
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

	// -----------------------------------------------------------------------
	// KeyMayExist (Bloom filter check)
	// -----------------------------------------------------------------------

	/// Returns `false` if the key definitely does not exist; `true` means it _may_ exist.
	/// Slow path: copies the key into native memory.
	///
	/// @param key the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	default boolean keyMayExist(byte[] key) {
		return RocksDB.keyMayExistBytes(dbPtr(), defaultReadOpts().ptr(), key);
	}

	/// [#keyMayExist(byte\[\])] with explicit [ReadOptions].
	///
	/// @param readOptions read options (e.g. snapshot)
	/// @param key         the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	default boolean keyMayExist(ReadOptions readOptions, byte[] key) {
		return RocksDB.keyMayExistBytes(dbPtr(), readOptions.ptr(), key);
	}

	/// Zero-copy for direct [ByteBuffer]s.
	///
	/// @param key direct [ByteBuffer] containing the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	default boolean keyMayExist(ByteBuffer key) {
		return RocksDB.keyMayExistSegment(dbPtr(), defaultReadOpts().ptr(), MemorySegment.ofBuffer(key), key.remaining());
	}

	/// Zero-copy for [MemorySegment]s.
	///
	/// @param key native segment containing the key to probe
	/// @return `false` if definitely absent, `true` if possibly present
	default boolean keyMayExist(MemorySegment key) {
		return RocksDB.keyMayExistSegment(dbPtr(), defaultReadOpts().ptr(), key, key.byteSize());
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

	// -----------------------------------------------------------------------
	// Iterator
	// -----------------------------------------------------------------------

	/// Returns a new iterator using the database's default read options.
	///
	/// @return a new [RocksIterator]; caller must close it
	default RocksIterator newIterator() {
		return RocksIterator.create(dbPtr(), defaultReadOpts().ptr());
	}

	/// Returns a new iterator using the supplied [ReadOptions].
	///
	/// @param readOptions read options, e.g. containing a snapshot
	/// @return a new [RocksIterator]; caller must close it
	default RocksIterator newIterator(ReadOptions readOptions) {
		return RocksIterator.create(dbPtr(), readOptions.ptr());
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

	// -----------------------------------------------------------------------
	// Snapshot
	// -----------------------------------------------------------------------

	/// Creates a snapshot of the current DB state. Must be closed after use.
	///
	/// @return a new [Snapshot]; caller must close it
	default Snapshot getSnapshot() {
		// Every implementor is a NativeObject (see #dbPtr()); passed through so the returned
		// Snapshot can detect this DB being closed before the snapshot is, rather than
		// releasing against a dangling pointer.
		return RocksDB.createSnapshot((NativeObject) this, dbPtr());
	}

	// -----------------------------------------------------------------------
	// DB Properties
	// -----------------------------------------------------------------------

	/// Returns the value of a DB property as a string, or [Optional#empty()] if not supported.
	///
	/// @param property the property to query
	/// @return the property value, or [Optional#empty()] if not supported
	default Optional<String> getProperty(Property property) {
		return RocksDB.getProperty(dbPtr(), property);
	}

	/// Returns the value of a numeric DB property, or [OptionalLong#empty()] if not supported.
	///
	/// @param property the property to query
	/// @return the numeric property value, or [OptionalLong#empty()] if not supported
	default OptionalLong getLongProperty(Property property) {
		return RocksDB.getLongProperty(dbPtr(), property);
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
