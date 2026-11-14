package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/// The database owns its column family handles ([ADR 0009](../../../../../../../../docs/adr/0009-db-owned-column-family-handles.md)).
class DbOwnedColumnFamilyHandlesTest {

	private static final ColumnFamilyName CF1 = ColumnFamilyName.of("cf1");

	private static final List<ColumnFamilyDescriptor> BOTH = List.of(
			ColumnFamilyDescriptor.of(ColumnFamilyName.DEFAULT), ColumnFamilyDescriptor.of(CF1));

	/// A database type that opens `dir` (which already holds `default` and `cf1`) with both families.
	record Opener(String name, Function<Path, NativeDB> open) {

		@Override
		public String toString() {
			return name;
		}
	}

	static Stream<Opener> openers() {
		return Stream.of(
				new Opener("ReadWriteDB", dir -> {
					try (var opts = Options.newOptions()) {
						return RocksDB.openReadWrite(opts, dir, BOTH);
					}
				}),
				new Opener("ReadOnlyDB", dir -> {
					try (var opts = Options.newOptions()) {
						return RocksDB.openReadOnly(opts, dir, BOTH);
					}
				}),
				new Opener("SecondaryDB", dir -> {
					try (var opts = Options.newOptions()) {
						return RocksDB.openSecondary(opts, dir, secondaryDir(dir), BOTH);
					}
				}),
				new Opener("TtlDB", dir -> {
					try (var opts = Options.newOptions()) {
						return RocksDB.openTtl(opts, dir, List.of(
								BOTH.get(0).withTtl(java.time.Duration.ZERO),
								BOTH.get(1).withTtl(java.time.Duration.ofHours(1))));
					}
				}),
				new Opener("TransactionDB", dir -> {
					try (var opts = Options.newOptions(); var txnOpts = TransactionDBOptions.newTransactionDBOptions()) {
						return RocksDB.openTransaction(opts, txnOpts, dir, BOTH);
					}
				}),
				new Opener("OptimisticTransactionDB", dir -> {
					try (var opts = Options.newOptions()) {
						return RocksDB.openOptimistic(opts, dir, BOTH);
					}
				}));
	}

