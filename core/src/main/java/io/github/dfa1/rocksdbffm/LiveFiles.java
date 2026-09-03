package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Iterator;
import java.util.NoSuchElementException;

/// FFM wrapper for `rocksdb_livefiles_t`: metadata for every live SST file belonging to a
/// database, captured at the moment [RocksDBMonitoringOperations#getLiveFiles()] was called.
///
/// Each [LiveFileInfo] returned by [#get(int)] or iteration is a lazy view over this native
/// list — no field is read from native memory until the matching accessor is called, so
/// scanning only the fields you need (e.g. just `level()` across a database with thousands of
/// files) costs one native call per file per field actually touched, not a fixed cost per file
/// up front. [#size()] and [#get(int)] never touch native memory (the count is cached at fetch
/// time, and `get` only builds a Java-side view), so both remain callable even after this
/// [LiveFiles] is closed; it's specifically calling an accessor on a [LiveFileInfo] view — which
/// does read native memory — that then throws [IllegalStateException], the same as any other
/// [NativeObject]-backed pointer used after close.
///
/// This deliberately diverges from [BackupEngine#getBackupInfo()]'s pattern of eagerly
/// snapshotting into plain [BackupInfo] records with no native resource left open: a database
/// under active operation can carry thousands of live SST files, where BackupEngine's own
/// backup count is normally small, so paying for every field of every file up front (the same
/// per-index, no-bulk-accessor `rocksdb_backup_engine_info_*` shape `rocksdb_livefiles_*` also
/// has) does not scale the same way here.
///
/// Use inside a try-with-resources block:
///
/// ```
/// try (var db = RocksDB.openReadOnly(dbDir);
///      var files = db.getLiveFiles()) {
///     for (LiveFileInfo file : files) {
///         System.out.println(file.name() + ": " + file.numberOfEntries() + " entries");
///     }
/// }
/// ```
public final class LiveFiles extends NativeObject implements Iterable<LiveFileInfo> {

	/// `const rocksdb_livefiles_t* rocksdb_livefiles(rocksdb_t* db);`
	private static final MethodHandle MH_LIVEFILES;
	/// `int rocksdb_livefiles_count(const rocksdb_livefiles_t*);`
	private static final MethodHandle MH_COUNT;
	/// `void rocksdb_livefiles_destroy(const rocksdb_livefiles_t*);`
	private static final MethodHandle MH_DESTROY;

	static {
		MH_LIVEFILES = NativeLibrary.lookup("rocksdb_livefiles",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_COUNT = NativeLibrary.lookup("rocksdb_livefiles_count",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_livefiles_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	// Cached at fetch time so size()/get() keep working after close() instead of going through
	// ptr() (which would throw IllegalStateException) — not a performance optimization, since
	// rocksdb_livefiles_count is just a std::vector::size() call. The underlying
	// std::vector<LiveFileMetaData> (rocksdb/db/c.cc's rocksdb_livefiles_t) is populated once by
	// rocksdb_livefiles() and never mutated afterward, so caching it is also always correct.
	private final int count;

	private LiveFiles(MemorySegment ptr, int count) {
		super(ptr);
		this.count = count;
	}

	/// Captures live-file metadata for the database behind `dbPtr`.
	///
	/// @param dbPtr native `rocksdb_t*` to inspect
	/// @return a new [LiveFiles] snapshot; caller must close it
	static LiveFiles fetch(MemorySegment dbPtr) {
		try {
			MemorySegment listPtr = (MemorySegment) MH_LIVEFILES.invokeExact(dbPtr);
			int count = (int) MH_COUNT.invokeExact(listPtr);
			return new LiveFiles(listPtr, count);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getLiveFiles failed", t);
		}
	}

	/// Returns the number of live SST files captured in this snapshot.
	///
	/// @return the number of files
	public int size() {
		return count;
	}

	/// Returns a lazy view over the file at `index`. No field is read from native memory
	/// until an accessor on the returned [LiveFileInfo] is called.
	///
	/// @param index zero-based index, must be less than [#size()]
	/// @return a [LiveFileInfo] view backed by this list
	public LiveFileInfo get(int index) {
		if (index < 0 || index >= count) {
			throw new IndexOutOfBoundsException("index " + index + " out of bounds for size " + count);
		}
		return new LiveFileInfo(this, index);
	}

	/// Returns an iterator over lazy [LiveFileInfo] views, one per live SST file.
	///
	/// @return an iterator over this list
	@Override
	public Iterator<LiveFileInfo> iterator() {
		return new Iterator<>() {
			private int i = 0;

			@Override
			public boolean hasNext() {
				return i < count;
			}

			@Override
			public LiveFileInfo next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				// Routed through the bounds-checked get(int), not constructed directly: if the
				// index bookkeeping here ever regresses (e.g. corrupts to negative), this throws
				// IndexOutOfBoundsException instead of handing a bad index to LiveFileInfo, whose
				// native accessors do an unchecked std::vector::operator[] — a negative int
				// reinterpreted as an enormous size_t is a near-certain native OOB read/crash.
				return get(i++);
			}
		};
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
