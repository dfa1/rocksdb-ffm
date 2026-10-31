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
				db.flush(FlushOptions.newFlushOptions());
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
	void compactRange_recordsCompactionWithSizesAndSstSpace(@TempDir Path dir) throws IOException {
		// Given — two L0 files holding the same key, below the default auto-compaction trigger (4).
		// compactRange() first trivially moves them to L6 (relinked, not rewritten: every stat is
		// 0), then the forced bottommost compaction really merges them; assert on that one
		try (var recording = startRecording(COMPACTION)) {
			try (var env = Env.defaultEnv();
			     var sfm = SstFileManager.create(env);
			     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sfm)
					     .addEventListener(JfrEventNotifier.create(sfm));
			     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
				for (String value : List.of("v1", "v2")) {
					db.put("k".getBytes(), value.getBytes());
					db.flush(FlushOptions.newFlushOptions());
				}

				// When
				try (var compactOpts = CompactOptions.newCompactOptions().setBottommostLevelCompaction(true)) {
					db.compactRange(compactOpts, null, null);
				}
			}
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(named(events, COMPACTION)).hasSize(2)
					.filteredOn(e -> e.getInt("inputLevel") == 6).singleElement().satisfies(e -> {
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
	void writeStall_recordsOneEventSpanningTheDelayedCondition(@TempDir Path dir) throws Exception {
		// Given — slowdown at two L0 files, cleared again by auto-compaction. Both files hold the
		// same key, so clearing them takes a real merge (milliseconds), not a trivial move
		// (microseconds): RocksDB delivers the opening and closing transitions from different
		// threads, and a stall shorter than that skew can arrive close-before-open. A second
		// listener, registered after the JFR one and so called after it, signals the end.
		var normalAgain = new CountDownLatch(1);
		EventNotifier stallOver = new EventNotifier() {
			@Override
			public void onStallConditionsChanged(WriteStallInfo info) {
				if (info.current() == WriteStallCondition.NORMAL) {
					normalAgain.countDown();
				}
			}
		};
		boolean cleared;
		try (var recording = startRecording(WRITE_STALL)) {
			try (var opts = Options.newOptions().setCreateIfMissing(true)
					.setLevel0FileNumCompactionTrigger(2)
					.setLevel0SlowdownWritesTrigger(2)
					.setLevel0StopWritesTrigger(999_999)
					.addEventListener(JfrEventNotifier.create())
					.addEventListener(stallOver);
			     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
				db.put("k".getBytes(), "v1".getBytes());
				db.flush(FlushOptions.newFlushOptions());
				db.put("k".getBytes(), "v2".getBytes());

				// When
				db.flush(FlushOptions.newFlushOptions());
				cleared = normalAgain.await(5, TimeUnit.SECONDS);
			}
			var events = stopAndRead(recording, dir);

			// Then
			assertThat(cleared).as("stall cleared within 5s").isTrue();
			assertThat(named(events, WRITE_STALL)).singleElement().satisfies(e -> {
				assertThat(e.getString("columnFamily")).isEqualTo("default");
				assertThat(e.getString("condition")).isEqualTo("DELAYED");
				assertThat(e.getDuration()).isPositive();
			});
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
				thrown = catchThrowable(() -> db.flush(FlushOptions.newFlushOptions()));
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
	void flush_withoutRecording_recordsNothing(@TempDir Path dir) throws IOException {
		// Given — the notifier sees the whole flush with no recording running
		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.addEventListener(JfrEventNotifier.create());
		     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
			db.put("k".getBytes(), "v".getBytes());
			db.flush(FlushOptions.newFlushOptions());
		}

		// When — a recording starting afterwards
		List<RecordedEvent> events;
		try (var recording = startRecording(FLUSH, MEMTABLE_SEALED)) {
			events = stopAndRead(recording, dir);
		}

		// Then
		assertThat(events).isEmpty();
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
