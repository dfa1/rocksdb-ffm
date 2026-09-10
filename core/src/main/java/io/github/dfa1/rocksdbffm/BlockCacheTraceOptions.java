package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_block_cache_trace_options_t`.
///
/// Controls sampling for
/// [RocksDBTracingOperations#startBlockCacheTrace(BlockCacheTraceOptions, BlockCacheTraceWriterOptions, java.nio.file.Path)].
/// May be closed immediately after being passed to `startBlockCacheTrace` -- RocksDB copies the
/// underlying struct by value.
public final class BlockCacheTraceOptions extends NativeObject {

	/// `rocksdb_block_cache_trace_options_t* rocksdb_block_cache_trace_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_block_cache_trace_options_destroy(rocksdb_block_cache_trace_options_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_block_cache_trace_options_set_sampling_frequency(rocksdb_block_cache_trace_options_t*, uint64_t);`
	private static final MethodHandle MH_SET_SAMPLING_FREQUENCY;
	/// `uint64_t rocksdb_block_cache_trace_options_get_sampling_frequency(rocksdb_block_cache_trace_options_t*);`
	private static final MethodHandle MH_GET_SAMPLING_FREQUENCY;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_block_cache_trace_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_block_cache_trace_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_SAMPLING_FREQUENCY = NativeLibrary.lookup("rocksdb_block_cache_trace_options_set_sampling_frequency",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_SAMPLING_FREQUENCY = NativeLibrary.lookup("rocksdb_block_cache_trace_options_get_sampling_frequency",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
	}

	private BlockCacheTraceOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates block cache trace options with RocksDB defaults: sampling frequency `1` (capture
	/// every block cache access).
	///
	/// @return a new instance; caller must close it
	public static BlockCacheTraceOptions newBlockCacheTraceOptions() {
		try {
			return new BlockCacheTraceOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("BlockCacheTraceOptions create failed", t);
		}
	}

	/// Captures one block cache access out of every `frequency`. Default: `1` (capture every
	/// access).
	///
	/// @param frequency sampling frequency; must be at least `1`
	/// @return `this` for chaining
	public BlockCacheTraceOptions setSamplingFrequency(long frequency) {
		NativeFields.setLong(MH_SET_SAMPLING_FREQUENCY, ptr(), frequency);
		return this;
	}

	/// Returns the configured sampling frequency.
	///
	/// @return current sampling frequency
	public long getSamplingFrequency() {
		return NativeFields.getLong(MH_GET_SAMPLING_FREQUENCY, ptr());
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
