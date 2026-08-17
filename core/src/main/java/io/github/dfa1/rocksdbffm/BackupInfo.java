package io.github.dfa1.rocksdbffm;

import java.time.Instant;

/// Immutable snapshot of a single RocksDB backup entry.
///
/// Instances are obtained from [BackupEngine#getBackupInfo()].
/// There is no native resource associated with this record — the native
/// `rocksdb_backup_engine_info_t*` is acquired, iterated, and destroyed
/// entirely inside [BackupEngine#getBackupInfo()].
///
/// ```
/// List<BackupInfo> infos = engine.getBackupInfo();
/// BackupInfo latest = infos.getLast();
/// System.out.printf("backup %s — %d bytes, %d files%n",
///     latest.backupId(), latest.size().toBytes(), latest.numberOfFiles());
/// ```
///
/// @param backupId unique identifier assigned by the engine
/// @param timestamp point in time when the backup was created
/// @param size total size of all files belonging to this backup
/// @param numberOfFiles number of SST/WAL/MANIFEST files in this backup
public record BackupInfo(
		BackupId backupId,
		Instant timestamp,
		MemorySize size,
		long numberOfFiles
) {
}
