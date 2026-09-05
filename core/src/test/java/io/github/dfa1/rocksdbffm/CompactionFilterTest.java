package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers [CompactionFilter], attached via [Options#setCompactionFilter(CompactionFilter)]. Every
/// test forces a full-range manual compaction (`compactRange()` flushes the memtable and rewrites
/// every SST file) since a filter never runs against data sitting only in the memtable.
class CompactionFilterTest {

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void keep_preservesEveryKeyThroughCompaction(@TempDir Path dir) {
		// Given
		CompactionFilter.FilterFn keepAll = (level, key, existingValue) -> CompactionFilter.FilterDecision.keep();
		try (var filter = CompactionFilter.create("keep-all", keepAll);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("a"), bytes("1"));
			db.put(bytes("b"), bytes("2"));

			// When
			db.compactRange();

			// Then
			assertThat(db.get(bytes("a"))).isEqualTo(bytes("1"));
			assertThat(db.get(bytes("b"))).isEqualTo(bytes("2"));
		}
	}

	@Test
	void remove_dropsOnlyMatchingKeysDuringCompaction(@TempDir Path dir) {
		// Given
		CompactionFilter.FilterFn dropTombstonePrefixed = (level, key, existingValue) -> {
			byte[] k = key.toArray(ValueLayout.JAVA_BYTE);
			return new String(k, StandardCharsets.UTF_8).startsWith("drop-")
					? CompactionFilter.FilterDecision.remove()
					: CompactionFilter.FilterDecision.keep();
		};
		try (var filter = CompactionFilter.create("drop-prefixed", dropTombstonePrefixed);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("drop-1"), bytes("x"));
			db.put(bytes("drop-2"), bytes("y"));
			db.put(bytes("keep-1"), bytes("z"));

			// When
			db.compactRange();

			// Then
			assertThat(db.get(bytes("drop-1"))).isNull();
			assertThat(db.get(bytes("drop-2"))).isNull();
			assertThat(db.get(bytes("keep-1"))).isEqualTo(bytes("z"));
		}
	}

	@Test
	void changeValue_rewritesTheValueDuringCompaction(@TempDir Path dir) {
		// Given
		CompactionFilter.FilterFn upperCase = (level, key, existingValue) -> {
			byte[] value = existingValue.toArray(ValueLayout.JAVA_BYTE);
			String upper = new String(value, StandardCharsets.UTF_8).toUpperCase(java.util.Locale.ROOT);
			return CompactionFilter.FilterDecision.changeValue(upper.getBytes(StandardCharsets.UTF_8));
		};
		try (var filter = CompactionFilter.create("upper-case", upperCase);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("k"), bytes("hello"));

			// When
			db.compactRange();

			// Then
			assertThat(db.get(bytes("k"))).isEqualTo(bytes("HELLO"));
		}
	}

	@Test
	void changeValue_reusesItsScratchBufferAcrossManyGrowingAndShrinkingValues(@TempDir Path dir) {
		// Given — the same compaction thread hands back a changeValue decision for many keys in
		// a row, with value sizes that grow and then shrink, to exercise the scratch buffer's
		// resize-on-grow and safe-reuse-on-shrink paths rather than just a single call
		CompactionFilter.FilterFn upperCase = (level, key, existingValue) -> {
			byte[] value = existingValue.toArray(ValueLayout.JAVA_BYTE);
			String upper = new String(value, StandardCharsets.UTF_8).toUpperCase(java.util.Locale.ROOT);
			return CompactionFilter.FilterDecision.changeValue(upper.getBytes(StandardCharsets.UTF_8));
		};
		int keyCount = 200;
		try (var filter = CompactionFilter.create("upper-case-many", upperCase);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			for (int i = 0; i < keyCount; i++) {
				// triangular-wave size: grows for the first half of keys, shrinks for the rest
				int size = 1 + (i < keyCount / 2 ? i : keyCount - i);
				db.put(bytes("k" + i), bytes("v".repeat(size)));
			}

			// When
			db.compactRange();

			// Then
			for (int i = 0; i < keyCount; i++) {
				int size = 1 + (i < keyCount / 2 ? i : keyCount - i);
				assertThat(db.get(bytes("k" + i))).isEqualTo(bytes("V".repeat(size)));
			}
		}
	}

	@Test
	void filterFnThatThrows_leavesTheKeyUnchangedRatherThanCrashOrLoseData(@TempDir Path dir) {
		// Given
		CompactionFilter.FilterFn throwing = (level, key, existingValue) -> {
			throw new RuntimeException("boom");
		};
		try (var filter = CompactionFilter.create("throwing", throwing);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("k"), bytes("v"));

			// When
			db.compactRange();

			// Then
			assertThat(db.get(bytes("k"))).isEqualTo(bytes("v"));
		}
	}

	@Test
	void setIgnoreSnapshots_returnsThisForChaining() {
		// Given
		CompactionFilter.FilterFn keepAll = (level, key, existingValue) -> CompactionFilter.FilterDecision.keep();

		// When
		try (var filter = CompactionFilter.create("keep-all", keepAll)) {
			var result = filter.setIgnoreSnapshots(false);

			// Then
			assertThat(result).isSameAs(filter);
		}
	}

	@Test
	void setIgnoreSnapshots_toFalse_makesRocksDbRejectTheCompactionSoTheFilterNeverRuns(@TempDir Path dir) {
		// Given — RocksDB's CompactionJob::SetupAndValidateCompactionFilter (db/compaction/compaction_job.cc)
		// rejects every compaction whose filter reports IgnoreSnapshots() == false with
		// Status::NotSupported, confirmed by upstream's own DBTestCompactionFilter.IgnoreSnapshotsFalse test.
		// rocksdb_compact_range has no errptr, so that failure is invisible here — this test documents it
		// through the compaction's observable no-op effect: a filter that would otherwise remove every key
		// leaves them all in place instead, because the compaction job never even ran the filter.
		CompactionFilter.FilterFn removeAll = (level, key, existingValue) -> CompactionFilter.FilterDecision.remove();
		try (var filter = CompactionFilter.create("remove-all", removeAll).setIgnoreSnapshots(false);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("a"), bytes("1"));
			db.put(bytes("b"), bytes("2"));

			// When
			db.compactRange();

			// Then
			assertThat(db.get(bytes("a"))).isEqualTo(bytes("1"));
			assertThat(db.get(bytes("b"))).isEqualTo(bytes("2"));
		}
	}

	@Test
	void filter_seesAZeroLengthExistingValueAsAnEmptyNotNullView(@TempDir Path dir) {
		// Given
		var seenLength = new java.util.concurrent.atomic.AtomicReference<Long>();
		CompactionFilter.FilterFn capturing = (level, key, existingValue) -> {
			seenLength.set(existingValue.byteSize());
			return CompactionFilter.FilterDecision.keep();
		};
		try (var filter = CompactionFilter.create("capturing-empty", capturing);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("k"), bytes(""));

			// When
			db.compactRange();

			// Then
			assertThat(seenLength.get()).isZero();
		}
	}

	@Test
	void setCompactionFilter_transferredFilter_ptrThrows() {
		// Given
		CompactionFilter.FilterFn keepAll = (level, key, existingValue) -> CompactionFilter.FilterDecision.keep();
		var filter = CompactionFilter.create("keep-all", keepAll);

		// When
		try (var opts = Options.newOptions().setCompactionFilter(filter)) {

			// Then
			assertThatThrownBy(filter::ptr).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void filter_seesTheCorrectExistingValue(@TempDir Path dir) {
		// Given
		var seenValue = new java.util.concurrent.atomic.AtomicReference<String>();
		CompactionFilter.FilterFn capturing = (level, key, existingValue) -> {
			seenValue.set(new String(existingValue.toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8));
			return CompactionFilter.FilterDecision.keep();
		};
		try (var filter = CompactionFilter.create("capturing", capturing);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("k"), bytes("expected-value"));

			// When
			db.compactRange();

			// Then
			assertThat(seenValue.get()).isEqualTo("expected-value");
		}
	}
}
