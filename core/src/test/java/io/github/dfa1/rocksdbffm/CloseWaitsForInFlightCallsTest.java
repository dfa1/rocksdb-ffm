package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/// A database closed while another thread is inside a call on it waits for that call instead of freeing the
/// database under it. Before, an iterator scan or a `compactRange()` racing a close killed the JVM.
class CloseWaitsForInFlightCallsTest {

	private static final byte[] KEY = "k".getBytes();
	private static final byte[] VALUE = "v".getBytes();

	@Test
	void close_waitsForACallInFlightOnAnotherThread(@TempDir Path dir) throws Exception {
		// Given -- a reader parked inside a pinned get, holding a value that points into the database
		var inMapper = new CountDownLatch(1);
		var releaseMapper = new CountDownLatch(1);
		try (var opts = Options.newOptions().setCreateIfMissing(true)) {
			var db = RocksDB.openReadWrite(dir, opts);
			db.put(KEY, VALUE);
			var reader = CompletableFuture.supplyAsync(() -> {
				try (var arena = Arena.ofConfined()) {
					return db.get(arena.allocateFrom(ValueLayout.JAVA_BYTE, KEY), value -> {
						inMapper.countDown();
						await(releaseMapper);
						return value.byteSize();
					});
				}
			});
			inMapper.await();

			// When
			var close = CompletableFuture.runAsync(db::close);
			var closeFinishedEarly = isDoneWithin(close, 300);
			releaseMapper.countDown();
			var read = reader.get(10, TimeUnit.SECONDS);
			close.get(10, TimeUnit.SECONDS);

			// Then -- the read finished against a live database, and only then did the close go through
			assertThat(closeFinishedEarly).isFalse();
			assertThat(read).isEqualTo((long) VALUE.length);
			assertThat(catchThrowable(() -> db.get(KEY))).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void dbClose_waitsForAnIteratorCallInFlightOnAnotherThread(@TempDir Path dir) throws Exception {
		// Given -- a scan parked inside an iterator's Mapper, holding a view of the current key
		var inMapper = new CountDownLatch(1);
		var releaseMapper = new CountDownLatch(1);
		try (var opts = Options.newOptions().setCreateIfMissing(true)) {
			var db = RocksDB.openReadWrite(dir, opts);
			db.put(KEY, VALUE);
			var iterator = db.newIterator();
			var reader = CompletableFuture.supplyAsync(() -> {
				iterator.seekToFirst();
				return iterator.key(key -> {
					inMapper.countDown();
					await(releaseMapper);
					return key.byteSize();
				});
			});
			inMapper.await();

			// When
			var close = CompletableFuture.runAsync(db::close);
			var closeFinishedEarly = isDoneWithin(close, 300);
			releaseMapper.countDown();
			var read = reader.get(10, TimeUnit.SECONDS);
			close.get(10, TimeUnit.SECONDS);

			// Then -- the iterator was destroyed only once the Mapper returned
			assertThat(closeFinishedEarly).isFalse();
			assertThat(read).isEqualTo((long) KEY.length);
			assertThat(catchThrowable(iterator::isValid)).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void transactionClose_waitsForItsIteratorCallInFlightOnAnotherThread(@TempDir Path dir) throws Exception {
		// Given -- a transaction's iterator parked inside its Mapper
		var inMapper = new CountDownLatch(1);
		var releaseMapper = new CountDownLatch(1);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(dir, opts);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions()) {
			var txn = db.beginTransaction(wo);
			var cf = db.getColumnFamily(ColumnFamilyName.DEFAULT);
			txn.put(cf, KEY, VALUE);
			var iterator = txn.newIterator(cf, ro);
			var reader = CompletableFuture.supplyAsync(() -> {
				iterator.seekToFirst();
				return iterator.value(value -> {
					inMapper.countDown();
					await(releaseMapper);
					return value.byteSize();
				});
			});
			inMapper.await();

			// When
			var close = CompletableFuture.runAsync(txn::close);
			var closeFinishedEarly = isDoneWithin(close, 300);
			releaseMapper.countDown();
			var read = reader.get(10, TimeUnit.SECONDS);
			close.get(10, TimeUnit.SECONDS);

			// Then
			assertThat(closeFinishedEarly).isFalse();
			assertThat(read).isEqualTo((long) VALUE.length);
			assertThat(catchThrowable(iterator::isValid)).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void iteratorClose_fromInsideItsOwnMapper_throwsInsteadOfWaitingForItself(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put(KEY, VALUE);
			var iterator = db.newIterator();
			iterator.seekToFirst();

			// When
			var thrown = iterator.key(key -> catchThrowable(iterator::close));

			// Then -- refused before anything was released, so the iterator still works and closes normally after
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(iterator.key()).isEqualTo(KEY);
			iterator.close();
		}
	}

	@Test
	void readOptionsClose_fromInsideItsIteratorsMapper_throwsAndReleasesNothing(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var ro = ReadOptions.newReadOptions()) {
			db.put(KEY, VALUE);
			var iterator = db.newIterator(ro);
			iterator.seekToFirst();

			// When
			var thrown = iterator.key(key -> catchThrowable(ro::close));

			// Then -- refused before the swap, so neither the options nor the iterator was released
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(iterator.key()).isEqualTo(KEY);
			iterator.close();
		}
	}

	@Test
	void transactionCommit_fromInsideItsIteratorsMapper_throwsAndCommitsNothing(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openOptimistic(dir, opts);
		     var wo = WriteOptions.newWriteOptions();
		     var ro = ReadOptions.newReadOptions()) {
			var txn = db.beginTransaction(wo);
			var cf = db.getColumnFamily(ColumnFamilyName.DEFAULT);
			txn.put(cf, KEY, VALUE);
			var iterator = txn.newIterator(cf, ro);
			iterator.seekToFirst();

			// When
			var thrown = iterator.value(value -> catchThrowable(txn::commit));

			// Then -- refused before the native commit: nothing is committed and the iterator still reads
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(db.get(KEY)).isNull();
			assertThat(iterator.value()).isEqualTo(VALUE);
			txn.close();
		}
	}

	private static boolean isDoneWithin(CompletableFuture<?> future, long millis) {
		try {
			future.get(millis, TimeUnit.MILLISECONDS);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
