package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/// FFM wrapper for a blob-enabled read-write `rocksdb_t*` instance.
///
/// BlobDB is a regular RocksDB opened with blob file options set in [Options].
/// Large values (≥ [Options#setMinBlobSize]) are stored in separate blob files
/// rather than inline in SSTs, reducing write amplification for value-heavy workloads.
///
/// Obtain via [RocksDB#openBlob]:
///
/// ```
/// try (Options opts = Options.newOptions()
///         .setCreateIfMissing(true)
///         .setEnableBlobFiles(true)
///         .setMinBlobSize(MemorySize.ofKB(4))) {
///     try (var db = RocksDB.openBlob(opts, path)) {
///         db.put("key".getBytes(), largeValue);
///     }
/// }
/// ```
///
/// Blob-specific statistics are available via [Property#BLOB_STATS],
/// [Property#NUM_BLOB_FILES], [Property#TOTAL_BLOB_FILE_SIZE], etc.
public final class BlobDB extends NativeObject {

	private final WriteOptions writeOpts;
	private final ReadOptions readOpts;

	BlobDB(MemorySegment ptr, WriteOptions writeOpts, ReadOptions readOpts) {
		super(ptr);
		this.writeOpts = writeOpts;
		this.readOpts = readOpts;
	}

	// -----------------------------------------------------------------------
	// Column families
	// -----------------------------------------------------------------------

	/// Creates a new column family described by `descriptor` and returns its handle.
	/// The caller must close the returned handle when done.
	///
	/// @param descriptor name and options for the new column family
	/// @return handle to the newly created column family; caller must close it
	public ColumnFamilyHandle createColumnFamily(ColumnFamilyDescriptor descriptor) {
		return RocksDB.createCf(ptr(), descriptor);
	}

	/// Drops the column family identified by `handle`.
	/// The handle should be closed after this call; it is no longer valid for reads/writes.
	///
	/// @param handle handle of the column family to drop
	public void dropColumnFamily(ColumnFamilyHandle handle) {
		RocksDB.dropCf(ptr(), handle);
	}

	// -----------------------------------------------------------------------
	// Put
	// -----------------------------------------------------------------------

	/// Stores `value` under `key`. Slow path: copies key/value into native memory.
	///
	/// @param key the key bytes
	/// @param value the value bytes
	public void put(byte[] key, byte[] value) {
		RocksDB.putBytes(ptr(), writeOpts.ptr(), key, value);
	}

	/// Stores `value` under `key` using the caller's [Arena] for native allocation.
	///
	/// @param arena the arena used to allocate native key/value segments
	/// @param key the key bytes
	/// @param value the value bytes
	public void put(Arena arena, byte[] key, byte[] value) {
		RocksDB.putBytes(arena, ptr(), writeOpts.ptr(), key, value);
	}

	/// Zero-copy put: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param key direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the value
	public void put(ByteBuffer key, ByteBuffer value) {
		RocksDB.putSegment(ptr(), writeOpts.ptr(),
				MemorySegment.ofBuffer(key), key.remaining(),
				MemorySegment.ofBuffer(value), value.remaining());
	}

	/// Zero-copy put: caller supplies pre-allocated native segments.
	///
	/// @param key native segment containing the key
	/// @param value native segment containing the value
	public void put(MemorySegment key, MemorySegment value) {
		RocksDB.putSegment(ptr(), writeOpts.ptr(), key, key.byteSize(), value, value.byteSize());
	}

	// -----------------------------------------------------------------------
	// Put — column family overloads
	// -----------------------------------------------------------------------

	/// Stores `value` under `key` in `cf`. Slow path: copies key/value into native memory.
	///
	/// @param cf    target column family
	/// @param key   the key to store
	/// @param value the value to associate with the key
	public void put(ColumnFamilyHandle cf, byte[] key, byte[] value) {
		RocksDB.putCfBytes(ptr(), writeOpts.ptr(), cf, key, value);
	}

	/// Zero-copy put into `cf`: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param cf    target column family
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the value
	public void put(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		RocksDB.putCfSegment(ptr(), writeOpts.ptr(), cf,
				MemorySegment.ofBuffer(key), key.remaining(),
				MemorySegment.ofBuffer(value), value.remaining());
	}

	/// Zero-copy put into `cf`: caller supplies pre-allocated native segments.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment containing the value
	public void put(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		RocksDB.putCfSegment(ptr(), writeOpts.ptr(), cf, key, key.byteSize(), value, value.byteSize());
	}

	// -----------------------------------------------------------------------
	// Merge
	// -----------------------------------------------------------------------

