package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers [NativeTool] paths that [io.github.dfa1.rocksdbffm.ldb.LdbToolTest]/
/// [io.github.dfa1.rocksdbffm.sstdump.SstDumpToolTest] don't exercise: `runInherited`, and the
/// launch-failure branch of both `run` and `runInherited`. Lives here (rather than in `core`)
/// because it needs the real extracted `ldb` binary this module bundles at test scope.
class NativeToolTest {

	@Test
	void runInherited_returnsTheSubprocessExitCode(@TempDir Path dbDir) {
		// Given
		try (var db = RocksDB.openReadWrite(dbDir)) {
			db.put("k".getBytes(), "v".getBytes());
		}
		Path toolDirectory = NativeTool.extractToolDirectory();

		// When
		int exitCode = NativeTool.runInherited(toolDirectory, "ldb",
				List.of("--db=" + dbDir, "checkconsistency"));

		// Then
		assertThat(exitCode).isZero();
	}

	@Test
	void run_missingExecutable_wrapsLaunchFailureAsUncheckedIOException() {
		// Given
		Path toolDirectory = NativeTool.extractToolDirectory();

		// When / Then
		assertThatThrownBy(() -> NativeTool.run(toolDirectory, "does-not-exist", List.of()))
				.isInstanceOf(UncheckedIOException.class);
	}

	@Test
	void runInherited_missingExecutable_wrapsLaunchFailureAsUncheckedIOException() {
		// Given
		Path toolDirectory = NativeTool.extractToolDirectory();

		// When / Then
		assertThatThrownBy(() -> NativeTool.runInherited(toolDirectory, "does-not-exist", List.of()))
				.isInstanceOf(UncheckedIOException.class);
	}

	// Neither run()'s nor runInherited()'s InterruptedException branch is covered here: both
	// require the calling thread's interrupt flag to still be observed by Process.waitFor() at the
	// moment it's called, which races against how fast the real ldb subprocess actually exits — an
	// already-terminated process lets waitFor() short-circuit without necessarily consulting the
	// interrupt flag first. This was tried (pre-setting the flag before calling runInherited(), the
	// version with the least code running before waitFor()) and passed consistently on macOS but
	// failed on the CI's Linux runner, confirming the race is real and environment-dependent, not
	// just theoretical — not worth a flaky test for a few lines of a defensive catch block.
}
