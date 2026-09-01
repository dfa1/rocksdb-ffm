package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Proves [EventNotifier] observes *real* automatic background compactions -- triggered purely
/// by write volume, the way production traffic would trigger them -- rather than a manually
/// invoked `compactRange()`. This is the scenario [BackgroundUpcallThreads] exists for: callbacks
/// firing repeatedly from RocksDB's own background threads under sustained write pressure.
///
/// Tuning the memtable and level-0 trigger down (via [Options#setWriteBufferSize],
/// [Options#setLevel0FileNumCompactionTrigger], [Options#setTargetFileSizeBase]) means this
/// reaches real compactions with tens of thousands of small keys instead of the tens of millions
/// RocksDB's defaults (64 MiB memtable, 4-file trigger) would otherwise require.
class EventNotifierCompactionIntegrationTest {

	private static final int KEY_COUNT = 50_000;

	@Test
	void sustainedWrites_triggerRealCompactions_observedByEventNotifier(@TempDir Path dir) {
		// Given -- a small memtable and a low level-0 trigger so ordinary writes below force
		// many flushes and, once enough level-0 files pile up, real automatic compactions
		AtomicInteger flushBeginCount = new AtomicInteger();
		AtomicInteger flushCompletedCount = new AtomicInteger();
		AtomicInteger compactionBeginCount = new AtomicInteger();
		AtomicInteger compactionCompletedCount = new AtomicInteger();
		List<Integer> compactionOutputLevels = new CopyOnWriteArrayList<>();
		List<String> compactionColumnFamilyNames = new CopyOnWriteArrayList<>();

		EventNotifier notifier = new EventNotifier() {
			@Override
			public void onFlushBegin(FlushJobInfo info) {
				flushBeginCount.incrementAndGet();
			}

			@Override
			public void onFlushCompleted(FlushJobInfo info) {
				flushCompletedCount.incrementAndGet();
			}

			@Override
			public void onCompactionBegin(CompactionJobInfo info) {
				compactionBeginCount.incrementAndGet();
			}

			@Override
			public void onCompactionCompleted(CompactionJobInfo info) {
				compactionCompletedCount.incrementAndGet();
				compactionOutputLevels.add(info.outputLevel());
				compactionColumnFamilyNames.add(info.columnFamilyName());
			}
		};

		try (var opts = Options.newOptions()
				     .setCreateIfMissing(true)
				     .setWriteBufferSize(MemorySize.ofKB(64))
				     .setLevel0FileNumCompactionTrigger(2)
				     .setTargetFileSizeBase(MemorySize.ofKB(64))
				     .addEventListener(notifier);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When -- plain sequential writes; no manual flush() or compactRange() anywhere
			for (int i = 0; i < KEY_COUNT; i++) {
				byte[] key = String.format("key-%08d", i).getBytes();
				byte[] value = String.format("value-%08d-padding-to-avoid-tiny-entries", i).getBytes();
				db.put(key, value);
			}

			// Drain background work so every in-flight flush/compaction has actually finished
			// (and its callbacks fired) before we assert on the counters below
			db.waitForCompact(WaitForCompactOptions.create().setFlush(true));

			// Then -- flushes happened (expected: dozens, given a 64 KB memtable and this much
			// data) and at least one real compaction was triggered and completed automatically
			assertThat(flushBeginCount.get()).isPositive();
			assertThat(flushCompletedCount.get()).isEqualTo(flushBeginCount.get());
			assertThat(compactionBeginCount.get()).isPositive();
			assertThat(compactionCompletedCount.get()).isEqualTo(compactionBeginCount.get());

			// Every observed compaction moved data out of level 0 and stayed on the default CF
			assertThat(compactionOutputLevels).allMatch(level -> level >= 1);
			assertThat(compactionColumnFamilyNames).allMatch("default"::equals);

			// The database is still fully correct after all that background churn
			assertThat(db.get(String.format("key-%08d", 0).getBytes()))
					.isEqualTo(String.format("value-%08d-padding-to-avoid-tiny-entries", 0).getBytes());
			assertThat(db.get(String.format("key-%08d", KEY_COUNT - 1).getBytes()))
					.isEqualTo(String.format("value-%08d-padding-to-avoid-tiny-entries", KEY_COUNT - 1).getBytes());
		}
	}
}
