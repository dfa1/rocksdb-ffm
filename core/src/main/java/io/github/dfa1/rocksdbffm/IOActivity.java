package io.github.dfa1.rocksdbffm;

/// Classifies the operation a write belongs to, for I/O tracing/stats, matching the C++
/// `Env::IOActivity` enum.
///
/// RocksDB documents this field as `EXPERIMENTAL` and "for RocksDB internal use only"
/// (`rocksdb/include/rocksdb/options.h`) — exposed here for completeness since the C API maps
/// it (`rocksdb_writeoptions_set_io_activity`), but treat it as unstable.
public enum IOActivity {
	/// A flush.
	FLUSH(0),
	/// A compaction.
	COMPACTION(1),
	/// Opening the database.
	DB_OPEN(2),
	/// A point lookup.
	GET(3),
	/// A batched point lookup.
	MULTI_GET(4),
	/// Reading via a [RocksIterator].
	DB_ITERATOR(5),
	/// Verifying the whole database's checksums.
	VERIFY_DB_CHECKSUM(6),
	/// Verifying individual file checksums.
	VERIFY_FILE_CHECKSUMS(7),
	/// A wide-column point lookup.
	GET_ENTITY(8),
	/// A batched wide-column point lookup.
	MULTI_GET_ENTITY(9),
	/// Reading file checksums from the current manifest.
	GET_FILE_CHECKSUMS_FROM_CURRENT_MANIFEST(10),
	/// No specific activity recorded (default).
	UNKNOWN(0xFF);

	private final int value;

	IOActivity(int value) {
		this.value = value;
	}

	// don't expose this
	int getValue() {
		return value;
	}

	static IOActivity fromValue(int value) {
		return switch (value) {
			case 0 -> FLUSH;
			case 1 -> COMPACTION;
			case 2 -> DB_OPEN;
			case 3 -> GET;
			case 4 -> MULTI_GET;
			case 5 -> DB_ITERATOR;
			case 6 -> VERIFY_DB_CHECKSUM;
			case 7 -> VERIFY_FILE_CHECKSUMS;
			case 8 -> GET_ENTITY;
			case 9 -> MULTI_GET_ENTITY;
			case 10 -> GET_FILE_CHECKSUMS_FROM_CURRENT_MANIFEST;
			default -> UNKNOWN;
		};
	}
}
