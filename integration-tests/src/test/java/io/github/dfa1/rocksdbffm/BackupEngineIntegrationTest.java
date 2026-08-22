package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupEngineIntegrationTest {

	@Test
	void createAndRestoreLatestBackup(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreDir = dir.resolve("restore");

		// Given — open DB, write data, create backup
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			db.put("k1".getBytes(), "v1".getBytes());
			db.put("k2".getBytes(), "v2".getBytes());
			engine.createNewBackup(db);

			List<BackupInfo> infos = engine.getBackupInfo();
			assertThat(infos).hasSize(1);
			assertThat(infos.getFirst().backupId()).isEqualTo(BackupId.of(1));
			assertThat(infos.getFirst().numberOfFiles()).isGreaterThan(0);
			assertThat(infos.getFirst().timestamp()).isAfter(Instant.EPOCH);
			assertThat(infos.getFirst().size().toBytes()).isGreaterThan(0);
		}

		// When — restore from latest backup
		try (var env = Env.defaultEnv();
		     var beOpts = BackupEngineOptions.create(backupDir);
		     var restOpts = RestoreOptions.create();
		     var engine = BackupEngine.open(beOpts, env)) {

			engine.restoreDbFromLatestBackup(restoreDir, restOpts);
		}

		// Then — restored DB contains original data
		try (var opts = Options.newOptions();
		     var db = RocksDB.openReadWrite(opts, restoreDir)) {

			assertThat(db.get("k1".getBytes())).isEqualTo("v1".getBytes());
			assertThat(db.get("k2".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	@Test
	void multipleBackupsAndRestoreByBackupId(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreV1 = dir.resolve("restore-v1");
		var restoreV2 = dir.resolve("restore-v2");

		// Given — two backups at different states
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			db.put("k".getBytes(), "v1".getBytes());
			engine.createNewBackup(db, true);

			db.put("k".getBytes(), "v2".getBytes());
			engine.createNewBackup(db, true);

			assertThat(engine.getBackupInfo()).hasSize(2);

			// When — restore each backup by ID
			try (var restOpts = RestoreOptions.create()) {
				engine.restoreDbFromBackup(BackupId.of(1), restoreV1, restOpts);
				engine.restoreDbFromBackup(BackupId.of(2), restoreV2, restOpts);
			}
		}

		// Then — each restored directory reflects the state at backup time
		try (var opts = Options.newOptions();
		     var db1 = RocksDB.openReadWrite(opts, restoreV1);
		     var db2 = RocksDB.openReadWrite(opts, restoreV2)) {

			assertThat(db1.get("k".getBytes())).isEqualTo("v1".getBytes());
			assertThat(db2.get("k".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	@Test
	void purgeOldBackups(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");

		// Given — three backups
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			engine.createNewBackup(db);
			engine.createNewBackup(db);
			engine.createNewBackup(db);
			assertThat(engine.getBackupInfo()).hasSize(3);

			// When — keep only the 2 most recent
			engine.purgeOldBackups(2);

			// Then
			List<BackupInfo> infos = engine.getBackupInfo();
			assertThat(infos).hasSize(2);
			assertThat(infos.getFirst().backupId()).isEqualTo(BackupId.of(2));
			assertThat(infos.getLast().backupId()).isEqualTo(BackupId.of(3));
		}
	}

	@Test
	void verifyBackup(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");

		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			db.put("k".getBytes(), "v".getBytes());
			engine.createNewBackup(db);

			// When
			engine.verifyBackup(BackupId.of(1));

			// Then — verify should succeed for a valid backup
		}
	}

	@Test
	void verifyNonExistentBackupThrows(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			engine.createNewBackup(db);

			// When
			var thrown = assertThatThrownBy(() -> engine.verifyBackup(BackupId.of(999)));

			// Then — verifying a non-existent ID throws
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void backupEngineOptions_fluent(@TempDir Path dir) {
		var backupDir = dir.resolve("backup");

		// Given / When
		try (var opts = BackupEngineOptions.create(backupDir)
				.setShareTableFiles(true)
				.setSync(false)
				.setDestroyOldData(false)
				.setBackupLogFiles(true)
				.setBackupRateLimit(MemorySize.ofMB(100))
				.setRestoreRateLimit(MemorySize.ofMB(200))
				.setMaxBackgroundOperations(2)
				.setCallbackTriggerIntervalSize(MemorySize.ofMB(8))
				.setMaxValidBackupsToOpen(-1)) {

			// Then — all options round-trip correctly
			assertThat(opts.isShareTableFiles()).isTrue();
			assertThat(opts.isSync()).isFalse();
			assertThat(opts.isDestroyOldData()).isFalse();
			assertThat(opts.isBackupLogFiles()).isTrue();
			assertThat(opts.getBackupRateLimit()).isEqualTo(MemorySize.ofMB(100));
			assertThat(opts.getRestoreRateLimit()).isEqualTo(MemorySize.ofMB(200));
			assertThat(opts.getMaxBackgroundOperations()).isEqualTo(2);
			assertThat(opts.getCallbackTriggerIntervalSize()).isEqualTo(MemorySize.ofMB(8));
			assertThat(opts.getMaxValidBackupsToOpen()).isEqualTo(-1);
		}
	}

	@Test
	void openWithBackupEngineOptions(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreDir = dir.resolve("restore");

		// Given — write data
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbDir)) {
			db.put("key".getBytes(), "value".getBytes());

			// When — backup via explicit BackupEngineOptions + Env
			try (var env = Env.defaultEnv();
			     var beOpts = BackupEngineOptions.create(backupDir)
					     .setShareTableFiles(true)
					     .setMaxBackgroundOperations(1);
			     var engine = BackupEngine.open(beOpts, env)) {

				engine.createNewBackup(db);
				assertThat(engine.getBackupInfo()).hasSize(1);

				// When — restore
				try (var restOpts = RestoreOptions.create()) {
					engine.restoreDbFromLatestBackup(restoreDir, restOpts);
				}
			}
		}

		// Then
		try (var opts = Options.newOptions();
		     var db = RocksDB.openReadWrite(opts, restoreDir)) {
			assertThat(db.get("key".getBytes())).isEqualTo("value".getBytes());
		}
	}

	@Test
	void backupAndRestoreBlobDb(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreDir = dir.resolve("restore");
		var value = new byte[(int) MemorySize.ofKB(64).toBytes()];

		// Given — a BlobDB with a value large enough to land in a blob file
		try (var opts = Options.newOptions()
				.setCreateIfMissing(true)
				.setEnableBlobFiles(true)
				.setMinBlobSize(MemorySize.ofBytes(0));
		     var db = RocksDB.openBlob(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			db.put("k".getBytes(), value);

			// When
			engine.createNewBackup(db, true);

			// Then — backup is recorded
			assertThat(engine.getBackupInfo()).hasSize(1);

			try (var restOpts = RestoreOptions.create()) {
				engine.restoreDbFromLatestBackup(restoreDir, restOpts);
			}
		}

		// Then — restored BlobDB contains the original value
		try (var opts = Options.newOptions();
		     var restored = RocksDB.openBlob(opts, restoreDir)) {
			assertThat(restored.get("k".getBytes())).isEqualTo(value);
		}
	}

	@Test
	void backupAndRestoreTtlDb(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreDir = dir.resolve("restore");
		var ttl = Duration.ofSeconds(60);

		// Given — a TtlDB
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dbDir, ttl);
		     var engine = BackupEngine.open(opts, backupDir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			engine.createNewBackup(db);

			// Then — backup is recorded
			assertThat(engine.getBackupInfo()).hasSize(1);

			try (var restOpts = RestoreOptions.create()) {
				engine.restoreDbFromLatestBackup(restoreDir, restOpts);
			}
		}

		// Then — restored TtlDB contains the original value
		try (var opts = Options.newOptions();
		     var restored = RocksDB.openTtl(opts, restoreDir, ttl)) {
			assertThat(restored.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void restoreDbFromBackup_withSeparateWalDir(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreDir = dir.resolve("restore");
		var walDir = dir.resolve("wal");

		// Given — two backups with different content, flushed so the restored DB
		// (opened without pointing back at walDir) can still see the data
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			db.put("k".getBytes(), "first".getBytes());
			engine.createNewBackup(db, true);

			db.put("k".getBytes(), "second".getBytes());
			engine.createNewBackup(db, true);

			// When — restore the first backup specifically, with WAL files
			// redirected to a separate directory
			try (var restOpts = RestoreOptions.create()) {
				engine.restoreDbFromBackup(BackupId.of(1), restoreDir, walDir, restOpts);
			}
		}

		// Then — restored DB reflects the state of the selected backup
		try (var opts = Options.newOptions();
		     var restored = RocksDB.openReadWrite(opts, restoreDir)) {
			assertThat(restored.get("k".getBytes())).isEqualTo("first".getBytes());
		}
	}

	@Test
	void restoreOptions_keepLogFiles() {
		// Given / When / Then — RestoreOptions can be created and configured
		try (var restOpts = RestoreOptions.create().setKeepLogFiles(true)) {
			assertThat(restOpts).isNotNull();
		}
	}
}
