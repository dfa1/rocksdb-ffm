package io.github.dfa1.rocksdbffm;

import java.util.List;

/// The options a database was last opened with, as read back from its `OPTIONS-*` file by
/// [RocksDB#loadLatestOptions(java.nio.file.Path, boolean)].
///
/// Hand [#dbOptions()] and [#columnFamilies()] straight to
/// [RocksDB#openReadWrite(Options, java.nio.file.Path, List, List)] (or any other
/// column-family `open*` overload) to reopen the database exactly as it was configured.
///
/// This object owns [#dbOptions()] and the [Options] inside every descriptor; closing it closes
/// them all. It may be closed as soon as the database has been opened.
///
/// Only settings RocksDB can serialize are restored: comparators, merge operators, compaction
/// filters, listeners, loggers and other object-valued settings must be set again by the caller.
public final class LoadedOptions implements AutoCloseable {

	private final Options dbOptions;
	private final List<ColumnFamilyDescriptor> columnFamilies;

	LoadedOptions(Options dbOptions, List<ColumnFamilyDescriptor> columnFamilies) {
		this.dbOptions = dbOptions;
		this.columnFamilies = List.copyOf(columnFamilies);
	}

	/// Returns the database-level options.
	///
	/// @return the options; owned by this object
	public Options dbOptions() {
		return dbOptions;
	}

	/// Returns one descriptor per column family, each carrying that family's options.
	///
	/// @return the descriptors, in the order stored in the `OPTIONS` file; owned by this object
	public List<ColumnFamilyDescriptor> columnFamilies() {
		return columnFamilies;
	}

	/// Closes [#dbOptions()] and every column family's options.
	@Override
	public void close() {
		dbOptions.close();
		for (ColumnFamilyDescriptor descriptor : columnFamilies) {
			descriptor.options().close();
		}
	}
}
