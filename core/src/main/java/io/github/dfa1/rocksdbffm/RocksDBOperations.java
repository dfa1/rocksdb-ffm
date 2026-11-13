package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// Root of the shared capability interfaces ([RocksDBReadOperations], [RocksDBWriteOperations],
/// [RocksDBCompactionOperations], [RocksDBMonitoringOperations], [RocksDBTracingOperations]): the one thing
/// their default methods need from an implementor is the `rocksdb_t*` to operate on.
public interface RocksDBOperations {

	/// Returns the native `rocksdb_t*` pointer to operate on. Equivalent to the [NativeObject#ptr()]
	/// every implementor already has — a separate accessor only because an interface cannot require a
	/// method from a specific superclass.
	///
	/// This is a borrowed pointer, an escape hatch for calling `rocksdb_*` functions this library does
	/// not wrap: it is valid only while this database is open, and the caller must not close, free or
	/// otherwise take ownership of it. Using it after [AutoCloseable#close()] is undefined behavior.
	///
	/// @return the native database pointer
	MemorySegment dbPtr();
}
