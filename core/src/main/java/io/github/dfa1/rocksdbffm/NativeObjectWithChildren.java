package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Base class for native wrappers that can produce child resources borrowing this object's
/// native pointer for their own release call (e.g. [Snapshot]'s `rocksdb_release_snapshot`
/// needs its owning DB's pointer) — and would otherwise dangle if this object closed first.
///
/// A child registers itself via [#registerChild(NativeObject)] (typically from its own
/// constructor) and unregisters via [#unregisterChild(NativeObject)] once released on its own,
/// so a long-lived parent doesn't accumulate strong references to every child it ever produced.
/// [#tryClose(MemorySegment)] closes every still-registered child, synchronously, while `ptr`
/// is still valid — before delegating to [#tryCloseResource(MemorySegment)] for this object's
/// own native destroy call — so a child's release call never runs against memory this object
/// has already freed, whichever side closes first.
abstract class NativeObjectWithChildren extends NativeObject {

	/// Effectively free (a [ConcurrentHashMap]-backed set allocates no backing table until
	/// first written) for any instance that never actually registers a child.
	private final Set<NativeObject> children = ConcurrentHashMap.newKeySet();

	protected NativeObjectWithChildren(MemorySegment ptr) {
		super(ptr);
	}

	/// Registers `child` to be closed automatically before this object's own native resource is
	/// destroyed, if `child` is not already closed by then.
	///
	/// @param child the child resource to close alongside (and before) this object
	final void registerChild(NativeObject child) {
		children.add(child);
	}

	/// Removes a previously-[#registerChild(NativeObject) registered] child, e.g. because it
	/// was closed on its own rather than by this object's [#close()]. A no-op if `child` was
	/// never registered, or already removed (including by a concurrent sweep in
	/// [#tryClose(MemorySegment)]).
	///
	/// @param child the child resource to stop tracking
	final void unregisterChild(NativeObject child) {
		children.remove(child);
	}

	@Override
	protected final void tryClose(MemorySegment ptr) throws Throwable {
		// child.close() is safe to call whether or not the child was already closed by its own
		// caller (NativeObject's close() is idempotent), and children is a ConcurrentHashMap-
		// backed set, so a child unregistering itself mid-iteration does not throw
		// ConcurrentModificationException.
		for (NativeObject child : children) {
			child.close();
		}
		children.clear();
		tryCloseResource(ptr);
	}

	/// Closes this object's own native resource. Same contract as
	/// [NativeObject#tryClose(MemorySegment)] — renamed only because `tryClose` itself is
	/// final here, to guarantee children are always closed first.
	///
	/// @param ptr the non-NULL primary native pointer to release
	/// @throws Throwable if the native destroy call fails
	protected abstract void tryCloseResource(MemorySegment ptr) throws Throwable;
}
