package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveFileInfoTest {

	@Test
	void getLiveFiles_emptyDb_returnsEmptyList(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var files = db.getLiveFiles()) {

			// When
			int size = files.size();

			// Then
			assertThat(size).isZero();
		}
	}

	@Test
	void getLiveFiles_afterFlush_returnsOneFileWithExpectedMetadata(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			db.flush(fo);

			// When
			try (var files = db.getLiveFiles()) {

				// Then
				assertThat(files.size()).isEqualTo(1);
				LiveFileInfo file = files.get(0);
				assertThat(file.columnFamilyName()).isEqualTo("default");
				assertThat(file.name()).endsWith(".sst");
				assertThat(file.directory()).isEqualTo(dir);
				assertThat(file.level()).isZero();
				assertThat(file.size().toBytes()).isPositive();
				assertThat(file.smallestKey()).isEqualTo("a".getBytes());
				assertThat(file.largestKey()).isEqualTo("b".getBytes());
				assertThat(file.numberOfEntries()).isEqualTo(2);
				assertThat(file.numberOfDeletions()).isZero();
			}
		}
	}

	@Test
	void getLiveFiles_iterate_visitsEveryFile(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("a".getBytes(), "1".getBytes());
			db.flush(fo);
			db.put("b".getBytes(), "2".getBytes());
			db.flush(fo);

			// When
			List<Long> entryCounts = new ArrayList<>();
			try (var files = db.getLiveFiles()) {
				for (LiveFileInfo file : files) {
					entryCounts.add(file.numberOfEntries());
				}
			}

			// Then
			assertThat(entryCounts).containsExactly(1L, 1L);
		}
	}

	@Test
	void getLiveFiles_readOnlyDb_seesFilesWrittenBeforeOpen(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("k".getBytes(), "v".getBytes());
			db.flush(fo);
		}

		// When
		try (var readOnly = RocksDB.openReadOnly(dir);
		     var files = readOnly.getLiveFiles()) {

			// Then
			assertThat(files.size()).isEqualTo(1);
		}
	}

	@Test
	void liveFileInfo_afterOwnerClosed_throws(@TempDir Path dir) {
		// Given
		LiveFileInfo file;
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("k".getBytes(), "v".getBytes());
			db.flush(fo);

			try (var files = db.getLiveFiles()) {
				file = files.get(0);
			}

			// When / Then
			assertThatThrownBy(file::name).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void transactionDB_getLiveFiles_afterFlush_returnsOneFile(@TempDir Path dir) {
		// Given
		try (var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTransaction(opts, txnDbOpts, dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("k".getBytes(), "v".getBytes());
			db.flush(fo);

			// When
			try (var files = db.getLiveFiles()) {

				// Then
				assertThat(files.size()).isEqualTo(1);
				assertThat(files.get(0).numberOfEntries()).isEqualTo(1);
			}
		}
	}
}
