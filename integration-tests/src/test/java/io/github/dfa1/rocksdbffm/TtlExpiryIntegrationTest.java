package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// What a [TtlDB] actually expires: keys written with a one second TTL, then a flush, a sleep past the TTL and a
/// manual compaction (expiry only happens in compaction, see `docs/how-to.md`), for each way of giving a column
/// family its TTL (or none), with and without a database-level TTL.
class TtlExpiryIntegrationTest {

	private static final Duration ONE_SECOND = Duration.ofSeconds(1);
	private static final Duration ONE_HOUR = Duration.ofHours(1);

	/// Native compares whole seconds (`timestamp + ttl < now`), so three seconds is safely past a one second TTL.
	private static final long PAST_THE_TTL_MS = 3_000;

	private static final byte[] KEY = "key".getBytes();
	private static final byte[] VALUE = "value".getBytes();

	private static final ColumnFamilyName SHORT = ColumnFamilyName.of("short");
	private static final ColumnFamilyName LONG = ColumnFamilyName.of("long");
	private static final ColumnFamilyName NEVER = ColumnFamilyName.of("never");

	/// Flushes the memtable of `cf` (compaction only sees files), waits past the TTL, then compacts the family.
	private static void expireAndCompact(TtlDB db, ColumnFamilyHandle cf) throws InterruptedException {
		try (var flushOptions = FlushOptions.newFlushOptions()) {
			db.flush(cf, flushOptions);
		}
		Thread.sleep(PAST_THE_TTL_MS);
		db.compactRange(cf);
	}

