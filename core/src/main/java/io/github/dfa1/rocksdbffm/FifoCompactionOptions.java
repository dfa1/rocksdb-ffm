package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_fifo_compaction_options_t`.
///
/// Attach via [Options#setFifoCompactionOptions(FifoCompactionOptions)] after also setting
/// [Options#setCompactionStyle(Options.CompactionStyle)] to [Options.CompactionStyle#FIFO] --
/// RocksDB ignores these settings under any other compaction style. `FifoCompactionOptions`
/// may be closed once attached; RocksDB copies the underlying struct by value.
///
/// ```
/// try (var fifo = FifoCompactionOptions.newFifoCompactionOptions()
///         .setMaxTableFilesSize(MemorySize.ofGB(1));
///      var opts = Options.newOptions()
///          .setCreateIfMissing(true)
///          .setCompactionStyle(Options.CompactionStyle.FIFO)
///          .setFifoCompactionOptions(fifo)) {
///     ...
/// }
/// ```
public final class FifoCompactionOptions extends AbstractOptions {

	/// `rocksdb_fifo_compaction_options_t* rocksdb_fifo_compaction_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_fifo_compaction_options_destroy(rocksdb_fifo_compaction_options_t* fifo_opts);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_fifo_compaction_options_set_allow_compaction(rocksdb_fifo_compaction_options_t* fifo_opts, unsigned char allow_compaction);`
	private static final MethodHandle MH_SET_ALLOW_COMPACTION;
	/// `unsigned char rocksdb_fifo_compaction_options_get_allow_compaction(rocksdb_fifo_compaction_options_t* fifo_opts);`
	private static final MethodHandle MH_GET_ALLOW_COMPACTION;
	/// `void rocksdb_fifo_compaction_options_set_max_table_files_size(rocksdb_fifo_compaction_options_t* fifo_opts, uint64_t size);`
	private static final MethodHandle MH_SET_MAX_TABLE_FILES_SIZE;
	/// `uint64_t rocksdb_fifo_compaction_options_get_max_table_files_size(rocksdb_fifo_compaction_options_t* fifo_opts);`
	private static final MethodHandle MH_GET_MAX_TABLE_FILES_SIZE;
	/// `void rocksdb_fifo_compaction_options_set_max_data_files_size(rocksdb_fifo_compaction_options_t* fifo_opts, uint64_t size);`
	private static final MethodHandle MH_SET_MAX_DATA_FILES_SIZE;
	/// `uint64_t rocksdb_fifo_compaction_options_get_max_data_files_size(rocksdb_fifo_compaction_options_t* fifo_opts);`
	private static final MethodHandle MH_GET_MAX_DATA_FILES_SIZE;
	/// `void rocksdb_fifo_compaction_options_set_use_kv_ratio_compaction(rocksdb_fifo_compaction_options_t* fifo_opts, unsigned char use_kv_ratio_compaction);`
	private static final MethodHandle MH_SET_USE_KV_RATIO_COMPACTION;
	/// `unsigned char rocksdb_fifo_compaction_options_get_use_kv_ratio_compaction(rocksdb_fifo_compaction_options_t* fifo_opts);`
	private static final MethodHandle MH_GET_USE_KV_RATIO_COMPACTION;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_fifo_compaction_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_fifo_compaction_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_ALLOW_COMPACTION = NativeLibrary.lookup("rocksdb_fifo_compaction_options_set_allow_compaction",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ALLOW_COMPACTION = NativeLibrary.lookup("rocksdb_fifo_compaction_options_get_allow_compaction",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_MAX_TABLE_FILES_SIZE = NativeLibrary.lookup(
				"rocksdb_fifo_compaction_options_set_max_table_files_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_TABLE_FILES_SIZE = NativeLibrary.lookup(
				"rocksdb_fifo_compaction_options_get_max_table_files_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_MAX_DATA_FILES_SIZE = NativeLibrary.lookup(
				"rocksdb_fifo_compaction_options_set_max_data_files_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_DATA_FILES_SIZE = NativeLibrary.lookup(
				"rocksdb_fifo_compaction_options_get_max_data_files_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_USE_KV_RATIO_COMPACTION = NativeLibrary.lookup(
				"rocksdb_fifo_compaction_options_set_use_kv_ratio_compaction",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_KV_RATIO_COMPACTION = NativeLibrary.lookup(
				"rocksdb_fifo_compaction_options_get_use_kv_ratio_compaction",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));
	}

	private FifoCompactionOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates FIFO compaction options with RocksDB defaults.
	///
	/// @return a new instance; caller must close it
	public static FifoCompactionOptions newFifoCompactionOptions() {
		try {
			return new FifoCompactionOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("fifo_compaction_options create failed", t);
		}
	}

	/// If true, allows compacting the oldest files into a single, larger file once the total
	/// size exceeds [#setMaxTableFilesSize] instead of just dropping the oldest file. Default:
	/// false.
	///
	/// @param value `true` to allow compacting old files together instead of dropping them
	/// @return `this` for chaining
	public FifoCompactionOptions setAllowCompaction(boolean value) {
		setBoolean(MH_SET_ALLOW_COMPACTION, value);
		return this;
	}

	/// Returns whether compacting old files together is allowed.
	///
	/// @return `true` if compacting old files together is allowed
	public boolean getAllowCompaction() {
		return getBoolean(MH_GET_ALLOW_COMPACTION);
	}

	/// Once the total size of all SST files exceeds this, the oldest file is dropped (or, with
	/// [#setAllowCompaction], compacted). Default: unlimited (`0`, FIFO compaction disabled by
	/// size).
	///
	/// @param size maximum total size of all SST files
	/// @return `this` for chaining
	public FifoCompactionOptions setMaxTableFilesSize(MemorySize size) {
		setMemorySize(MH_SET_MAX_TABLE_FILES_SIZE, size);
		return this;
	}

	/// Returns the configured maximum total size of all SST files.
	///
	/// @return current maximum total SST file size
	public MemorySize getMaxTableFilesSize() {
		return getMemorySize(MH_GET_MAX_TABLE_FILES_SIZE);
	}

	/// Upper bound on the total size of data files (excludes metadata like index/filter
	/// blocks) before the oldest file is dropped. Default: unlimited (`0`).
	///
	/// @param size maximum total size of data files
	/// @return `this` for chaining
	public FifoCompactionOptions setMaxDataFilesSize(MemorySize size) {
		setMemorySize(MH_SET_MAX_DATA_FILES_SIZE, size);
		return this;
	}

	/// Returns the configured maximum total size of data files.
	///
	/// @return current maximum total data file size
	public MemorySize getMaxDataFilesSize() {
		return getMemorySize(MH_GET_MAX_DATA_FILES_SIZE);
	}

	/// If true, uses the key-value ratio (rather than raw file size) to decide when to drop
	/// the oldest file. Default: false.
	///
	/// @param value `true` to use the key-value ratio for FIFO compaction decisions
	/// @return `this` for chaining
	public FifoCompactionOptions setUseKvRatioCompaction(boolean value) {
		setBoolean(MH_SET_USE_KV_RATIO_COMPACTION, value);
		return this;
	}

	/// Returns whether the key-value ratio is used for FIFO compaction decisions.
	///
	/// @return `true` if the key-value ratio is used for FIFO compaction decisions
	public boolean getUseKvRatioCompaction() {
		return getBoolean(MH_GET_USE_KV_RATIO_COMPACTION);
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
