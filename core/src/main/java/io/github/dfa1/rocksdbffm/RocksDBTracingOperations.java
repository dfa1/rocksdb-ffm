package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

/// Shared tracing/replay operations for every wrapper around a plain `rocksdb_t*`, including
/// [TransactionDB] (reached through its base DB pointer, see
/// [NativeDBWithBaseDB#dbPtr()]). Implemented directly, alongside [RocksDBReadOperations]
/// and/or [RocksDBWriteOperations] where applicable, by the same set of types as
/// [RocksDBMonitoringOperations]: `ReadWriteDB`, `TtlDB`, `OptimisticTransactionDB`,
/// `ReadOnlyDB`, `SecondaryDB`, `TransactionDB`.
///
/// A separate interface rather than folded into [RocksDBWriteOperations]: `rocksdb_start_trace`/
/// `rocksdb_end_trace` operate on the plain `rocksdb_t*` and capture reads as well as writes,
/// so tracing is available on read-only and secondary handles too, not just write-capable ones
/// — the same reasoning that keeps [RocksDBMonitoringOperations] separate.
///
/// [#startIoTrace]/[#endIoTrace] capture file I/O instead of database operations, and
/// [#startBlockCacheTrace]/[#endBlockCacheTrace] capture block cache accesses (hit/miss, block
/// type, caller) -- both are independent trace streams that can run alongside [#startTrace].
public interface RocksDBTracingOperations {

	/// Returns the native `rocksdb_t*` pointer to operate on.
	///
	/// @return the native database pointer
	MemorySegment dbPtr();

	/// Starts capturing every read/write operation on this database to a trace file at
	/// `tracePath`, using the default [Env] (`Env::Default()`) and default [EnvOptions] to open
	/// the trace file. Call [#endTrace()] to stop.
	///
	/// @param tracePath    file to write the trace to; must not already exist
	/// @param traceOptions options controlling sampling rate, filter, and rollover size
	default void startTrace(Path tracePath, TraceOptions traceOptions) {
		RocksDBTracingOperationsBindings.startTrace(this, tracePath, traceOptions);
	}

	/// [#startTrace(Path, TraceOptions)] with an explicit [Env]/[EnvOptions] pair, e.g. to
	/// trace into [Env#memEnv()] in tests or tune the trace file's I/O behavior. Both remain
	/// owned by the caller -- RocksDB only reads them while opening the trace file and does not
	/// retain them afterward.
	///
	/// @param tracePath    file to write the trace to; must not already exist
	/// @param env          environment used to open the trace file
	/// @param envOptions   file-I/O tuning for the trace file
	/// @param traceOptions options controlling sampling rate, filter, and rollover size
	default void startTrace(Path tracePath, Env env, EnvOptions envOptions, TraceOptions traceOptions) {
		RocksDBTracingOperationsBindings.startTrace(this, tracePath, env, envOptions, traceOptions);
	}

	/// Stops a trace started with [#startTrace].
	default void endTrace() {
		RocksDBTracingOperationsBindings.endTrace(this);
	}

	/// Starts capturing every file I/O operation (as opposed to [#startTrace], which captures
	/// database-level reads/writes) to a trace file at `tracePath`, using the default
	/// [Env]/[EnvOptions] pair. Call [#endIoTrace()] to stop.
	///
	/// @param tracePath    file to write the trace to; must not already exist
	/// @param traceOptions options controlling sampling rate, filter, and rollover size
	default void startIoTrace(Path tracePath, TraceOptions traceOptions) {
		RocksDBTracingOperationsBindings.startIoTrace(this, tracePath, traceOptions);
	}

