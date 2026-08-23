package io.github.dfa1.rocksdbffm;

/// The role a file plays within a database, matching RocksDB's `FileType` enum. Used by
/// [LiveFileStorageInfo#fileType()] to distinguish SST files from the WAL, MANIFEST, and other
/// files needed to reconstruct the database.
public enum FileType {
	/// Write-ahead log file.
	WAL_FILE(0),
	/// The `LOCK` file.
	DB_LOCK_FILE(1),
	/// An SST file.
	TABLE_FILE(2),
	/// A MANIFEST file.
	DESCRIPTOR_FILE(3),
	/// The `CURRENT` file.
	CURRENT_FILE(4),
	/// A temporary file.
	TEMP_FILE(5),
	/// The current or an old `LOG` file.
	INFO_LOG_FILE(6),
	/// Metadata database file.
	META_DATABASE(7),
	/// The `IDENTITY` file.
	IDENTITY_FILE(8),
	/// The `OPTIONS` file.
	OPTIONS_FILE(9),
	/// A blob file.
	BLOB_FILE(10),
	/// Compaction progress file.
	COMPACTION_PROGRESS_FILE(11);

	private final int value;

	FileType(int value) {
		this.value = value;
	}

	int getValue() {
		return value;
	}

	static FileType fromValue(int v) {
		return switch (v) {
			case 0 -> WAL_FILE;
			case 1 -> DB_LOCK_FILE;
			case 2 -> TABLE_FILE;
			case 3 -> DESCRIPTOR_FILE;
			case 4 -> CURRENT_FILE;
			case 5 -> TEMP_FILE;
			case 6 -> INFO_LOG_FILE;
			case 7 -> META_DATABASE;
			case 8 -> IDENTITY_FILE;
			case 9 -> OPTIONS_FILE;
			case 10 -> BLOB_FILE;
			case 11 -> COMPACTION_PROGRESS_FILE;
			default -> TEMP_FILE;
		};
	}
}
