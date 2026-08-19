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
public final class ReadWriteDB extends NativeObject implements RocksDbWriteOps {

	private final WriteOptions writeOpts;
	private final ReadOptions readOpts;

	ReadWriteDB(MemorySegment ptr) {
		super(ptr);
		this.writeOpts = RocksDB.DEFAULT_WRITE_OPTIONS;
		this.readOpts = RocksDB.DEFAULT_READ_OPTIONS;
	}

	@Override
	public MemorySegment dbPtr() {
		return ptr();
	}

	@Override
	public WriteOptions defaultWriteOpts() {
		return writeOpts;
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
