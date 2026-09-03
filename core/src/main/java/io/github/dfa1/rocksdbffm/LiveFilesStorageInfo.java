package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Iterator;
import java.util.NoSuchElementException;

/// FFM wrapper for `rocksdb_livefiles_storage_info_t`: every file needed to reconstruct a
/// database — SST files, WAL, MANIFEST, `CURRENT`, `OPTIONS`, blob files, and more — not just
/// the SST-only view [LiveFiles] gives. Captured at the moment
/// [RocksDBMonitoringOperations#getLiveFilesStorageInfo] was called.
///
/// Each [LiveFileStorageInfo] returned by [#get(int)] or iteration is a lazy view over this
/// native list, the same pattern [LiveFileInfo] uses over [LiveFiles]: no field is read from
/// native memory until the matching accessor is called.
///
/// Use inside a try-with-resources block:
///
/// ```
/// try (var db = RocksDB.openReadOnly(dbDir);
///      var files = db.getLiveFilesStorageInfo()) {
///     for (LiveFileStorageInfo file : files) {
///         System.out.println(file.fileType() + " " + file.relativeFilename());
///     }
/// }
/// ```
public final class LiveFilesStorageInfo extends NativeObject implements Iterable<LiveFileStorageInfo> {

	/// `rocksdb_livefiles_storage_info_t* rocksdb_get_livefiles_storage_info(rocksdb_t* db, const rocksdb_livefiles_storage_info_options_t* options, char** errptr);`
	private static final MethodHandle MH_GET;
	/// `size_t rocksdb_livefiles_storage_info_count(const rocksdb_livefiles_storage_info_t* info);`
	private static final MethodHandle MH_COUNT;
	/// `void rocksdb_livefiles_storage_info_destroy(rocksdb_livefiles_storage_info_t* info);`
	private static final MethodHandle MH_DESTROY;

	static {
		MH_GET = NativeLibrary.lookup("rocksdb_get_livefiles_storage_info",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_COUNT = NativeLibrary.lookup("rocksdb_livefiles_storage_info_count",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_livefiles_storage_info_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	// See LiveFiles.count for why this is cached rather than re-derived: size()/get() need to
	// keep working after close() without touching ptr(), not a performance concern.
	private final int count;

	private LiveFilesStorageInfo(MemorySegment ptr, int count) {
		super(ptr);
		this.count = count;
	}

	/// Captures storage info for the database behind `dbPtr`.
	///
	/// @param dbPtr      native `rocksdb_t*` to inspect
	/// @param optionsPtr native `rocksdb_livefiles_storage_info_options_t*`, or
	///                   [MemorySegment#NULL] to use RocksDB's defaults
	/// @return a new [LiveFilesStorageInfo] snapshot; caller must close it
	static LiveFilesStorageInfo fetch(MemorySegment dbPtr, MemorySegment optionsPtr) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment listPtr = (MemorySegment) MH_GET.invokeExact(dbPtr, optionsPtr, err);
			RocksDB.checkError(err);
			long count = (long) MH_COUNT.invokeExact(listPtr);
			return new LiveFilesStorageInfo(listPtr, (int) count);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getLiveFilesStorageInfo failed", t);
		}
	}

	/// Returns the number of files captured in this snapshot.
	///
	/// @return the number of files
	public int size() {
		return count;
	}

	/// Returns a lazy view over the file at `index`. No field is read from native memory
	/// until an accessor on the returned [LiveFileStorageInfo] is called.
	///
	/// @param index zero-based index, must be less than [#size()]
	/// @return a [LiveFileStorageInfo] view backed by this list
	public LiveFileStorageInfo get(int index) {
		if (index < 0 || index >= count) {
			throw new IndexOutOfBoundsException("index " + index + " out of bounds for size " + count);
		}
		return new LiveFileStorageInfo(this, index);
	}

	/// Returns an iterator over lazy [LiveFileStorageInfo] views, one per file.
	///
	/// @return an iterator over this list
	@Override
	public Iterator<LiveFileStorageInfo> iterator() {
		return new Iterator<>() {
			private int i = 0;

			@Override
			public boolean hasNext() {
				return i < count;
			}

			@Override
			public LiveFileStorageInfo next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				return new LiveFileStorageInfo(LiveFilesStorageInfo.this, i++);
			}
		};
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
