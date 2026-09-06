package io.github.dfa1.rocksdbffm.sstdump;

import io.github.dfa1.rocksdbffm.Options;
import io.github.dfa1.rocksdbffm.SstFileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers [Main#run(String[])] — the package-private seam [Main#main(String[])] wraps in
/// `System.exit`, tested here instead so the JVM running the test suite doesn't exit.
class MainTest {

	@Test
	void run_returnsTheSubprocessExitCode(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("k".getBytes(), "v".getBytes());
			writer.finish();
		}

		// When
		int exitCode = Main.run(new String[] {"--file=" + sstPath, "--command=identify"});

		// Then
		assertThat(exitCode).isZero();
	}
}
