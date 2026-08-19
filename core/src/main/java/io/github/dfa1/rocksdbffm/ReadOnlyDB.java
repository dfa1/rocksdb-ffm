package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// FFM wrapper for a read-only `rocksdb_t*` instance.
///
/// Obtain via [RocksDB#openReadOnly].
///
/// ```
/// try (var db = RocksDB.openReadOnly(path)) {
///     byte[] value = db.get("key".getBytes());
/// }
/// ```
public final class ReadOnlyDB extends NativeObject implements ReadColumnFamilyOperations {

	ReadOnlyDB(MemorySegment ptr) {
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
