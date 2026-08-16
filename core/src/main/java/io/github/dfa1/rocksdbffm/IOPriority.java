package io.github.dfa1.rocksdbffm;

/// Priority for requesting bytes from a rate limiter, matching the C++ `Env::IOPriority` enum.
public enum IOPriority {
	/// Lowest priority; throttled first under contention.
	LOW(0),
	/// Between [#LOW] and [#HIGH].
	MID(1),
	/// Highest priority among the built-in levels.
	HIGH(2),
	/// Reserved for user-initiated requests.
	USER(3),
	/// No specific priority; the request's cost is attributed to the rate limiter's total.
	TOTAL(4);

	private final int value;

	IOPriority(int value) {
		this.value = value;
	}

	// don't expose this
	int getValue() {
		return value;
	}

	static IOPriority fromValue(int value) {
		return switch (value) {
			case 0 -> LOW;
			case 1 -> MID;
			case 2 -> HIGH;
			case 3 -> USER;
			case 4 -> TOTAL;
			default -> TOTAL;
		};
	}
}
