package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// FFM wrapper for a read-write `rocksdb_t*` instance.
///
/// Obtain via [RocksDB#openReadWrite] or [RocksDB#openTtl].
///
/// ```
/// try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(path, dbOpts)) {
///     db.put("key".getBytes(), "value".getBytes());
///     byte[] value = db.get("key".getBytes());
/// }
/// ```
public final class ReadWriteDB extends NativeDB
		implements RocksDBReadOperations, RocksDBWriteOperations, RocksDBCompactionOperations, RocksDBMonitoringOperations,
		RocksDBTracingOperations {

	ReadWriteDB(MemorySegment ptr) {
		super(ptr);
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryCloseResource(MemorySegment ptr) throws Throwable {
		NativeCalls.closeDb(ptr);
	}
}
