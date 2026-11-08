package io.github.dfa1.rocksdbffm;

import java.nio.file.Path;
import java.util.Objects;

/// A directory SST files can be placed in, with the size at which RocksDB moves on to the next one.
///
/// Used by [Options#setDbPaths(java.util.List)] and [Options#setCfPaths(java.util.List)] for tiered
/// storage across several mount points, e.g. hot data on NVMe and colder data on a bigger, slower disk.
///
/// No native resource is associated with this record: the native `rocksdb_dbpath_t` only lives for the
/// duration of the setter call.
///
/// @param path       directory SST files are stored in
/// @param targetSize amount of data to keep in this directory before spilling to the next one
public record DbPath(Path path, MemorySize targetSize) {

	/// Creates a [DbPath].
	///
	/// @param path       directory SST files are stored in
	/// @param targetSize amount of data to keep in this directory before spilling to the next one
	public DbPath {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(targetSize, "targetSize");
	}

	/// Creates a [DbPath].
	///
	/// @param path       directory SST files are stored in
	/// @param targetSize amount of data to keep in this directory before spilling to the next one
	/// @return a new [DbPath]
	public static DbPath of(Path path, MemorySize targetSize) {
		return new DbPath(path, targetSize);
	}
}
