package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// A [NativeObject] that borrows native memory from other objects (its database, its [ReadOptions], ...), so their
/// close must release it first. Split out of [NativeObject] so the many wrappers that borrow from nobody (options,
/// batches, pinned slices) carry none of this bookkeeping.
abstract class NativeObjectChild extends NativeObject {

	/// The objects this one borrows native memory from, set once by [#childOf(NativeObjectParent)] or
	/// [#childOf(NativeObjectParent, NativeObjectParent)]; `release()` unregisters from them after the native
	/// release call. Plain fields: they are assigned before the registration that publishes this object to a
	/// parent's sweep, and that registration is what makes the writes visible to the sweeping thread.
	private NativeObjectParent parent;
	private NativeObjectParent secondParent;

	/// Private monitor: `synchronized (this)` would let user code that locks a wrapper it holds block, or deadlock
	/// with, a parent's close.
	private final Object lock;

	protected NativeObjectChild(MemorySegment owningPointer) {
		super(owningPointer);
		this.lock = new Object();
	}

	/// Declares that this object borrows native memory from `parent`: it is registered as its child, so the parent's close releases it first, and
	/// its release unregisters it when it is closed on its own, so a long-lived parent does not accumulate every
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
	/// its release unregisters it when it is closed on its own, so a long-lived parent does not accumulate every
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

	/// Releases the native resource, unless this object is immortal.
	///
	/// Must not be called from inside a [Mapper] of an iterator that is, or borrows from, this object (the iterator
	/// itself, its [ReadOptions], its transaction): the mapper is still reading memory the close would free, and a
	/// close that waited for it would wait for its own thread. Close it once the mapper has returned.
	///
	/// @throws IllegalStateException if called from inside such a [Mapper]
	@Override
	public void close() {
		refuseFromInsideAMapper();
		super.close();
	}

	/// Throws if the calling thread is inside a [Mapper] of an iterator that is, or borrows from, this object. Called
	/// before anything is released, by [#close()] and by any other call that invalidates such an iterator (a
	/// transaction's commit and rollbacks).
	///
	/// @throws IllegalStateException if called from inside such a [Mapper]
	final void refuseFromInsideAMapper() {
		if (inUseByCurrentThread()) {
			throw new IllegalStateException(getClass().getSimpleName() + " cannot be released from inside a Mapper "
					+ "of an iterator that uses it: release it once the Mapper has returned");
		}
	}

	/// @return `true` if the calling thread is inside a call on this object, or on one borrowing from it, that runs
	///         caller code (an iterator's [Mapper]). Nothing runs caller code by default.
	boolean inUseByCurrentThread() {
		return false;
	}

	/// [NativeObject#release()] under this object's monitor, then unregisters from the parents.
	///
	/// A call that loses the race to another thread already releasing this object blocks until that release has
	/// finished, instead of returning at once: a parent's sweep calls this on each child and then frees its own
	/// pointer, so returning early would let the parent be destroyed while a child, released by another thread, is
	/// still using it. Parents are always locked before their children and [#tryClose(MemorySegment)] never locks a
	/// parent, so the wait cannot deadlock.
	///
	/// Unregistering comes last: a parent that starts closing while this release is still running must still find
	/// this object in its children, so its sweep waits on the monitor instead of freeing the parent under a native
	/// release in progress. When this runs because that parent's own sweep is releasing its children, its pointer
	/// stays valid until the sweep ends. Unregistering is idempotent, so a call that lost the race repeats it harmlessly.
	@Override
	final void release() {
		synchronized (lock) {
			try {
				super.release();
			} finally {
				if (parent != null) {
					parent.unregisterChild(this);
				}
				if (secondParent != null) {
					secondParent.unregisterChild(this);
				}
			}
		}
	}
}
