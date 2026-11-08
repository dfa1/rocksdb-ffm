package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsDbPathsTest {

	@TempDir
	Path tmp;

	@Test
	void setDbPaths_spillsSstFilesToSecondPath() throws IOException {
		// Given
		Path dbDir = tmp.resolve("db");
		Path hot = Files.createDirectories(tmp.resolve("hot"));
		Path cold = Files.createDirectories(tmp.resolve("cold"));
		byte[] value = new byte[16 * 1024];

		// When
		try (var options = Options.newOptions()
				.setCreateIfMissing(true)
				.setDbPaths(List.of(DbPath.of(hot, MemorySize.ofKB(1)), DbPath.of(cold, MemorySize.ofGB(1))));
		     var db = RocksDB.openReadWrite(options, dbDir);
		     var flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
			for (int i = 0; i < 5; i++) {
				db.put(("key" + i).getBytes(), value);
				db.flush(flushOptions);
			}
		}

		// Then
		assertThat(countSstFiles(hot)).isPositive();
		assertThat(countSstFiles(cold)).isPositive();
	}

	@Test
	void setCfPaths_acceptsPaths() throws IOException {
		// Given
		Path dbDir = tmp.resolve("db");
		Path cfDir = Files.createDirectories(tmp.resolve("cf"));
		byte[] value = new byte[16 * 1024];

		// When
		try (var options = Options.newOptions()
				.setCreateIfMissing(true)
				.setCfPaths(List.of(DbPath.of(cfDir, MemorySize.ofGB(1))));
		     var db = RocksDB.openReadWrite(options, dbDir);
		     var flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
			db.put("key".getBytes(), value);
			db.flush(flushOptions);
		}

		// Then
		assertThat(countSstFiles(cfDir)).isPositive();
	}

	@Test
	void setDbPaths_emptyList_isAccepted() {
		// Given
		try (var options = Options.newOptions()) {

			// When
			var result = options.setDbPaths(List.of());

			// Then
			assertThat(result).isSameAs(options);
		}
	}

	@Test
	void setDbPaths_nullList_isRejected() {
		// Given
		try (var options = Options.newOptions()) {

			// When
			var thrown = assertThatThrownBy(() -> options.setDbPaths(null));

			// Then
			thrown.isInstanceOf(NullPointerException.class);
		}
	}

	@Test
	void dbPath_nullComponents_areRejected() {
		// Given
		Path path = tmp;

		// When
		var nullPath = assertThatThrownBy(() -> DbPath.of(null, MemorySize.ofMB(1)));
		var nullSize = assertThatThrownBy(() -> DbPath.of(path, null));

		// Then
		nullPath.isInstanceOf(NullPointerException.class);
		nullSize.isInstanceOf(NullPointerException.class);
	}

	private static long countSstFiles(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(f -> f.getFileName().toString().endsWith(".sst")).count();
		}
	}
}
