package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/// A [RocksIterator] is a child of the [ReadOptions] it was created with, and a [Snapshot] released while
/// installed in [ReadOptions] poisons them: using either afterwards throws instead of reading freed memory.
class ReadOptionsOwnershipTest {

	private static final byte[] KEY = "k".getBytes();
	private static final byte[] VALUE = "v".getBytes();

	@Test
	void iterator_usedAfterItsReadOptionsClosed_throws(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put(KEY, VALUE);
			var ro = ReadOptions.newReadOptions().setIterateUpperBound("z".getBytes());
			var it = db.newIterator(ro);
			it.seekToFirst();

			// When
			ro.close();
			var thrown = catchThrowable(it::next);

			// Then
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(catchThrowable(it::close)).isNull();
		}
	}

	@Test
	void iterator_closedBeforeItsReadOptions_isClean(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var ro = ReadOptions.newReadOptions()) {
			var it = db.newIterator(ro);
			it.close();
			it.close();

			// When
			var thrown = catchThrowable(ro::close);

			// Then
			assertThat(thrown).isNull();
		}
	}

	@Test
	void dbClosedFirst_thenReadOptions_withIteratorOpen_isClean(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openReadWrite(dir, opts);
		var ro = ReadOptions.newReadOptions();
		var it = db.newIterator(ro);

		// When
		db.close();
		var thrown = catchThrowable(ro::close);

		// Then
		assertThat(thrown).isNull();
		assertThat(catchThrowable(it::isValid)).isInstanceOf(IllegalStateException.class);
		opts.close();
	}

	@Test
	void transactionIterator_usedAfterItsReadOptionsClosed_throws(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var txnOpts = TransactionDBOptions.newTransactionDBOptions();
		     var db = RocksDB.openTransaction(dir, opts, txnOpts);
		     var wo = WriteOptions.newWriteOptions();
		     var txn = db.beginTransaction(wo)) {
			var cf = db.getColumnFamily(ColumnFamilyName.DEFAULT);
			var ro = ReadOptions.newReadOptions().setIterateUpperBound("z".getBytes());
			var it = txn.newIterator(cf, ro);

			// When
			ro.close();
			var thrown = catchThrowable(it::isValid);

			// Then
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void read_afterInstalledSnapshotClosed_throws(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var ro = ReadOptions.newReadOptions()) {
			db.put(KEY, VALUE);
			var snapshot = db.getSnapshot();
			ro.setSnapshot(snapshot);

			// When
			snapshot.close();
			var thrown = catchThrowable(() -> db.get(ro, KEY));

			// Then
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(catchThrowable(() -> db.newIterator(ro))).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void read_afterDbClosesTheSnapshot_throws(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openReadWrite(dir, opts);
		var ro = ReadOptions.newReadOptions();
		ro.setSnapshot(db.getSnapshot());

		// When
		db.close();
		var thrown = catchThrowable(ro::readPtr);

		// Then
		assertThat(thrown).isInstanceOf(IllegalStateException.class);
		ro.close();
		opts.close();
	}

	@Test
	void read_afterSnapshotReplacedAndOldOneClosed_works(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var ro = ReadOptions.newReadOptions()) {
			db.put(KEY, VALUE);
			var old = db.getSnapshot();
			ro.setSnapshot(old);
			try (var current = db.getSnapshot()) {
				ro.setSnapshot(current);

				// When
				old.close();
				var result = db.get(ro, KEY);

				// Then
				assertThat(result).isEqualTo(VALUE);
			}
			ro.setSnapshot(null);
		}
	}

	@Test
	void read_afterSnapshotClearedAndClosed_works(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var ro = ReadOptions.newReadOptions()) {
			db.put(KEY, VALUE);
			var snapshot = db.getSnapshot();
			ro.setSnapshot(snapshot);
			ro.setSnapshot(null);

			// When
			snapshot.close();
			var result = db.get(ro, KEY);

			// Then
			assertThat(result).isEqualTo(VALUE);
		}
	}
}
