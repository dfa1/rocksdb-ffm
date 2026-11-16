package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// A [NativeObject] that borrows native memory from other objects (its database, its [ReadOptions], ...), so their
/// close must release it first. Split out of [NativeObject] so the many wrappers that borrow from nobody (options,
/// batches, pinned slices) carry none of this bookkeeping.
abstract class NativeChild extends NativeObject {

	/// The objects this one borrows native memory from, set once by [#borrowedFrom(NativeObjectWithChildren...)];
	/// [#released()] unregisters from them after the native release call. A plain field: it is assigned in full
	/// before the registration that publishes this object to a parent's sweep, and that registration is what
	/// makes the write visible to the sweeping thread.
	private NativeObjectWithChildren[] parents;

	protected NativeChild(MemorySegment owningPointer) {
		super(owningPointer);
	}

	/// Declares that this object borrows native memory from each of `owners` (usually one; a [RocksIterator] has its
	/// database and its [ReadOptions]): it is registered as a child of each, so their close releases it first, and
	/// [#released()] unregisters it when it is closed on its own, so a long-lived owner does not accumulate every
	/// child it ever produced. Call it once, last in the constructor, once every field
	/// [#tryClose(MemorySegment)] reads is assigned, because registering makes this object visible to a concurrent
	/// sweep by an owner.
	///
	/// An immortal owner (a shared `DEFAULTS` instance) is never swept, so its
	/// [NativeObjectWithChildren#registerChild(NativeObject)] does nothing.
	///
	/// @param owners the objects whose native memory this one borrows
	final void borrowedFrom(NativeObjectWithChildren... owners) {
		parents = owners;
		for (NativeObjectWithChildren owner : owners) {
			owner.registerChild(this);
		}
		if (isClosed()) {
			// An owner that was already closing abandoned this object: close() will never reach release(), so drop
			// the registrations with the other owners here instead of leaving a dead entry until they close.
			for (NativeObjectWithChildren owner : owners) {
				owner.unregisterChild(this);
			}
		}
	}

	/// Unregisters from every owner. Runs last in [NativeObject#release()]: a parent that starts closing while this
	/// release is still running must still find this object in its children, so its sweep waits on the monitor
	/// instead of freeing the parent under a native release in progress. When this runs because that parent's own
	/// sweep is releasing its children, its pointer stays valid until the sweep ends.
	@Override
	final void released() {
		if (parents != null) {
			for (NativeObjectWithChildren owner : parents) {
				owner.unregisterChild(this);
			}
		}
	}
}
