package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Optional;

/// FFM wrapper for a TTL-aware read-write `rocksdb_t*` instance.
///
/// Obtain via [RocksDB#openTtl].
///
/// Keys are lazily expired during the next compaction that covers their range.
/// A [Duration#ZERO] TTL disables expiry entirely.
///
/// ```
/// try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openTtl(dbOpts, path, Duration.ofSeconds(60))) {
///     db.put("key".getBytes(), "value".getBytes());
///     byte[] value = db.get("key".getBytes());
/// }
/// ```
public final class TtlDB extends NativeDB
		implements RocksDBReadOperations, RocksDBWriteOperations, RocksDBCompactionOperations, RocksDBMonitoringOperations,
		RocksDBTracingOperations {

	private final Duration ttl; // null for a per-column-family open

	TtlDB(MemorySegment ptr, Duration ttl) {
		super(ptr);
		this.ttl = ttl;
	}

	/// Returns the TTL passed when this database was opened with a single TTL, or empty when it was opened
	/// with one TTL per column family. The C API cannot report a database's TTL, so this is only what the
	/// caller supplied. [Duration#ZERO] means expiry is disabled.
	///
	/// @return the TTL passed at open, or empty for a per-column-family open
	public Optional<Duration> getTtl() {
		return Optional.ofNullable(ttl);
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryCloseResource(MemorySegment ptr) throws Throwable {
		NativeCalls.closeDb(ptr);
	}
}
