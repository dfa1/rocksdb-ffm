package io.github.dfa1.rocksdbffm;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/// Describes a column family by name and optional per-family options.
///
/// Used when opening a database with multiple column families or creating a new one.
/// If no [Options] are supplied, the column family uses RocksDB's default options — not the
/// DB-level options, so e.g. a comparator or merge operator set there does not carry over.
///
/// A descriptor may also carry a time-to-live ([#withTtl(Duration)]), honored only by a [TtlDB]: opening
/// or creating a column family with a TTL on any other database type is rejected rather than ignored.
///
/// ## Who owns the [Options]
///
/// A descriptor only *references* its [Options]; it never closes them and holds no copy.
///
/// - [#of(ColumnFamilyName)] uses the shared, immortal default options: owned by the library, never freed.
/// - [#of(ColumnFamilyName, Options)]: the caller who created the options owns them and closes them.
/// - The descriptors of a [LoadedOptions] reference options that the [LoadedOptions] owns: closing it closes them all.
///
/// RocksDB copies the options during the open or create call, so they only need to stay open until that
/// call returns, and may be closed right after. A descriptor must not outlive its options, though: reusing
/// one after they were closed (for another open, or a `createColumnFamily`) throws [IllegalStateException].
/// One [Options] may back several descriptors, as long as it stays open until the last call using any of them.
public final class ColumnFamilyDescriptor {

	private final ColumnFamilyName name;
	private final Options options;
	private final Duration ttl; // null when none was given

	private ColumnFamilyDescriptor(ColumnFamilyName name, Options options, Duration ttl) {
		this.name = Objects.requireNonNull(name, "name");
		this.options = Objects.requireNonNull(options, "options");
		this.ttl = ttl;
	}

	/// Creates a descriptor with the given name and no explicit options.
	///
	/// @param name column family name
	/// @return a new descriptor
	public static ColumnFamilyDescriptor of(ColumnFamilyName name) {
		return new ColumnFamilyDescriptor(name, Options.DEFAULTS, null);
	}

	/// Creates a descriptor with the given name and per-family options.
	///
	/// @param name    column family name
	/// @param options per-column-family options; caller retains ownership
	/// @return a new descriptor
	public static ColumnFamilyDescriptor of(ColumnFamilyName name, Options options) {
		return new ColumnFamilyDescriptor(name, options, null);
	}

	/// Returns a copy of this descriptor with a time-to-live: keys of this column family expire lazily during
	/// the next compaction that covers them. Only a [TtlDB] accepts a descriptor with a TTL. [Duration#ZERO]
	/// disables expiry, the same as giving no TTL.
	///
	/// @param ttl the time-to-live of the family's keys
	/// @return a new descriptor with the same name and options
	/// @throws NullPointerException     if `ttl` is `null`
	/// @throws IllegalArgumentException if `ttl` is negative or longer than [Integer#MAX_VALUE] seconds
	public ColumnFamilyDescriptor withTtl(Duration ttl) {
		TtlDB.toTtlSeconds(ttl);
		return new ColumnFamilyDescriptor(name, options, ttl);
	}

	/// Returns the time-to-live given with [#withTtl(Duration)], if any.
	///
	/// @return the TTL, or empty when none was given
	public Optional<Duration> ttl() {
		return Optional.ofNullable(ttl);
	}

	/// Returns the TTL in the whole seconds native expects; `0` (never expire) when none was given.
	int ttlSeconds() {
		return ttl == null ? 0 : TtlDB.toTtlSeconds(ttl);
	}

	/// Rejects a descriptor that carries a TTL, for every database type but [TtlDB], where it would
	/// otherwise be silently ignored.
	///
	/// @throws IllegalArgumentException if this descriptor has a TTL
	void requireNoTtl() {
		if (ttl != null) {
			throw new IllegalArgumentException(
					"column family " + name + " has a TTL, which only a TtlDB honors; open it with RocksDB.openTtl");
		}
	}

	/// Returns the column family name.
	///
	/// @return column family name
	public ColumnFamilyName name() {
		return name;
	}

	/// Returns the per-family options: the caller's, or the immortal [Options#DEFAULTS] when none
	/// were specified. Never `null`.
	Options options() {
		return options;
	}
}
