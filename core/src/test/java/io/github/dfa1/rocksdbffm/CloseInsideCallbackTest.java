package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/// A database closed from inside a callback is closed under whatever invoked the callback. Before
/// [NativeDB#close()] refused it, each of these either killed the JVM or never returned:
///
/// - a [Mapper] of a pinned get: SIGSEGV in `CachableEntry<Block>::ReleaseCacheHandle`, when the pinned value was
///   released after the block cache it pointed into;
/// - a [MergeOperator] on the reading thread: SIGSEGV in `ThreadLocalPtr::CompareAndSwap`, when the `Get` in
///   progress returned its super version to a freed database;
/// - a [Logger] callback on the flushing thread: SIGSEGV in `DBImpl::Flush`;
/// - a [MergeOperator], [CompactionFilter] or [CompactionFilterFactory] on a compaction thread: `rocksdb_close`
///   waits forever for the compaction that is running the callback.
///
/// The [EventNotifier] case is in [EventNotifierTest].
class CloseInsideCallbackTest {

	private static final byte[] KEY = "k".getBytes();
	private static final byte[] VALUE = "v".getBytes();

	private static MemorySegment nativeKey(Arena arena) {
		return arena.allocateFrom(ValueLayout.JAVA_BYTE, KEY);
	}

	/// Tries to close `db` from inside a callback and records the first outcome.
	private static void closeInto(AtomicReference<Throwable> thrown, AtomicReference<? extends NativeDB> db) {
		NativeDB target = db.get();
		if (target != null) {
			thrown.compareAndSet(null, catchThrowable(target::close));
		}
	}

	@Test
	void pinnedGetMapper_thatClosesItsDatabase_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given -- a value read from an SST file, so the pinned value points into the block cache
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var flushOptions = FlushOptions.newFlushOptions();
		     var arena = Arena.ofConfined()) {
			db.put(KEY, VALUE);
			db.flush(flushOptions);

			// When
			var thrown = catchThrowable(() -> db.get(nativeKey(arena), value -> {
				db.close();
				return value.byteSize();
			}));

			// Then -- the close was refused and the database is still open
			var result = db.get(KEY);
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}

	@Test
	void transactionDbPinnedGetMapper_thatClosesItsDatabase_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var txnDbOpts = TransactionDBOptions.newTransactionDBOptions();
		     var db = RocksDB.openTransaction(dir, opts, txnDbOpts);
		     var arena = Arena.ofConfined()) {
			db.put(KEY, VALUE);

			// When
			var thrown = catchThrowable(() -> db.get(nativeKey(arena), value -> {
				db.close();
				return value.byteSize();
			}));

			// Then
			var result = db.get(KEY);
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}

	@Test
	void readBatchMapper_thatClosesItsDatabase_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var batch = ReadBatch.create(db, 1);
		     var arena = Arena.ofConfined()) {
			db.put(KEY, VALUE);

			// When
			var thrown = catchThrowable(() -> batch.get(List.of(nativeKey(arena)), value -> {
				db.close();
				return value.byteSize();
			}));

			// Then
			var result = db.get(KEY);
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}

	@Test
	void iteratorMapper_thatClosesItsDatabase_throwsInsteadOfReadingAFreedEntry(@TempDir Path dir) {
		// Given -- closing the database would release the iterator whose entry the mapper is reading
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var iterator = db.newIterator()) {
			db.put(KEY, VALUE);
			iterator.seekToFirst();

			// When
			var thrown = catchThrowable(() -> iterator.value(value -> {
				db.close();
				return value.byteSize();
			}));

			// Then
			var result = db.get(KEY);
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}

	@Test
	void mergeOperator_thatClosesItsDatabaseDuringARead_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given -- the operands are still in the memtable, so the merge runs on the thread calling get()
		var dbRef = new AtomicReference<ReadWriteDB>();
		var thrown = new AtomicReference<Throwable>();
		MergeOperator operator = MergeOperator.custom("last-operand", (key, existingValue, operands) -> {
			closeInto(thrown, dbRef);
			return operands.getLast().toArray(ValueLayout.JAVA_BYTE);
		});
		try (var opts = Options.newOptions().setCreateIfMissing(true).setMergeOperator(operator);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put(KEY, "old".getBytes());
			db.merge(KEY, VALUE);
			dbRef.set(db);

			// When
			var result = db.get(KEY);

			// Then
			assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}

	@Test
	void mergeOperator_thatClosesItsDatabaseDuringACompaction_throwsInsteadOfHanging(@TempDir Path dir) {
		// Given
		var dbRef = new AtomicReference<ReadWriteDB>();
		var thrown = new AtomicReference<Throwable>();
		MergeOperator operator = MergeOperator.custom("last-operand", (key, existingValue, operands) -> {
			closeInto(thrown, dbRef);
			return operands.getLast().toArray(ValueLayout.JAVA_BYTE);
		});
		try (var opts = Options.newOptions().setCreateIfMissing(true).setMergeOperator(operator);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put(KEY, "old".getBytes());
			db.merge(KEY, VALUE);
			dbRef.set(db);

			// When
			db.compactRange();

			// Then
			var result = db.get(KEY);
			assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}

	@Test
	void compactionFilter_thatClosesItsDatabase_throwsInsteadOfHanging(@TempDir Path dir) {
		// Given
		var dbRef = new AtomicReference<ReadWriteDB>();
		var thrown = new AtomicReference<Throwable>();
		var filter = CompactionFilter.create("closing", (level, key, existingValue) -> {
			closeInto(thrown, dbRef);
			return CompactionFilter.FilterDecision.keep();
		});
		try (var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put(KEY, VALUE);
			dbRef.set(db);

			// When
			db.compactRange();

			// Then
			var result = db.get(KEY);
			assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}

	@Test
	void compactionFilterFactory_thatClosesItsDatabase_throwsInsteadOfHanging(@TempDir Path dir) {
		// Given
		var dbRef = new AtomicReference<ReadWriteDB>();
		var thrown = new AtomicReference<Throwable>();
		CompactionFilterFactory.CreateFilterFn closing = context -> {
			closeInto(thrown, dbRef);
			return null;
		};
		try (var factory = CompactionFilterFactory.create("closing", closing);
		     var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilterFactory(factory);
		     var db = RocksDB.openReadWrite(dir, opts)) {
			db.put(KEY, VALUE);
			dbRef.set(db);

			// When
			db.compactRange();

			// Then
			var result = db.get(KEY);
			assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}

	@Test
	void loggerCallback_thatClosesItsDatabase_throwsInsteadOfCrashing(@TempDir Path dir) {
		// Given -- "Manual flush start" is logged on the thread calling flush(), in the middle of DBImpl::Flush
		var dbRef = new AtomicReference<ReadWriteDB>();
		var thrown = new AtomicReference<Throwable>();
		try (var logger = Logger.newCallbackLogger(LogLevel.DEBUG, (level, message) -> closeInto(thrown, dbRef));
		     var opts = Options.newOptions().setCreateIfMissing(true).setInfoLog(logger);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var flushOptions = FlushOptions.newFlushOptions()) {
			db.put(KEY, VALUE);
			dbRef.set(db);

			// When
			db.flush(flushOptions);

			// Then
			dbRef.set(null);
			var result = db.get(KEY);
			assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
			assertThat(result).isEqualTo(VALUE);
		}
	}
}
