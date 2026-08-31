package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveFileInfoTest {

	// -----------------------------------------------------------------------
	// LiveFiles.size() / get(int)
	// -----------------------------------------------------------------------

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
	void get_negativeIndex_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("k".getBytes(), "v".getBytes());
			db.flush(fo);

			try (var files = db.getLiveFiles()) {

				// When / Then
				assertThatThrownBy(() -> files.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
			}
		}
	}

	@Test
	void get_indexEqualToSize_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("k".getBytes(), "v".getBytes());
			db.flush(fo);

			try (var files = db.getLiveFiles()) {
				int size = files.size();

				// When / Then
				assertThatThrownBy(() -> files.get(size)).isInstanceOf(IndexOutOfBoundsException.class);
			}
		}
	}

	@Test
	void sizeAndGet_remainUsableAfterLiveFilesClosed_onlyFieldAccessThrows(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("k".getBytes(), "v".getBytes());
			db.flush(fo);

			var files = db.getLiveFiles();
			files.close();

			// When — size()/get() are pure Java bookkeeping (cached count, no native read),
			// so they stay usable even after the native list behind them is destroyed
			int size = files.size();
			LiveFileInfo file = files.get(0);

			// Then
			assertThat(size).isEqualTo(1);
			assertThatThrownBy(file::name).isInstanceOf(IllegalStateException.class);
		}
	}

	// -----------------------------------------------------------------------
	// LiveFiles.iterator()
	// -----------------------------------------------------------------------

	@Test
	void iterator_emptyDb_hasNoElements(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var files = db.getLiveFiles()) {

			// When
			var it = files.iterator();

			// Then
			assertThat(it.hasNext()).isFalse();
			assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
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
			List<String> names = new ArrayList<>();
			try (var files = db.getLiveFiles()) {
				for (LiveFileInfo file : files) {
					entryCounts.add(file.numberOfEntries());
					names.add(file.name());
				}
			}

			// Then — two distinct files visited, not the same index read twice (which a
			// corrupted iteration index could otherwise mask, since both files here happen
			// to share the same entry count)
			assertThat(entryCounts).containsExactly(1L, 1L);
			assertThat(names).doesNotHaveDuplicates();
		}
	}

	// -----------------------------------------------------------------------
	// LiveFileInfo accessors
	// -----------------------------------------------------------------------

	@Test
	void getLiveFiles_afterFlush_returnsOneFileWithExpectedMetadata(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			db.flush(fo);
			SequenceNumber latest = db.getLatestSequenceNumber();

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
				assertThat(file.smallestSequenceNumber().toLong()).isLessThanOrEqualTo(file.largestSequenceNumber().toLong());
				assertThat(file.largestSequenceNumber()).isEqualTo(latest);
				assertThat(file.numberOfEntries()).isEqualTo(2);
				assertThat(file.numberOfDeletions()).isZero();
			}
		}
	}

	@Test
	void getLiveFiles_afterDelete_reportsDeletionInSeparateFlushedFile(@TempDir Path dir) {
		// Given — put+flush first so the delete lands in its own, separately flushed file
		// (a delete in the same memtable as its own put would just drop the obsolete put
		// during flush instead of producing a file with both an entry and a deletion)
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("a".getBytes(), "1".getBytes());
			db.flush(fo);
			db.delete("a".getBytes());
			db.flush(fo);

			// When
			try (var files = db.getLiveFiles()) {
				LiveFileInfo deletionFile = files.get(0);
				for (LiveFileInfo file : files) {
					if (file.numberOfDeletions() > 0) {
						deletionFile = file;
					}
				}

				// Then
				assertThat(files.size()).isEqualTo(2);
				assertThat(deletionFile.numberOfEntries()).isEqualTo(1);
				assertThat(deletionFile.numberOfDeletions()).isEqualTo(1);
			}
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

	// -----------------------------------------------------------------------
	// TransactionDB (implements MonitoringOperations directly, not RocksDBReadOperations)
	// -----------------------------------------------------------------------

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
