package io.github.dfa1.rocksdbffm.sstdump;

import io.github.dfa1.rocksdbffm.Options;
import io.github.dfa1.rocksdbffm.SstFileWriter;
import io.github.dfa1.rocksdbffm.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// ldb/sst_dump are not bundled for windows-* classifiers yet (see docs/reference.md#artifacts).
@DisabledOnOs(OS.WINDOWS)
class SstDumpToolTest {

	@Test
	void identify_validSstFile_isSuccessful(@TempDir Path dir) {
		// Given
		Path sstPath = writeSstFile(dir);

		// When
		ToolResult result = SstDumpTool.identify(sstPath);

		// Then
		assertThat(result.isSuccess())
				.as("exitCode=%d stdout=%s stderr=%s", result.exitCode(), result.stdout(), result.stderr())
				.isTrue();
	}

	@Test
	void identify_notAnSstFile_fails(@TempDir Path dir) throws Exception {
		// Given
		Path notSst = dir.resolve("not-an-sst.txt");
		Files.writeString(notSst, "not an sst file");

		// When
		ToolResult result = SstDumpTool.identify(notSst);

		// Then
		assertThat(result.isSuccess()).isFalse();
	}

	@Test
	void request_scanCommand_printsKeys(@TempDir Path dir) {
		// Given
		Path sstPath = writeSstFile(dir);

		// When
		ToolResult result = SstDumpTool.request(sstPath).command(SstDumpCommand.SCAN).run();

		// Then
		assertThat(result.isSuccess())
				.as("exitCode=%d stdout=%s stderr=%s", result.exitCode(), result.stdout(), result.stderr())
				.isTrue();
		assertThat(result.stdout()).contains("aaa").contains("bbb");
	}

	private static Path writeSstFile(Path dir) {
		Path sstPath = dir.resolve("data.sst");
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(opts)) {
			writer.open(sstPath);
			writer.put("aaa".getBytes(), "val1".getBytes());
			writer.put("bbb".getBytes(), "val2".getBytes());
			writer.finish();
		}
		return sstPath;
	}
}
