package io.github.dfa1.rocksdbffm;

import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/// An [EventNotifier] that records RocksDB's background activity as JDK Flight Recorder events, so
/// flushes, compactions and write stalls show up in a JFR timeline next to GC pauses and thread
/// states. RocksDB runs that work on its own native threads, which JFR cannot otherwise see.
///
/// ```
/// try (var opts = Options.newOptions().setCreateIfMissing(true)
///         .addEventListener(JfrEventNotifier.create());
///      var db = RocksDB.openReadWrite(opts, dbPath)) {
///     ...
/// }
/// ```
///
/// | Event                             | Kind     | Spans                                              |
/// |:----------------------------------|:---------|:---------------------------------------------------|
/// | `rocksdbffm.Flush`                | duration | flush begin → completed                            |
/// | `rocksdbffm.Compaction`           | duration | compaction begin → completed                       |
/// | `rocksdbffm.WriteStall`           | duration | one [WriteStallCondition] other than `NORMAL`      |
/// | `rocksdbffm.MemTableSealed`       | instant  |                                                    |
/// | `rocksdbffm.BackgroundError`      | instant  |                                                    |
/// | `rocksdbffm.ExternalFileIngested` | instant  |                                                    |
///
/// With no recording running, each callback costs one `isEnabled()`/`shouldCommit()` check: no
/// `*Info` field is read and nothing is retained. Nothing else in this library references
/// `jdk.jfr`, so a runtime image without that module works as long as this class is never loaded.
///
/// Use one instance per database: write stalls are tracked by column family name, which RocksDB's
/// callback does not qualify with a database. A flush, compaction or stall already in progress
/// when a recording starts is not recorded.
public final class JfrEventNotifier implements EventNotifier {

	private final SstFileManager sstFileManager;
	private final Map<Job, FlushEvent> flushes = new ConcurrentHashMap<>();
	private final Map<Job, CompactionEvent> compactions = new ConcurrentHashMap<>();
	private final Map<String, WriteStallEvent> stalls = new ConcurrentHashMap<>();

	private JfrEventNotifier(SstFileManager sstFileManager) {
		this.sstFileManager = sstFileManager;
		// load and register every event class now, not inside the first RocksDB callback that
		// needs it, which would widen the stall-transition race described below
		new FlushEvent();
		new CompactionEvent();
		new WriteStallEvent();
		new MemTableSealedEvent();
		new BackgroundErrorEvent();
		new ExternalFileIngestedEvent();
	}

	/// Creates a notifier whose flush, compaction and ingestion events carry no SST space fields
	/// (they stay `0`/`false`).
	///
	/// @return a new notifier
	public static JfrEventNotifier create() {
		return new JfrEventNotifier(null);
	}

	/// Creates a notifier that also records `sstFileManager`'s total size, trash size and
	/// space-limit flag on every flush, compaction and ingestion event — the only moments SST
	/// space changes, so no timer is needed. Pass the same instance given to
	/// [Options#setSstFileManager(SstFileManager)], and keep it open as long as the database.
	///
	/// @param sstFileManager the manager to read space usage from
	/// @return a new notifier
	public static JfrEventNotifier create(SstFileManager sstFileManager) {
		return new JfrEventNotifier(Objects.requireNonNull(sstFileManager, "sstFileManager"));
	}

	/// Starts a `rocksdbffm.Flush` event if a recording has it enabled.
	///
	/// @param info details of the flush about to run
	@Override
	public void onFlushBegin(FlushJobInfo info) {
		var event = new FlushEvent();
		if (event.isEnabled()) {
			event.begin();
			flushes.put(new Job(info.threadId(), info.jobId()), event);
		}
	}

	/// Commits the `rocksdbffm.Flush` event started by [#onFlushBegin(FlushJobInfo)].
	///
	/// @param info details of the completed flush
	@Override
	public void onFlushCompleted(FlushJobInfo info) {
		if (flushes.isEmpty()) {
			return;
		}
		FlushEvent event = flushes.remove(new Job(info.threadId(), info.jobId()));
		if (event == null) {
			return;
		}
		event.end();
		if (event.shouldCommit()) {
			event.columnFamily = info.columnFamilyName();
			event.reason = info.flushReason().name();
			event.fileNumber = info.fileNumber();
			event.triggeredWritesSlowdown = info.triggeredWritesSlowdown();
			event.triggeredWritesStop = info.triggeredWritesStop();
			recordSstSpace(event);
			event.commit();
		}
	}

