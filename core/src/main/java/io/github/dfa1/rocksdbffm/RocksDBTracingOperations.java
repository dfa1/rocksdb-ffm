package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

/// Shared tracing/replay operations for every wrapper around a plain `rocksdb_t*`, including
/// [TransactionDB] (reached through its base DB pointer, see
/// [NativeObjectWithBaseDb#dbPtr()]). Implemented directly, alongside [RocksDBReadOperations]
/// and/or [RocksDBWriteOperations] where applicable, by the same set of types as
/// [RocksDBMonitoringOperations]: `ReadWriteDB`, `TtlDB`, `BlobDB`, `OptimisticTransactionDB`,
/// `ReadOnlyDB`, `SecondaryDB`, `TransactionDB`.
///
/// A separate interface rather than folded into [RocksDBWriteOperations]: `rocksdb_start_trace`/
/// `rocksdb_end_trace` operate on the plain `rocksdb_t*` and capture reads as well as writes,
/// so tracing is available on read-only and secondary handles too, not just write-capable ones
/// — the same reasoning that keeps [RocksDBMonitoringOperations] separate.
public interface RocksDBTracingOperations {

	/// Returns the native `rocksdb_t*` pointer to operate on.
	///
	/// @return the native database pointer
	MemorySegment dbPtr();

	/// Starts capturing every read/write operation on this database to a trace file at
	/// `tracePath`, using a temporary default [Env]/[EnvOptions] pair (closed before this
	/// method returns -- RocksDB only needs them to open the trace file, not afterward). Call
	/// [#endTrace()] to stop.
	///
	/// @param traceOptions options controlling sampling rate, filter, and rollover size
	/// @param tracePath    file to write the trace to; must not already exist
	default void startTrace(TraceOptions traceOptions, Path tracePath) {
		RocksDB.startTrace(this, traceOptions, tracePath);
	}

	/// [#startTrace(TraceOptions, Path)] with an explicit [Env]/[EnvOptions] pair, e.g. to
	/// trace into [Env#memEnv()] in tests or tune the trace file's I/O behavior. Both remain
	/// owned by the caller -- RocksDB only reads them while opening the trace file and does not
	/// retain them afterward.
	///
	/// @param env          environment used to open the trace file
	/// @param envOptions   file-I/O tuning for the trace file
	/// @param traceOptions options controlling sampling rate, filter, and rollover size
	/// @param tracePath    file to write the trace to; must not already exist
	default void startTrace(Env env, EnvOptions envOptions, TraceOptions traceOptions, Path tracePath) {
		RocksDB.startTrace(this, env, envOptions, traceOptions, tracePath);
	}

	/// Stops a trace started with [#startTrace].
	default void endTrace() {
		RocksDB.endTrace(this);
	}
}
