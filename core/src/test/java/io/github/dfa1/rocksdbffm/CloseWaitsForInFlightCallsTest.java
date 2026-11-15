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
