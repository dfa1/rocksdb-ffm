package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Closing a [Snapshot] and the [ReadOptions] it is installed in from two threads at the same moment must not touch
/// the options' native struct after it was destroyed: the snapshot's release used to write `set_snapshot(NULL)` into
/// the options while the other thread destroyed them.
class SnapshotReadOptionsRaceTest {

	private static final int ITERATIONS = 5_000;

	@Test
	void closingASnapshotAndItsReadOptionsConcurrently_neverFailsOrCrashes(@TempDir Path dir) throws Exception {
		// Given
		var failures = new ConcurrentLinkedQueue<Throwable>();
		var barrier = new CyclicBarrier(2);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var executor = Executors.newFixedThreadPool(2)) {

			// When — the options are created and closed on one thread, the snapshot is closed on the other
			var closeSnapshots = executor.submit(() -> {
				for (int i = 0; i < ITERATIONS; i++) {
					Holder.snapshot = db.getSnapshot();
					await(barrier);
					await(barrier);
					Holder.snapshot.close();
					await(barrier);
				}
				return null;
			});
			var closeOptions = executor.submit(() -> {
				for (int i = 0; i < ITERATIONS; i++) {
					await(barrier);
					var readOptions = ReadOptions.newReadOptions().setSnapshot(Holder.snapshot);
					await(barrier);
					readOptions.close();
					await(barrier);
				}
				return null;
			});
			closeSnapshots.get(120, TimeUnit.SECONDS);
			closeOptions.get(120, TimeUnit.SECONDS);

			// Then
			assertThat(failures).isEmpty();
		}
	}

	private static final class Holder {
		static volatile Snapshot snapshot;
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
