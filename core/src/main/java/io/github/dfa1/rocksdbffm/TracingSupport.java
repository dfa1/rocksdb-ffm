package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/// FFM plumbing for [RocksDBTracingOperations]'s default methods -- kept out of [RocksDB] itself
/// so that class doesn't also have to hold every feature area's `MH_` fields. Not an
/// `interface`-nested holder: interface fields are implicitly `public static final`, so a
/// `private static final MethodHandle` (required by CLAUDE.md) cannot live directly on
/// `RocksDBTracingOperations` -- this package-private top-level class is the closest equivalent.
final class TracingSupport {

	/// `void rocksdb_start_trace(rocksdb_t* db, rocksdb_env_t* env, const rocksdb_envoptions_t* env_options, const rocksdb_trace_options_t* options, const char* trace_path, char** errptr);`
	private static final MethodHandle MH_START_TRACE;
	/// `void rocksdb_end_trace(rocksdb_t* db, char** errptr);`
	private static final MethodHandle MH_END_TRACE;
	/// `void rocksdb_start_io_trace(rocksdb_t* db, rocksdb_env_t* env, const rocksdb_envoptions_t* env_options, const rocksdb_trace_options_t* options, const char* trace_path, char** errptr);`
	private static final MethodHandle MH_START_IO_TRACE;
	/// `void rocksdb_end_io_trace(rocksdb_t* db, char** errptr);`
	private static final MethodHandle MH_END_IO_TRACE;
	/// `void rocksdb_start_block_cache_trace(rocksdb_t* db, rocksdb_env_t* env, const rocksdb_envoptions_t* env_options, const rocksdb_trace_options_t* options, const char* trace_path, char** errptr);`
	private static final MethodHandle MH_START_BLOCK_CACHE_TRACE;
	/// `void rocksdb_start_block_cache_trace_with_options(rocksdb_t* db, rocksdb_env_t* env, const rocksdb_envoptions_t* env_options, const rocksdb_block_cache_trace_options_t* options, const rocksdb_block_cache_trace_writer_options_t* writer_options, const char* trace_path, char** errptr);`
	private static final MethodHandle MH_START_BLOCK_CACHE_TRACE_WITH_OPTIONS;
	/// `void rocksdb_end_block_cache_trace(rocksdb_t* db, char** errptr);`
	private static final MethodHandle MH_END_BLOCK_CACHE_TRACE;

	static {
		MH_START_TRACE = NativeLibrary.lookup("rocksdb_start_trace",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,  // db
						ValueLayout.ADDRESS,  // env
						ValueLayout.ADDRESS,  // env_options
						ValueLayout.ADDRESS,  // options
						ValueLayout.ADDRESS,  // trace_path
						ValueLayout.ADDRESS)); // errptr

		MH_END_TRACE = NativeLibrary.lookup("rocksdb_end_trace",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_START_IO_TRACE = NativeLibrary.lookup("rocksdb_start_io_trace",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,  // db
						ValueLayout.ADDRESS,  // env
						ValueLayout.ADDRESS,  // env_options
						ValueLayout.ADDRESS,  // options
						ValueLayout.ADDRESS,  // trace_path
						ValueLayout.ADDRESS)); // errptr

		MH_END_IO_TRACE = NativeLibrary.lookup("rocksdb_end_io_trace",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_START_BLOCK_CACHE_TRACE = NativeLibrary.lookup("rocksdb_start_block_cache_trace",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,  // db
						ValueLayout.ADDRESS,  // env
						ValueLayout.ADDRESS,  // env_options
						ValueLayout.ADDRESS,  // options
						ValueLayout.ADDRESS,  // trace_path
						ValueLayout.ADDRESS)); // errptr