	private static Path secondaryDir(Path dir) {
		try {
			return Files.createDirectories(dir.resolve("secondary"));
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/// Creates a database at `dir` holding `default` and `cf1`.
	private static void prepare(Path dir) {
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(CF1))) {
			db.put(cf, "k".getBytes(), "v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// every database type
	// -----------------------------------------------------------------------

	@ParameterizedTest
	@MethodSource("openers")
	void open_registersHandlesInDescriptorOrder(Opener opener, @TempDir Path dir) {
		// Given
		prepare(dir);

		// When
		try (var db = opener.open().apply(dir)) {
			var families = db.getColumnFamilies();

			// Then
			assertThat(families.keySet()).containsExactly(ColumnFamilyName.DEFAULT, CF1);
			assertThat(db.getColumnFamily(CF1).getName()).isEqualTo(CF1);
			assertThat(db.getColumnFamily(ColumnFamilyName.DEFAULT).getName()).isEqualTo(ColumnFamilyName.DEFAULT);
		}
	}

	@ParameterizedTest
	@MethodSource("openers")
	void getColumnFamily_unknownName_throwsNoSuchElementException(Opener opener, @TempDir Path dir) {
		// Given
		prepare(dir);
		try (var db = opener.open().apply(dir)) {

			// When
			var thrown = catchThrowable(() -> db.getColumnFamily(ColumnFamilyName.of("nope")));

			// Then
			assertThat(thrown).isInstanceOf(NoSuchElementException.class);
		}
	}

	@ParameterizedTest
	@MethodSource("openers")
	void closingTheDb_releasesItsHandles(Opener opener, @TempDir Path dir) {
		// Given
		prepare(dir);
		var db = opener.open().apply(dir);
		var cf = db.getColumnFamily(CF1);

		// When
		db.close();

		// Then — the handle was released with the DB: using it throws instead of touching freed memory
		assertThat(catchThrowable(cf::getId)).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@MethodSource("openers")
	void closingAHandle_isANoOp_andItStaysUsable(Opener opener, @TempDir Path dir) {
		// Given
		prepare(dir);
		try (var db = opener.open().apply(dir)) {
			var cf = db.getColumnFamily(CF1);

			// When
			cf.close();
			cf.close();

			// Then
			assertThat(cf.getName()).isEqualTo(CF1);
			assertThat(db.getColumnFamily(CF1)).isSameAs(cf);
		}
	}

	@ParameterizedTest
	@MethodSource("openers")
	void closingTheDbTwice_isSafe(Opener opener, @TempDir Path dir) {
		// Given
		prepare(dir);
		var db = opener.open().apply(dir);
		db.close();

		// When
		var thrown = catchThrowable(db::close);

		// Then
		assertThat(thrown).isNull();
	}

	@ParameterizedTest
	@MethodSource("openers")
	void lookups_onAClosedDb_throwIllegalStateException(Opener opener, @TempDir Path dir) {
		// Given
		prepare(dir);
		var db = opener.open().apply(dir);
		db.close();

		// When
		var single = catchThrowable(() -> db.getColumnFamily(CF1));
		var all = catchThrowable(db::getColumnFamilies);

		// Then
		assertThat(single).isInstanceOf(IllegalStateException.class);
		assertThat(all).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@MethodSource("openers")
	void getColumnFamilies_isASnapshot_andUnmodifiable(Opener opener, @TempDir Path dir) {
		// Given
		prepare(dir);
		try (var db = opener.open().apply(dir)) {
			var families = db.getColumnFamilies();

			// When
			var thrown = catchThrowable(() -> families.put(ColumnFamilyName.of("x"), db.getColumnFamily(CF1)));

			// Then
			assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
		}
	}

	// -----------------------------------------------------------------------
	// opens without descriptors still expose the default family
	// -----------------------------------------------------------------------

	@Test
	void singlePathOpen_exposesTheDefaultFamily(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			var families = db.getColumnFamilies();

			// Then
			assertThat(families.keySet()).containsExactly(ColumnFamilyName.DEFAULT);
			assertThat(db.getColumnFamily(ColumnFamilyName.DEFAULT).getId()).isZero();
		}
	}

	@Test
	void singlePathOpen_defaultHandleWorksWithTheColumnFamilyOverloads(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			var cf = db.getColumnFamily(ColumnFamilyName.DEFAULT);
			db.put(cf, "k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	// -----------------------------------------------------------------------
	// create and drop
	// -----------------------------------------------------------------------

	@Test
	void createColumnFamily_registersTheHandle_afterTheOpenedOnes(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(CF1));

			// Then
			assertThat(db.getColumnFamilies().keySet()).containsExactly(ColumnFamilyName.DEFAULT, CF1);
			assertThat(db.getColumnFamily(CF1)).isSameAs(cf);
		}
	}

	@Test
	void createdHandle_isReleasedWithTheDb(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openReadWrite(opts, dir);
		var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(CF1));

		// When
		db.close();
		opts.close();

		// Then
		assertThat(catchThrowable(cf::getId)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void dropColumnFamily_removesTheEntry_andReleasesTheHandle(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(CF1));

			// When
			db.dropColumnFamily(cf);

			// Then
			assertThat(db.getColumnFamilies().keySet()).containsExactly(ColumnFamilyName.DEFAULT);
			assertThat(catchThrowable(() -> db.getColumnFamily(CF1))).isInstanceOf(NoSuchElementException.class);
			assertThat(catchThrowable(cf::getId)).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void dropColumnFamily_ofTheDefaultFamily_failsAndKeepsTheEntry(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			var defaultCf = db.getColumnFamily(ColumnFamilyName.DEFAULT);

			// When
			var thrown = catchThrowable(() -> db.dropColumnFamily(defaultCf));

			// Then
			assertThat(thrown).isInstanceOf(RocksDBException.class);
			assertThat(db.getColumnFamily(ColumnFamilyName.DEFAULT)).isSameAs(defaultCf);
			assertThat(defaultCf.getId()).isZero();
		}
	}

	@Test
	void dropColumnFamily_withAnotherDbsHandle_isRejected(@TempDir Path dir, @TempDir Path otherDir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var other = RocksDB.openReadWrite(opts, otherDir)) {
			var foreign = other.createColumnFamily(ColumnFamilyDescriptor.of(CF1));

			// When
			var thrown = catchThrowable(() -> db.dropColumnFamily(foreign));

			// Then
			assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
			assertThat(other.getColumnFamily(CF1)).isSameAs(foreign);
		}
	}

	// -----------------------------------------------------------------------
	// concurrency
	// -----------------------------------------------------------------------

	@Test
	void concurrentCreatesAndLookups_neverLoseAFamily(@TempDir Path dir) throws Exception {
		// Given
		var names = java.util.stream.IntStream.range(0, 16).mapToObj(i -> ColumnFamilyName.of("cf" + i)).toList();
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {

			// When — families are created while other threads keep reading the registry
			var futures = names.stream().map(n -> executor.submit(() -> {
				db.createColumnFamily(ColumnFamilyDescriptor.of(n));
				db.getColumnFamilies();
				return db.getColumnFamily(n);
			})).toList();
			for (var future : futures) {
				future.get();
			}

			// Then
			assertThat(db.getColumnFamilies().keySet()).containsExactlyInAnyOrderElementsOf(
					java.util.stream.Stream.concat(java.util.stream.Stream.of(ColumnFamilyName.DEFAULT), names.stream()).toList());
		}
	}

	// -----------------------------------------------------------------------
	// validation
	// -----------------------------------------------------------------------

	@Test
	void open_withDuplicateDescriptorNames_isRejectedBeforeTheNativeOpen(@TempDir Path dir) {
		// Given
		prepare(dir);
		var duplicated = List.of(
				ColumnFamilyDescriptor.of(ColumnFamilyName.DEFAULT), ColumnFamilyDescriptor.of(CF1),
				ColumnFamilyDescriptor.of(CF1));

		try (var opts = Options.newOptions()) {
			// When
			var thrown = catchThrowable(() -> RocksDB.openReadWrite(opts, dir, duplicated));

			// Then
			assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessageContaining("duplicate column family name");
		}
	}

	// -----------------------------------------------------------------------
	// interaction with other children
	// -----------------------------------------------------------------------

	@Test
	void closingTheDb_withAnOpenIteratorOnAFamily_closesCleanly(@TempDir Path dir) {
		// Given
		prepare(dir);
		var opts = Options.newOptions();
		var db = RocksDB.openReadWrite(opts, dir, BOTH);
		var iterator = db.newIterator(db.getColumnFamily(CF1));
		iterator.seekToFirst();

		// When
		var thrown = catchThrowable(db::close);
		opts.close();

		// Then
		assertThat(thrown).isNull();
		assertThat(catchThrowable(iterator::isValid)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void transactionDb_transactionPutThroughALookedUpHandle(@TempDir Path dir) {
		// Given
		prepare(dir);
		try (var opts = Options.newOptions();
		     var txnOpts = TransactionDBOptions.newTransactionDBOptions();
		     var db = RocksDB.openTransaction(opts, txnOpts, dir, BOTH);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions();
		     var txn = db.beginTransaction(wo)) {
			var cf = db.getColumnFamily(CF1);

			// When
			txn.put(cf, "k2".getBytes(), "v2".getBytes());
			var result = txn.get(cf, ro, "k2".getBytes());

			// Then
			assertThat(result).isEqualTo("v2".getBytes());
		}
	}

	@Test
	void readBatch_overTheDefaultFamily_usesTheRegisteredHandle(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("a".getBytes(), "1".getBytes());

			// When
			try (var batch = ReadBatch.create(db, 1)) {
				var values = batch.get(List.of("a".getBytes()));

				// Then
				assertThat(values).hasSize(1);
				assertThat(values.get(0)).isEqualTo("1".getBytes());
			}
		}
	}
}
