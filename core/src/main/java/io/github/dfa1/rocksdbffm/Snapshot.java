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
/// Obtain via [RocksDBReadOperations#getSnapshot()] (implemented by [ReadWriteDB],
/// [ReadOnlyDB], [TtlDB], [BlobDB], [SecondaryDB], and [OptimisticTransactionDB]),
/// [TransactionDB#getSnapshot()], or [Transaction#getSnapshot()]. Always close after use to
/// release the underlying native snapshot. Closing the owning DB (or transaction) first is
/// safe — the DB registers every snapshot it produces as a [NativeObjectWithChildren] child and
/// releases any still-outstanding ones itself, synchronously, before destroying its own native
/// handle — so a snapshot never outlives the pointer its release call needs, whichever side
/// closes first.
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

	/// The DB object that produced this snapshot — NULL for transaction-owned snapshots, which
	/// register with nothing (`rocksdb_free` doesn't touch the DB at all, so there is no
	/// ordering hazard to guard against). Used in [#tryClose(MemorySegment)] only to
	/// unregister this snapshot from the DB's child set once released on its own, so a
	/// long-lived DB doesn't accumulate strong references to every snapshot it ever produced —
	/// registration itself happens in the constructor below, via
	/// [NativeObjectWithChildren#registerChild(NativeObject)].
	private final NativeObjectWithChildren owningDb;

	/// Creates a snapshot owned by a RocksDB or TransactionDB instance, and registers it with
	/// `owningDb` so it is released automatically if `owningDb` closes first.
	///
	/// @param owningDb the DB object `dbPtr` belongs to
	/// @param dbPtr    native pointer passed to `rocksdb_release_snapshot`
	/// @param ptr      the native snapshot pointer
	Snapshot(NativeObjectWithChildren owningDb, MemorySegment dbPtr, MemorySegment ptr) {
		super(ptr);
		this.owningDb = owningDb;
		this.dbPtr = dbPtr;
		owningDb.registerChild(this);
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
		return SequenceNumber.of(NativeFields.getLong(MH_SEQUENCE_NUMBER, ptr()));
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		if (owningDb == null) {
			RocksDB.free(ptr);
			return;
		}
		// Unregister first: if this runs because owningDb's own close() is sweeping its
		// children (owningDb closed first), dbPtr is still valid — owningDb captures its raw
		// pointer before nulling its own AtomicReference and only then closes children — so
		// this release call is always safe, whichever side closed first.
		owningDb.unregisterChild(this);
		MH_RELEASE.invokeExact(dbPtr, ptr);
	}

}
