package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/// Read-only view of a completed or in-progress memtable flush, passed to
/// [EventNotifier#onFlushBegin(FlushJobInfo)] and [EventNotifier#onFlushCompleted(FlushJobInfo)].
///
/// Wraps a `const rocksdb_flushjobinfo_t*` owned by RocksDB for the duration of the callback only:
/// every accessor reads through that pointer on demand, so an instance must never be retained or
/// used after the callback method returns.
public final class FlushJobInfo {

	/// `uint32_t rocksdb_flushjobinfo_cf_id(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_CF_ID = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_cf_id", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `const char* rocksdb_flushjobinfo_cf_name(const rocksdb_flushjobinfo_t*, size_t* size);`
	private static final MethodHandle MH_CF_NAME = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_cf_name",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

	/// `const char* rocksdb_flushjobinfo_file_path(const rocksdb_flushjobinfo_t*, size_t* size);`
	private static final MethodHandle MH_FILE_PATH = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_file_path",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_flushjobinfo_file_number(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_FILE_NUMBER = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_file_number", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_flushjobinfo_oldest_blob_file_number(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_OLDEST_BLOB_FILE_NUMBER = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_oldest_blob_file_number",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_flushjobinfo_thread_id(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_THREAD_ID = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_thread_id", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `int rocksdb_flushjobinfo_job_id(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_JOB_ID = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_job_id", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `unsigned char rocksdb_flushjobinfo_triggered_writes_slowdown(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_TRIGGERED_WRITES_SLOWDOWN = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_triggered_writes_slowdown",
			FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

	/// `unsigned char rocksdb_flushjobinfo_triggered_writes_stop(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_TRIGGERED_WRITES_STOP = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_triggered_writes_stop",
			FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_flushjobinfo_smallest_seqno(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_SMALLEST_SEQNO = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_smallest_seqno", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_flushjobinfo_largest_seqno(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_LARGEST_SEQNO = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_largest_seqno", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint32_t rocksdb_flushjobinfo_flush_reason(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_FLUSH_REASON = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_flush_reason", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `uint32_t rocksdb_flushjobinfo_blob_compression_type(const rocksdb_flushjobinfo_t*);`
	private static final MethodHandle MH_BLOB_COMPRESSION_TYPE = NativeLibrary.lookup(
			"rocksdb_flushjobinfo_blob_compression_type",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	private final MemorySegment ptr;

	FlushJobInfo(MemorySegment ptr) {
		this.ptr = ptr;
	}

	/// Returns the id of the column family that was flushed.
	///
	/// @return the column family id
	public int columnFamilyId() {
		return NativeFields.getInt(MH_CF_ID, ptr);
	}

	/// Returns the name of the column family that was flushed.
	///
	/// @return the column family name
	public String columnFamilyName() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment sizeHolder = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment namePtr = (MemorySegment) MH_CF_NAME.invokeExact(ptr, sizeHolder);
			return RocksDB.toJavaString(namePtr, sizeHolder.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("FlushJobInfo.columnFamilyName failed", t);
		}
	}

	/// Returns the path of the SST file produced by this flush.
	///
	/// @return the flushed file's path
	public Path filePath() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment sizeHolder = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment pathPtr = (MemorySegment) MH_FILE_PATH.invokeExact(ptr, sizeHolder);
			return Path.of(RocksDB.toJavaString(pathPtr, sizeHolder.get(ValueLayout.JAVA_LONG, 0)));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("FlushJobInfo.filePath failed", t);
		}
	}

	/// Returns the number of the SST file produced by this flush.
	///
	/// @return the flushed file's number
	public long fileNumber() {
		return NativeFields.getLong(MH_FILE_NUMBER, ptr);
	}

	/// Returns the number of the oldest blob file this flush's SST file references.
	///
	/// @return the oldest referenced blob file number
	public long oldestBlobFileNumber() {
		return NativeFields.getLong(MH_OLDEST_BLOB_FILE_NUMBER, ptr);
	}

	/// Returns the id of the thread that performed this flush.
	///
	/// @return the flush thread id
	public long threadId() {
		return NativeFields.getLong(MH_THREAD_ID, ptr);
	}

	/// Returns the id of this flush job.
	///
	/// @return the job id
	public int jobId() {
		return NativeFields.getInt(MH_JOB_ID, ptr);
	}

	/// Returns whether this flush triggered a write slowdown.
	///
	/// @return `true` if writes were slowed down as a result of this flush
	public boolean triggeredWritesSlowdown() {
		return NativeFields.getBoolean(MH_TRIGGERED_WRITES_SLOWDOWN, ptr);
	}

	/// Returns whether this flush triggered writes to stop entirely.
	///
	/// @return `true` if writes were stopped as a result of this flush
	public boolean triggeredWritesStop() {
		return NativeFields.getBoolean(MH_TRIGGERED_WRITES_STOP, ptr);
	}

	/// Returns the smallest sequence number written to the flushed SST file.
	///
	/// @return the smallest sequence number
	public SequenceNumber smallestSequenceNumber() {
		return SequenceNumber.of(NativeFields.getLong(MH_SMALLEST_SEQNO, ptr));
	}

	/// Returns the largest sequence number written to the flushed SST file.
	///
	/// @return the largest sequence number
	public SequenceNumber largestSequenceNumber() {
		return SequenceNumber.of(NativeFields.getLong(MH_LARGEST_SEQNO, ptr));
	}

	/// Returns why this flush was triggered.
	///
	/// @return the flush reason
	public FlushReason flushReason() {
		return FlushReason.fromValue(NativeFields.getInt(MH_FLUSH_REASON, ptr));
	}

	/// Returns the compression type used for blob values referenced by this flush.
	///
	/// @return the blob compression type
	public CompressionType blobCompressionType() {
		return CompressionType.fromValue(NativeFields.getInt(MH_BLOB_COMPRESSION_TYPE, ptr));
	}
}
