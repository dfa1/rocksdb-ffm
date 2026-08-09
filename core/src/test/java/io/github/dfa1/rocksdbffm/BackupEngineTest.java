package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupEngineTest {

	@Test
	void createNewBackup_isVisibleInGetBackupInfo(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dir.resolve("db"));
		     var engine = BackupEngine.open(opts, dir.resolve("backup"))) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			engine.createNewBackup(db, true);

			// Then
			List<BackupInfo> result = engine.getBackupInfo();
			assertThat(result).hasSize(1);
			assertThat(result.getFirst().backupId()).isEqualTo(BackupId.of(1));
			assertThat(result.getFirst().numberOfFiles()).isGreaterThan(0);
		}
	}

	@Test
	void getBackupInfo_isEmptyForFreshEngine(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dir.resolve("db"));
		     var engine = BackupEngine.open(opts, dir.resolve("backup"))) {

			// When
			var result = engine.getBackupInfo();

			// Then
			assertThat(result).isEmpty();
		}
	}

	@Test
	void verifyBackup_succeedsForValidBackup(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dir.resolve("db"));
		     var engine = BackupEngine.open(opts, dir.resolve("backup"))) {

			db.put("k".getBytes(), "v".getBytes());
			engine.createNewBackup(db);

			// When / Then — must not throw
			engine.verifyBackup(BackupId.of(1));
		}
	}

	@Test
	void verifyBackup_throwsForUnknownBackupId(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dir.resolve("db"));
		     var engine = BackupEngine.open(opts, dir.resolve("backup"))) {

			engine.createNewBackup(db);

			// When
			var thrown = assertThatThrownBy(() -> engine.verifyBackup(BackupId.of(42)));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void purgeOldBackups_keepsOnlyMostRecent(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dir.resolve("db"));
		     var engine = BackupEngine.open(opts, dir.resolve("backup"))) {

			engine.createNewBackup(db);
			engine.createNewBackup(db);
			engine.createNewBackup(db);

			// When
			engine.purgeOldBackups(1);

			// Then
			var result = engine.getBackupInfo();
			assertThat(result).hasSize(1);
			assertThat(result.getFirst().backupId()).isEqualTo(BackupId.of(3));
		}
	}

	@Test
	void restoreDbFromLatestBackup_recoversData(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreDir = dir.resolve("restore");

		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			db.put("k".getBytes(), "v".getBytes());
			engine.createNewBackup(db);

			// When
			try (var restoreOptions = RestoreOptions.create()) {
				engine.restoreDbFromLatestBackup(restoreDir, restoreOptions);
			}
		}

		// Then
		try (var opts = Options.newOptions();
		     var restored = RocksDB.open(opts, restoreDir)) {
			assertThat(restored.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void restoreDbFromLatestBackup_withSeparateWalDir(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreDir = dir.resolve("restore");
		var walDir = dir.resolve("wal");

		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			// flushBeforeBackup=true: the value must land in an SST file rather than
			// staying WAL-only, since we open the restored DB below without pointing
			// it back at walDir — a WAL-only write would be invisible there.
			db.put("k".getBytes(), "v".getBytes());
			engine.createNewBackup(db, true);

			// When
			try (var restoreOptions = RestoreOptions.create()) {
				engine.restoreDbFromLatestBackup(restoreDir, walDir, restoreOptions);
			}
		}

		// Then
		try (var opts = Options.newOptions();
		     var restored = RocksDB.open(opts, restoreDir)) {
			assertThat(restored.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void restoreDbFromBackup_selectsSpecificBackupId(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");
		var restoreDir = dir.resolve("restore");

		// Given — two backups with different content
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dbDir);
		     var engine = BackupEngine.open(opts, backupDir)) {

			db.put("k".getBytes(), "first".getBytes());
			engine.createNewBackup(db, true);

			db.put("k".getBytes(), "second".getBytes());
			engine.createNewBackup(db, true);

			// When — restore the first backup, not the latest
			try (var restoreOptions = RestoreOptions.create()) {
				engine.restoreDbFromBackup(BackupId.of(1), restoreDir, restoreOptions);
			}
		}

		// Then
		try (var opts = Options.newOptions();
		     var restored = RocksDB.open(opts, restoreDir)) {
			assertThat(restored.get("k".getBytes())).isEqualTo("first".getBytes());
		}
	}

	@Test
	void open_withBackupEngineOptionsAndEnv(@TempDir Path dir) {
		var dbDir = dir.resolve("db");
		var backupDir = dir.resolve("backup");

		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.open(opts, dbDir);
		     var env = Env.defaultEnv();
		     var beOpts = BackupEngineOptions.create(backupDir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			try (var engine = BackupEngine.open(beOpts, env)) {
				engine.createNewBackup(db);

				// Then
				assertThat(engine.getBackupInfo()).hasSize(1);
			}
		}
	}

	@Test
	void close_isIdempotent(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.open(opts, dir.resolve("db"));
		var engine = BackupEngine.open(opts, dir.resolve("backup"));

		// When
		engine.close();

		// Then — closing twice must not crash the JVM
		engine.close();

		db.close();
		opts.close();
	}
}
