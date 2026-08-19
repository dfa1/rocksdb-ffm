package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for a RocksDB secondary instance (`rocksdb_t*` opened via
/// `rocksdb_open_as_secondary`).
///
/// A secondary instance is a _read-only replica_ of a primary database.
/// It tails the primary's WAL and MANIFEST files from a dedicated
/// `secondaryPath` directory. Call [#tryCatchUpWithPrimary()] to
/// apply any new writes that the primary has flushed since the last catch-up.
///
/// Write operations are not available on a secondary instance; attempting a
/// write via the underlying `rocksdb_t*` would return an error from RocksDB.
///
/// ```
/// // Primary (already open elsewhere)
/// try (Options opts = Options.newOptions().setCreateIfMissing(true);
///      SecondaryDB secondary = SecondaryDB.open(opts, primaryPath, secondaryPath)) {
///     // Catch up with whatever the primary has written
///     secondary.tryCatchUpWithPrimary();
///     byte[] value = secondary.get("key".getBytes());
/// }
/// ```
public final class SecondaryDB extends NativeObject implements RocksDbReadOps {

	// -----------------------------------------------------------------------
	// Method handles unique to SecondaryDB
	// -----------------------------------------------------------------------

	/// `void rocksdb_try_catch_up_with_primary(rocksdb_t* db, char** errptr);`
	private static final MethodHandle MH_CATCH_UP;

	static {
		MH_CATCH_UP = NativeLibrary.lookup("rocksdb_try_catch_up_with_primary",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	SecondaryDB(MemorySegment ptr) {
		super(ptr);
	}

	@Override
	public MemorySegment dbPtr() {
		return ptr();
	}

	// -----------------------------------------------------------------------
	// Catch-up
	// -----------------------------------------------------------------------

	/// Tries to catch up with the primary by reading and applying any new records
	/// from the primary's WAL and newly flushed SST files.
	///
	/// This is a best-effort operation; the secondary may still lag the primary
	/// if the primary has not yet flushed a write.
	public void tryCatchUpWithPrimary() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_CATCH_UP.invokeExact(ptr(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("tryCatchUpWithPrimary failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		RocksDB.close(ptr);
	}
}
