package io.github.dfa1.rocksdbffm.ldb;

import io.github.dfa1.rocksdbffm.NativeTool;
import io.github.dfa1.rocksdbffm.RocksDB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LdbToolTest {

	@Test
	void checkConsistency_freshDatabase_isConsistent(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("k".getBytes(), "v".getBytes());
		}

		// When
		NativeTool.Result result = LdbTool.checkConsistency(dir);

		// Then
		assertThat(result.isSuccess())
				.as("exitCode=%d stdout=%s stderr=%s", result.exitCode(), result.stdout(), result.stderr())
				.isTrue();
	}

	@Test
	void manifestDump_freshDatabase_printsManifestContents(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("k".getBytes(), "v".getBytes());
		}

		// When
		NativeTool.Result result = LdbTool.manifestDump(dir, false);

		// Then
		assertThat(result.isSuccess())
				.as("exitCode=%d stdout=%s stderr=%s", result.exitCode(), result.stdout(), result.stderr())
				.isTrue();
		assertThat(result.stdout()).isNotBlank();
	}

	@Test
	void run_listColumnFamilies_includesDefault(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir)) {
			db.put("k".getBytes(), "v".getBytes());
		}

		// When
		NativeTool.Result result = LdbTool.run(dir, "list_column_families");

		// Then
		assertThat(result.isSuccess())
				.as("exitCode=%d stdout=%s stderr=%s", result.exitCode(), result.stdout(), result.stderr())
				.isTrue();
		assertThat(result.stdout()).contains("default");
	}
}
