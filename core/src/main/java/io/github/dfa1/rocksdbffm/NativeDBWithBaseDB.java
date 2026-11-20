package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// Base class for native wrappers that additionally hold a second native pointer: the
/// `rocksdb_t*` "base DB" obtained via `rocksdb_transactiondb_get_base_db` /
/// `rocksdb_optimistictransactiondb_get_base_db`, used directly by some operations instead of
/// the primary handle [NativeObject] manages.
///
/// Per `rocksdb/db/c.cc`, each `get_base_db` call heap-allocates a small `rocksdb_t` wrapper
/// struct (`{ DB* rep; }`) around the already-owned database object — a distinct allocation
/// from the database itself. A subclass's [#tryClose(MemorySegment, MemorySegment)] frees that
/// wrapper; it neither destroys nor can destroy the real database object, which the primary close
/// already owns and destroys.
///
/// The base DB pointer is private to this class — subclasses have no name for it, so they
/// cannot accidentally read it unchecked. They get it as a parameter of
/// [#tryClose(MemorySegment, MemorySegment)], and otherwise only through [#dbPtr()], which calls
/// [#ptr()] first and therefore throws [IllegalStateException] once this object is closed, the
/// same way every other native call on this object already does.
abstract class NativeDBWithBaseDB extends NativeDB {

	private final MemorySegment baseDb;

	protected NativeDBWithBaseDB(MemorySegment ptr, MemorySegment baseDb) {
		super(ptr);
		this.baseDb = baseDb;
	}

	/// Returns the base `rocksdb_t*` pointer, after checking (via [#ptr()]) that this object
	/// has not been closed. The guarded implementation lives here once, not duplicated per subclass.
	///
	/// @return the base DB pointer
	/// @throws IllegalStateException if this object has been closed
	@Override
	public final MemorySegment dbPtr() {
		ptr();
		return baseDb;
	}

	@Override
	protected final void tryClose(MemorySegment ptr) throws Throwable {
		awaitInFlightCalls();
		tryClose(ptr, baseDb);
	}

	/// [NativeObject#tryClose(MemorySegment)] with the base DB pointer handed in: starts with `releaseChildren()`,
	/// then frees the base DB wrapper and the primary pointer.
	///
	/// @param ptr    the non-NULL primary native pointer to release
	/// @param baseDb the base DB wrapper to free
	/// @throws Throwable if a native destroy call fails
	protected abstract void tryClose(MemorySegment ptr, MemorySegment baseDb) throws Throwable;
}