		MH_START_BLOCK_CACHE_TRACE_WITH_OPTIONS = NativeLibrary.lookup(
				"rocksdb_start_block_cache_trace_with_options",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,  // db
						ValueLayout.ADDRESS,  // env
						ValueLayout.ADDRESS,  // env_options
						ValueLayout.ADDRESS,  // options
						ValueLayout.ADDRESS,  // writer_options
						ValueLayout.ADDRESS,  // trace_path
						ValueLayout.ADDRESS)); // errptr

		MH_END_BLOCK_CACHE_TRACE = NativeLibrary.lookup("rocksdb_end_block_cache_trace",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private TracingSupport() {
	}

	/// [#startTrace(RocksDBTracingOperations, Env, EnvOptions, TraceOptions, Path)] using a
	/// temporary default [Env]/[EnvOptions] pair, closed before this method returns.
	static void startTrace(RocksDBTracingOperations db, TraceOptions traceOptions, Path tracePath) {
		try (Env env = Env.defaultEnv(); EnvOptions envOptions = EnvOptions.newEnvOptions()) {
			startTrace(db, env, envOptions, traceOptions, tracePath);
		}
	}

	static void startTrace(RocksDBTracingOperations db, Env env, EnvOptions envOptions,
			TraceOptions traceOptions, Path tracePath) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(tracePath.toString());
			MH_START_TRACE.invokeExact(db.dbPtr(), env.ptr(), envOptions.ptr(), traceOptions.ptr(), pathSeg, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("startTrace failed", t);
		}
	}

	static void endTrace(RocksDBTracingOperations db) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_END_TRACE.invokeExact(db.dbPtr(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("endTrace failed", t);
		}
	}

	/// [#startIoTrace(RocksDBTracingOperations, Env, EnvOptions, TraceOptions, Path)] using a
	/// temporary default [Env]/[EnvOptions] pair, closed before this method returns.
	static void startIoTrace(RocksDBTracingOperations db, TraceOptions traceOptions, Path tracePath) {
		try (Env env = Env.defaultEnv(); EnvOptions envOptions = EnvOptions.newEnvOptions()) {
			startIoTrace(db, env, envOptions, traceOptions, tracePath);
		}
	}

	static void startIoTrace(RocksDBTracingOperations db, Env env, EnvOptions envOptions,
			TraceOptions traceOptions, Path tracePath) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(tracePath.toString());
			MH_START_IO_TRACE.invokeExact(db.dbPtr(), env.ptr(), envOptions.ptr(), traceOptions.ptr(), pathSeg, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("startIoTrace failed", t);
		}
	}

	static void endIoTrace(RocksDBTracingOperations db) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_END_IO_TRACE.invokeExact(db.dbPtr(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("endIoTrace failed", t);
		}
	}

	/// [#startBlockCacheTrace(RocksDBTracingOperations, Env, EnvOptions, TraceOptions, Path)]
	/// using a temporary default [Env]/[EnvOptions] pair, closed before this method returns.
	static void startBlockCacheTrace(RocksDBTracingOperations db, TraceOptions traceOptions, Path tracePath) {
		try (Env env = Env.defaultEnv(); EnvOptions envOptions = EnvOptions.newEnvOptions()) {
			startBlockCacheTrace(db, env, envOptions, traceOptions, tracePath);
		}
	}

	static void startBlockCacheTrace(RocksDBTracingOperations db, Env env, EnvOptions envOptions,
			TraceOptions traceOptions, Path tracePath) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(tracePath.toString());
			MH_START_BLOCK_CACHE_TRACE.invokeExact(db.dbPtr(), env.ptr(), envOptions.ptr(), traceOptions.ptr(),
					pathSeg, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("startBlockCacheTrace failed", t);
		}
	}

	/// [#startBlockCacheTrace(RocksDBTracingOperations, Env, EnvOptions, BlockCacheTraceOptions,
	/// BlockCacheTraceWriterOptions, Path)] using a temporary default [Env]/[EnvOptions] pair,
	/// closed before this method returns.
	static void startBlockCacheTrace(RocksDBTracingOperations db, BlockCacheTraceOptions traceOptions,
			BlockCacheTraceWriterOptions writerOptions, Path tracePath) {
		try (Env env = Env.defaultEnv(); EnvOptions envOptions = EnvOptions.newEnvOptions()) {
			startBlockCacheTrace(db, env, envOptions, traceOptions, writerOptions, tracePath);
		}
	}

	static void startBlockCacheTrace(RocksDBTracingOperations db, Env env, EnvOptions envOptions,
			BlockCacheTraceOptions traceOptions, BlockCacheTraceWriterOptions writerOptions, Path tracePath) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(tracePath.toString());
			MH_START_BLOCK_CACHE_TRACE_WITH_OPTIONS.invokeExact(db.dbPtr(), env.ptr(), envOptions.ptr(),
					traceOptions.ptr(), writerOptions.ptr(), pathSeg, err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("startBlockCacheTrace failed", t);
		}
	}

	static void endBlockCacheTrace(RocksDBTracingOperations db) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_END_BLOCK_CACHE_TRACE.invokeExact(db.dbPtr(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("endBlockCacheTrace failed", t);
		}
	}
}
