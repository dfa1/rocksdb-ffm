package io.github.dfa1.rocksdbffm.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.Stream;

/// Shared constants and helpers used by both [FfmBenchmark] and [JniBenchmark].
final class TestData {

	static final int WRITE_BATCH_SIZE = 100;

	/// Typical RocksDB key/value sizes, not placeholder-sized ASCII strings —
	/// large enough that the byte[] copy cost actually shows up against the
	/// fixed per-call overhead.
	static final int BATCH_KEY_SIZE = 16;
	static final int BATCH_VALUE_SIZE = 1024;

	static final byte[] READ_KEY_BYTES = "read-key".getBytes();
	static final byte[] READ_VALUE_BYTES = "read-value-data-0123456789".getBytes();
	static final byte[] WRITE_KEY_BYTES = "bench-key".getBytes();
	static final byte[] WRITE_VALUE_BYTES = "bench-value-data-0123456789".getBytes();

	// Fixed seed: reproducible benchmark runs, not cryptographic randomness.
	private static final Random RANDOM = new Random(42);

	static byte[][] batchKeys() {
		return batchKeys(WRITE_BATCH_SIZE);
	}

	static byte[][] batchKeys(int count) {
		return randomBytes(count, BATCH_KEY_SIZE);
	}

	static byte[][] batchValues() {
		return batchValues(WRITE_BATCH_SIZE);
	}

	static byte[][] batchValues(int count) {
		return randomBytes(count, BATCH_VALUE_SIZE);
	}

	static byte[][] randomBytes(int count, int size) {
		byte[][] result = new byte[count][];
		for (int i = 0; i < count; i++) {
			byte[] bytes = new byte[size];
			RANDOM.nextBytes(bytes);
			result[i] = bytes;
		}
		return result;
	}

	static void deleteDir(Path root) throws IOException {
		try (Stream<Path> paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder())
					.forEach(path -> {
						try {
							Files.delete(path);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
		}
	}

	private TestData() {
		// no instances
	}
}