	/// Merges `value` into `key` via the configured merge operator. Slow path: copies key/value into
	/// native memory.
	///
	/// @param key the key to merge into
	/// @param value the merge operand
	public void merge(byte[] key, byte[] value) {
		RocksDB.mergeBytes(ptr(), writeOpts.ptr(), key, value);
	}

	/// Merges `value` into `key` using the caller's [Arena] for native allocation.
	///
	/// @param arena the arena used to allocate native key/value segments
	/// @param key the key to merge into
	/// @param value the merge operand
	public void merge(Arena arena, byte[] key, byte[] value) {
		RocksDB.mergeBytes(arena, ptr(), writeOpts.ptr(), key, value);
	}

	/// Zero-copy merge: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param key direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the merge operand
	public void merge(ByteBuffer key, ByteBuffer value) {
		RocksDB.mergeSegment(ptr(), writeOpts.ptr(),
				MemorySegment.ofBuffer(key), key.remaining(),
				MemorySegment.ofBuffer(value), value.remaining());
	}

	/// Zero-copy merge: caller supplies pre-allocated native segments.
	///
	/// @param key native segment containing the key
	/// @param value native segment containing the merge operand
	public void merge(MemorySegment key, MemorySegment value) {
		RocksDB.mergeSegment(ptr(), writeOpts.ptr(), key, key.byteSize(), value, value.byteSize());
	}

	// -----------------------------------------------------------------------
	// Merge — column family overloads
	// -----------------------------------------------------------------------

	/// Merges `value` into `key` in `cf`. Slow path: copies key/value into native memory.
	///
	/// @param cf    target column family
	/// @param key   the key to merge into
	/// @param value the merge operand
	public void merge(ColumnFamilyHandle cf, byte[] key, byte[] value) {
		RocksDB.mergeCfBytes(ptr(), writeOpts.ptr(), cf, key, value);
	}

