package io.github.dfa1.rocksdbffm;

import java.util.Objects;

/// Describes a column family by name and optional per-family options.
///
/// Used when opening a database with multiple column families or creating a new one.
/// If no [Options] are supplied, the column family uses RocksDB's default options — not the
/// DB-level options, so e.g. a comparator or merge operator set there does not carry over.
///
/// The caller retains ownership of any [Options] passed to this descriptor. RocksDB copies them
/// during the open or create call, so they only need to stay open until that call returns.
public final class ColumnFamilyDescriptor {

	private final ColumnFamilyName name;
	private final Options options;

	private ColumnFamilyDescriptor(ColumnFamilyName name, Options options) {
		this.name = Objects.requireNonNull(name, "name");
		this.options = Objects.requireNonNull(options, "options");
	}

	/// Creates a descriptor with the given name and no explicit options.
	///
	/// @param name column family name
	/// @return a new descriptor
	public static ColumnFamilyDescriptor of(ColumnFamilyName name) {
		return new ColumnFamilyDescriptor(name, Options.DEFAULTS);
	}

	/// Creates a descriptor with the given name and per-family options.
	///
	/// @param name    column family name
	/// @param options per-column-family options; caller retains ownership
	/// @return a new descriptor
	public static ColumnFamilyDescriptor of(ColumnFamilyName name, Options options) {
		return new ColumnFamilyDescriptor(name, options);
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
