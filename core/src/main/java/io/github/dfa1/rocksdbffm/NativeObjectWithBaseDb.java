package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// Base class for native wrappers that additionally hold a second native pointer: the
/// `rocksdb_t*` "base DB" obtained via `rocksdb_transactiondb_get_base_db` /
/// `rocksdb_optimistictransactiondb_get_base_db`, used directly by some operations instead of
/// the primary handle [NativeObject] manages.
///
/// Per `rocksdb/db/c.cc`, each `get_base_db` call heap-allocates a small `rocksdb_t` wrapper
/// struct (`{ DB* rep; }`) around the already-owned database object — a distinct allocation
/// from the database itself. [#tryCloseBaseDb(MemorySegment)] frees only that wrapper,
/// best-effort (a failure there does not prevent the primary pointer from still being closed);
/// it neither destroys nor can destroy the real database object, which the primary handle's own
/// close ([#tryClosePrimary(MemorySegment)], run afterward) already owns and destroys.
///
/// The base DB pointer is private to this class — subclasses have no name for it, so they
/// cannot accidentally read it unchecked. The only access is [#dbPtr()], which calls [#ptr()]
/// first and therefore throws [IllegalStateException] once this object is closed, the same way
/// every other native call on this object already does.
abstract class NativeObjectWithBaseDb extends NativeObject {

	private final MemorySegment baseDb;

	protected NativeObjectWithBaseDb(MemorySegment ptr, MemorySegment baseDb) {
		super(ptr);
		this.baseDb = baseDb;
	}

	/// Returns the base `rocksdb_t*` pointer, after checking (via [#ptr()]) that this object
	/// has not been closed.
	///
	/// @return the base DB pointer
	/// @throws IllegalStateException if this object has been closed
	protected final MemorySegment dbPtr() {
		ptr();
		return baseDb;
	}

	@Override
	protected final void tryClose(MemorySegment ptr) throws Throwable {
		try {
			tryCloseBaseDb(baseDb);
		} catch (Throwable t) {
			// best-effort: still attempt to close the primary pointer below
		}
		tryClosePrimary(ptr);
	}

	/// Frees the small `rocksdb_t` wrapper struct that [#dbPtr()] returns, for subclasses whose
	/// C API exposes a matching close call (e.g. `rocksdb_optimistictransactiondb_close_base_db`).
	/// This does not touch the underlying database object itself — only
	/// [#tryClosePrimary(MemorySegment)] does that, via its own teardown. Default: no-op.
	///
	/// @param baseDb the base DB pointer to release
	/// @throws Throwable if the native destroy call fails
	protected void tryCloseBaseDb(MemorySegment baseDb) throws Throwable {
		// no separate close needed by default
	}

	/// Closes the primary native pointer. Same contract as [NativeObject#tryClose(MemorySegment)].
	///
	/// @param ptr the non-NULL primary native pointer to release
	/// @throws Throwable if the native destroy call fails
	protected abstract void tryClosePrimary(MemorySegment ptr) throws Throwable;
}
