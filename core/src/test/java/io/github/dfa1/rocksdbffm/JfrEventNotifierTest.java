package io.github.dfa1.rocksdbffm;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/// Records into a real in-process JFR [Recording], stops it (which flushes every committed event
/// synchronously) and reads the dump back, so no event can still be in flight when asserting.
/// Every DB is closed before the recording stops, since RocksDB's callbacks run on its
/// background threads.
class JfrEventNotifierTest {

	private static final String FLUSH = "rocksdbffm.Flush";
	private static final String COMPACTION = "rocksdbffm.Compaction";
	private static final String WRITE_STALL = "rocksdbffm.WriteStall";
	private static final String MEMTABLE_SEALED = "rocksdbffm.MemTableSealed";
	private static final String BACKGROUND_ERROR = "rocksdbffm.BackgroundError";
	private static final String INGESTED = "rocksdbffm.ExternalFileIngested";

	@Test
	void flush_recordsFlushAndMemTableSealedEvents(@TempDir Path dir) throws IOException {
		// Given
		try (var recording = startRecording(FLUSH, MEMTABLE_SEALED)) {
			try (var opts = Options.newOptions().setCreateIfMissing(true)
					.addEventListener(JfrEventNotifier.create());
			     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
				db.put("k".getBytes(), "v".getBytes());

				// When
				flush(db);
			}
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, FLUSH)).singleElement().satisfies(e -> {
				assertThat(e.getString("columnFamily")).isEqualTo("default");
				assertThat(e.getString("reason")).isEqualTo("MANUAL_FLUSH");
				assertThat(e.getLong("fileNumber")).isPositive();
				assertThat(e.getBoolean("triggeredWritesStop")).isFalse();
				assertThat(e.getLong("sstTotalSize")).isZero();
				assertThat(e.getDuration()).isPositive();
			});
			assertThat(named(events, MEMTABLE_SEALED)).singleElement().satisfies(e -> {
				assertThat(e.getString("columnFamily")).isEqualTo("default");
				assertThat(e.getLong("entries")).isEqualTo(1);
				assertThat(e.getLong("deletes")).isZero();
			});
		}
	}

	@Test
	void flushWithClosedSstFileManager_stillRecordsFlushWithoutSstSpace(@TempDir Path dir) throws IOException {
		// Given — the manager closed while the database is still running
		try (var recording = startRecording(FLUSH)) {
			try (var env = Env.defaultEnv();
			     var sfm = SstFileManager.create(env);
			     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sfm)
					     .addEventListener(JfrEventNotifier.create(sfm));
			     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
				db.put("k".getBytes(), "v".getBytes());
				sfm.close();

				// When
				flush(db);
			}
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, FLUSH)).singleElement().satisfies(e -> {
				assertThat(e.getString("reason")).isEqualTo("MANUAL_FLUSH");
				assertThat(e.getLong("sstTotalSize")).isZero();
				assertThat(e.getBoolean("sstSpaceLimitReached")).isFalse();
			});
		}
	}

	@Test
	void compactRange_recordsCompactionWithSizesAndSstSpace(@TempDir Path dir) throws IOException {
		// Given — two L0 files holding the same key, below the default auto-compaction trigger (4).
		// compactRange() first trivially moves them to L6 (relinked, not rewritten: every stat is
		// 0), then the forced bottommost compaction really merges them; assert on the one that
		// has input records
		try (var recording = startRecording(COMPACTION)) {
			try (var env = Env.defaultEnv();
			     var sfm = SstFileManager.create(env);
			     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sfm)
					     .addEventListener(JfrEventNotifier.create(sfm));
			     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
				for (String value : List.of("v1", "v2")) {
					db.put("k".getBytes(), value.getBytes());
					flush(db);
				}

				// When
				try (var compactOpts = CompactOptions.newCompactOptions().setBottommostLevelCompaction(true)) {
					db.compactRange(compactOpts, null, null);
				}
			}
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, COMPACTION)).filteredOn(e -> e.getLong("inputRecords") > 0)
					.singleElement().satisfies(e -> {
				assertThat(e.getString("columnFamily")).isEqualTo("default");
				assertThat(e.getString("reason")).isEqualTo("MANUAL_COMPACTION");
				assertThat(e.getInt("outputLevel")).isEqualTo(6);
				assertThat(e.getLong("inputFiles")).isEqualTo(2);
				assertThat(e.getLong("outputFiles")).isEqualTo(1);
				assertThat(e.getLong("inputBytes")).isPositive();
				assertThat(e.getLong("outputBytes")).isPositive();
				assertThat(e.getLong("inputRecords")).isEqualTo(2);
				assertThat(e.getLong("outputRecords")).isEqualTo(1);
				assertThat(e.getDuration("elapsed")).isPositive();
				assertThat(e.getBoolean("aborted")).isFalse();
				assertThat(e.getLong("sstTotalSize")).isPositive();
				assertThat(e.getBoolean("sstSpaceLimitReached")).isFalse();
				assertThat(e.getDuration()).isPositive();
			});
		}
	}

	@Test
	void writeStall_opensEventWhenSlowdownStarts(@TempDir Path dir) throws Exception {
		// Given — slowdown at two L0 files. A second listener, registered after the JFR one and so
		// called after it on the same thread, reads the notifier's state as the DELAYED transition
		// is delivered. Whether the stall is later committed depends on RocksDB delivering its
		// transitions in order, which it doesn't guarantee, so that is covered by the recordStall tests.
		// For the same reason the compaction this triggers can clear the stall before DELAYED is
		// delivered, so the scenario is retried on a fresh database a few times
		var openStallsAtDelayed = new AtomicInteger(-1);
		boolean delivered = false;

		// When
		for (int attempt = 0; attempt < 5 && openStallsAtDelayed.get() != 1; attempt++) {
			var notifier = JfrEventNotifier.create();
			var delayed = new CountDownLatch(1);
			EventNotifier probe = new EventNotifier() {
				@Override
				public void onStallConditionsChanged(WriteStallInfo info) {
					if (info.current() == WriteStallCondition.DELAYED) {
						openStallsAtDelayed.set(notifier.openStalls());
						delayed.countDown();
					}
				}
			};
			try (var _ = startRecording(WRITE_STALL);
			     var opts = Options.newOptions().setCreateIfMissing(true)
					     .setLevel0FileNumCompactionTrigger(2)
					     .setLevel0SlowdownWritesTrigger(2)
					     .setLevel0StopWritesTrigger(999_999)
					     .addEventListener(notifier)
					     .addEventListener(probe);
			     var db = RocksDB.openReadWrite(opts, dir.resolve("db" + attempt))) {
				db.put("k".getBytes(), "v1".getBytes());
				flush(db);
				db.put("k".getBytes(), "v2".getBytes());
				flush(db);
				delivered = delayed.await(5, TimeUnit.SECONDS);
			}
		}

		// Then
		assertThat(delivered).as("DELAYED transition delivered within 5s").isTrue();
		assertThat(openStallsAtDelayed).hasValue(1);
	}

	@Test
	void recordStall_familiesWhoseDisplayStringsCoincide_keepSeparateStalls(@TempDir Path dir) throws Exception {
		// Given — two different non-UTF-8 names that both display as the replacement character
		var first = ColumnFamilyName.ofBytes(new byte[]{(byte) 0xFF});
		var second = ColumnFamilyName.ofBytes(new byte[]{(byte) 0xFE});
		var notifier = JfrEventNotifier.create();
		try (var recording = startRecording(WRITE_STALL)) {

			// When
			notifier.recordStall(first, WriteStallCondition.NORMAL, WriteStallCondition.DELAYED);
			notifier.recordStall(second, WriteStallCondition.NORMAL, WriteStallCondition.DELAYED);
			var openStalls = notifier.openStalls();
			notifier.recordStall(first, WriteStallCondition.DELAYED, WriteStallCondition.NORMAL);
			notifier.recordStall(second, WriteStallCondition.DELAYED, WriteStallCondition.NORMAL);
			var events = stopAndRead(recording, dir);

			// Then — one stall each, not one shared entry
			assertThat(openStalls).isEqualTo(2);
			assertThat(named(events, WRITE_STALL)).hasSize(2);
			assertThat(notifier.openStalls()).isZero();
		}
	}

	@Test
	void recordStall_delayedThenNormal_recordsOneEventSpanningTheStall(@TempDir Path dir) throws Exception {
		// Given
		var notifier = JfrEventNotifier.create();
		try (var recording = startRecording(WRITE_STALL)) {
			notifier.recordStall(ColumnFamilyName.DEFAULT, WriteStallCondition.NORMAL, WriteStallCondition.DELAYED);
			Thread.sleep(5);

			// When
			notifier.recordStall(ColumnFamilyName.DEFAULT, WriteStallCondition.DELAYED, WriteStallCondition.NORMAL);
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, WRITE_STALL)).singleElement().satisfies(e -> {
				assertThat(e.getString("columnFamily")).isEqualTo("default");
				assertThat(e.getString("condition")).isEqualTo("DELAYED");
				assertThat(e.getDuration()).isPositive();
			});
			assertThat(notifier.openStalls()).isZero();
		}
	}

	@Test
	void recordStall_delayedThenStoppedThenNormal_recordsOneEventPerCondition(@TempDir Path dir) throws Exception {
		// Given
		var notifier = JfrEventNotifier.create();
		try (var recording = startRecording(WRITE_STALL)) {
			notifier.recordStall(ColumnFamilyName.DEFAULT, WriteStallCondition.NORMAL, WriteStallCondition.DELAYED);
			Thread.sleep(5);
			notifier.recordStall(ColumnFamilyName.DEFAULT, WriteStallCondition.DELAYED, WriteStallCondition.STOPPED);
			Thread.sleep(5);

			// When
			notifier.recordStall(ColumnFamilyName.DEFAULT, WriteStallCondition.STOPPED, WriteStallCondition.NORMAL);
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, WRITE_STALL)).extracting(e -> e.getString("condition"))
					.containsExactlyInAnyOrder("DELAYED", "STOPPED");
			assertThat(notifier.openStalls()).isZero();
		}
	}

	@Test
	void recordStall_previousNotMatchingOpenStall_dropsItInsteadOfCommittingWrongTiming(@TempDir Path dir)
			throws Exception {
		// Given — a DELAYED stall left open because its closing transition arrived before it
		var notifier = JfrEventNotifier.create();
		try (var recording = startRecording(WRITE_STALL)) {
			notifier.recordStall(ColumnFamilyName.DEFAULT, WriteStallCondition.NORMAL, WriteStallCondition.DELAYED);
			Thread.sleep(5);

			// When — a later stall starts from NORMAL, which the open DELAYED one can't be the
			// predecessor of, so it is dropped rather than committed as one long bogus span
			notifier.recordStall(ColumnFamilyName.DEFAULT, WriteStallCondition.NORMAL, WriteStallCondition.STOPPED);
			Thread.sleep(5);
			notifier.recordStall(ColumnFamilyName.DEFAULT, WriteStallCondition.STOPPED, WriteStallCondition.NORMAL);
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, WRITE_STALL)).singleElement()
					.satisfies(e -> assertThat(e.getString("condition")).isEqualTo("STOPPED"));
		}
	}

	@Test
	void flushOverSpaceLimit_recordsBackgroundError(@TempDir Path dir) throws IOException {
		// Given — a 1-byte SST space limit, so the first flush fails
		Throwable thrown;
		try (var recording = startRecording(BACKGROUND_ERROR)) {
			try (var env = Env.defaultEnv();
			     var sfm = SstFileManager.create(env).setMaxAllowedSpaceUsage(MemorySize.ofBytes(1));
			     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sfm)
					     .addEventListener(JfrEventNotifier.create(sfm));
			     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
				db.put("k".getBytes(), "v".getBytes());

				// When
				thrown = catchThrowable(() -> flush(db));
			}
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(thrown).isInstanceOf(RocksDBException.class);
			assertThat(named(events, BACKGROUND_ERROR)).singleElement().satisfies(e -> {
				assertThat(e.getString("reason")).isEqualTo("FLUSH");
				assertThat(e.getString("message")).isNotBlank();
			});
		}
	}

	@Test
	void onBackgroundError_withoutError_recordsEventWithNoMessage(@TempDir Path dir) throws IOException {
		// Given — RocksDB's status had already been cleared, so the bridge passes no error
		var notifier = JfrEventNotifier.create();
		try (var recording = startRecording(BACKGROUND_ERROR)) {

			// When
			notifier.onBackgroundError(BackgroundErrorReason.FLUSH, null);
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, BACKGROUND_ERROR)).singleElement().satisfies(e -> {
				assertThat(e.getString("reason")).isEqualTo("FLUSH");
				assertThat(e.getString("message")).isNull();
			});
		}
	}

	@Test
	void ingestExternalFile_recordsIngestion(@TempDir Path dir) throws IOException {
		// Given
		Path sst = dir.resolve("data.sst");
		try (var writerOpts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(writerOpts)) {
			writer.open(sst);
			writer.put("k".getBytes(), "v".getBytes());
			writer.finish();
		}
		try (var recording = startRecording(INGESTED)) {
			try (var opts = Options.newOptions().setCreateIfMissing(true)
					.addEventListener(JfrEventNotifier.create());
			     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {

				// When
				db.ingestExternalFile(sst);
			}
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, INGESTED)).singleElement().satisfies(e -> {
				assertThat(e.getString("columnFamily")).isEqualTo("default");
				assertThat(e.getString("externalFile")).isEqualTo(sst.toString());
				assertThat(e.getString("internalFile")).endsWith(".sst");
			});
		}
	}

	@Test
	void flush_withRecording_holdsPendingEventUntilCompleted(@TempDir Path dir) {
		// Given — a second listener, registered after the JFR one and so called after it on the
		// same thread, reads the notifier's state as the flush begins
		var notifier = JfrEventNotifier.create();
		var pendingAtBegin = new AtomicInteger(-1);
		EventNotifier probe = new EventNotifier() {
			@Override
			public void onFlushBegin(FlushJobInfo info) {
				pendingAtBegin.set(notifier.pendingFlushes());
			}
		};
		try (var _ = startRecording(FLUSH);
		     var opts = Options.newOptions().setCreateIfMissing(true)
				     .addEventListener(notifier).addEventListener(probe);
		     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			flush(db);

			// Then
			assertThat(pendingAtBegin).hasValue(1);
		}
	}

	@Test
	void flush_withoutRecording_retainsNothing(@TempDir Path dir) {
		// Given — no recording, and a second listener reading the notifier's state as the flush
		// begins (see the test above, which shows the same probe sees a pending event when recording)
		var notifier = JfrEventNotifier.create();
		var pendingAtBegin = new AtomicInteger(-1);
		EventNotifier probe = new EventNotifier() {
			@Override
			public void onFlushBegin(FlushJobInfo info) {
				pendingAtBegin.set(notifier.pendingFlushes());
			}
		};
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.addEventListener(notifier).addEventListener(probe);
		     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			flush(db);

			// Then
			assertThat(pendingAtBegin).hasValue(0);
		}
	}

	private static void flush(ReadWriteDB db) {
		try (var flushOptions = FlushOptions.newFlushOptions()) {
			db.flush(flushOptions);
		}
	}

	private static Recording startRecording(String... eventNames) {
		var recording = new Recording();
		for (String name : eventNames) {
			recording.enable(name);
		}
		recording.start();
		return recording;
	}

	private static List<RecordedEvent> stopAndRead(Recording recording, Path dir) throws IOException {
		recording.stop();
		Path file = dir.resolve("recording.jfr");
		recording.dump(file);
		return RecordingFile.readAllEvents(file);
	}

	private static List<RecordedEvent> named(List<RecordedEvent> events, String name) {
		return events.stream().filter(e -> e.getEventType().getName().equals(name)).toList();
	}
}
