package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/// A [Transaction] is a child of the database it was begun on: closing the database releases it first,
/// so using it afterwards throws instead of touching freed native memory.
class TransactionIsChildOfDbTest {

	private static final byte[] KEY = "k".getBytes();
	private static final byte[] VALUE = "v".getBytes();

	/// An open database of one transaction flavor, and how to begin a transaction on it.
	record Opened(NativeDB db, Function<WriteOptions, Transaction> begin,
	              Function<WriteOptions, Transaction> beginWithSnapshot) {
	}

	record Flavor(String name, Function<Path, Opened> open) {

		@Override
		public String toString() {
			return name;
		}
	}

	static Stream<Flavor> flavors() {
		return Stream.of(
				new Flavor("TransactionDB", dir -> {
					try (var opts = Options.newOptions().setCreateIfMissing(true);
					     var txnOpts = TransactionDBOptions.newTransactionDBOptions()) {
						var db = RocksDB.openTransaction(dir, opts, txnOpts);
						return new Opened(db, db::beginTransaction, wo -> {
							try (var snapshotOptions = TransactionOptions.newTransactionOptions().setSetSnapshot(true)) {
								return db.beginTransaction(wo, snapshotOptions);
							}
						});
					}
				}),
				new Flavor("OptimisticTransactionDB", dir -> {
					try (var opts = Options.newOptions().setCreateIfMissing(true)) {
						var db = RocksDB.openOptimistic(dir, opts);
						return new Opened(db, db::beginTransaction, wo -> {
							try (var snapshotOptions = OptimisticTransactionOptions.newOptimisticTransactionOptions()
									.setSetSnapshot(true)) {
								return db.beginTransaction(wo, snapshotOptions);
							}
						});
					}
				}));
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void closingTheDb_releasesAnOpenTransaction(Flavor flavor, @TempDir Path dir) {
		// Given
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions()) {
			var txn = opened.begin().apply(wo);
			var cf = opened.db().getColumnFamily(ColumnFamilyName.DEFAULT);
			txn.put(cf, KEY, VALUE);

			// When — the database is closed while the transaction is still open and uncommitted
			opened.db().close();

			// Then — the transaction was released with it: using it throws instead of crashing
			assertThat(catchThrowable(() -> txn.put(cf, KEY, VALUE))).isInstanceOf(IllegalStateException.class);
			assertThat(catchThrowable(txn::commit)).isInstanceOf(IllegalStateException.class);
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void closingAReleasedTransaction_isSafe(Flavor flavor, @TempDir Path dir) {
		// Given
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions()) {
			var txn = opened.begin().apply(wo);
			opened.db().close();

			// When
			var thrown = catchThrowable(txn::close);

			// Then
			assertThat(thrown).isNull();
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void closingTheTransactionFirst_thenTheDb_isSafe(Flavor flavor, @TempDir Path dir) {
		// Given
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions()) {
			var txn = opened.begin().apply(wo);
			txn.rollback();
			txn.close();

			// When
			var thrown = catchThrowable(opened.db()::close);

			// Then
			assertThat(thrown).isNull();
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void closingTheDbTwice_withAnOpenTransaction_isSafe(Flavor flavor, @TempDir Path dir) {
		// Given
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions()) {
			opened.begin().apply(wo);
			opened.db().close();

			// When
			var thrown = catchThrowable(opened.db()::close);

			// Then
			assertThat(thrown).isNull();
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void manyTransactions_closedOnTheirOwn_doNotBlockTheDbClose(Flavor flavor, @TempDir Path dir) {
		// Given — transactions come and go, each unregistering itself from the database
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions()) {
			for (int i = 0; i < 50; i++) {
				try (var txn = opened.begin().apply(wo)) {
					txn.rollback();
				}
			}
			var open = opened.begin().apply(wo);

			// When
			opened.db().close();

			// Then — only the one still open was swept
			assertThat(catchThrowable(open::rollback)).isInstanceOf(IllegalStateException.class);
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void aTransactionsIterator_isReleasedWithTheTransaction(Flavor flavor, @TempDir Path dir) {
		// Given
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions()) {
			var txn = opened.begin().apply(wo);
			var cf = opened.db().getColumnFamily(ColumnFamilyName.DEFAULT);
			txn.put(cf, KEY, VALUE);
			var iterator = txn.newIterator(cf, ro);
			iterator.seekToFirst();

			// When — the transaction is closed while its iterator is still open
			txn.close();

			// Then — the iterator went first (it is a child of the transaction), so this is not a use after free
			assertThat(catchThrowable(iterator::isValid)).isInstanceOf(IllegalStateException.class);
			opened.db().close();
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void closingTheDb_withATransactionAndItsIteratorOpen_closesCleanly(Flavor flavor, @TempDir Path dir) {
		// Given
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions()) {
			var txn = opened.begin().apply(wo);
			var cf = opened.db().getColumnFamily(ColumnFamilyName.DEFAULT);
			txn.put(cf, KEY, VALUE);
			var iterator = txn.newIterator(cf, ro);
			iterator.seekToFirst();

			// When
			var thrown = catchThrowable(opened.db()::close);

			// Then — the transaction released its iterator before being destroyed, itself before the database
			assertThat(thrown).isNull();
			assertThat(catchThrowable(iterator::isValid)).isInstanceOf(IllegalStateException.class);
			assertThat(catchThrowable(txn::commit)).isInstanceOf(IllegalStateException.class);
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void aTransactionsSnapshot_isReleasedWithTheTransaction(Flavor flavor, @TempDir Path dir) {
		// Given — the snapshot is borrowed from the transaction, which owns the native snapshot behind it
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions()) {
			var txn = opened.beginWithSnapshot().apply(wo);
			var snapshot = txn.getSnapshot();
			assertThat(snapshot.sequenceNumber()).isNotNull();

			// When
			txn.close();

			// Then — released first, so reading it throws instead of touching the transaction's freed snapshot
			assertThat(catchThrowable(snapshot::sequenceNumber)).isInstanceOf(IllegalStateException.class);
			opened.db().close();
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void closingTheDb_withATransactionSnapshotOpen_closesCleanly(Flavor flavor, @TempDir Path dir) {
		// Given
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions()) {
			var txn = opened.beginWithSnapshot().apply(wo);
			var snapshot = txn.getSnapshot();

			// When
			var thrown = catchThrowable(opened.db()::close);

			// Then
			assertThat(thrown).isNull();
			assertThat(catchThrowable(snapshot::sequenceNumber)).isInstanceOf(IllegalStateException.class);
			assertThat(catchThrowable(snapshot::close)).isNull();
		}
	}

	@ParameterizedTest
	@MethodSource("flavors")
	void aTransactionsIterator_isReleasedByCommitRollbackAndRollbackToSavePoint(Flavor flavor, @TempDir Path dir) {
		// Given
		var opened = flavor.open().apply(dir);
		try (var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions()) {
			var txn = opened.begin().apply(wo);
			var cf = opened.db().getColumnFamily(ColumnFamilyName.DEFAULT);
			txn.put(cf, KEY, VALUE);
			var afterCommit = txn.newIterator(cf, ro);
			var afterRollback = txn.newIterator(cf, ro);
			var afterSavePoint = txn.newIterator(cf, ro);
			afterSavePoint.seekToFirst();

			// When
			txn.setSavePoint();
			txn.rollbackToSavePoint();
			var savePointResult = catchThrowable(afterSavePoint::isValid);
			txn.rollback();
			var rollbackResult = catchThrowable(afterRollback::isValid);
			txn.commit();
			var commitResult = catchThrowable(afterCommit::isValid);

			// Then
			assertThat(savePointResult).isInstanceOf(IllegalStateException.class);
			assertThat(rollbackResult).isInstanceOf(IllegalStateException.class);
			assertThat(commitResult).isInstanceOf(IllegalStateException.class);
			txn.close();
			opened.db().close();
		}
	}
}
