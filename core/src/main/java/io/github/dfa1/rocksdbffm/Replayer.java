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
/// Replays a trace file captured via [RocksDBTracingOperations#startTrace] against a target
/// database, reissuing each recorded operation in order, subject to
/// [ReplayOptions#setFastForward] timing and [ReplayOptions#setNumThreads] concurrency.
/// Typically opened against a *different* database than the one that produced the trace --
/// e.g. replaying captured production traffic against a candidate build to validate a change
/// before rollout.
///
/// ```
/// try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(candidatePath, dbOpts);
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

	/// The database trace records are replayed into; the replayer keeps raw pointers to it, so it is released
	/// before the database is.
	private final NativeObjectWithChildren owningDb;

	/// The column families this replayer replays into. The native replayer keeps raw pointers to their handles, which
	/// [NativeDB#forget(ColumnFamilyHandle)] frees when one is dropped.
	private final List<ColumnFamilyHandle> columnFamilies;

	private Replayer(NativeObjectWithChildren owningDb, List<ColumnFamilyHandle> columnFamilies, MemorySegment ptr) {
		super(ptr);
		this.owningDb = owningDb;
		this.columnFamilies = columnFamilies;
		owningDb.registerChild(this);
	}

	/// Returns [#ptr()] for a call that replays into the column families, after checking that none was dropped.
	///
	/// @return the native replayer pointer
	/// @throws IllegalStateException if this replayer is closed or one of its column families was dropped
	private MemorySegment openPtr() {
		MemorySegment ptr = ptr();
		for (ColumnFamilyHandle cf : columnFamilies) {
			cf.ptr();
		}
		return ptr;
	}

	/// Opens `tracePath` for replay against `db`'s default column family, using the default
	/// [Env] (`Env::Default()`) and default [EnvOptions] to open the trace file.
	///
	/// @param db        target database to replay operations against
	/// @param tracePath trace file previously written by [RocksDBTracingOperations#startTrace]
	/// @return a new [Replayer]; caller must close it
	public static Replayer create(RocksDBWriteOperations db, Path tracePath) {
		return create(db, tracePath, List.of(), Env.DEFAULTS, EnvOptions.DEFAULTS);
	}

	/// [#create(RocksDBWriteOperations, Path)] against specific column families, with an
	/// explicit [Env]/[EnvOptions] pair (e.g. [Env#memEnv()] in tests). The env/envOptions pair
	/// remains owned by the caller -- RocksDB only reads it while opening the trace file. The
	/// replayer does keep raw pointers to the `cfs` handles: dropping one of them makes
	/// [#prepare()] and [#replay(ReplayOptions)] throw [IllegalStateException].
	///
	/// @param db         target database to replay operations against
	/// @param tracePath  trace file previously written by [RocksDBTracingOperations#startTrace]
	/// @param cfs        column families to replay against; empty means the default column
	///                   family only
	/// @param env        environment used to open the trace file
	/// @param envOptions file-I/O tuning for reading the trace file
	/// @return a new [Replayer]; caller must close it
	public static Replayer create(RocksDBWriteOperations db, Path tracePath, List<ColumnFamilyHandle> cfs, Env env,
			EnvOptions envOptions) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment cfArr = arena.allocate(ValueLayout.ADDRESS, cfs.size());
			for (int i = 0; i < cfs.size(); i++) {
				cfArr.setAtIndex(ValueLayout.ADDRESS, i, cfs.get(i).ptr());
			}
			MemorySegment pathSeg = arena.allocateFrom(tracePath.toString());
			MemorySegment ptr = (MemorySegment) MH_CREATE.invokeExact(
					db.dbPtr(), cfArr, (long) cfs.size(), env.ptr(), envOptions.ptr(), pathSeg, err);
			NativeCalls.checkError(err);
			return new Replayer((NativeObjectWithChildren) db, List.copyOf(cfs), ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("Replayer.create failed", t);
		}
	}

	/// Reads and validates the trace file's header. Must be called once, before the first
	/// [#replay(ReplayOptions)].
	///
	/// @throws IllegalStateException if this replayer is closed or one of its column families was dropped
	public void prepare() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_PREPARE.invokeExact(openPtr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("prepare failed", t);
		}
	}

	/// Returns the timestamp recorded in the trace file's header -- when the original
	/// [RocksDBTracingOperations#startTrace] capture began.
	///
	/// @return capture start time
	public Instant getHeaderTimestamp() {
		long micros = NativeFields.getLong(MH_GET_HEADER_TIMESTAMP, ptr());
		return Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
	}

	/// Reissues every operation recorded in the trace file against the target database,
	/// according to `options`. Blocks until replay completes.
	///
	/// @param options concurrency and timing controls for playback
	/// @throws IllegalStateException if this replayer is closed or one of its column families was dropped
	public void replay(ReplayOptions options) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_REPLAY.invokeExact(openPtr(), options.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("replay failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		owningDb.unregisterChild(this);
		MH_DESTROY.invokeExact(ptr);
	}
}
