package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// FFM wrapper for a read-write `rocksdb_t*` instance.
///
/// Obtain via [RocksDB#openReadWrite] or [RocksDB#openTtl].
///
/// ```
/// try (var db = RocksDB.openReadWrite(path)) {
///     db.put("key".getBytes(), "value".getBytes());
///     byte[] value = db.get("key".getBytes());
/// }
/// ```
public final class ReadWriteDB extends NativeObjectWithChildren implements RocksDBReadOperations, RocksDBWriteOperations {

	ReadWriteDB(MemorySegment ptr) {
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
	protected void tryCloseResource(MemorySegment ptr) throws Throwable {
		RocksDB.close(ptr);
	}
}
