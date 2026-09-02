package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_replay_options_t`.
///
/// Controls [Replayer#replay(ReplayOptions)] playback: how many threads reissue operations
/// concurrently, and how much to compress or stretch the original capture timing.
///
/// ```
/// try (var replayer = Replayer.create(db, tracePath);
///      var opts = ReplayOptions.newReplayOptions().setNumThreads(4).setFastForward(2.0)) {
///     replayer.prepare();
///     replayer.replay(opts);
/// }
/// ```
public final class ReplayOptions extends AbstractOptions {

	/// `rocksdb_replay_options_t* rocksdb_replay_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_replay_options_destroy(rocksdb_replay_options_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_replay_options_set_num_threads(rocksdb_replay_options_t*, uint32_t);`
	private static final MethodHandle MH_SET_NUM_THREADS;
	/// `uint32_t rocksdb_replay_options_get_num_threads(rocksdb_replay_options_t*);`
	private static final MethodHandle MH_GET_NUM_THREADS;
	/// `void rocksdb_replay_options_set_fast_forward(rocksdb_replay_options_t*, double);`
	private static final MethodHandle MH_SET_FAST_FORWARD;
	/// `double rocksdb_replay_options_get_fast_forward(rocksdb_replay_options_t*);`
	private static final MethodHandle MH_GET_FAST_FORWARD;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_replay_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_replay_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_NUM_THREADS = NativeLibrary.lookup("rocksdb_replay_options_set_num_threads",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_NUM_THREADS = NativeLibrary.lookup("rocksdb_replay_options_get_num_threads",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_FAST_FORWARD = NativeLibrary.lookup("rocksdb_replay_options_set_fast_forward",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_FAST_FORWARD = NativeLibrary.lookup("rocksdb_replay_options_get_fast_forward",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
	}

	private ReplayOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates replay options with RocksDB defaults: single-threaded, original capture timing
	/// (`fastForward` of `1.0`).
	///
	/// @return a new instance; caller must close it
	public static ReplayOptions newReplayOptions() {
		try {
			return new ReplayOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("ReplayOptions create failed", t);
		}
	}

	/// Number of threads used to issue replayed operations concurrently. Default: `1`.
	///
	/// @param numThreads number of replay threads
	/// @return `this` for chaining
	public ReplayOptions setNumThreads(int numThreads) {
		setInt(MH_SET_NUM_THREADS, numThreads);
		return this;
	}

	/// Returns the configured number of replay threads.
	///
	/// @return current number of replay threads
	public int getNumThreads() {
		return getInt(MH_GET_NUM_THREADS);
	}

	/// Speed multiplier applied to the original capture timing: `2.0` replays twice as fast as
	/// captured, `0.5` replays at half speed. Default: `1.0` (original speed).
	///
	/// @param fastForward speed multiplier; must be positive
	/// @return `this` for chaining
	public ReplayOptions setFastForward(double fastForward) {
		setDouble(MH_SET_FAST_FORWARD, fastForward);
		return this;
	}

	/// Returns the configured speed multiplier.
	///
	/// @return current speed multiplier
	public double getFastForward() {
		return getDouble(MH_GET_FAST_FORWARD);
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
