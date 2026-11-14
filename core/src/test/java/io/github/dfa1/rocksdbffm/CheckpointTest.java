package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CheckpointTest {

	// -----------------------------------------------------------------------
	// Basic export and read-only open
	// -----------------------------------------------------------------------

	@Test
	void exportTo_createsReadableSnapshot(@TempDir Path dir) {
		// Given
		var cpDir = dir.resolve("checkpoint");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			try (var cp = Checkpoint.newCheckpoint(db)) {
				cp.exportTo(cpDir);
			}
		}

		// Then — checkpoint is a valid read-only database
		try (var snap = RocksDB.openReadOnly(cpDir)) {
			assertThat(snap.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void snapshot_isIsolatedFromSubsequentWrites(@TempDir Path dir) {
		// Given
		var cpDir = dir.resolve("checkpoint");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts)) {
			db.put("k".getBytes(), "before".getBytes());

			try (var cp = Checkpoint.newCheckpoint(db)) {
				cp.exportTo(cpDir);
			}

			// When — write after checkpoint
			db.put("k".getBytes(), "after".getBytes());
		}

		// Then — snapshot still sees the value at checkpoint time
		try (var snap = RocksDB.openReadOnly(cpDir)) {
			assertThat(snap.get("k".getBytes())).isEqualTo("before".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Multiple checkpoints from one Checkpoint object
	// -----------------------------------------------------------------------

	@Test
	void multipleExports_captureProgressiveState(@TempDir Path dir) {
		// Given
		var cp1Dir = dir.resolve("checkpoint-1");
		var cp2Dir = dir.resolve("checkpoint-2");

		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var cp = Checkpoint.newCheckpoint(db)) {

			db.put("k".getBytes(), "v1".getBytes());
			cp.exportTo(cp1Dir);

			// When — mutate and take a second checkpoint with the same object
			db.put("k".getBytes(), "v2".getBytes());
			cp.exportTo(cp2Dir);
		}

		// Then — each snapshot reflects state at export time
		try (var snap1 = RocksDB.openReadOnly(cp1Dir)) {
			assertThat(snap1.get("k".getBytes())).isEqualTo("v1".getBytes());
		}
		try (var snap2 = RocksDB.openReadOnly(cp2Dir)) {
			assertThat(snap2.get("k".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// Absent key in snapshot
	// -----------------------------------------------------------------------

	@Test
	void snapshot_returnsNull_forKeyAbsentAtCheckpointTime(@TempDir Path dir) {
		// Given
		var cpDir = dir.resolve("checkpoint");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts)) {
			// checkpoint taken before any write
			try (var cp = Checkpoint.newCheckpoint(db)) {
				cp.exportTo(cpDir);
			}

			// When — write after checkpoint
			db.put("k".getBytes(), "v".getBytes());
		}

		// Then — snapshot does not see the post-checkpoint key
		try (var snap = RocksDB.openReadOnly(cpDir)) {
			assertThat(snap.get("k".getBytes())).isNull();
		}
	}

	// -----------------------------------------------------------------------
	// Delete visibility
	// -----------------------------------------------------------------------

	@Test
	void snapshot_preservesDeletedKey_thatWasDeletedAfterCheckpoint(@TempDir Path dir) {
		// Given
		var cpDir = dir.resolve("checkpoint");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts)) {
			db.put("k".getBytes(), "v".getBytes());

			try (var cp = Checkpoint.newCheckpoint(db)) {
				cp.exportTo(cpDir);
			}

			// When — delete after checkpoint
			db.delete("k".getBytes());
		}

		// Then — snapshot still has the key
		try (var snap = RocksDB.openReadOnly(cpDir)) {
			assertThat(snap.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// exportTo(Path) convenience overload (defaults to MemorySize.ZERO)
	// -----------------------------------------------------------------------

	@Test
	void exportTo_withoutLogSize_flushesMemtableToSst(@TempDir Path dir) throws IOException {
		// Given — the write stays in the memtable/WAL: no explicit flush
		var cpDir = dir.resolve("checkpoint");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var cp = Checkpoint.newCheckpoint(db)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			cp.exportTo(cpDir);
		}

		// Then
		var sstFiles = countFiles(cpDir, ".sst");
		assertThat(sstFiles).isPositive();
	}

	@Test
	void exportTo_withHugeLogSize_keepsDataInWal(@TempDir Path dir) throws IOException {
		// Given — contrast case: a threshold the WAL never reaches, so nothing is flushed
		var cpDir = dir.resolve("checkpoint");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var cp = Checkpoint.newCheckpoint(db)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			cp.exportTo(cpDir, MemorySize.ofBytes(Long.MAX_VALUE));
		}

		// Then
		var sstFiles = countFiles(cpDir, ".sst");
		var walFiles = countFiles(cpDir, ".log");
		assertThat(sstFiles).isZero();
		assertThat(walFiles).isPositive();
	}

	private static long countFiles(Path dir, String suffix) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.getFileName().toString().endsWith(suffix)).count();
		}
	}

	// -----------------------------------------------------------------------
	// newCheckpoint across the other DB types
	// -----------------------------------------------------------------------

	@Test
	void newCheckpoint_fromBlobFiles_createsReadableSnapshot(@TempDir Path dir) {
		// Given
		var cpDir = dir.resolve("checkpoint");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true).setEnableBlobFiles(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			try (var cp = Checkpoint.newCheckpoint(db)) {
				cp.exportTo(cpDir);
			}
		}

		// Then — read-only open can't resolve blob values, reopen read-write instead
		try (Options snapOpts = Options.newOptions().setCreateIfMissing(true); var snap = RocksDB.openReadWrite(cpDir, snapOpts)) {
			assertThat(snap.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void newCheckpoint_fromTtlDB_createsReadableSnapshot(@TempDir Path dir) {
		// Given
		var cpDir = dir.resolve("checkpoint");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openTtl(dir.resolve("db"), dbOpts, Duration.ofSeconds(60))) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			try (var cp = Checkpoint.newCheckpoint(db)) {
				cp.exportTo(cpDir);
			}
		}

		// Then — reopen as a TTL DB too, so the trailing timestamp RocksDB stores
		// alongside the value is stripped the same way the original write path did
		try (Options snapOpts = Options.newOptions().setCreateIfMissing(true); var snap = RocksDB.openTtl(cpDir, snapOpts, Duration.ofSeconds(60))) {
			assertThat(snap.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void newCheckpoint_fromReadOnlyDB_createsReadableSnapshot(@TempDir Path dir) {
		// Given — flush before reopening read-only: a checkpoint taken from a
		// read-only handle cannot trigger a flush of its own (no write access),
		// so unflushed WAL data would otherwise be silently dropped from the export
		var cpDir = dir.resolve("checkpoint");
		try (Options rwOpts = Options.newOptions().setCreateIfMissing(true); var rw = RocksDB.openReadWrite(dir.resolve("db"), rwOpts);
		     var fo = FlushOptions.newFlushOptions()) {
			rw.put("k".getBytes(), "v".getBytes());
			rw.flush(fo);
		}

		try (var ro = RocksDB.openReadOnly(dir.resolve("db"))) {
			// When
			try (var cp = Checkpoint.newCheckpoint(ro)) {
				cp.exportTo(cpDir);
			}
		}

		// Then
		try (var snap = RocksDB.openReadOnly(cpDir)) {
			assertThat(snap.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void newCheckpoint_fromSecondaryDB_createsReadableSnapshot(
			@TempDir Path primaryDir, @TempDir Path secondaryDir, @TempDir Path cpParentDir) {
		// Given
		var cpDir = cpParentDir.resolve("checkpoint");
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var primary = RocksDB.openReadWrite(primaryDir, opts);
		     var fo = FlushOptions.newFlushOptions()) {
			primary.put("k".getBytes(), "v".getBytes());
			primary.flush(fo);
		}

		try (var opts = Options.newOptions();
		     var secondary = RocksDB.openSecondary(primaryDir, secondaryDir, opts)) {
			secondary.tryCatchUpWithPrimary();

			// When
			try (var cp = Checkpoint.newCheckpoint(secondary)) {
				cp.exportTo(cpDir);
			}
		}

		// Then
		try (var snap = RocksDB.openReadOnly(cpDir)) {
			assertThat(snap.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}
}
