package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Map;
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
	private final Map<String, Duration> columnFamilyTtls;

	TtlDB(MemorySegment ptr, Duration ttl, Map<String, Duration> columnFamilyTtls) {
		super(ptr);
		this.ttl = ttl;
		this.columnFamilyTtls = columnFamilyTtls;
	}

	/// Returns the TTL passed when this database was opened with a single TTL, or empty when it was opened
	/// with one TTL per column family (see [#getColumnFamilyTtls()]). The C API cannot report a database's
	/// TTL, so this is only what the caller supplied. [Duration#ZERO] means expiry is disabled.
	///
	/// @return the TTL passed at open, or empty for a per-column-family open
	public Optional<Duration> getTtl() {
		return Optional.ofNullable(ttl);
	}

	/// Returns the TTL of each column family, by name, as passed to the per-column-family open, in descriptor
	/// order; empty when it was opened with a single TTL (see [#getTtl()]). Only what the caller supplied:
	/// the C API cannot report TTLs, and column families created after open are not included.
	/// [Duration#ZERO] means expiry is disabled for that family.
	///
	/// @return an unmodifiable name-to-TTL map, empty for a single-TTL open
	public Map<String, Duration> getColumnFamilyTtls() {
		return columnFamilyTtls;
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryCloseResource(MemorySegment ptr) throws Throwable {
		NativeCalls.closeDb(ptr);
	}
}
