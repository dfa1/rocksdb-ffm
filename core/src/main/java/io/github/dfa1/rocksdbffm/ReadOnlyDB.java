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
public final class ReadOnlyDB extends NativeObject implements RocksDbReadOps {

	private final ReadOptions readOpts;

	ReadOnlyDB(MemorySegment ptr) {
		super(ptr);
		this.readOpts = RocksDB.DEFAULT_READ_OPTIONS;
	}

	@Override
	public MemorySegment dbPtr() {
		return ptr();
	}

	@Override
	public ReadOptions defaultReadOpts() {
		return readOpts;
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		RocksDB.close(ptr);
	}
}
