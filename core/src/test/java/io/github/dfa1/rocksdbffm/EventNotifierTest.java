package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Every test here exercises a real RocksDB background-thread callback (flush, compaction, or
/// external-file ingestion), which permanently attaches a RocksDB background thread to the JVM.
/// That used to deadlock the Surefire fork on exit; see [BackgroundUpcallThreads] for why, and
/// [EventNotifierExitTest] for the regression test guarding the fix.
class EventNotifierTest {

	@Test
	void flush_firesBeginAndCompletedWithMatchingDetails(@TempDir Path dir) {
		// Given — a notifier that copies out the fields it needs, since FlushJobInfo is only
		// valid for the duration of each callback
		AtomicInteger beginCount = new AtomicInteger();
		List<String> completedCfNames = new CopyOnWriteArrayList<>();
		List<FlushReason> completedReasons = new CopyOnWriteArrayList<>();
		List<String> completedFilePaths = new CopyOnWriteArrayList<>();
		List<Integer> completedCfIds = new CopyOnWriteArrayList<>();
		List<Long> completedFileNumbers = new CopyOnWriteArrayList<>();
		List<Long> completedOldestBlobFileNumbers = new CopyOnWriteArrayList<>();
		List<Long> completedThreadIds = new CopyOnWriteArrayList<>();
		List<Integer> completedJobIds = new CopyOnWriteArrayList<>();
		List<Boolean> completedTriggeredSlowdowns = new CopyOnWriteArrayList<>();
		List<Boolean> completedTriggeredStops = new CopyOnWriteArrayList<>();
		List<SequenceNumber> completedSmallestSeqnos = new CopyOnWriteArrayList<>();
		List<SequenceNumber> completedLargestSeqnos = new CopyOnWriteArrayList<>();
		List<CompressionType> completedBlobCompressionTypes = new CopyOnWriteArrayList<>();
		EventNotifier notifier = new EventNotifier() {
			@Override
			public void onFlushBegin(FlushJobInfo info) {
				beginCount.incrementAndGet();
			}

			@Override
			public void onFlushCompleted(FlushJobInfo info) {
				completedCfNames.add(info.columnFamilyName());
				completedReasons.add(info.flushReason());
				completedFilePaths.add(info.filePath().toString());
				completedCfIds.add(info.columnFamilyId());
				completedFileNumbers.add(info.fileNumber());
				completedOldestBlobFileNumbers.add(info.oldestBlobFileNumber());
				completedThreadIds.add(info.threadId());
				completedJobIds.add(info.jobId());
				completedTriggeredSlowdowns.add(info.triggeredWritesSlowdown());
				completedTriggeredStops.add(info.triggeredWritesStop());
				completedSmallestSeqnos.add(info.smallestSequenceNumber());
				completedLargestSeqnos.add(info.largestSequenceNumber());
				completedBlobCompressionTypes.add(info.blobCompressionType());
			}
		};

		try (var opts = Options.newOptions().setCreateIfMissing(true).addEventListener(notifier);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.flush(FlushOptions.newFlushOptions());
		}

		// Then
		assertThat(beginCount.get()).isPositive();
		assertThat(completedCfNames).containsExactly("default");
		assertThat(completedReasons).containsExactly(FlushReason.MANUAL_FLUSH);
		assertThat(completedFilePaths.get(0)).endsWith(".sst");
		assertThat(completedCfIds).containsExactly(0);
		assertThat(completedFileNumbers.get(0)).isPositive();
		assertThat(completedOldestBlobFileNumbers).containsExactly(0L);
		assertThat(completedThreadIds.get(0)).isPositive();
		assertThat(completedJobIds.get(0)).isPositive();
		assertThat(completedTriggeredSlowdowns).containsExactly(false);
		assertThat(completedTriggeredStops).containsExactly(false);
		assertThat(completedSmallestSeqnos).containsExactly(SequenceNumber.of(1));
		assertThat(completedLargestSeqnos).containsExactly(SequenceNumber.of(1));
		assertThat(completedBlobCompressionTypes).containsExactly(CompressionType.NO_COMPRESSION);
	}

	@Test
	void flush_firesMemTableSealed(@TempDir Path dir) {
		// Given
		List<String> sealedCfNames = new CopyOnWriteArrayList<>();
		List<SequenceNumber> firstSeqnos = new CopyOnWriteArrayList<>();
		List<SequenceNumber> earliestSeqnos = new CopyOnWriteArrayList<>();
		List<Long> numEntries = new CopyOnWriteArrayList<>();
		List<Long> numDeletes = new CopyOnWriteArrayList<>();
		List<byte[]> newestUdts = new CopyOnWriteArrayList<>();
		EventNotifier notifier = new EventNotifier() {
			@Override
			public void onMemTableSealed(MemTableInfo info) {
				sealedCfNames.add(info.columnFamilyName());
				firstSeqnos.add(info.firstSequenceNumber());
				earliestSeqnos.add(info.earliestSequenceNumber());
				numEntries.add(info.numEntries());
				numDeletes.add(info.numDeletes());
				newestUdts.add(info.newestUserDefinedTimestamp());
			}
		};

		try (var opts = Options.newOptions().setCreateIfMissing(true).addEventListener(notifier);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.flush(FlushOptions.newFlushOptions());
		}

		// Then
		assertThat(sealedCfNames).containsExactly("default");
		assertThat(firstSeqnos).containsExactly(SequenceNumber.of(1));
		assertThat(earliestSeqnos).containsExactly(SequenceNumber.of(0));
		assertThat(numEntries).containsExactly(1L);
		assertThat(numDeletes).containsExactly(0L);
		assertThat(newestUdts.get(0)).isEmpty();
	}

	@Test
	void compactRange_firesCompactionCompletedWithMatchingDetails(@TempDir Path dir) {
		// Given — a couple of flushed SST files, so compactRange() has something to compact.
		// Kept below RocksDB's default level0_file_num_compaction_trigger (4) so the manual
		// compactRange() call below is the only thing that triggers a compaction.
		List<Integer> outputLevels = new CopyOnWriteArrayList<>();
		List<CompactionReason> reasons = new CopyOnWriteArrayList<>();
		List<RocksDBException> statuses = new CopyOnWriteArrayList<>();
		List<Integer> cfIds = new CopyOnWriteArrayList<>();
		List<Long> threadIds = new CopyOnWriteArrayList<>();
		List<Integer> jobIds = new CopyOnWriteArrayList<>();
		List<Integer> numL0Files = new CopyOnWriteArrayList<>();
		List<Integer> baseInputLevels = new CopyOnWriteArrayList<>();
		List<CompressionType> compressions = new CopyOnWriteArrayList<>();
		List<CompressionType> blobCompressionTypes = new CopyOnWriteArrayList<>();
		List<Boolean> aborteds = new CopyOnWriteArrayList<>();
		List<Long> inputFilesCounts = new CopyOnWriteArrayList<>();
		List<Long> outputFilesCounts = new CopyOnWriteArrayList<>();
		List<java.time.Duration> elapsedTimes = new CopyOnWriteArrayList<>();
		List<Long> numCorruptKeys = new CopyOnWriteArrayList<>();
		List<Long> inputRecords = new CopyOnWriteArrayList<>();
		List<Long> outputRecords = new CopyOnWriteArrayList<>();
		List<MemorySize> totalInputBytes = new CopyOnWriteArrayList<>();
		List<MemorySize> totalOutputBytes = new CopyOnWriteArrayList<>();
		List<Long> numInputFiles = new CopyOnWriteArrayList<>();
		List<Long> numInputFilesAtOutputLevel = new CopyOnWriteArrayList<>();
		EventNotifier notifier = new EventNotifier() {
			@Override
			public void onCompactionCompleted(CompactionJobInfo info) {
				outputLevels.add(info.outputLevel());
				reasons.add(info.compactionReason());
				statuses.add(info.status());
				cfIds.add(info.columnFamilyId());
				threadIds.add(info.threadId());
				jobIds.add(info.jobId());
				numL0Files.add(info.numL0Files());
				baseInputLevels.add(info.baseInputLevel());
				compressions.add(info.compression());
				blobCompressionTypes.add(info.blobCompressionType());
				aborteds.add(info.aborted());
				inputFilesCounts.add(info.inputFilesCount());
				outputFilesCounts.add(info.outputFilesCount());
				elapsedTimes.add(info.elapsed());
				numCorruptKeys.add(info.numCorruptKeys());
				inputRecords.add(info.inputRecords());
				outputRecords.add(info.outputRecords());
				totalInputBytes.add(info.totalInputBytes());
				totalOutputBytes.add(info.totalOutputBytes());
				numInputFiles.add(info.numInputFiles());
				numInputFilesAtOutputLevel.add(info.numInputFilesAtOutputLevel());
			}
		};

		try (var opts = Options.newOptions().setCreateIfMissing(true).addEventListener(notifier);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			for (int i = 0; i < 2; i++) {
				db.put(("k" + i).getBytes(), ("v" + i).getBytes());
				db.flush(FlushOptions.newFlushOptions());
			}

			// When
			db.compactRange();
		}

		// Then
		assertThat(outputLevels).isNotEmpty();
		assertThat(reasons).containsExactly(CompactionReason.MANUAL_COMPACTION);
		assertThat(statuses).allSatisfy(status -> assertThat(status).isNull());
		assertThat(cfIds).containsExactly(0);
		assertThat(threadIds.get(0)).isPositive();
		assertThat(jobIds.get(0)).isPositive();
		// RocksDB's own CompactionJobInfo::num_l0_files is not populated by BuildCompactionJobInfo
		// upstream (see db_impl_compaction_flush.cc); a compaction-job-stats-derived field like
		// numCorruptKeys/inputRecords/etc. can likewise legitimately stay zero depending on
		// whether the picked compaction takes the full CompactionJob::Run() path or a cheaper one
		// (e.g. a trivial move) -- these are asserted as non-negative rather than pinned to an
		// exact value, so the test stays meaningful without depending on that internal choice.
		assertThat(numL0Files.get(0)).isGreaterThanOrEqualTo(0);
		assertThat(baseInputLevels.get(0)).isGreaterThanOrEqualTo(0);
		assertThat(compressions.get(0)).isNotNull();
		assertThat(blobCompressionTypes.get(0)).isNotNull();
		assertThat(aborteds).containsExactly(false);
		assertThat(inputFilesCounts.get(0)).isPositive();
		assertThat(outputFilesCounts.get(0)).isPositive();
		assertThat(elapsedTimes.get(0)).isNotNull();
		assertThat(numCorruptKeys.get(0)).isGreaterThanOrEqualTo(0L);
		assertThat(inputRecords.get(0)).isGreaterThanOrEqualTo(0L);
		assertThat(outputRecords.get(0)).isGreaterThanOrEqualTo(0L);
		assertThat(totalInputBytes.get(0)).isNotNull();
		assertThat(totalOutputBytes.get(0)).isNotNull();
		assertThat(numInputFiles.get(0)).isPositive();
		assertThat(numInputFilesAtOutputLevel.get(0)).isGreaterThanOrEqualTo(0L);
	}

	@Test
	void ingestExternalFile_firesOnExternalFileIngested(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		Path dbPath = dir.resolve("db");
		try (var writerOpts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(writerOpts)) {
			writer.open(sstPath);
			writer.put("aaa".getBytes(), "val1".getBytes());
			writer.finish();
		}

		List<String> ingestedCfNames = new CopyOnWriteArrayList<>();
		List<Path> externalFilePaths = new CopyOnWriteArrayList<>();
		List<Path> internalFilePaths = new CopyOnWriteArrayList<>();
		List<SequenceNumber> globalSeqnos = new CopyOnWriteArrayList<>();
		EventNotifier notifier = new EventNotifier() {
			@Override
			public void onExternalFileIngested(ExternalFileIngestionInfo info) {
				ingestedCfNames.add(info.columnFamilyName());
				externalFilePaths.add(info.externalFilePath());
				internalFilePaths.add(info.internalFilePath());
				globalSeqnos.add(info.globalSequenceNumber());
			}
		};

		try (var opts = Options.newOptions().setCreateIfMissing(true).addEventListener(notifier);
		     var db = RocksDB.openReadWrite(opts, dbPath)) {

			// When
			db.ingestExternalFile(sstPath);
		}

		// Then
		assertThat(ingestedCfNames).containsExactly("default");
		assertThat(externalFilePaths).containsExactly(sstPath);
		assertThat(internalFilePaths.get(0)).startsWith(dbPath).hasExtension("sst");
		assertThat(globalSeqnos).containsExactly(SequenceNumber.of(0));
	}

	@Test
	void writeStall_firesOnStallConditionsChangedWithMatchingDetails(@TempDir Path dir) throws InterruptedException {
		// Given — level-0 compaction/slowdown triggers both set to 1 (the lowest legal value;
		// RocksDB requires stop >= slowdown >= compaction trigger and silently raises whichever
		// is too low to satisfy it, so this is the only combination that can produce DELAYED
		// from a single flush) and the stop trigger set unreachably high, so only the DELAYED
		// condition is ever in play -- matching upstream's own SoftLimit test in db_test.cc,
		// which isolates DELAYED the same way. STOPPED was deliberately not chosen: reaching it
		// needs at least two L0 files, but auto-compaction reliably clears each single L0 file
		// before a second flush can land (confirmed empirically), so it can't be hit
		// deterministically without disabling auto-compaction -- which itself suppresses the
		// L0-count stall check entirely (see `!mutable_cf_options.disable_auto_compactions` in
		// `db/column_family.cc`'s `GetWriteStallConditionAndCause`).
		//
		// The callback itself is asynchronous relative to a synchronous `flush(wait=true)`
		// return: per db_test.cc's `SoftLimit`, `OnStallConditionsChanged` fires from
		// `JobContext::Clean()` on the background flush's cleanup path, not from the foreground
		// flush call. A short bounded wait on a latch is the correct way to observe it from
		// pure `c.h` (no `SyncPoint` equivalent is exposed); confirmed reliable across repeated
		// runs, typically firing in well under a second.
		CountDownLatch delayedObserved = new CountDownLatch(1);
		List<String> columnFamilyNames = new CopyOnWriteArrayList<>();
		List<WriteStallCondition> currentConditions = new CopyOnWriteArrayList<>();
		List<WriteStallCondition> previousConditions = new CopyOnWriteArrayList<>();
		EventNotifier notifier = new EventNotifier() {
			@Override
			public void onStallConditionsChanged(WriteStallInfo info) {
				columnFamilyNames.add(info.columnFamilyName());
				currentConditions.add(info.current());
				previousConditions.add(info.previous());
				if (info.current() == WriteStallCondition.DELAYED) {
					delayedObserved.countDown();
				}
			}
		};

		try (var opts = Options.newOptions()
				     .setCreateIfMissing(true)
				     .setLevel0FileNumCompactionTrigger(1)
				     .setLevel0SlowdownWritesTrigger(1)
				     .setLevel0StopWritesTrigger(999_999)
				     .addEventListener(notifier);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.flush(FlushOptions.newFlushOptions());
			boolean fired = delayedObserved.await(5, TimeUnit.SECONDS);

			// Then
			assertThat(fired).as("onStallConditionsChanged(DELAYED) fired within 5s").isTrue();
			assertThat(columnFamilyNames).contains("default");
			assertThat(currentConditions).contains(WriteStallCondition.DELAYED);
			assertThat(previousConditions).contains(WriteStallCondition.NORMAL);
		}
	}

	@Test
	void addEventListener_canBeCalledMultipleTimes(@TempDir Path dir) {
		// Given — two independent notifiers attached to the same Options
		AtomicInteger firstCount = new AtomicInteger();
		AtomicInteger secondCount = new AtomicInteger();
		EventNotifier first = new EventNotifier() {
			@Override
			public void onFlushCompleted(FlushJobInfo info) {
				firstCount.incrementAndGet();
			}
		};
		EventNotifier second = new EventNotifier() {
			@Override
			public void onFlushCompleted(FlushJobInfo info) {
				secondCount.incrementAndGet();
			}
		};

		try (var opts = Options.newOptions().setCreateIfMissing(true)
				.addEventListener(first).addEventListener(second);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.flush(FlushOptions.newFlushOptions());
		}

		// Then
		assertThat(firstCount.get()).isPositive();
		assertThat(secondCount.get()).isPositive();
	}

	@Test
	void notifier_withNoOverrides_defaultCallbacksAreNoOpsAndDoNotThrow(@TempDir Path dir) {
		// Given — a listener that overrides nothing, exercising every EventNotifier default
		// method's no-op body (onFlushBegin/onFlushCompleted/onCompactionBegin/
		// onCompactionCompleted/onExternalFileIngested/onMemTableSealed) via real flush,
		// compaction, and ingestion callbacks that all land on the interface defaults.
		EventNotifier notifier = new EventNotifier() {
		};

		Path sstPath = dir.resolve("data.sst");
		try (var writerOpts = Options.newOptions().setCreateIfMissing(true);
		     var writer = SstFileWriter.newSstFileWriter(writerOpts)) {
			writer.open(sstPath);
			writer.put("aaa".getBytes(), "val1".getBytes());
			writer.finish();
		}

		try (var opts = Options.newOptions().setCreateIfMissing(true).addEventListener(notifier);
		     var db = RocksDB.openReadWrite(opts, dir.resolve("db"))) {
			db.put("k".getBytes(), "v".getBytes());
			db.flush(FlushOptions.newFlushOptions());

			// When
			db.ingestExternalFile(sstPath);
			db.compactRange();

			// Then — no exception escaped any default callback
		}
	}
}
