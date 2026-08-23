package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// Shared read-only inspection operations for every wrapper around a plain `rocksdb_t*` —
/// including [TransactionDB], reached through its base DB pointer
/// ([NativeObjectWithBaseDb#dbPtr()]). Unlike [RocksDBReadOperations]'s put/get/merge/delete
/// surface, none of these calls need a per-type `MethodHandle`: `rocksdb_livefiles()` is the
/// same native symbol regardless of which DB type's `rocksdb_t*` it's called on, so there is no
/// "genuinely different native symbol" reason (per the project's no-shared-call-site rule) to
/// hand-duplicate it the way `TransactionDB` must for `rocksdb_transactiondb_property_value`
/// and friends.
///
/// Currently just live SST file metadata, but the natural home for future inspection surfaces
/// that only need `rocksdb_t*` — e.g. a `rocksdb_get_livefiles_storage_info`-based wrapper
/// covering WAL/MANIFEST/CURRENT files too, not just SST.
///
/// Deliberately its own interface rather than folded into [RocksDBReadOperations] via
/// inheritance: monitoring is a distinct concern from reading data, and keeping it separate
/// means [TransactionDB] — which implements neither `RocksDBReadOperations` nor
/// `RocksDBWriteOperations` — can still pick up `getLiveFiles()` on its own, without dragging
/// in (or being implied to support) either of those. Every `RocksDBReadOperations` implementor
/// (`ReadWriteDB`, `TtlDB`, `BlobDB`, `ReadOnlyDB`, `SecondaryDB`, `OptimisticTransactionDB`)
/// also declares `implements MonitoringOperations` directly, alongside it.
public interface MonitoringOperations {

	/// Returns the native `rocksdb_t*` pointer to operate on.
	///
	/// @return the native database pointer
	MemorySegment dbPtr();

	/// Captures metadata for every live SST file currently belonging to this database —
	/// column family, level, size, key range, sequence number range, and entry/deletion
	/// counts. Fields are read from native memory lazily, one native call per field actually
	/// accessed, so scanning a single field across many files does not pay for the rest.
	///
	/// @return a new [LiveFiles] snapshot; caller must close it
	default LiveFiles getLiveFiles() {
		return LiveFiles.fetch(dbPtr());
	}
}
