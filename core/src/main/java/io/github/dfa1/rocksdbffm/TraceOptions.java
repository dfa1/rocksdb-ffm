package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Set;

/// FFM wrapper for `rocksdb_trace_options_t`.
///
/// Controls [RocksDBTracingOperations#startTrace] capture behavior: rollover size, sampling
/// rate, which operation types to exclude, and write-order preservation. May be closed
/// immediately after being passed to `startTrace` -- RocksDB copies the underlying struct by
/// value.
///
/// ```
/// try (var opts = TraceOptions.newTraceOptions()
///             .setSamplingFrequency(10)
///             .setFilter(Set.of(TraceFilter.ITERATOR_SEEK, TraceFilter.ITERATOR_SEEK_FOR_PREV))) {
///     db.startTrace(opts, tracePath);
///     // ... workload runs while trace is captured ...
///     db.endTrace();
/// }
/// ```
public final class TraceOptions extends AbstractOptions {

	/// `rocksdb_trace_options_t* rocksdb_trace_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_trace_options_destroy(rocksdb_trace_options_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_trace_options_set_max_trace_file_size(rocksdb_trace_options_t*, uint64_t);`
	private static final MethodHandle MH_SET_MAX_TRACE_FILE_SIZE;
	/// `uint64_t rocksdb_trace_options_get_max_trace_file_size(rocksdb_trace_options_t*);`
	private static final MethodHandle MH_GET_MAX_TRACE_FILE_SIZE;
	/// `void rocksdb_trace_options_set_sampling_frequency(rocksdb_trace_options_t*, uint64_t);`
	private static final MethodHandle MH_SET_SAMPLING_FREQUENCY;
	/// `uint64_t rocksdb_trace_options_get_sampling_frequency(rocksdb_trace_options_t*);`
	private static final MethodHandle MH_GET_SAMPLING_FREQUENCY;
	/// `void rocksdb_trace_options_set_filter(rocksdb_trace_options_t*, uint64_t);`
	private static final MethodHandle MH_SET_FILTER;
	/// `uint64_t rocksdb_trace_options_get_filter(rocksdb_trace_options_t*);`
	private static final MethodHandle MH_GET_FILTER;
	/// `void rocksdb_trace_options_set_preserve_write_order(rocksdb_trace_options_t*, unsigned char);`
	private static final MethodHandle MH_SET_PRESERVE_WRITE_ORDER;
	/// `unsigned char rocksdb_trace_options_get_preserve_write_order(rocksdb_trace_options_t*);`
	private static final MethodHandle MH_GET_PRESERVE_WRITE_ORDER;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_trace_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_trace_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_MAX_TRACE_FILE_SIZE = NativeLibrary.lookup("rocksdb_trace_options_set_max_trace_file_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_MAX_TRACE_FILE_SIZE = NativeLibrary.lookup("rocksdb_trace_options_get_max_trace_file_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_SAMPLING_FREQUENCY = NativeLibrary.lookup("rocksdb_trace_options_set_sampling_frequency",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_SAMPLING_FREQUENCY = NativeLibrary.lookup("rocksdb_trace_options_get_sampling_frequency",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_FILTER = NativeLibrary.lookup("rocksdb_trace_options_set_filter",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_FILTER = NativeLibrary.lookup("rocksdb_trace_options_get_filter",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_PRESERVE_WRITE_ORDER = NativeLibrary.lookup("rocksdb_trace_options_set_preserve_write_order",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_PRESERVE_WRITE_ORDER = NativeLibrary.lookup("rocksdb_trace_options_get_preserve_write_order",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));
	}

	private TraceOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates trace options with RocksDB defaults: 64 GB rollover, sampling frequency `1`
	/// (capture every operation), no filter (every operation type is traced), write order not
	/// preserved.
	///
	/// @return a new instance; caller must close it
	public static TraceOptions newTraceOptions() {
		try {
			return new TraceOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("TraceOptions create failed", t);
		}
	}

	/// Caps the trace file at `size`; RocksDB stops recording further operations once the file
	/// reaches this size. Default: 64 GB.
	///
	/// @param size maximum trace file size
	/// @return `this` for chaining
	public TraceOptions setMaxTraceFileSize(MemorySize size) {
		setMemorySize(MH_SET_MAX_TRACE_FILE_SIZE, size);
		return this;
	}

	/// Returns the configured maximum trace file size.
	///
	/// @return current maximum trace file size
	public MemorySize getMaxTraceFileSize() {
		return getMemorySize(MH_GET_MAX_TRACE_FILE_SIZE);
	}

	/// Captures one operation out of every `frequency`, evaluated after [#setFilter(Set)]
	/// exclusions are applied. Default: `1` (capture every operation).
	///
	/// @param frequency sampling frequency; must be at least `1`
	/// @return `this` for chaining
	public TraceOptions setSamplingFrequency(long frequency) {
		setLong(MH_SET_SAMPLING_FREQUENCY, frequency);
		return this;
	}

	/// Returns the configured sampling frequency.
	///
	/// @return current sampling frequency
	public long getSamplingFrequency() {
		return getLong(MH_GET_SAMPLING_FREQUENCY);
	}

	/// Excludes the given operation types from the trace, evaluated before
	/// [#setSamplingFrequency(long)] sampling. Default: empty (every operation type is traced).
	///
	/// @param filters operation types to exclude
	/// @return `this` for chaining
	public TraceOptions setFilter(Set<TraceFilter> filters) {
		try {
			MH_SET_FILTER.invokeExact(ptr(), TraceFilter.toMask(filters));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setFilter failed", t);
		}
		return this;
	}

	/// Returns the configured set of excluded operation types.
	///
	/// @return current filter set; empty means every operation type is traced
	public Set<TraceFilter> getFilter() {
		try {
			return TraceFilter.fromMask((long) MH_GET_FILTER.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getFilter failed", t);
		}
	}

	/// If `true`, write records in the trace preserve the WAL's exact order, at some
	/// performance cost. Default: `false` (write records may be reordered relative to the WAL).
	///
	/// @param value `true` to preserve write order
	/// @return `this` for chaining
	public TraceOptions setPreserveWriteOrder(boolean value) {
		setBoolean(MH_SET_PRESERVE_WRITE_ORDER, value);
		return this;
	}

	/// Returns whether write order is preserved.
	///
	/// @return `true` if write order is preserved
	public boolean getPreserveWriteOrder() {
		return getBoolean(MH_GET_PRESERVE_WRITE_ORDER);
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
