package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// Read-only view of a compaction, passed to [EventNotifier#onCompactionBegin(CompactionJobInfo)]
/// and [EventNotifier#onCompactionCompleted(CompactionJobInfo)].
///
/// Wraps a `const rocksdb_compactionjobinfo_t*` owned by RocksDB for the duration of the callback
/// only: every accessor reads through that pointer on demand, so an instance must never be
/// retained or used after the callback method returns.
///
/// Per-file details (input/output file lists, per-file table properties) and the detailed
/// `CompactionJobStats` counters are not exposed yet; only the flat, single-value fields are.
public final class CompactionJobInfo {

	/// `uint32_t rocksdb_compactionjobinfo_cf_id(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_CF_ID = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_cf_id", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `const char* rocksdb_compactionjobinfo_cf_name(const rocksdb_compactionjobinfo_t*, size_t* size);`
	private static final MethodHandle MH_CF_NAME = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_cf_name",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

	/// `void rocksdb_compactionjobinfo_status(const rocksdb_compactionjobinfo_t*, char** errptr);`
	private static final MethodHandle MH_STATUS = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_status",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_compactionjobinfo_thread_id(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_THREAD_ID = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_thread_id", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `int rocksdb_compactionjobinfo_job_id(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_JOB_ID = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_job_id", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `int rocksdb_compactionjobinfo_num_l0_files(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_NUM_L0_FILES = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_num_l0_files", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `int rocksdb_compactionjobinfo_base_input_level(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_BASE_INPUT_LEVEL = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_base_input_level",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `int rocksdb_compactionjobinfo_output_level(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_OUTPUT_LEVEL = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_output_level", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `uint32_t rocksdb_compactionjobinfo_compaction_reason(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_COMPACTION_REASON = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_compaction_reason",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `uint32_t rocksdb_compactionjobinfo_compression(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_COMPRESSION = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_compression", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `uint32_t rocksdb_compactionjobinfo_blob_compression_type(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_BLOB_COMPRESSION_TYPE = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_blob_compression_type",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	/// `unsigned char rocksdb_compactionjobinfo_aborted(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_ABORTED = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_aborted", FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

	/// `size_t rocksdb_compactionjobinfo_input_files_count(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_INPUT_FILES_COUNT = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_input_files_count",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `size_t rocksdb_compactionjobinfo_output_files_count(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_OUTPUT_FILES_COUNT = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_output_files_count",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_compactionjobinfo_elapsed_micros(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_ELAPSED_MICROS = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_elapsed_micros",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_compactionjobinfo_num_corrupt_keys(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_NUM_CORRUPT_KEYS = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_num_corrupt_keys",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_compactionjobinfo_input_records(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_INPUT_RECORDS = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_input_records", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_compactionjobinfo_output_records(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_OUTPUT_RECORDS = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_output_records", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_compactionjobinfo_total_input_bytes(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_TOTAL_INPUT_BYTES = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_total_input_bytes",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `uint64_t rocksdb_compactionjobinfo_total_output_bytes(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_TOTAL_OUTPUT_BYTES = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_total_output_bytes",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `size_t rocksdb_compactionjobinfo_num_input_files(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_NUM_INPUT_FILES = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_num_input_files",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	/// `size_t rocksdb_compactionjobinfo_num_input_files_at_output_level(const rocksdb_compactionjobinfo_t*);`
	private static final MethodHandle MH_NUM_INPUT_FILES_AT_OUTPUT_LEVEL = NativeLibrary.lookup(
			"rocksdb_compactionjobinfo_num_input_files_at_output_level",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

	private final MemorySegment ptr;

	CompactionJobInfo(MemorySegment ptr) {
		this.ptr = ptr;
	}

	/// Returns the id of the column family being compacted.
	///
	/// @return the column family id
	public int columnFamilyId() {
		return NativeFields.getInt(MH_CF_ID, ptr);
	}

	/// Returns the name of the column family being compacted.
	///
	/// @return the column family name
	public String columnFamilyName() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment sizeHolder = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment namePtr = (MemorySegment) MH_CF_NAME.invokeExact(ptr, sizeHolder);
			return RocksDB.toJavaString(namePtr, sizeHolder.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("CompactionJobInfo.columnFamilyName failed", t);
		}
	}

	/// Throws if this compaction did not succeed.
	///
	/// @throws RocksDBException describing why the compaction failed, if it did
	public void checkStatus() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_STATUS.invokeExact(ptr, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("CompactionJobInfo.checkStatus failed", t);
		}
	}

	/// Returns the id of the thread that performed this compaction.
	///
	/// @return the compaction thread id
	public long threadId() {
		return NativeFields.getLong(MH_THREAD_ID, ptr);
	}

	/// Returns the id of this compaction job.
	///
	/// @return the job id
	public int jobId() {
		return NativeFields.getInt(MH_JOB_ID, ptr);
	}

	/// Returns the number of level-0 files that were part of this compaction's input.
	///
	/// @return the number of level-0 input files
	public int numL0Files() {
		return NativeFields.getInt(MH_NUM_L0_FILES, ptr);
	}

	/// Returns the lowest level that had files as input to this compaction.
	///
	/// @return the base input level
	public int baseInputLevel() {
		return NativeFields.getInt(MH_BASE_INPUT_LEVEL, ptr);
	}

	/// Returns the level this compaction wrote its output to.
	///
	/// @return the output level
	public int outputLevel() {
		return NativeFields.getInt(MH_OUTPUT_LEVEL, ptr);
	}

	/// Returns why this compaction was triggered.
	///
	/// @return the compaction reason
	public CompactionReason compactionReason() {
		return CompactionReason.fromValue(NativeFields.getInt(MH_COMPACTION_REASON, ptr));
	}

	/// Returns the compression type used for this compaction's output.
	///
	/// @return the output compression type
	public CompressionType compression() {
		return CompressionType.fromValue(NativeFields.getInt(MH_COMPRESSION, ptr));
	}

	/// Returns the compression type used for blob values referenced by this compaction.
	///
	/// @return the blob compression type
	public CompressionType blobCompressionType() {
		return CompressionType.fromValue(NativeFields.getInt(MH_BLOB_COMPRESSION_TYPE, ptr));
	}

	/// Returns whether this compaction was aborted before completing.
	///
	/// @return `true` if the compaction was aborted
	public boolean aborted() {
		return NativeFields.getBoolean(MH_ABORTED, ptr);
	}

	/// Returns the number of input files across all input levels.
	///
	/// @return the input file count
	public long inputFilesCount() {
		return NativeFields.getLong(MH_INPUT_FILES_COUNT, ptr);
	}

	/// Returns the number of output files produced by this compaction.
	///
	/// @return the output file count
	public long outputFilesCount() {
		return NativeFields.getLong(MH_OUTPUT_FILES_COUNT, ptr);
	}

	/// Returns how long this compaction took to run.
	///
	/// @return the elapsed time
	public java.time.Duration elapsed() {
		return java.time.Duration.ofNanos(NativeFields.getLong(MH_ELAPSED_MICROS, ptr) * 1000);
	}

	/// Returns the number of corrupt keys encountered while compacting.
	///
	/// @return the number of corrupt keys
	public long numCorruptKeys() {
		return NativeFields.getLong(MH_NUM_CORRUPT_KEYS, ptr);
	}

	/// Returns the number of records read as input to this compaction.
	///
	/// @return the input record count
	public long inputRecords() {
		return NativeFields.getLong(MH_INPUT_RECORDS, ptr);
	}

	/// Returns the number of records written as output by this compaction.
	///
	/// @return the output record count
	public long outputRecords() {
		return NativeFields.getLong(MH_OUTPUT_RECORDS, ptr);
	}

	/// Returns the total size of this compaction's input files.
	///
	/// @return the total input size
	public MemorySize totalInputBytes() {
		return NativeFields.getMemorySize(MH_TOTAL_INPUT_BYTES, ptr);
	}

	/// Returns the total size of this compaction's output files.
	///
	/// @return the total output size
	public MemorySize totalOutputBytes() {
		return NativeFields.getMemorySize(MH_TOTAL_OUTPUT_BYTES, ptr);
	}

	/// Returns the number of input files, counted the same way as [#inputFilesCount()].
	///
	/// @return the number of input files
	public long numInputFiles() {
		return NativeFields.getLong(MH_NUM_INPUT_FILES, ptr);
	}

	/// Returns the number of input files that were at the output level already.
	///
	/// @return the number of input files at the output level
	public long numInputFilesAtOutputLevel() {
		return NativeFields.getLong(MH_NUM_INPUT_FILES_AT_OUTPUT_LEVEL, ptr);
	}
}
