package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.time.Duration;

/// FFM wrapper for a TTL-aware read-write `rocksdb_t*` instance.
///
/// Obtain via [RocksDB#openTtl].
///
/// Keys are lazily expired during the next compaction that covers their range.
/// A [Duration#ZERO] TTL disables expiry entirely.
///
/// ```
/// try (var db = RocksDB.openTtl(path, Duration.ofSeconds(60))) {
///     db.put("key".getBytes(), "value".getBytes());
///     byte[] value = db.get("key".getBytes());
/// }
/// ```
public final class TtlDB extends NativeObjectWithChildren implements RocksDBReadOperations, RocksDBWriteOperations {

	private final Duration ttl;

	TtlDB(MemorySegment ptr, Duration ttl) {
		super(ptr);
		this.ttl = ttl;
	}

	@Override
	public MemorySegment dbPtr() {
		return ptr();
	}

	/// Returns the TTL configured when this database was opened.
	/// [Duration#ZERO] means expiry is disabled.
	///
	/// @return the configured TTL duration
	public Duration getTtl() {
		return ttl;
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryCloseResource(MemorySegment ptr) throws Throwable {
		RocksDB.close(ptr);
	}
}
