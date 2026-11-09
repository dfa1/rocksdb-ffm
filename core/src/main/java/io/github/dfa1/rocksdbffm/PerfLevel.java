package io.github.dfa1.rocksdbffm;

/// Controls how much instrumentation [PerfContext] collects.
///
/// Set via [PerfContext#setPerfLevel(PerfLevel)]. Higher levels collect more
/// data at the cost of additional overhead.
///
/// Values follow the C++ `enum PerfLevel` in `rocksdb/perf_level.h`, not the `rocksdb_enable_*`
/// constants in `c.h`: those are stale (they predate [#ENABLE_WAIT] and
/// [#ENABLE_TIME_AND_CPU_TIME_EXCEPT_FOR_MUTEX]), while `rocksdb_set_perf_level` casts its
/// argument straight to the C++ enum.
///
/// ```
/// PerfContext.setPerfLevel(PerfLevel.ENABLE_COUNT);
/// try (PerfContext ctx = PerfContext.newPerfContext()) {
///     db.get("key".getBytes());
///     long hits = ctx.metric(PerfMetric.BLOCK_CACHE_HIT_COUNT);
/// }
/// ```
public enum PerfLevel {

	/// Not yet set — behaves like [#DISABLE].
	UNINITIALIZED(0),
	/// All instrumentation disabled.
	DISABLE(1),
	/// Counts only (no timing). Very low overhead. RocksDB's default on every thread.
	ENABLE_COUNT(2),
	/// Counts + time user threads spend blocked waiting on RocksDB itself (`*_wait_*`/`*_delay_*`
	/// metrics), but not end-to-end operation timings.
	ENABLE_WAIT(3),
	/// Counts + wall-clock timings, except for mutex waits.
	ENABLE_TIME_EXCEPT_FOR_MUTEX(4),
	/// [#ENABLE_TIME_EXCEPT_FOR_MUTEX] + CPU-time metrics.
	ENABLE_TIME_AND_CPU_TIME_EXCEPT_FOR_MUTEX(5),
	/// Counts + all timings, including mutex waits.
	ENABLE_TIME(6);

	final int value;

	PerfLevel(int value) {
		this.value = value;
	}

	static PerfLevel fromValue(int value) {
		return switch (value) {
			case 0 -> UNINITIALIZED;
			case 1 -> DISABLE;
			case 2 -> ENABLE_COUNT;
			case 3 -> ENABLE_WAIT;
			case 4 -> ENABLE_TIME_EXCEPT_FOR_MUTEX;
			case 5 -> ENABLE_TIME_AND_CPU_TIME_EXCEPT_FOR_MUTEX;
			case 6 -> ENABLE_TIME;
			default -> throw new IllegalArgumentException("Unknown PerfLevel value: " + value);
		};
	}
}
