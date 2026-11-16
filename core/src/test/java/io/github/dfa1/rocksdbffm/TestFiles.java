package io.github.dfa1.rocksdbffm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/// File helpers shared across tests.
final class TestFiles {

	private TestFiles() {
	}

	static long countFiles(Path dir, String suffix) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(path -> path.getFileName().toString().endsWith(suffix)).count();
		}
	}
}
