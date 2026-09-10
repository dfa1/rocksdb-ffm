package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_block_cache_trace_writer_options_t`.
///
/// Controls trace-file rollover for
/// [RocksDBTracingOperations#startBlockCacheTrace(BlockCacheTraceOptions, BlockCacheTraceWriterOptions, java.nio.file.Path)].
/// May be closed immediately after being passed to `startBlockCacheTrace` -- RocksDB copies the
/// underlying struct by value.
public final class BlockCacheTraceWriterOptions extends NativeObject {

	/// `rocksdb_block_cache_trace_writer_options_t* rocksdb_block_cache_trace_writer_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_block_cache_trace_writer_options_destroy(rocksdb_block_cache_trace_writer_options_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_block_cache_trace_writer_options_set_max_trace_file_size(rocksdb_block_cache_trace_writer_options_t*, uint64_t);`
	private static final MethodHandle MH_SET_MAX_TRACE_FILE_SIZE;
	/// `uint64_t rocksdb_block_cache_trace_writer_options_get_max_trace_file_size(rocksdb_block_cache_trace_writer_options_t*);`
	private static final MethodHandle MH_GET_MAX_TRACE_FILE_SIZE;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_block_cache_trace_writer_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_block_cache_trace_writer_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_MAX_TRACE_FILE_SIZE = NativeLibrary.lookup(
				"rocksdb_block_cache_trace_writer_options_set_max_trace_file_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_TRACE_FILE_SIZE = NativeLibrary.lookup(
				"rocksdb_block_cache_trace_writer_options_get_max_trace_file_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
	}

	private BlockCacheTraceWriterOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates block cache trace writer options with RocksDB defaults: 64 GB rollover.
	///
	/// @return a new instance; caller must close it
	public static BlockCacheTraceWriterOptions newBlockCacheTraceWriterOptions() {
		try {
			return new BlockCacheTraceWriterOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("BlockCacheTraceWriterOptions create failed", t);
		}
	}

	/// Caps the trace file at `size`; RocksDB stops recording further block cache accesses once
	/// the file reaches this size. Default: 64 GB.
	///
	/// @param size maximum trace file size
	/// @return `this` for chaining
	public BlockCacheTraceWriterOptions setMaxTraceFileSize(MemorySize size) {
		NativeFields.setMemorySize(MH_SET_MAX_TRACE_FILE_SIZE, ptr(), size);
		return this;
	}

	/// Returns the configured maximum trace file size.
	///
	/// @return current maximum trace file size
	public MemorySize getMaxTraceFileSize() {
		return NativeFields.getMemorySize(MH_GET_MAX_TRACE_FILE_SIZE, ptr());
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