	/// [#startIoTrace(Path, TraceOptions)] with an explicit [Env]/[EnvOptions] pair. Both remain
	/// owned by the caller -- RocksDB only reads them while opening the trace file and does not
	/// retain them afterward.
	///
	/// @param tracePath    file to write the trace to; must not already exist
	/// @param env          environment used to open the trace file
	/// @param envOptions   file-I/O tuning for the trace file
	/// @param traceOptions options controlling sampling rate, filter, and rollover size
	default void startIoTrace(Path tracePath, Env env, EnvOptions envOptions, TraceOptions traceOptions) {
		RocksDBTracingOperationsBindings.startIoTrace(this, tracePath, env, envOptions, traceOptions);
	}

	/// Stops a trace started with [#startIoTrace].
	default void endIoTrace() {
		RocksDBTracingOperationsBindings.endIoTrace(this);
	}

	/// Starts capturing every block cache access (hit/miss, block type, caller, key) to a trace
	/// file at `tracePath`, using [TraceOptions] and the default [Env]/[EnvOptions] pair. Call
	/// [#endBlockCacheTrace()] to stop.
	///
	/// @param tracePath    file to write the trace to; must not already exist
	/// @param traceOptions options controlling sampling rate, filter, and rollover size
	default void startBlockCacheTrace(Path tracePath, TraceOptions traceOptions) {
		RocksDBTracingOperationsBindings.startBlockCacheTrace(this, tracePath, traceOptions);
	}

	/// [#startBlockCacheTrace(Path, TraceOptions)] with an explicit [Env]/[EnvOptions] pair. Both
	/// remain owned by the caller -- RocksDB only reads them while opening the trace file and does
	/// not retain them afterward.
	///
	/// @param tracePath    file to write the trace to; must not already exist
	/// @param env          environment used to open the trace file
	/// @param envOptions   file-I/O tuning for the trace file
	/// @param traceOptions options controlling sampling rate, filter, and rollover size
	default void startBlockCacheTrace(Path tracePath, Env env, EnvOptions envOptions, TraceOptions traceOptions) {
		RocksDBTracingOperationsBindings.startBlockCacheTrace(this, tracePath, env, envOptions, traceOptions);
	}

	/// [#startBlockCacheTrace(Path, TraceOptions)] using [BlockCacheTraceOptions] (sampling only)
	/// and [BlockCacheTraceWriterOptions] (rollover size) instead of the shared [TraceOptions]
	/// shape, using the default [Env]/[EnvOptions] pair.
	///
	/// @param tracePath     file to write the trace to; must not already exist
	/// @param traceOptions  options controlling block cache access sampling rate
	/// @param writerOptions options controlling the trace file's rollover size
	default void startBlockCacheTrace(Path tracePath,
			BlockCacheTraceOptions traceOptions,
			BlockCacheTraceWriterOptions writerOptions) {
		RocksDBTracingOperationsBindings.startBlockCacheTrace(this, tracePath, traceOptions, writerOptions);
	}

	/// [#startBlockCacheTrace(Path, BlockCacheTraceOptions, BlockCacheTraceWriterOptions)] with an
	/// explicit [Env]/[EnvOptions] pair. Both remain owned by the caller -- RocksDB only reads
	/// them while opening the trace file and does not retain them afterward.
	///
	/// @param tracePath     file to write the trace to; must not already exist
	/// @param env           environment used to open the trace file
	/// @param envOptions    file-I/O tuning for the trace file
	/// @param traceOptions  options controlling block cache access sampling rate
	/// @param writerOptions options controlling the trace file's rollover size
	default void startBlockCacheTrace(Path tracePath,
			Env env,
			EnvOptions envOptions,
			BlockCacheTraceOptions traceOptions,
			BlockCacheTraceWriterOptions writerOptions) {
		RocksDBTracingOperationsBindings.startBlockCacheTrace(this, tracePath, env, envOptions, traceOptions,
				writerOptions);
	}

	/// Stops a trace started with either `startBlockCacheTrace` overload.
	default void endBlockCacheTrace() {
		RocksDBTracingOperationsBindings.endBlockCacheTrace(this);
	}
}
