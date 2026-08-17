package io.github.dfa1.rocksdbffm;

/// Filesystem/storage temperature hint, matching RocksDB's `Temperature` enum. Lets a
/// pluggable `FileSystem` place files on different storage tiers (e.g. local SSD vs. cheaper
/// object storage) based on how "hot" the data is expected to be. A no-op for the default
/// `FileSystem` — only takes effect with a custom one that inspects it.
///
/// Every option this is used with is EXPERIMENTAL per `rocksdb/include/rocksdb/advanced_options.h`
/// and `options.h`, and defaults to [#UNKNOWN].
public enum Temperature {
	/// No temperature hint (default).
	UNKNOWN(0x00),
	/// Frequently accessed data.
	HOT(0x04),
	/// Infrequently accessed data.
	WARM(0x08),
	/// Rarely accessed data, colder than [#WARM].
	COOL(0x0A),
	/// Rarely accessed data, colder than [#COOL].
	COLD(0x0C),
	/// The coldest tier.
	ICE(0x10);

	private final int value;

	Temperature(int value) {
		this.value = value;
	}

	// don't expose the raw value
	int getValue() {
		return value;
	}

	static Temperature fromValue(int v) {
		return switch (v) {
			case 0x00 -> UNKNOWN;
			case 0x04 -> HOT;
			case 0x08 -> WARM;
			case 0x0A -> COOL;
			case 0x0C -> COLD;
			case 0x10 -> ICE;
			default -> UNKNOWN;
		};
	}
}
