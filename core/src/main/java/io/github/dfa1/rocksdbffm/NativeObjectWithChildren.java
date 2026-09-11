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
///
/// [#registerChild(NativeObject)] is lock-free — see its doc for the TOCTOU race it closes
/// (issues/143): a child's native "create" call can succeed an instant before this object
/// closes, racing its own constructor's `registerChild` call against the one-shot sweep. Rather
/// than a lock around the check, `registerChild` inserts optimistically and validates
/// afterward, undoing if the validation says it shouldn't have happened — an "insert, recheck,
/// compensate" pattern that needs nothing beyond [NativeObject]'s existing `AtomicReference`
/// and [ConcurrentHashMap]'s own atomic add/remove.
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
	/// Closes a TOCTOU race (issues/143): `child`'s underlying native "create" call can succeed
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
	/// [NativeObject#transferOwnership()] -- a real native resource leak (its release call would
	/// itself be a use-after-free against a pointer this object's own
	/// [#tryCloseResource(MemorySegment)] may already have freed), but not the JVM crash a later
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
	final void registerChild(NativeObject child) {
		children.add(child);
		if (isClosed() && children.remove(child)) {
			child.transferOwnership();
		}
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
		// ConcurrentModificationException. No children.clear() afterward: every child.close()
		// call above already unregisters that child from `children` as part of its own
		// tryClose (see Snapshot/RocksIterator), so a normal sweep already drains the set on its
		// own; anything still present after this loop is exactly the registerChild-added-during-
		// this-very-sweep stragglers described there, and registerChild's own recheck cleans
		// those up (whether that happens before or after this method returns).
		for (NativeObject child : children) {
			child.close();
		}
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
