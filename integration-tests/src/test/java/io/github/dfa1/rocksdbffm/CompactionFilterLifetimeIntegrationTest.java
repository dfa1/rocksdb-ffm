package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// A [CompactionFilter] attached with [Options#setCompactionFilter(CompactionFilter)] is immortal (#320): one filter,
/// created once, is reused by many [Options] and databases. Opens, compacts and closes many databases, in both close
/// orders and from several threads, and checks that the shared filter keeps filtering every time.
class CompactionFilterLifetimeIntegrationTest {

	private static final int CYCLES = 16;
	private static final int THREAD_COUNT = 4;

	private static final AtomicInteger CALLS = new AtomicInteger();

	/// Removes every key starting with `drop-`; created once, like any filter should be.
	private static final CompactionFilter DROP_PREFIX = CompactionFilter.create("drop-prefix",
			(level, key, existingValue) -> {
				CALLS.incrementAndGet();
				return new String(key.toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8).startsWith("drop-")
						? CompactionFilter.FilterDecision.remove()
						: CompactionFilter.FilterDecision.keep();
			});

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void manyOpenCompactCloseCycles_theSharedFilterFiltersEveryTime(@TempDir Path dir) {
		// Given
		var callsBefore = CALLS.get();

		// When — alternating which of the options and the database is closed first
		for (int cycle = 0; cycle < CYCLES; cycle++) {
			var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(DROP_PREFIX);
			var db = RocksDB.openReadWrite(dir.resolve("db-" + cycle), opts);
			for (int i = 0; i < 50; i++) {
				db.put(bytes("keep-" + i), bytes("v"));
				db.put(bytes("drop-" + i), bytes("v"));
			}
			db.compactRange();
			db.put(bytes("keep-late"), bytes("v"));
			db.put(bytes("drop-late"), bytes("v"));
			if (cycle % 2 == 0) {
				opts.close();
			}
			db.compactRange();

			// Then — checked while the database is still open
			assertThat(db.get(bytes("keep-0"))).isEqualTo(bytes("v"));
			assertThat(db.get(bytes("keep-late"))).isEqualTo(bytes("v"));
			assertThat(db.get(bytes("drop-0"))).isNull();
			assertThat(db.get(bytes("drop-late"))).isNull();
			db.close();
			opts.close();
		}

		// Then
		assertThat(CALLS.get() - callsBefore).isGreaterThanOrEqualTo(CYCLES * 100);
	}

	@Test
	void oneOptionsSharedByManyDatabasesOnManyThreads_theSharedFilterFiltersEveryTime(@TempDir Path dir)
			throws InterruptedException {
		// Given
		var failures = new ConcurrentLinkedQueue<Throwable>();
		var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(DROP_PREFIX);
		var executor = Executors.newFixedThreadPool(THREAD_COUNT);

		// When — every thread opens, compacts and closes its own databases from the shared options
		for (int t = 0; t < THREAD_COUNT; t++) {
			int thread = t;
			executor.submit(() -> {
				try {
					for (int cycle = 0; cycle < CYCLES / 2; cycle++) {
						try (var db = RocksDB.openReadWrite(dir.resolve("t" + thread + "-" + cycle), opts)) {
							for (int i = 0; i < 20; i++) {
								db.put(bytes("keep-" + i), bytes("v"));
								db.put(bytes("drop-" + i), bytes("v"));
							}
							db.compactRange();
							if (db.get(bytes("drop-0")) != null || db.get(bytes("keep-0")) == null) {
								failures.add(new AssertionError("filter did not apply in t" + thread + "-" + cycle));
							}
						}
					}
				} catch (Throwable t1) {
					failures.add(t1);
				}
			});
		}
		executor.shutdown();
		var finished = executor.awaitTermination(120, TimeUnit.SECONDS);
		opts.close();

		// Then
		assertThat(finished).isTrue();
		assertThat(failures).isEmpty();
	}
}
