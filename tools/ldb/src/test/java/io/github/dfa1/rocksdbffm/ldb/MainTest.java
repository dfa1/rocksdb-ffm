package io.github.dfa1.rocksdbffm.ldb;

import io.github.dfa1.rocksdbffm.RocksDB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers [Main#run(String[])] — the package-private seam [Main#main(String[])] wraps in
/// `System.exit`, tested here instead so the JVM running the test suite doesn't exit.
class MainTest {

	@Test
	void run_returnsTheSubprocessExitCode(@TempDir Path dbDir) {
		// Given
		try (var db = RocksDB.openReadWrite(dbDir)) {
			db.put("k".getBytes(), "v".getBytes());
		}

		// When
		int exitCode = Main.run(new String[] {"--db=" + dbDir, "checkconsistency"});

		// Then
		assertThat(exitCode).isZero();
	}
}
