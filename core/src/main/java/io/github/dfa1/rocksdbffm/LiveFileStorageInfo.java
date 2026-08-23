package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/// Lazy view over a single file's storage metadata, indexed into its owning
/// [LiveFilesStorageInfo] list.
///
/// Each accessor is a single native call that reads only its own field, the same lazy pattern
/// [LiveFileInfo] uses over [LiveFiles]. Instances become invalid the same way: an accessor
/// throws [IllegalStateException] once the owning [LiveFilesStorageInfo] is closed.
///
/// Obtained from [LiveFilesStorageInfo#get(int)] or by iterating a [LiveFilesStorageInfo]
/// instance — never constructed directly.
public final class LiveFileStorageInfo {

	/// `const char* rocksdb_livefiles_storage_info_relative_filename(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_RELATIVE_FILENAME;
	/// `const char* rocksdb_livefiles_storage_info_directory(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_DIRECTORY;
	/// `uint64_t rocksdb_livefiles_storage_info_file_number(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_FILE_NUMBER;
	/// `int rocksdb_livefiles_storage_info_file_type(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_FILE_TYPE;
	/// `uint64_t rocksdb_livefiles_storage_info_size(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_SIZE;
	/// `int rocksdb_livefiles_storage_info_temperature(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_TEMPERATURE;
	/// `const char* rocksdb_livefiles_storage_info_file_checksum(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_FILE_CHECKSUM;
	/// `const char* rocksdb_livefiles_storage_info_file_checksum_func_name(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_FILE_CHECKSUM_FUNC_NAME;
	/// `const char* rocksdb_livefiles_storage_info_replacement_contents(const rocksdb_livefiles_storage_info_t*, size_t index, size_t* size);`
	private static final MethodHandle MH_REPLACEMENT_CONTENTS;
	/// `unsigned char rocksdb_livefiles_storage_info_trim_to_size(const rocksdb_livefiles_storage_info_t*, size_t index);`
	private static final MethodHandle MH_TRIM_TO_SIZE;

	static {
		MH_RELATIVE_FILENAME = NativeLibrary.lookup("rocksdb_livefiles_storage_info_relative_filename",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_DIRECTORY = NativeLibrary.lookup("rocksdb_livefiles_storage_info_directory",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_FILE_NUMBER = NativeLibrary.lookup("rocksdb_livefiles_storage_info_file_number",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_FILE_TYPE = NativeLibrary.lookup("rocksdb_livefiles_storage_info_file_type",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_SIZE = NativeLibrary.lookup("rocksdb_livefiles_storage_info_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_TEMPERATURE = NativeLibrary.lookup("rocksdb_livefiles_storage_info_temperature",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_FILE_CHECKSUM = NativeLibrary.lookup("rocksdb_livefiles_storage_info_file_checksum",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_FILE_CHECKSUM_FUNC_NAME = NativeLibrary.lookup("rocksdb_livefiles_storage_info_file_checksum_func_name",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_REPLACEMENT_CONTENTS = NativeLibrary.lookup("rocksdb_livefiles_storage_info_replacement_contents",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_TRIM_TO_SIZE = NativeLibrary.lookup("rocksdb_livefiles_storage_info_trim_to_size",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	}

	private final LiveFilesStorageInfo owner;
	private final int index;

	LiveFileStorageInfo(LiveFilesStorageInfo owner, int index) {
		this.owner = owner;
		this.index = index;
	}

	/// Returns the file's name within its directory, e.g. `"000012.sst"` or `"CURRENT"`.
	///
	/// @return the relative file name
	public String relativeFilename() {
		try {
			MemorySegment p = (MemorySegment) MH_RELATIVE_FILENAME.invokeExact(owner.ptr(), (long) index);
			return RocksDB.toBorrowedJavaString(p);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("relativeFilename failed", t);
		}
	}

	/// Returns the directory containing this file — a DB path, the WAL directory, etc.
	///
	/// @return the containing directory
	public Path directory() {
		try {
			MemorySegment p = (MemorySegment) MH_DIRECTORY.invokeExact(owner.ptr(), (long) index);
			return Path.of(RocksDB.toBorrowedJavaString(p));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("directory failed", t);
		}
	}

	/// Returns the file's id within the database, or `0` if the file has no number (e.g.
	/// `CURRENT`).
	///
	/// @return the file number, or `0` if not applicable
	public long fileNumber() {
		try {
			return (long) MH_FILE_NUMBER.invokeExact(owner.ptr(), (long) index);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("fileNumber failed", t);
		}
	}

	/// Returns the role this file plays within the database.
	///
	/// @return the file type
	public FileType fileType() {
		try {
			return FileType.fromValue((int) MH_FILE_TYPE.invokeExact(owner.ptr(), (long) index));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("fileType failed", t);
		}
	}

	/// Returns the file size. See also [#trimToSize()].
	///
	/// @return the file size
	public MemorySize size() {
		try {
			return MemorySize.ofBytes((long) MH_SIZE.invokeExact(owner.ptr(), (long) index));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("size failed", t);
		}
	}

	/// Returns the storage temperature hint for this file. EXPERIMENTAL: see [Temperature].
	///
	/// @return the temperature hint
	public Temperature temperature() {
		try {
			return Temperature.fromValue((int) MH_TEMPERATURE.invokeExact(owner.ptr(), (long) index));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("temperature failed", t);
		}
	}

	/// Returns the file's checksum, or an empty string if checksums were not requested (see
	/// [LiveFilesStorageInfoOptions#setIncludeChecksumInfo]) or no checksum function is
	/// configured for the database.
	///
	/// @return the file checksum, or an empty string if unavailable
	public String fileChecksum() {
		try {
			MemorySegment p = (MemorySegment) MH_FILE_CHECKSUM.invokeExact(owner.ptr(), (long) index);
			return RocksDB.toBorrowedJavaString(p);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("fileChecksum failed", t);
		}
	}

	/// Returns the name of the function used to compute [#fileChecksum()], or `"Unknown"` if
	/// none is configured.
	///
	/// @return the checksum function name
	public String fileChecksumFuncName() {
		try {
			MemorySegment p = (MemorySegment) MH_FILE_CHECKSUM_FUNC_NAME.invokeExact(owner.ptr(), (long) index);
			return RocksDB.toBorrowedJavaString(p);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("fileChecksumFuncName failed", t);
		}
	}

	/// Returns the captured contents to use in place of this file's on-disk contents (used for
	/// `CURRENT`, whose true contents at the time of this snapshot may already have changed on
	/// disk), or an empty array if the file's on-disk contents should be used as-is.
	///
	/// @return the replacement contents, or an empty array if none
	public byte[] replacementContents() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment sizeOut = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment p = (MemorySegment) MH_REPLACEMENT_CONTENTS.invokeExact(owner.ptr(), (long) index, sizeOut);
			return RocksDB.toByteArray(p, sizeOut.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("replacementContents failed", t);
		}
	}

	/// If `true`, the on-disk file is allowed to be larger than [#size()], but only the first
	/// `size()` bytes should be used. If `false`, the file is corrupt unless its on-disk size
	/// exactly matches [#size()].
	///
	/// @return `true` if the on-disk file may be trimmed to `size()`
	public boolean trimToSize() {
		try {
			return RocksDB.fromByte((byte) MH_TRIM_TO_SIZE.invokeExact(owner.ptr(), (long) index));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("trimToSize failed", t);
		}
	}
}
