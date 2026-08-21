package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_snapshot_t`.
///
/// A snapshot provides a consistent, point-in-time view of the database.
/// Reads performed with a snapshot set on [ReadOptions] see only data
/// that was committed before the snapshot was taken.
///
/// Obtain via [RocksDBReadOperations#getSnapshot()] (implemented by [ReadWriteDB], [TtlDB],
/// [BlobDB], and [OptimisticTransactionDB]), [TransactionDB#getSnapshot()], or
/// [Transaction#getSnapshot()]. Always close after use to release the underlying native
/// snapshot — and close it before closing the DB (or transaction) that produced it; closing
/// out of order is not an error (the release is silently skipped, since the DB's own teardown
/// already destroyed the snapshot internally), but the snapshot's data is only guaranteed
/// valid while the DB itself is still open.
///
/// ```
/// try (Snapshot snap = db.getSnapshot();
///      ReadOptions ro = ReadOptions.newReadOptions().setSnapshot(snap)) {
///     byte[] v1 = db.get(ro, key);
///     db.put(key, newValue);
///     byte[] v2 = db.get(ro, key); // still returns v1 — consistent read
/// }
/// ```
public final class Snapshot extends NativeObject {

	/// `uint64_t rocksdb_snapshot_get_sequence_number(const rocksdb_snapshot_t* snapshot);`
	private static final MethodHandle MH_SEQUENCE_NUMBER;
	/// `void rocksdb_release_snapshot(rocksdb_t* db, const rocksdb_snapshot_t* snapshot);`
	private static final MethodHandle MH_RELEASE;
	// rocksdb_free(ptr*) — for Transaction snapshots

	static {
		MH_SEQUENCE_NUMBER = NativeLibrary.lookup("rocksdb_snapshot_get_sequence_number",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_RELEASE = NativeLibrary.lookup("rocksdb_release_snapshot",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	/// DB pointer used to release the snapshot; NULL signals that `rocksdb_free`
	/// should be used instead (transaction snapshot ownership model).
	private final MemorySegment dbPtr;

	/// The DB object that produced this snapshot, used only as a liveness check in
	/// [#tryClose(MemorySegment)] — NULL for transaction-owned snapshots, which have no such
	/// hazard (`rocksdb_free` doesn't touch the DB at all).
	///
	/// If that DB is closed before this snapshot is, `dbPtr` becomes a dangling pointer: the
	/// DB's own teardown already destroyed every snapshot it owned internally, and calling
	/// `rocksdb_release_snapshot` again would be a use-after-free. Checking `owningDb.ptr()`
	/// first turns that into a clean skip instead: it throws `IllegalStateException`, which
	/// propagates out of `tryClose` and is caught by `NativeObject#close()`'s catch-all, so the
	/// (already-unnecessary) release call is simply never made.
	private final NativeObject owningDb;

	/// Creates a snapshot owned by a RocksDB or TransactionDB instance.
	///
	/// @param owningDb the DB object `dbPtr` belongs to, checked for liveness before release
	/// @param dbPtr    native pointer passed to `rocksdb_release_snapshot`
	/// @param ptr      the native snapshot pointer
	Snapshot(NativeObject owningDb, MemorySegment dbPtr, MemorySegment ptr) {
		super(ptr);
		this.owningDb = owningDb;
		this.dbPtr = dbPtr;
	}

	/// Creates a snapshot owned by a Transaction instance.
	/// Released via `rocksdb_free` rather than `rocksdb_release_snapshot`.
	Snapshot(MemorySegment ptr) {
		super(ptr);
		this.owningDb = null;
		this.dbPtr = MemorySegment.NULL;
	}

	/// Returns the sequence number at which this snapshot was taken.
	/// Useful for ordering and debugging.
	///
	/// @return the snapshot's sequence number
	public SequenceNumber sequenceNumber() {
		try {
			return SequenceNumber.of((long) MH_SEQUENCE_NUMBER.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("snapshot sequenceNumber failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		if (owningDb == null) {
			RocksDB.free(ptr);
			return;
		}
		owningDb.ptr(); // liveness check: throws if the owning DB is already closed
		MH_RELEASE.invokeExact(dbPtr, ptr);
	}

}
