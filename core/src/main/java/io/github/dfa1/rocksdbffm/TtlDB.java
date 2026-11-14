package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
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

	/// `rocksdb_column_family_handle_t* rocksdb_create_column_family_with_ttl(rocksdb_t* db, const rocksdb_options_t* column_family_options, const char* column_family_name, int ttl, char** errptr);`
	private static final MethodHandle MH_CREATE_CF_WITH_TTL;

	static {
		MH_CREATE_CF_WITH_TTL = NativeLibrary.lookup("rocksdb_create_column_family_with_ttl",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS));
	}

	private final Duration ttl; // null for a per-column-family open

	TtlDB(MemorySegment ptr, Duration ttl) {
		super(ptr);
		this.ttl = ttl;
	}

	/// Converts a TTL to the whole seconds native expects. Native treats a non-positive TTL as "never expire",
	/// so a negative one is rejected rather than silently disabling expiry, and so is one that does not fit.
	///
	/// @param ttl the time-to-live
	/// @return `ttl` in whole seconds
	/// @throws NullPointerException     if `ttl` is `null`
	/// @throws IllegalArgumentException if `ttl` is negative or longer than [Integer#MAX_VALUE] seconds
	static int toTtlSeconds(Duration ttl) {
		if (ttl.isNegative()) {
			throw new IllegalArgumentException("ttl must not be negative: " + ttl);
		}
		long seconds = ttl.toSeconds();
		if (seconds > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("ttl exceeds " + Integer.MAX_VALUE + " seconds: " + ttl);
		}
		return (int) seconds;
	}

	/// Returns the TTL passed when this database was opened with a single TTL, or empty when it was opened
	/// with one TTL per column family. The C API cannot report a database's TTL, so this is only what the
	/// caller supplied. [Duration#ZERO] means expiry is disabled.
	///
	/// @return the TTL passed at open, or empty for a per-column-family open
	public Optional<Duration> getTtl() {
		return Optional.ofNullable(ttl);
	}

	/// Creates a column family with the TTL given by [ColumnFamilyDescriptor#withTtl(Duration)]; a descriptor
	/// without one never expires its keys, the same as [Duration#ZERO]. It does *not* inherit the TTL this
	/// database was opened with: native applies a TTL per column family, and `0` means "never expire".
	/// The handle is owned by this database, like the ones returned by the open; closing it is a no-op.
	///
	/// @param descriptor name, options and optional TTL for the new column family
	/// @return handle to the newly created column family; owned by this database
	@Override
	public ColumnFamilyHandle createColumnFamily(ColumnFamilyDescriptor descriptor) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment handle = (MemorySegment) MH_CREATE_CF_WITH_TTL.invokeExact(
					dbPtr(), descriptor.options().ptr(), descriptor.name().toNative(arena), descriptor.ttlSeconds(), err);
			NativeCalls.checkError(err);
			return adopt(descriptor.name(), handle);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("createColumnFamily failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryCloseResource(MemorySegment ptr) throws Throwable {
		NativeCalls.closeDb(ptr);
	}
}