	/// Starts a `rocksdbffm.Compaction` event if a recording has it enabled.
	///
	/// @param info details of the compaction about to run
	@Override
	public void onCompactionBegin(CompactionJobInfo info) {
		var event = new CompactionEvent();
		if (event.isEnabled()) {
			event.begin();
			compactions.put(new Job(info.threadId(), info.jobId()), event);
		}
	}

	/// Commits the `rocksdbffm.Compaction` event started by
	/// [#onCompactionBegin(CompactionJobInfo)].
	///
	/// @param info details of the completed compaction
	@Override
	public void onCompactionCompleted(CompactionJobInfo info) {
		if (compactions.isEmpty()) {
			return;
		}
		CompactionEvent event = compactions.remove(new Job(info.threadId(), info.jobId()));
		if (event == null) {
			return;
		}
		event.end();
		if (event.shouldCommit()) {
			event.columnFamily = info.columnFamilyName();
			event.reason = info.compactionReason().name();
			event.inputLevel = info.baseInputLevel();
			event.outputLevel = info.outputLevel();
			event.inputFiles = info.inputFilesCount();
			event.outputFiles = info.outputFilesCount();
			event.inputBytes = info.totalInputBytes().toBytes();
			event.outputBytes = info.totalOutputBytes().toBytes();
			event.inputRecords = info.inputRecords();
			event.outputRecords = info.outputRecords();
			event.elapsed = info.elapsed().toNanos();
			event.compression = info.compression().name();
			event.aborted = info.aborted();
			recordSstSpace(event);
			event.commit();
		}
	}

	/// Commits the `rocksdbffm.ExternalFileIngested` event.
	///
	/// @param info details of the ingested file
	@Override
	public void onExternalFileIngested(ExternalFileIngestionInfo info) {
		var event = new ExternalFileIngestedEvent();
		if (event.shouldCommit()) {
			event.columnFamily = info.columnFamilyName();
			event.externalFile = info.externalFilePath().toString();
			event.internalFile = info.internalFilePath().toString();
			event.globalSequenceNumber = info.globalSequenceNumber().toLong();
			recordSstSpace(event);
			event.commit();
		}
	}

	/// Commits the `rocksdbffm.BackgroundError` event.
	///
	/// @param reason which subsystem reported the error
	/// @param error  the error RocksDB recorded
	@Override
	public void onBackgroundError(BackgroundErrorReason reason, RocksDBException error) {
		var event = new BackgroundErrorEvent();
		if (event.shouldCommit()) {
			event.reason = reason.name();
			event.message = error.getMessage();
			event.commit();
		}
	}

	/// Ends the column family's open `rocksdbffm.WriteStall` event, if any, and starts a new one
	/// when the new condition is not `NORMAL`.
	///
	/// @param info the new and previous stall conditions
	@Override
	public void onStallConditionsChanged(WriteStallInfo info) {
		if (stalls.isEmpty() && !new WriteStallEvent().isEnabled()) {
			return;
		}
		String columnFamily = info.columnFamilyName();
		WriteStallCondition current = info.current();
		String previous = info.previous().name();
		// ponytail: RocksDB delivers one column family's transitions from different threads, so
		// a stall shorter than that delivery skew can arrive close-before-open and is lost.
		// Matching `previous` against the open condition at least never commits a bogus stall.
		stalls.compute(columnFamily, (cf, open) -> {
			if (open != null && open.condition.equals(previous)) {
				open.end();
				open.commit();
			}
			var next = new WriteStallEvent();
			if (current == WriteStallCondition.NORMAL || !next.isEnabled()) {
				return null;
			}
			next.columnFamily = cf;
			next.condition = current.name();
			next.begin();
			return next;
		});
	}

