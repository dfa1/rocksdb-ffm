package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/// FFM wrapper for `rocksdb_replayer_t`.
///
/// Replays a trace file captured via [RocksDBWriteOperations#startTrace] against a target
/// database, reissuing each recorded operation in order, subject to
/// [ReplayOptions#setFastForward] timing and [ReplayOptions#setNumThreads] concurrency.
/// Typically opened against a *different* database than the one that produced the trace --
/// e.g. replaying captured production traffic against a candidate build to validate a change
/// before rollout.
///
/// ```
/// try (var db = RocksDB.openReadWrite(candidatePath);
///      var replayer = Replayer.create(db, tracePath)) {
///     replayer.prepare();
///     replayer.replay(ReplayOptions.newReplayOptions());
/// }
/// ```
public final class Replayer extends NativeObject {

	/// `rocksdb_replayer_t* rocksdb_new_default_replayer(rocksdb_t* db, rocksdb_column_family_handle_t** column_families, size_t num_column_families, rocksdb_env_t* env, const rocksdb_envoptions_t* env_options, const char* trace_path, char** errptr);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_replayer_prepare(rocksdb_replayer_t*, char** errptr);`
	private static final MethodHandle MH_PREPARE;
	/// `uint64_t rocksdb_replayer_get_header_timestamp(const rocksdb_replayer_t*);`
	private static final MethodHandle MH_GET_HEADER_TIMESTAMP;
	/// `void rocksdb_replayer_replay(rocksdb_replayer_t*, const rocksdb_replay_options_t*, char** errptr);`
	private static final MethodHandle MH_REPLAY;
	/// `void rocksdb_replayer_destroy(rocksdb_replayer_t*);`
	private static final MethodHandle MH_DESTROY;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_new_default_replayer",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS,   // db
						ValueLayout.ADDRESS,   // column_families
						ValueLayout.JAVA_LONG, // num_column_families
						ValueLayout.ADDRESS,   // env
						ValueLayout.ADDRESS,   // env_options
						ValueLayout.ADDRESS,   // trace_path
						ValueLayout.ADDRESS)); // errptr

		MH_PREPARE = NativeLibrary.lookup("rocksdb_replayer_prepare",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_GET_HEADER_TIMESTAMP = NativeLibrary.lookup("rocksdb_replayer_get_header_timestamp",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_REPLAY = NativeLibrary.lookup("rocksdb_replayer_replay",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_replayer_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	private Replayer(MemorySegment ptr) {
		super(ptr);
	}

	/// Opens `tracePath` for replay against `db`'s default column family, using a temporary
	/// default [Env]/[EnvOptions] pair (closed before this method returns -- RocksDB only needs
	/// them to open the trace file, not afterward).
	///
	/// @param db        target database to replay operations against
	/// @param tracePath trace file previously written by [RocksDBWriteOperations#startTrace]
	/// @return a new [Replayer]; caller must close it
	public static Replayer create(RocksDBWriteOperations db, Path tracePath) {
		try (Env env = Env.defaultEnv(); EnvOptions envOptions = EnvOptions.newEnvOptions()) {
			return create(db, List.of(), env, envOptions, tracePath);
		}
	}

	/// [#create(RocksDBWriteOperations, Path)] against specific column families, with an
	/// explicit [Env]/[EnvOptions] pair (e.g. [Env#memEnv()] in tests). Both `cfs` and the
	/// env/envOptions pair remain owned by the caller -- RocksDB only reads them while opening
	/// the trace file and does not retain them afterward.
	///
	/// @param db         target database to replay operations against
	/// @param cfs        column families to replay against; empty means the default column
	///                   family only
	/// @param env        environment used to open the trace file
	/// @param envOptions file-I/O tuning for reading the trace file
	/// @param tracePath  trace file previously written by [RocksDBWriteOperations#startTrace]
	/// @return a new [Replayer]; caller must close it
	public static Replayer create(RocksDBWriteOperations db, List<ColumnFamilyHandle> cfs, Env env,
			EnvOptions envOptions, Path tracePath) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MemorySegment cfArr = arena.allocate(ValueLayout.ADDRESS, cfs.size());
			for (int i = 0; i < cfs.size(); i++) {
				cfArr.setAtIndex(ValueLayout.ADDRESS, i, cfs.get(i).ptr());
			}
			MemorySegment pathSeg = arena.allocateFrom(tracePath.toString());
			MemorySegment ptr = (MemorySegment) MH_CREATE.invokeExact(
					db.dbPtr(), cfArr, (long) cfs.size(), env.ptr(), envOptions.ptr(), pathSeg, err);
			RocksDB.checkError(err);
			return new Replayer(ptr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Replayer.create failed", t);
		}
	}

	/// Reads and validates the trace file's header. Must be called once, before the first
	/// [#replay(ReplayOptions)].
	public void prepare() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_PREPARE.invokeExact(ptr(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("prepare failed", t);
		}
	}

	/// Returns the timestamp recorded in the trace file's header -- when the original
	/// [RocksDBWriteOperations#startTrace] capture began.
	///
	/// @return capture start time
	public Instant getHeaderTimestamp() {
		try {
			long micros = (long) MH_GET_HEADER_TIMESTAMP.invokeExact(ptr());
			return Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getHeaderTimestamp failed", t);
		}
	}

	/// Reissues every operation recorded in the trace file against the target database,
	/// according to `options`. Blocks until replay completes.
	///
	/// @param options concurrency and timing controls for playback
	public void replay(ReplayOptions options) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = RocksDB.errHolder(arena);
			MH_REPLAY.invokeExact(ptr(), options.ptr(), err);
			RocksDB.checkError(err);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("replay failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
