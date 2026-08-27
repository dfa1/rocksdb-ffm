package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

// There is no C API extension point for a callback-based custom Env (see
// docs/c-api-gaps.md), so failures are injected by making RocksDB's real, default
// Env hit a genuine OS-level I/O error instead: revoking write permission on the DB
// directory forces the next file creation (e.g. a flush's new SST file) to fail with
// EACCES. Root bypasses POSIX permission checks entirely, and Windows has no
// equivalent permission model, so both are skipped.
@DisabledOnOs(OS.WINDOWS)
class FaultInjectionTest {

	private static final Set<PosixFilePermission> READ_EXECUTE_ONLY = EnumSet.of(
			PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE);
	private static final Set<PosixFilePermission> READ_WRITE_EXECUTE = EnumSet.of(
			PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE);

	@Test
	void flush_withReadOnlyDbDirectory_throwsRocksDBException(@TempDir Path dir) throws IOException {
		assumeFalse("root".equals(System.getProperty("user.name")), "root bypasses POSIX permission checks");

		// Given
		try (var options = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(options, dir);
		     var flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
			db.put("k".getBytes(), "v".getBytes());
			Files.setPosixFilePermissions(dir, READ_EXECUTE_ONLY);

			try {
				// When
				ThrowingCallable flush = () -> db.flush(flushOptions);

				// Then
				assertThatThrownBy(flush).isInstanceOf(RocksDBException.class);
			} finally {
				Files.setPosixFilePermissions(dir, READ_WRITE_EXECUTE);
			}
		}
	}
}
