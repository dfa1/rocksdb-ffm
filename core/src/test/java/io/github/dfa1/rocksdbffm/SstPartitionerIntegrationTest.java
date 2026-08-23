package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SstPartitionerIntegrationTest {

	private static final String[] PREFIXES = {"aa", "bb", "cc", "dd", "ee"};
	private static final int KEYS_PER_PREFIX = 200;

	@Test
	void fixedPrefixPartitioner_forcesOneFilePerPrefixOnCompaction(@TempDir Path dir) {
		// Given — a database with keys spread across 5 distinct 2-byte prefixes, compacted
		// with no partitioner configured, so nothing stops compaction from merging every
		// prefix into a single small output file
		try (var db = RocksDB.openReadWrite(dir)) {
			for (String prefix : PREFIXES) {
				for (int i = 0; i < KEYS_PER_PREFIX; i++) {
					String key = prefix + String.format("%04d", i);
					db.put(key.getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8));
				}
			}
			db.compactRange();
		}
		int baselineFileCount;
		try (var db = RocksDB.openReadOnly(dir);
		     var files = db.getLiveFiles()) {
			baselineFileCount = files.size();
		}

		// When — reopen with a fixed-prefix(2) partitioner and force a full recompaction;
		// existing files are not retroactively repartitioned by reopening alone, only by
		// running compaction again under the new factory
		List<String> smallestPrefixes = new ArrayList<>();
		List<String> largestPrefixes = new ArrayList<>();
		int partitionedFileCount;
		try (var partitioner = SstPartitionerFactory.newFixedPrefix(2);
		     var opts = Options.newOptions().setSstPartitionerFactory(partitioner);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var compactOpts = CompactOptions.newCompactOptions().setBottommostLevelCompaction(true)) {
			// bottommost-level compaction is forced: the baseline compaction already placed
			// everything at the bottom level with nothing left to merge, so a plain
			// compactRange() would see nothing to do and skip re-running the partitioner
			// entirely — forcing it is what actually rewrites the file(s) under the new factory
			db.compactRange(compactOpts, null, null);

			try (var files = db.getLiveFiles()) {
				partitionedFileCount = files.size();
				for (LiveFileInfo file : files) {
					smallestPrefixes.add(new String(file.smallestKey(), StandardCharsets.UTF_8).substring(0, 2));
					largestPrefixes.add(new String(file.largestKey(), StandardCharsets.UTF_8).substring(0, 2));
				}
			}
		}

		// Then — baseline compaction merged every prefix into far fewer files than there are
		// prefixes (small data, well under the default target file size), but forcing
		// recompaction under the fixed-prefix(2) partitioner produces exactly one file per
		// prefix, and every file's key range stays within a single prefix
		assertThat(baselineFileCount).isLessThan(PREFIXES.length);
		assertThat(partitionedFileCount).isEqualTo(PREFIXES.length);
		assertThat(smallestPrefixes).containsExactlyInAnyOrder(PREFIXES);
		for (int i = 0; i < partitionedFileCount; i++) {
			assertThat(smallestPrefixes.get(i)).isEqualTo(largestPrefixes.get(i));
		}
	}
}