	/// Zero-copy merge into `cf`: wraps the direct buffers' native memory without heap→native copy.
	///
	/// @param cf    target column family
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] containing the merge operand
	public void merge(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		RocksDB.mergeCfSegment(ptr(), writeOpts.ptr(), cf,
				MemorySegment.ofBuffer(key), key.remaining(),
				MemorySegment.ofBuffer(value), value.remaining());
	}

	/// Zero-copy merge into `cf`: caller supplies pre-allocated native segments.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment containing the merge operand
	public void merge(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		RocksDB.mergeCfSegment(ptr(), writeOpts.ptr(), cf, key, key.byteSize(), value, value.byteSize());
	}

	// -----------------------------------------------------------------------
	// Get
	// -----------------------------------------------------------------------

	/// Get via PinnableSlice — pins data directly from the block/blob cache.
	///
	/// @param key the key bytes to look up
	/// @return the value bytes, or `null` if not found
	public byte[] get(byte[] key) {
		return RocksDB.getBytes(ptr(), readOpts.ptr(), key);
	}

	/// Get with explicit [ReadOptions], e.g. for snapshot-pinned reads.
	///
	/// @param readOptions options controlling the read (e.g. snapshot)
	/// @param key the key bytes to look up
	/// @return the value bytes, or `null` if not found
	public byte[] get(ReadOptions readOptions, byte[] key) {
		return RocksDB.getBytes(ptr(), readOptions.ptr(), key);
	}

	/// Single-copy get via `rocksdb_get_into_buffer` + direct output [ByteBuffer].
	/// Copies nothing into `value` when its remaining capacity is too small.
	///
	/// @param key direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	public CopyResult get(ByteBuffer key, ByteBuffer value) {
		return RocksDB.getIntoBuffer(ptr(), readOpts.ptr(),
				MemorySegment.ofBuffer(key), key.remaining(), value);
	}

	/// Single-copy get into a caller-supplied native segment via `rocksdb_get_into_buffer`.
	/// Copies nothing into `value` when its capacity is too small.
	///
	/// @param key   native segment containing the key
	/// @param value native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	public CopyResult get(MemorySegment key, MemorySegment value) {
		return RocksDB.getIntoSegment(ptr(), readOpts.ptr(), key, key.byteSize(), value);
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
	public <R> Optional<R> get(MemorySegment key, Mapper<R> fn) {
		return RocksDB.withPinned(ptr(), readOpts.ptr(), key, fn);
	}

	// -----------------------------------------------------------------------
	// Get — column family overloads
	// -----------------------------------------------------------------------

	/// Get via PinnableSlice from `cf`. Returns `null` if not found.
	///
	/// @param cf  target column family
	/// @param key the key to look up
	/// @return value bytes, or `null` if not found
	public byte[] get(ColumnFamilyHandle cf, byte[] key) {
		return RocksDB.getCfBytes(ptr(), readOpts.ptr(), cf, key);
	}

	/// Get from `cf` with explicit [ReadOptions]. Returns `null` if not found.
	///
	/// @param cf          target column family
	/// @param readOptions read options (e.g. snapshot)
	/// @param key         the key to look up
	/// @return value bytes, or `null` if not found
	public byte[] get(ColumnFamilyHandle cf, ReadOptions readOptions, byte[] key) {
		return RocksDB.getCfBytes(ptr(), readOptions.ptr(), cf, key);
	}

	/// Single-copy get from `cf` via `rocksdb_get_into_buffer_cf` into a direct [ByteBuffer].
	/// Copies nothing into `value` when its remaining capacity is too small.
	///
	/// @param cf    target column family
	/// @param key   direct [ByteBuffer] containing the key
	/// @param value direct [ByteBuffer] to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	public CopyResult get(ColumnFamilyHandle cf, ByteBuffer key, ByteBuffer value) {
		return RocksDB.getCfIntoBuffer(ptr(), readOpts.ptr(), cf,
				MemorySegment.ofBuffer(key), key.remaining(), value);
	}

	/// Single-copy get from `cf` into a caller-supplied native segment via
	/// `rocksdb_get_into_buffer_cf`. Copies nothing into `value` when its capacity is too small.
	///
	/// @param cf    target column family
	/// @param key   native segment containing the key
	/// @param value native segment to write the value into
	/// @return [CopyResult.Copied] if copied, [CopyResult.NotEnoughCapacity] if `value` is too
	/// small, or [CopyResult.NotFound] if the key is absent
	public CopyResult get(ColumnFamilyHandle cf, MemorySegment key, MemorySegment value) {
		return RocksDB.getCfIntoSegment(ptr(), readOpts.ptr(), cf, key, key.byteSize(), value);
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
	public <R> Optional<R> get(ColumnFamilyHandle cf, MemorySegment key, Mapper<R> fn) {
		return RocksDB.withPinnedCf(ptr(), readOpts.ptr(), cf, key, fn);
	}

	// -----------------------------------------------------------------------
	// Delete
	// -----------------------------------------------------------------------

	/// Removes `key` from the database. Slow path: copies the key into native memory.
	///
	/// @param key the key bytes to remove
	public void delete(byte[] key) {
		RocksDB.deleteBytes(ptr(), writeOpts.ptr(), key);
	}

	/// Zero-copy for direct [ByteBuffer]s.
	///
	/// @param key direct [ByteBuffer] containing the key to remove
	public void delete(ByteBuffer key) {
		RocksDB.deleteSegment(ptr(), writeOpts.ptr(), MemorySegment.ofBuffer(key), key.remaining());
	}

	/// Zero-copy native-first path.
	///
	/// @param key native segment containing the key to remove
	public void delete(MemorySegment key) {
		RocksDB.deleteSegment(ptr(), writeOpts.ptr(), key, key.byteSize());
	}

	// -----------------------------------------------------------------------
	// Delete — column family overloads
	// -----------------------------------------------------------------------

	/// Removes `key` from `cf`. Slow path: copies the key into native memory.
	///
	/// @param cf  target column family
	/// @param key the key to remove
	public void delete(ColumnFamilyHandle cf, byte[] key) {
		RocksDB.deleteCfBytes(ptr(), writeOpts.ptr(), cf, key);
	}

	/// Zero-copy delete from `cf` for direct [ByteBuffer]s.
	///
	/// @param cf  target column family
	/// @param key direct [ByteBuffer] containing the key to remove
	public void delete(ColumnFamilyHandle cf, ByteBuffer key) {
		RocksDB.deleteCfSegment(ptr(), writeOpts.ptr(), cf,
				MemorySegment.ofBuffer(key), key.remaining());
	}

	/// Zero-copy delete from `cf` for [MemorySegment]s.
	///
	/// @param cf  target column family
	/// @param key native segment containing the key to remove
	public void delete(ColumnFamilyHandle cf, MemorySegment key) {
		RocksDB.deleteCfSegment(ptr(), writeOpts.ptr(), cf, key, key.byteSize());
	}

	// -----------------------------------------------------------------------
	// Write (batch)
	// -----------------------------------------------------------------------

	/// Applies all mutations in `batch` atomically to the database.
	///
	/// @param batch the write batch to apply
	public void write(WriteBatch batch) {
		RocksDB.writeBatch(ptr(), writeOpts.ptr(), batch);
	}

	// -----------------------------------------------------------------------
	// Snapshot
	// -----------------------------------------------------------------------

	/// Creates a snapshot of the current DB state. Must be closed after use.
	///
	/// @return a new [Snapshot] pinning the current sequence number
	public Snapshot getSnapshot() {
		return RocksDB.createSnapshot(ptr());
	}

	// -----------------------------------------------------------------------
	// Iterator
	// -----------------------------------------------------------------------

	/// Returns a new iterator using the database's default read options.
	///
	/// @return a new [RocksIterator] positioned before the first entry
	public RocksIterator newIterator() {
		return RocksIterator.create(ptr(), readOpts.ptr());
	}

	/// Returns a new iterator using the supplied [ReadOptions].
	///
	/// @param readOptions options controlling iteration (e.g. snapshot)
	/// @return a new [RocksIterator] positioned before the first entry
	public RocksIterator newIterator(ReadOptions readOptions) {
		return RocksIterator.create(ptr(), readOptions.ptr());
	}

	// -----------------------------------------------------------------------
	// Iterator — column family overloads
	// -----------------------------------------------------------------------

	/// Returns a new iterator scoped to `cf` using the database's default read options.
	///
	/// @param cf target column family
	/// @return a new [RocksIterator]; caller must close it
	public RocksIterator newIterator(ColumnFamilyHandle cf) {
		return RocksDB.createIteratorCf(ptr(), readOpts.ptr(), cf);
	}

	/// Returns a new iterator scoped to `cf` using the supplied [ReadOptions].
	///
	/// @param cf          target column family
	/// @param readOptions read options (e.g. snapshot)
	/// @return a new [RocksIterator]; caller must close it
	public RocksIterator newIterator(ColumnFamilyHandle cf, ReadOptions readOptions) {
		return RocksDB.createIteratorCf(ptr(), readOptions.ptr(), cf);
	}

	// -----------------------------------------------------------------------
	// Flush
	// -----------------------------------------------------------------------

	/// Flushes all memtable data to SST/blob files. Blocks when [FlushOptions#isWait()] is `true`.
	///
	/// @param flushOptions options controlling flush behavior (e.g. wait)
	public void flush(FlushOptions flushOptions) {
		RocksDB.flush(ptr(), flushOptions);
	}

	/// Flushes the WAL to disk.
	///
	/// @param sync if `true`, performs an `fsync` after writing
	public void flushWal(boolean sync) {
		RocksDB.flushWal(ptr(), sync);
	}

	// -----------------------------------------------------------------------
	// Flush — column family overloads
	// -----------------------------------------------------------------------

	/// Flushes the memtable for `cf` to SST/blob files.
	///
	/// @param cf           target column family
	/// @param flushOptions options controlling flush behavior
	public void flush(ColumnFamilyHandle cf, FlushOptions flushOptions) {
		RocksDB.flushCf(ptr(), flushOptions, cf);
	}

	// -----------------------------------------------------------------------
	// DB Properties
	// -----------------------------------------------------------------------

	/// Returns the value of a DB property as a string, or [Optional#empty()] if not supported.
	/// Use [Property#BLOB_STATS], [Property#NUM_BLOB_FILES], etc. for blob-specific metrics.
	///
	/// @param property the property to query
	/// @return the property value, or empty if the property is not supported
	public Optional<String> getProperty(Property property) {
		return RocksDB.getProperty(ptr(), property);
	}

	/// Returns the value of a numeric DB property, or [OptionalLong#empty()] if not supported.
	///
	/// @param property the numeric property to query
	/// @return the property value as a `long`, or empty if not supported
	public OptionalLong getLongProperty(Property property) {
		return RocksDB.getLongProperty(ptr(), property);
	}

	// -----------------------------------------------------------------------
	// DB Properties — column family overloads
	// -----------------------------------------------------------------------

	/// Returns the value of a property for `cf`, or [Optional#empty()] if not supported.
	///
	/// @param cf       target column family
	/// @param property the property to query
	/// @return the property value, or empty if not supported
	public Optional<String> getProperty(ColumnFamilyHandle cf, Property property) {
		return RocksDB.getPropertyCf(ptr(), cf, property);
	}

	/// Returns the value of a numeric property for `cf`, or [OptionalLong#empty()] if not supported.
	///
	/// @param cf       target column family
	/// @param property the property to query
	/// @return the numeric property value, or empty if not supported
	public OptionalLong getLongProperty(ColumnFamilyHandle cf, Property property) {
		return RocksDB.getLongPropertyCf(ptr(), cf, property);
	}

	// -----------------------------------------------------------------------
	// SST File Ingest
	// -----------------------------------------------------------------------

	/// Ingests SST files produced by [SstFileWriter] into the database.
	///
	/// @param files paths to SST files to ingest
	/// @param options options controlling the ingest behavior
	public void ingestExternalFile(List<Path> files, IngestExternalFileOptions options) {
		RocksDB.ingestExternalFile(ptr(), files, options);
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		writeOpts.close();
		readOpts.close();
		RocksDB.close(ptr);
	}
}
