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
/// launch-failure/interrupted-while-waiting branches of both `run` and `runInherited`. Lives here
/// (rather than in `core`) because it needs the real extracted `ldb` binary this module bundles at
/// test scope.
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

	// run()'s own InterruptedException branch (as opposed to runInherited()'s, tested below) isn't
	// covered here: run() reads the subprocess's entire stdout/stderr before calling waitFor(), and
	// `ldb --help` is fast enough that the process has usually already exited by then — waitFor()
	// short-circuits on an already-terminated process without necessarily consulting the interrupt
	// flag first, making a pre-set interrupt unreliable to observe there. runInherited() calls
	// waitFor() immediately after start() instead, leaving no such race.

	@Test
	void runInherited_interruptedWhileWaitingForTheSubprocess_wrapsAsUncheckedIOException() {
		// Given
		Path toolDirectory = NativeTool.extractToolDirectory();
		Thread.currentThread().interrupt();

		try {
			// When / Then
			assertThatThrownBy(() -> NativeTool.runInherited(toolDirectory, "ldb", List.of("--help")))
					.isInstanceOf(UncheckedIOException.class);
			assertThat(Thread.currentThread().isInterrupted())
					.as("the interrupt flag must be restored, not swallowed")
					.isTrue();
		} finally {
			Thread.interrupted(); // clear it so it doesn't leak into later tests
		}
	}
}
