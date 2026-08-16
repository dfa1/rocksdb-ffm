package io.github.dfa1.rocksdbffm;

/// Determines when a [TransactionDB]'s writes become visible to other transactions,
/// matching the C++ `TxnDBWritePolicy` enum.
public enum WritePolicy {
	/// Write only the committed data to the DB (default). Reads by a transaction see only
	/// its own uncommitted writes plus any other transaction's committed writes.
	WRITE_COMMITTED(0),
	/// Write data after the prepare phase of two-phase commit, before the transaction commits.
	WRITE_PREPARED(1),
	/// Write data before the prepare phase, allowing very large transactions at the cost of
	/// weaker isolation guarantees.
	WRITE_UNPREPARED(2);

	private final int value;

	WritePolicy(int value) {
		this.value = value;
	}

	// don't expose this
	int getValue() {
		return value;
	}

	static WritePolicy fromValue(int value) {
		return switch (value) {
			case 0 -> WRITE_COMMITTED;
			case 1 -> WRITE_PREPARED;
			case 2 -> WRITE_UNPREPARED;
			default -> WRITE_COMMITTED;
		};
	}
}
