package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// A [NativeObject] that borrows native memory from other objects (its database, its [ReadOptions], ...), so their
/// close must release it first. Split out of [NativeObject] so the many wrappers that borrow from nobody (options,
/// batches, pinned slices) carry none of this bookkeeping.
abstract class NativeObjectChild extends NativeObject {

	/// The objects this one borrows native memory from, set once by [#childOf(NativeObjectParent)] or
	/// [#childOf(NativeObjectParent, NativeObjectParent)]; [#afterRelease()] unregisters from them after the native
	/// release call. Plain fields: they are assigned before the registration that publishes this object to a
	/// parent's sweep, and that registration is what makes the writes visible to the sweeping thread.
	private NativeObjectParent parent;
	private NativeObjectParent secondParent;

	/// Private monitor: `synchronized (this)` would let user code that locks a wrapper it holds block, or deadlock
	/// with, a parent's close.
	private final Object lock = new Object();

	protected NativeObjectChild(MemorySegment owningPointer) {
		super(owningPointer);
	}

	/// Declares that this object borrows native memory from `parent`: it is registered as its child, so the parent's close releases it first, and
	/// [#afterRelease()] unregisters it when it is closed on its own, so a long-lived parent does not accumulate every
	/// child it ever produced. Call it once, last in the constructor, once every field
	/// [#tryClose(MemorySegment)] reads is assigned, because registering makes this object visible to a concurrent
	/// sweep by a parent.
	///
	/// An immortal parent (a shared `DEFAULTS` instance) is never swept, so its
	/// [NativeObjectParent#registerChild(NativeObjectChild)] does nothing.
	///
	/// @param parent the object whose native memory this one borrows
	final void childOf(NativeObjectParent parent) {
		this.parent = parent;
		parent.registerChild(this);
	}

	/// Declares that this object borrows native memory from both parents (a [RocksIterator] has its database and its [ReadOptions]): it is registered as a child of each, so either one's close releases it first, and
	/// [#afterRelease()] unregisters it when it is closed on its own, so a long-lived parent does not accumulate every
	/// child it ever produced. Call it once, last in the constructor, once every field
	/// [#tryClose(MemorySegment)] reads is assigned, because registering makes this object visible to a concurrent
	/// sweep by a parent.
	///
	/// An immortal parent (a shared `DEFAULTS` instance) is never swept, so its
	/// [NativeObjectParent#registerChild(NativeObjectChild)] does nothing.
	///
	/// @param first the first object whose native memory this one borrows
	/// @param second the second one
	final void childOf(NativeObjectParent first, NativeObjectParent second) {
		this.parent = first;
		this.secondParent = second;
		first.registerChild(this);
		if (!isClosed()) {
			// A parent that was already closing abandoned this object (see NativeObjectParent#registerChild), so
			// there is nothing to register with the other one; the reverse leaves a dead entry to drop here.
			second.registerChild(this);
			if (isClosed()) {
				first.unregisterChild(this);
			}
		}
	}

	/// Releases the native resource exactly once, whatever [#close()] would otherwise do: the entry point for the
	/// owning [NativeObjectParent] to free a child whose [#close()] is a no-op. Idempotent and never throws.
	///
	/// A call that loses the race to another thread already releasing this object blocks until that release has
	/// finished, instead of returning at once: a parent's sweep calls this on each child and then frees its own
	/// pointer, so returning early would let the parent be destroyed while a child, released by another thread, is
	/// still using it. Parents are always locked before their children and [#tryClose(MemorySegment)] never locks a
	/// parent, so the wait cannot deadlock.
	final void release() {
		synchronized (lock) {
			doRelease();
		}
	}

	/// Releases through `release()` so an in-progress release is still observed by a sweep waiting on the monitor.
	@Override
	public void close() {
		if (isImmortal() || isClosed()) {
			return;
		}
		release();
	}

	/// Unregisters from every parent. Runs last in `release()`: a parent that starts closing while this
	/// release is still running must still find this object in its children, so its sweep waits on the monitor
	/// instead of freeing the parent under a native release in progress. When this runs because that parent's own
	/// sweep is releasing its children, its pointer stays valid until the sweep ends.
	@Override
	final void afterRelease() {
		if (parent != null) {
			parent.unregisterChild(this);
		}
		if (secondParent != null) {
			secondParent.unregisterChild(this);
		}
	}
}
