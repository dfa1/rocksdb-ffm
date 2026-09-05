package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers [CompactionFilterFactory], attached via
/// [Options#setCompactionFilterFactory(CompactionFilterFactory)]. Every test forces a full-range
/// manual compaction (`compactRange()` flushes the memtable and rewrites every SST file) since a
/// factory never runs against data sitting only in the memtable.
class CompactionFilterFactoryTest {

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void create_producesAFilterThatActuallyRunsDuringCompaction(@TempDir Path dir) {
		// Given
		CompactionFilterFactory.CreateFilterFn removeAllFactory = context -> CompactionFilter.create("remove-all",
				(level, key, existingValue) -> CompactionFilter.FilterDecision.remove());
		try (var factory = CompactionFilterFactory.create("remove-all-factory", removeAllFactory);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilterFactory(factory);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("a"), bytes("1"));
			db.put(bytes("b"), bytes("2"));

			// When
			db.compactRange();

			// Then
			assertThat(db.get(bytes("a"))).isNull();
			assertThat(db.get(bytes("b"))).isNull();
		}
	}

	@Test
	void create_returningNull_runsTheCompactionWithNoFilter(@TempDir Path dir) {
		// Given
		CompactionFilterFactory.CreateFilterFn declineToFilter = context -> null;
		try (var factory = CompactionFilterFactory.create("no-op-factory", declineToFilter);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilterFactory(factory);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("a"), bytes("1"));

			// When
			db.compactRange();

			// Then
			assertThat(db.get(bytes("a"))).isEqualTo(bytes("1"));
		}
	}

	@Test
	void create_isInvokedOncePerCompaction_withAFreshFilterInstanceEachTime(@TempDir Path dir) {
		// Given — a factory that counts how many times it was asked to create a filter, proving
		// (unlike a direct CompactionFilter) a new instance is minted per compaction rather than
		// one shared instance being reused
		AtomicInteger createCount = new AtomicInteger();
		CompactionFilterFactory.CreateFilterFn countingFactory = context -> {
			createCount.incrementAndGet();
			return CompactionFilter.create("keep-all", (level, key, existingValue) -> CompactionFilter.FilterDecision.keep());
		};
		try (var factory = CompactionFilterFactory.create("counting-factory", countingFactory);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilterFactory(factory);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("a"), bytes("1"));

			// When
			db.compactRange();

			// Then
			assertThat(createCount.get()).isGreaterThanOrEqualTo(1);
			assertThat(db.get(bytes("a"))).isEqualTo(bytes("1"));
		}
	}

	@Test
	void createCompactionFilter_seesTheManualCompactionContextFlag(@TempDir Path dir) {
		// Given
		var seenManual = new AtomicReference<Boolean>();
		CompactionFilterFactory.CreateFilterFn capturingFactory = context -> {
			seenManual.set(context.isManualCompaction());
			return CompactionFilter.create("keep-all", (level, key, existingValue) -> CompactionFilter.FilterDecision.keep());
		};
		try (var factory = CompactionFilterFactory.create("capturing-factory", capturingFactory);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilterFactory(factory);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("a"), bytes("1"));

			// When — compactRange() is an explicit, client-requested (manual) compaction
			db.compactRange();

			// Then
			assertThat(seenManual.get()).isTrue();
		}
	}

	@Test
	void createCompactionFilter_thatThrows_runsTheCompactionWithNoFilterRatherThanCrash(@TempDir Path dir) {
		// Given
		CompactionFilterFactory.CreateFilterFn throwingFactory = context -> {
			throw new RuntimeException("boom");
		};
		try (var factory = CompactionFilterFactory.create("throwing-factory", throwingFactory);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilterFactory(factory);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put(bytes("a"), bytes("1"));

			// When
			db.compactRange();

			// Then
			assertThat(db.get(bytes("a"))).isEqualTo(bytes("1"));
		}
	}

	@Test
	void setCompactionFilterFactory_transferredFactory_ptrThrows() {
		// Given
		CompactionFilterFactory.CreateFilterFn fn = context -> null;
		var factory = CompactionFilterFactory.create("no-op-factory", fn);

		// When
		try (var opts = Options.newOptions().setCompactionFilterFactory(factory)) {

			// Then
			assertThatThrownBy(factory::ptr).isInstanceOf(IllegalStateException.class);
		}
	}
}
