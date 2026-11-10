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

	private final String name;
	private final Options options;

	private ColumnFamilyDescriptor(String name, Options options) {
		this.name = Objects.requireNonNull(name, "name");
		this.options = options;
	}

	/// Creates a descriptor with the given UTF-8 name and no explicit options.
	///
	/// @param name column family name as a UTF-8 string
	/// @return a new descriptor
	public static ColumnFamilyDescriptor of(String name) {
		return new ColumnFamilyDescriptor(name, null);
	}

	/// Creates a descriptor with the given UTF-8 name and per-family options.
	///
	/// @param name    column family name as a UTF-8 string
	/// @param options per-column-family options; caller retains ownership
	/// @return a new descriptor
	public static ColumnFamilyDescriptor of(String name, Options options) {
		return new ColumnFamilyDescriptor(name, options);
	}

	/// Returns the column family name.
	///
	/// @return column family name
	public String name() {
		return name;
	}

	/// Returns the per-family options, or `null` if none were specified.
	Options options() {
		return options;
	}
}