	@Test
	void databaseTtl_expiresTheDefaultFamily_butNotAFreshKey(@TempDir Path dir) throws InterruptedException {
		// Given — a database-level TTL of one second
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dir, ONE_SECOND);
		     var flushOptions = FlushOptions.newFlushOptions()) {
			var cf = db.getColumnFamily(ColumnFamilyName.DEFAULT);
			db.put(KEY, VALUE);
			db.flush(flushOptions);
			Thread.sleep(PAST_THE_TTL_MS);
			db.put("fresh".getBytes(), VALUE);
			db.flush(flushOptions);

			// When
			db.compactRange(cf);

			// Then — the old key is gone, the one written after the sleep is not
			assertThat(db.get(KEY)).isNull();
			assertThat(db.get("fresh".getBytes())).isEqualTo(VALUE);
		}
	}

	@Test
	void expiredKey_isStillReadableUntilACompactionRuns(@TempDir Path dir) throws InterruptedException {
		// Given — a key written with a one second TTL, long past its TTL, in the memtable and then in a file
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dir, ONE_SECOND);
		     var flushOptions = FlushOptions.newFlushOptions()) {
			db.put(KEY, VALUE);
			Thread.sleep(PAST_THE_TTL_MS);

			// When — nothing has compacted it yet
			var inMemtable = db.get(KEY);
			db.flush(flushOptions);
			var inFile = db.get(KEY);
			db.compactRange();
			var afterCompaction = db.get(KEY);

			// Then — expiry is lazy: the TTL only makes the key eligible, a compaction is what removes it
			assertThat(inMemtable).as("expired, in the memtable").isEqualTo(VALUE);
			assertThat(inFile).as("expired, flushed to a file").isEqualTo(VALUE);
			assertThat(afterCompaction).as("expired, compacted").isNull();
		}
	}

	@Test
	void databaseTtl_zero_neverExpires(@TempDir Path dir) throws InterruptedException {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dir, Duration.ZERO)) {
			db.put(KEY, VALUE);

			// When
			expireAndCompact(db, db.getColumnFamily(ColumnFamilyName.DEFAULT));

			// Then
			assertThat(db.get(KEY)).isEqualTo(VALUE);
		}
	}

	@Test
	void perFamilyTtl_eachFamilyFollowsItsOwnTtl(@TempDir Path dir) throws InterruptedException {
		// Given — a database without a TTL of its own, and three families: 1 second, 1 hour, and none
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dir, Duration.ZERO)) {
			var shortCf = db.createColumnFamily(ColumnFamilyDescriptor.of(SHORT).withTtl(ONE_SECOND));
			var longCf = db.createColumnFamily(ColumnFamilyDescriptor.of(LONG).withTtl(ONE_HOUR));
			var neverCf = db.createColumnFamily(ColumnFamilyDescriptor.of(NEVER));
			for (var cf : List.of(shortCf, longCf, neverCf)) {
				db.put(cf, KEY, VALUE);
			}
			for (var cf : List.of(shortCf, longCf)) {
				try (var flushOptions = FlushOptions.newFlushOptions()) {
					db.flush(cf, flushOptions);
				}
			}
			try (var flushOptions = FlushOptions.newFlushOptions()) {
				db.flush(neverCf, flushOptions);
			}
			Thread.sleep(PAST_THE_TTL_MS);

			// When
			db.compactRange(shortCf);
			db.compactRange(longCf);
			db.compactRange(neverCf);

			// Then
			assertThat(db.get(shortCf, KEY)).as("1 second TTL").isNull();
			assertThat(db.get(longCf, KEY)).as("1 hour TTL").isEqualTo(VALUE);
			assertThat(db.get(neverCf, KEY)).as("no TTL").isEqualTo(VALUE);
		}
	}

	@Test
	void familyCreatedWithoutTtl_doesNotInheritTheDatabaseTtl(@TempDir Path dir) throws InterruptedException {
		// Given — a database-level TTL of one second, a family created without a TTL, one created with 1 hour
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dir, ONE_SECOND)) {
			var plain = db.createColumnFamily(ColumnFamilyDescriptor.of(NEVER));
			var longCf = db.createColumnFamily(ColumnFamilyDescriptor.of(LONG).withTtl(ONE_HOUR));
			var defaultCf = db.getColumnFamily(ColumnFamilyName.DEFAULT);
			for (var cf : List.of(plain, longCf, defaultCf)) {
				db.put(cf, KEY, VALUE);
				try (var flushOptions = FlushOptions.newFlushOptions()) {
					db.flush(cf, flushOptions);
				}
			}
			Thread.sleep(PAST_THE_TTL_MS);

			// When
			db.compactRange(plain);
			db.compactRange(longCf);
			db.compactRange(defaultCf);

			// Then — only the default family follows the database-level TTL
			assertThat(db.get(defaultCf, KEY)).as("default family, database TTL 1s").isNull();
			assertThat(db.get(plain, KEY)).as("created without a TTL").isEqualTo(VALUE);
			assertThat(db.get(longCf, KEY)).as("created with 1 hour").isEqualTo(VALUE);
		}
	}

	@Test
	void familyCreatedWithTtl_expiresEvenWhenTheDatabaseTtlIsLonger(@TempDir Path dir) throws InterruptedException {
		// Given — a database-level TTL of one hour, a family created with one second
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dir, ONE_HOUR)) {
			var shortCf = db.createColumnFamily(ColumnFamilyDescriptor.of(SHORT).withTtl(ONE_SECOND));
			var defaultCf = db.getColumnFamily(ColumnFamilyName.DEFAULT);
			db.put(shortCf, KEY, VALUE);
			db.put(defaultCf, KEY, VALUE);
			try (var flushOptions = FlushOptions.newFlushOptions()) {
				db.flush(shortCf, flushOptions);
				db.flush(defaultCf, flushOptions);
			}
			Thread.sleep(PAST_THE_TTL_MS);

			// When
			db.compactRange(shortCf);
			db.compactRange(defaultCf);

			// Then
			assertThat(db.get(shortCf, KEY)).as("family TTL 1s").isNull();
			assertThat(db.get(defaultCf, KEY)).as("default family, database TTL 1h").isEqualTo(VALUE);
		}
	}

	@Test
	void perFamilyOpen_appliesTheTtlsGivenOnTheDescriptors(@TempDir Path dir) throws InterruptedException {
		// Given — the families exist (created through a TtlDB, so their values carry timestamps)
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(opts, dir, Duration.ZERO)) {
			db.createColumnFamily(ColumnFamilyDescriptor.of(SHORT));
			db.createColumnFamily(ColumnFamilyDescriptor.of(LONG));
			db.createColumnFamily(ColumnFamilyDescriptor.of(NEVER));
		}

		// When — reopened with one TTL per family: 1 second, 1 hour, and none (default: 1 second)
		try (var opts = Options.newOptions();
		     var db = RocksDB.openTtl(opts, dir, List.of(
				     ColumnFamilyDescriptor.of(ColumnFamilyName.DEFAULT).withTtl(ONE_SECOND),
				     ColumnFamilyDescriptor.of(SHORT).withTtl(ONE_SECOND),
				     ColumnFamilyDescriptor.of(LONG).withTtl(ONE_HOUR),
				     ColumnFamilyDescriptor.of(NEVER)))) {
			var families = db.getColumnFamilies();
			for (var cf : families.values()) {
				db.put(cf, KEY, VALUE);
				try (var flushOptions = FlushOptions.newFlushOptions()) {
					db.flush(cf, flushOptions);
				}
			}
			Thread.sleep(PAST_THE_TTL_MS);
			for (var cf : families.values()) {
				db.compactRange(cf);
			}

			// Then
			assertThat(db.get(families.get(ColumnFamilyName.DEFAULT), KEY)).as("default, 1s").isNull();
			assertThat(db.get(families.get(SHORT), KEY)).as("short, 1s").isNull();
			assertThat(db.get(families.get(LONG), KEY)).as("long, 1h").isEqualTo(VALUE);
			assertThat(db.get(families.get(NEVER), KEY)).as("never, no TTL").isEqualTo(VALUE);
			assertThat(db.getTtl()).isEmpty();
		}
	}
}
