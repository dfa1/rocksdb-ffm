package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// Base of every database type: a [NativeObjectWithChildren] that exposes the `rocksdb_t*` the shared
/// capability interfaces ([RocksDBReadOperations], [RocksDBWriteOperations], ...) operate on.
///
/// For the types whose `rocksdb_t*` is their own [#ptr()] (everything but the transaction databases)
/// that is the default here; [NativeObjectWithBaseDb] overrides it for the types that carry a separate
/// base pointer.
abstract class NativeDB extends NativeObjectWithChildren {

	protected NativeDB(MemorySegment ptr) {
		super(ptr);
	}

	/// Returns the native `rocksdb_t*` pointer to operate on, after checking that this object has not
	/// been closed.
	///
	/// @return the native database pointer
	/// @throws IllegalStateException if this object has been closed
	public MemorySegment dbPtr() {
		return ptr();
	}
}
