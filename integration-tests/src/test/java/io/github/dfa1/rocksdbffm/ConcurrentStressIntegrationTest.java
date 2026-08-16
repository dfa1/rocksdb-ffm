package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.assertj.core.api.Assertions.assertThat;

/// Multi-threaded stress test modeled on RocksDB's `db_stress` tool: many threads hammer
/// a shared DB handle with randomized operations while an in-memory "expected state" oracle
/// tracks what the DB should contain, so any divergence is a genuine correctness bug rather
/// than a benign race. Unlike upstream `db_stress`, which mostly stresses the storage engine
/// itself, the second test here targets this project's own FFM wrapper layer: rapid
/// concurrent create/close of native handles (snapshots, read options, iterators) is exactly
/// the kind of ownership/lifecycle bug (`close()` racing a native call, use-after-free) that
/// is specific to this codebase rather than to RocksDB proper.
class ConcurrentStressIntegrationTest {

	private static final int KEY_COUNT = 128;
	private static final int THREAD_COUNT = 8;
	private static final int OPS_PER_THREAD = 5_000;

	@Test
	void concurrentPutGetDelete_matchesExpectedState(@TempDir Path dir) throws InterruptedException {
		// Given — a per-key lock stripes writers/readers so oracle updates and the
		// matching DB call happen atomically, mirroring db_stress's key-range locking.
		var locks = new ReentrantReadWriteLock[KEY_COUNT];
		var expected = new byte[KEY_COUNT][];
		for (int i = 0; i < KEY_COUNT; i++) {
			locks[i] = new ReentrantReadWriteLock();
		}
		var failures = new ConcurrentLinkedQueue<Throwable>();

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
			var latch = new CountDownLatch(THREAD_COUNT);

			// When
			for (int t = 0; t < THREAD_COUNT; t++) {
				executor.submit(() -> {
					try {
						var random = ThreadLocalRandom.current();
						for (int op = 0; op < OPS_PER_THREAD; op++) {
							int keyIndex = random.nextInt(KEY_COUNT);
							byte[] key = ("key-" + keyIndex).getBytes();
							var lock = locks[keyIndex];
							switch (random.nextInt(3)) {
								case 0 -> {
									byte[] value = ("v-" + random.nextLong()).getBytes();
									lock.writeLock().lock();
									try {
										db.put(key, value);
										expected[keyIndex] = value;
									} finally {
										lock.writeLock().unlock();
									}
								}
								case 1 -> {
									lock.writeLock().lock();
									try {
										db.delete(key);
										expected[keyIndex] = null;
									} finally {
										lock.writeLock().unlock();
									}
								}
								default -> {
									lock.readLock().lock();
									try {
										assertThat(db.get(key)).isEqualTo(expected[keyIndex]);
									} finally {
										lock.readLock().unlock();
									}
								}
							}
						}
					} catch (Throwable t2) {
						failures.add(t2);
					} finally {
						latch.countDown();
					}
				});
			}
			boolean completed = latch.await(60, TimeUnit.SECONDS);
			executor.shutdown();

			// Then
			assertThat(completed).isTrue();
			assertThat(failures).isEmpty();
			for (int i = 0; i < KEY_COUNT; i++) {
				assertThat(db.get(("key-" + i).getBytes())).isEqualTo(expected[i]);
			}
		}
	}

	@Test
	void concurrentSnapshotsAndIterators_surviveWriteLoad(@TempDir Path dir) throws InterruptedException {
		// Given
		int readerThreads = 4;
		int writerThreads = 2;
		int readerIterations = 500;
		int writerOpsPerThread = 5_000;
		var failures = new ConcurrentLinkedQueue<Throwable>();

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			for (int i = 0; i < KEY_COUNT; i++) {
				db.put(("key-" + i).getBytes(), "seed".getBytes());
			}

			ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads);
			var latch = new CountDownLatch(readerThreads + writerThreads);

			// When
			for (int w = 0; w < writerThreads; w++) {
				executor.submit(() -> {
					try {
						var random = ThreadLocalRandom.current();
						for (int op = 0; op < writerOpsPerThread; op++) {
							int keyIndex = random.nextInt(KEY_COUNT);
							byte[] key = ("key-" + keyIndex).getBytes();
							if (random.nextBoolean()) {
								db.put(key, ("v-" + random.nextLong()).getBytes());
							} else {
								db.delete(key);
							}
						}
					} catch (Throwable t) {
						failures.add(t);
					} finally {
						latch.countDown();
					}
				});
			}
			for (int r = 0; r < readerThreads; r++) {
				executor.submit(() -> {
					try {
						for (int i = 0; i < readerIterations; i++) {
							// Fresh snapshot + read options + iterator every iteration: hammers
							// create/close of these native handles concurrently with writers.
							try (var snapshot = db.getSnapshot();
							     var readOptions = ReadOptions.newReadOptions().setSnapshot(snapshot);
							     var iterator = db.newIterator(readOptions)) {
								int seen = 0;
								for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
									iterator.key();
									iterator.value();
									seen++;
								}
								if (seen > KEY_COUNT) {
									throw new AssertionError("iterator saw " + seen + " keys, expected at most " + KEY_COUNT);
								}
							}
						}
					} catch (Throwable t) {
						failures.add(t);
					} finally {
						latch.countDown();
					}
				});
			}
			boolean completed = latch.await(60, TimeUnit.SECONDS);
			executor.shutdown();

			// Then
			assertThat(completed).isTrue();
			assertThat(failures).isEmpty();

			// DB must still be fully functional after the storm of concurrent handle churn.
			db.put("healthcheck".getBytes(), "ok".getBytes());
			assertThat(db.get("healthcheck".getBytes())).isEqualTo("ok".getBytes());
		}
	}
}
