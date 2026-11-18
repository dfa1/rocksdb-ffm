package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Base class for native wrappers that can produce child resources borrowing this object's
/// native pointer for their own release call (e.g. [Snapshot]'s `rocksdb_release_snapshot`
/// needs its owning DB's pointer) — and would otherwise dangle if this object closed first.
///
/// A child registers itself with [NativeObjectChild#childOf(NativeObjectParent)], last in its own
/// constructor, and [NativeObjectChild]'s release unregisters it once released on its own, so a long-lived parent
/// doesn't accumulate strong references to every child it ever produced.
/// A parent's [#tryClose(MemorySegment)] starts with [#releaseChildren()], which closes every still-registered
/// child, synchronously, while `ptr` is still valid — before its own native destroy call — so a child's release
/// call never runs against memory this object has already freed, whichever side closes first.
///
/// [#registerChild(NativeObjectChild)] is lock-free — see its doc for the TOCTOU race it narrows
/// (issues/143): a child's native "create" call can succeed an instant before this object
/// closes, racing its own constructor's `registerChild` call against the one-shot sweep. Rather
/// than a lock around the check, `registerChild` inserts optimistically and validates
/// afterward, undoing if the validation says it shouldn't have happened — an "insert, recheck,
/// compensate" pattern that needs nothing beyond [NativeObject]'s `AtomicReference` pointer (the recheck reads it through
/// [NativeObject#isClosed()]) and [ConcurrentHashMap]'s own atomic add/remove.
abstract class NativeObjectParent extends NativeObjectChild {

	/// A concurrent set, never behind a lock: [#unregisterChild(NativeObjectChild)] runs inside a child's own monitor
	/// while a sweep calls into the children, so a lock here would deadlock against them.
	private final Set<NativeObjectChild> children;

	protected NativeObjectParent(MemorySegment ptr) {
		super(ptr);
		children = ConcurrentHashMap.newKeySet();
	}

	/// Registers `child` to be closed automatically before this object's own native resource is
	/// destroyed, if `child` is not already closed by then.
	///
	/// Best effort, not a guarantee: closing this object while another thread is still creating a child from it
	/// is unsupported (see [NativeObject]), and if this object is destroyed before that native "create" call
	/// runs, nothing here can help. What this does is turn the survivable half of that race from a later crash
	/// into a leak plus an [IllegalStateException].
	///
	/// Narrows a TOCTOU race (issues/143): `child`'s underlying native "create" call can succeed
	/// an instant before this object closes, so by the time `child`'s constructor reaches this
	/// call, [#tryClose(MemorySegment)] may already have run its one-shot sweep. A naive
	/// "check [NativeObject#isClosed()], then add" is itself racy -- the check and the add are
	/// two separate operations, and this object can finish closing in the gap between them, no
	/// matter how that check is implemented (a lock guarding the pair would work, but isn't the
	/// only option).
	///
	/// Instead this adds first, unconditionally, then validates: [ConcurrentHashMap] guarantees
	/// an `add` that completes before another thread's later traversal of the *same* set is
	/// visible to it, with no extra synchronization needed for that part. So either
	/// [#tryClose(MemorySegment)]'s sweep has not truly started yet at the moment `child` is
	/// added (in which case the eventual sweep, whenever it runs, is guaranteed to see it — the
	/// same guarantee the old, un-raced code relied on), or `isClosed()` -- read immediately
	/// after the add -- observes `true`. That second case means the add might have missed the
	/// sweep's already-in-progress or already-finished traversal (`children`'s iterator is only
	/// weakly consistent for genuinely concurrent modifications), so this removes what it just
	/// added and, only if that removal actually found it still there, abandons `child` via
	/// [NativeObject#transferOwnership()] -- a real native resource leak, of everything its release would have freed
	/// (the native object and any native memory the wrapper owns, such as an iterator's length slot; its release call would
	/// itself be a use-after-free against a pointer this object's own
	/// [#tryClose(MemorySegment)] may already have freed), but not the JVM crash a later
	/// use of a silently-dangling `child` would otherwise risk. If the removal instead finds
	/// `child` already gone, the sweep won the race and already closed it properly (through the
	/// normal path below, while `ptr` was still valid) -- nothing left to do.
	///
	/// Both outcomes of a "child visited by the sweep" and "child abandoned here" landing on the
	/// very same instance at once are safe regardless of which wins: [NativeObject#close()] and
	/// [NativeObject#transferOwnership()] both just (idempotently) null the same
	/// `AtomicReference`, so whichever runs second is a no-op.
	///
	/// @param child the child resource to close alongside (and before) this object
	final void registerChild(NativeObjectChild child) {
		if (isImmortal()) {
			// Never swept, so tracking the child would only pin it for the life of the JVM.
			return;
		}
		children.add(child);
		if (isClosed() && children.remove(child)) {
			child.transferOwnership();
		}
	}

	/// Removes a previously-[#registerChild(NativeObjectChild) registered] child, e.g. because it
	/// was closed on its own rather than by this object's [#close()]. A no-op if `child` was
	/// never registered, or already removed (including by a concurrent sweep in
	/// [#tryClose(MemorySegment)]).
	///
	/// @param child the child resource to stop tracking
	final void unregisterChild(NativeObjectChild child) {
		children.remove(child);
	}

	/// Releases every still-registered child of the given type, leaving this object and its other
	/// children open — for a native call that invalidates one kind of borrowed object (e.g. a
	/// transaction's iterators on commit) without destroying its parent.
	///
	/// A parent's [#tryClose(MemorySegment)] calls [#releaseChildren()] first. `release()` is safe on a child whether
	/// or not its own caller already closed it, and a child unregistering itself mid-iteration does not throw.
	/// Nothing is cleared afterward: every `release()` already unregisters its child, so a sweep drains the set on
	/// its own; anything left is a `registerChild` made during this very sweep, which that call's own recheck cleans
	/// up.
	///
	/// @param type the kind of child to release
	final void releaseChildren(Class<? extends NativeObjectChild> type) {
		for (NativeObjectChild child : children) {
			if (type.isInstance(child)) {
				child.release();
			}
		}
	}

	/// Releases every still-registered child, synchronously: the first step of a parent's
	/// [#tryClose(MemorySegment)], while its own pointer is still valid.
	final void releaseChildren() {
		releaseChildren(NativeObjectChild.class);
	}

	/// @return how many children are registered right now, for tests asserting that a closed child unregistered
	final int childCount() {
		return children.size();
	}
}
