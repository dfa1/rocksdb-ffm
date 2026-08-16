package io.github.dfa1.rocksdbffm;

/// Statistics levels for RocksDB.
public enum StatsLevel {
	/// Disable all statistics collection.
	DISABLE_ALL(0),
	/// Collect all statistics except ticker types.
	EXCEPT_TICKERS(0),
	/// Collect all statistics except histograms and timers.
	EXCEPT_HISTOGRAM_OR_TIMERS(1),
	/// Collect all statistics except timer metrics.
	EXCEPT_TIMERS(2),
	/// Collect all statistics except detailed timers.
	EXCEPT_DETAILED_TIMERS(3),
	/// Collect all statistics except mutex wait time.
	EXCEPT_TIME_FOR_MUTEX(4),
	/// Collect all available statistics.
	ALL(5);

	private final int value;

	StatsLevel(int value) {
		this.value = value;
	}

	// don't expose this
	int getValue() {
		return value;
	}

	// DISABLE_ALL and EXCEPT_TICKERS share native value 0 (kExceptTickers = kDisableAll
	// upstream, in rocksdb/include/rocksdb/statistics.h) -- DISABLE_ALL wins on the way back.
	static StatsLevel fromValue(int value) {
		return switch (value) {
			case 0 -> DISABLE_ALL;
			case 1 -> EXCEPT_HISTOGRAM_OR_TIMERS;
			case 2 -> EXCEPT_TIMERS;
			case 3 -> EXCEPT_DETAILED_TIMERS;
			case 4 -> EXCEPT_TIME_FOR_MUTEX;
			case 5 -> ALL;
			default -> DISABLE_ALL;
		};
	}
}
