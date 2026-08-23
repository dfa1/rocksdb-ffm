package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/// Lazy view over a single live SST file's metadata, indexed into its owning [LiveFiles] list.
///
/// Each accessor is a single native call that reads only its own field — nothing is read from
/// native memory until the accessor is invoked, so scanning a single field (e.g. `level()`)
/// across every file in a large database does not pay for the fields never touched. Once the
/// owning [LiveFiles] is closed, each accessor's own [NativeObject#ptr()] check on it throws
/// [IllegalStateException] — but only if that check runs before a concurrent `close()` frees the
/// underlying list; per [NativeObject]'s own documented caveat, `close()` racing a live call on
/// another thread is not synchronized against, and this class's per-field design means every
/// accessor call is its own such window instead of one at construction, so code that reads
/// several fields off one instance must not let `close()` on the owning [LiveFiles] run
/// concurrently with any of them.
///
/// Obtained from [LiveFiles#get(int)] or by iterating a [LiveFiles] instance — never
/// constructed directly.
public final class LiveFileInfo {

	/// `const char* rocksdb_livefiles_column_family_name(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_CF_NAME;
	/// `const char* rocksdb_livefiles_name(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_NAME;
	/// `const char* rocksdb_livefiles_directory(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_DIRECTORY;
	/// `int rocksdb_livefiles_level(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_LEVEL;
	/// `size_t rocksdb_livefiles_size(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_SIZE;
	/// `const char* rocksdb_livefiles_smallestkey(const rocksdb_livefiles_t*, int index, size_t* size);`
	private static final MethodHandle MH_SMALLESTKEY;
	/// `const char* rocksdb_livefiles_largestkey(const rocksdb_livefiles_t*, int index, size_t* size);`
	private static final MethodHandle MH_LARGESTKEY;
	/// `uint64_t rocksdb_livefiles_smallest_seqno(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_SMALLEST_SEQNO;
	/// `uint64_t rocksdb_livefiles_largest_seqno(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_LARGEST_SEQNO;
	/// `uint64_t rocksdb_livefiles_entries(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_ENTRIES;
	/// `uint64_t rocksdb_livefiles_deletions(const rocksdb_livefiles_t*, int index);`
	private static final MethodHandle MH_DELETIONS;

	static {
		MH_CF_NAME = NativeLibrary.lookup("rocksdb_livefiles_column_family_name",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_NAME = NativeLibrary.lookup("rocksdb_livefiles_name",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_DIRECTORY = NativeLibrary.lookup("rocksdb_livefiles_directory",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_LEVEL = NativeLibrary.lookup("rocksdb_livefiles_level",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_SIZE = NativeLibrary.lookup("rocksdb_livefiles_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_SMALLESTKEY = NativeLibrary.lookup("rocksdb_livefiles_smallestkey",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_LARGESTKEY = NativeLibrary.lookup("rocksdb_livefiles_largestkey",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SMALLEST_SEQNO = NativeLibrary.lookup("rocksdb_livefiles_smallest_seqno",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_LARGEST_SEQNO = NativeLibrary.lookup("rocksdb_livefiles_largest_seqno",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_ENTRIES = NativeLibrary.lookup("rocksdb_livefiles_entries",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_DELETIONS = NativeLibrary.lookup("rocksdb_livefiles_deletions",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	}

	private final LiveFiles owner;
	private final int index;

	LiveFileInfo(LiveFiles owner, int index) {
		this.owner = owner;
		this.index = index;
	}

	/// Returns the name of the column family this file belongs to.
	///
	/// @return the column family name
	public String columnFamilyName() {
		try {
			MemorySegment p = (MemorySegment) MH_CF_NAME.invokeExact(owner.ptr(), index);
			return borrowedString(p);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("columnFamilyName failed", t);
		}
	}

	/// Returns the relative file name, e.g. `/000012.sst`.
	///
	/// @return the relative file name
	public String name() {
		try {
			MemorySegment p = (MemorySegment) MH_NAME.invokeExact(owner.ptr(), index);
			return borrowedString(p);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("name failed", t);
		}
	}

	/// Returns the directory containing this file.
	///
	/// @return the containing directory
	public Path directory() {
		try {
			MemorySegment p = (MemorySegment) MH_DIRECTORY.invokeExact(owner.ptr(), index);
			return Path.of(borrowedString(p));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("directory failed", t);
		}
	}

	/// Reads a NUL-terminated string from a pointer this class does not own — a borrowed view
	/// into the parent [LiveFiles] list's internal `std::string` storage, live only as long as
	/// that list is open. Unlike [RocksDB#toJavaString(MemorySegment)], this does not free `p`;
	/// using that method here instead would double-free memory this class never allocated.
	///
	/// @param p non-NULL borrowed `const char*`
	/// @return the decoded string
	private static String borrowedString(MemorySegment p) {
		return p.reinterpret(Long.MAX_VALUE).getString(0);
	}

	/// Returns the LSM level this file resides at.
	///
	/// @return the LSM level
	public int level() {
		try {
			return (int) MH_LEVEL.invokeExact(owner.ptr(), index);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("level failed", t);
		}
	}

	/// Returns the file size on disk.
	///
	/// @return the file size
	public MemorySize size() {
		try {
			return MemorySize.ofBytes((long) MH_SIZE.invokeExact(owner.ptr(), index));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("size failed", t);
		}
	}

	/// Returns the smallest user key stored in this file.
	///
	/// @return the smallest key
	public byte[] smallestKey() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment sizeOut = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment p = (MemorySegment) MH_SMALLESTKEY.invokeExact(owner.ptr(), index, sizeOut);
			return RocksDB.toByteArray(p, sizeOut.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("smallestKey failed", t);
		}
	}

	/// Returns the largest user key stored in this file.
	///
	/// @return the largest key
	public byte[] largestKey() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment sizeOut = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment p = (MemorySegment) MH_LARGESTKEY.invokeExact(owner.ptr(), index, sizeOut);
			return RocksDB.toByteArray(p, sizeOut.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("largestKey failed", t);
		}
	}

	/// Returns the smallest sequence number of any key in this file.
	///
	/// @return the smallest sequence number
	public SequenceNumber smallestSequenceNumber() {
		try {
			return SequenceNumber.of((long) MH_SMALLEST_SEQNO.invokeExact(owner.ptr(), index));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("smallestSequenceNumber failed", t);
		}
	}

	/// Returns the largest sequence number of any key in this file.
	///
	/// @return the largest sequence number
	public SequenceNumber largestSequenceNumber() {
		try {
			return SequenceNumber.of((long) MH_LARGEST_SEQNO.invokeExact(owner.ptr(), index));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("largestSequenceNumber failed", t);
		}
	}

	/// Returns the total number of entries in this file, including deletion markers.
	///
	/// @return the number of entries
	public long numberOfEntries() {
		try {
			return (long) MH_ENTRIES.invokeExact(owner.ptr(), index);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("numberOfEntries failed", t);
		}
	}

	/// Returns the number of entries in this file that are deletion markers.
	///
	/// @return the number of deletion markers
	public long numberOfDeletions() {
		try {
			return (long) MH_DELETIONS.invokeExact(owner.ptr(), index);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("numberOfDeletions failed", t);
		}
	}
}
