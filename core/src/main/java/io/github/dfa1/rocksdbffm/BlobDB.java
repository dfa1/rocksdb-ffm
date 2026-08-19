package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

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
public final class BlobDB extends NativeObject implements ReadColumnFamilyOperations, WriteOperations {

	BlobDB(MemorySegment ptr) {
		super(ptr);
	}

	@Override
	public MemorySegment dbPtr() {
		return ptr();
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		RocksDB.close(ptr);
	}
}