	/// Commits the `rocksdbffm.MemTableSealed` event.
	///
	/// @param info details of the sealed memtable
	@Override
	public void onMemTableSealed(MemTableInfo info) {
		var event = new MemTableSealedEvent();
		if (event.shouldCommit()) {
			event.columnFamily = info.columnFamilyName();
			event.entries = info.numEntries();
			event.deletes = info.numDeletes();
			event.commit();
		}
	}

	private void recordSstSpace(SstSpaceEvent event) {
		if (sstFileManager != null) {
			event.sstTotalSize = sstFileManager.getTotalSize().toBytes();
			event.sstTrashSize = sstFileManager.getTotalTrashSize().toBytes();
			event.sstSpaceLimitReached = sstFileManager.isMaxAllowedSpaceReached();
		}
	}

	/// Flush/compaction job identity: `jobId` alone is only unique within one database.
	private record Job(long threadId, int jobId) {
	}

	@Category("RocksDB")
	@StackTrace(false)
	abstract static class SstSpaceEvent extends Event {
		@Label("SST Total Size")
		@Description("SstFileManager total SST size; 0 without an SstFileManager")
		@DataAmount
		long sstTotalSize;

		@Label("SST Trash Size")
		@DataAmount
		long sstTrashSize;

		@Label("SST Space Limit Reached")
		boolean sstSpaceLimitReached;
	}

	@Name("rocksdbffm.Flush")
	@Label("Flush")
	@Category("RocksDB")
	@StackTrace(false)
	static final class FlushEvent extends SstSpaceEvent {
		@Label("Column Family")
		String columnFamily;

		@Label("Reason")
		String reason;

		@Label("File Number")
		long fileNumber;

		@Label("Triggered Writes Slowdown")
		boolean triggeredWritesSlowdown;

		@Label("Triggered Writes Stop")
		boolean triggeredWritesStop;
	}

	@Name("rocksdbffm.Compaction")
	@Label("Compaction")
	@Category("RocksDB")
	@StackTrace(false)
	static final class CompactionEvent extends SstSpaceEvent {
		@Label("Column Family")
		String columnFamily;

		@Label("Reason")
		String reason;

		@Label("Input Level")
		int inputLevel;

		@Label("Output Level")
		int outputLevel;

		@Label("Input Files")
		long inputFiles;

		@Label("Output Files")
		long outputFiles;

		@Label("Input Bytes")
		@DataAmount
		long inputBytes;

		@Label("Output Bytes")
		@DataAmount
		long outputBytes;

		@Label("Input Records")
		long inputRecords;

		@Label("Output Records")
		long outputRecords;

		@Label("Elapsed")
		@Description("RocksDB's own measurement of the compaction's run time")
		@Timespan
		long elapsed;

		@Label("Compression")
		String compression;

		@Label("Aborted")
		boolean aborted;
	}

	@Name("rocksdbffm.WriteStall")
	@Label("Write Stall")
	@Category("RocksDB")
	@StackTrace(false)
	static final class WriteStallEvent extends Event {
		@Label("Column Family")
		String columnFamily;

		@Label("Condition")
		String condition;
	}

	@Name("rocksdbffm.MemTableSealed")
	@Label("MemTable Sealed")
	@Category("RocksDB")
	@StackTrace(false)
	static final class MemTableSealedEvent extends Event {
		@Label("Column Family")
		String columnFamily;

		@Label("Entries")
		long entries;

		@Label("Deletes")
		long deletes;
	}

	@Name("rocksdbffm.BackgroundError")
	@Label("Background Error")
	@Category("RocksDB")
	@StackTrace(false)
	static final class BackgroundErrorEvent extends Event {
		@Label("Reason")
		String reason;

		@Label("Message")
		String message;
	}

	@Name("rocksdbffm.ExternalFileIngested")
	@Label("External File Ingested")
	@Category("RocksDB")
	@StackTrace(false)
	static final class ExternalFileIngestedEvent extends SstSpaceEvent {
		@Label("Column Family")
		String columnFamily;

		@Label("External File")
		String externalFile;

		@Label("Internal File")
		String internalFile;

		@Label("Global Sequence Number")
		long globalSequenceNumber;
	}
}
