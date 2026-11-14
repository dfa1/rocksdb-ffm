package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicReference;

/// Base class for all Java wrappers around native RocksDB pointers.
///
/// Holds the native pointer in an [AtomicReference]. On [#close()],
/// the reference is atomically swapped to [MemorySegment#NULL], ensuring
/// [#tryClose(MemorySegment)] is called exactly once even under
/// concurrent or repeated close() calls.
///
/// That guarantee is narrower than it might read: it covers concurrent or repeated calls to
/// [#close()] itself, not `close()` racing a concurrent call to any *other* method. Nothing
/// synchronizes `close()` against a live call to, say, a `get`/`put` method on another thread —
/// one thread's [#ptr()] succeeding does not stop a second thread's `close()` from freeing that
/// same memory immediately afterward, before the native call that read `ptr()` actually runs.
/// Do not call `close()` on one thread while another thread might still be calling any method on
/// the same object; synchronize externally if your usage can't rule that out.
public abstract class NativeObject implements AutoCloseable {

	private static final System.Logger LOG = System.getLogger(NativeObject.class.getName());

	private final AtomicReference<MemorySegment> owningPointer;

	/// Set by [#makeImmortal()], which only ever runs once per object and before anything else can race with it.
	private volatile boolean immortal;

	private static final NativeObjectWithChildren[] NO_PARENTS = new NativeObjectWithChildren[0];

	/// The objects this one borrows native memory from, set once by [#borrowedFrom(NativeObjectWithChildren...)];
	/// [#release()] unregisters from them after the native release call. A plain field: it is assigned in full
	/// before the registration that publishes this object to a parent's sweep, and that registration is what
	/// makes the write visible to the sweeping thread.
	private NativeObjectWithChildren[] parents = NO_PARENTS;

	/// Set once, by [#markOwnedByParent()], when the owning [NativeObjectWithChildren] releases this
	/// object instead of its holder: [#close()] is then a no-op and only [#release()] frees it.
	private volatile boolean ownedByParent;

	/// Initializes this wrapper with the given native pointer.
	///
	/// @param owningPointer the non-NULL native pointer this object now owns
	protected NativeObject(MemorySegment owningPointer) {
		this.owningPointer = new AtomicReference<>(owningPointer);
	}

	/// Returns the current native pointer.
	///
	/// @return the live native pointer
	/// @throws IllegalStateException if this object has been closed or its ownership transferred
	public final MemorySegment ptr() {
		MemorySegment p = owningPointer.get();
		if (MemorySegment.NULL.equals(p)) {
			throw new IllegalStateException("native object is closed or ownership has been transferred");
		}
		return p;
	}

	/// Returns whether this object is closed or its ownership was transferred away — i.e.
	/// whether [#ptr()] would throw — without throwing. Package-private: used only by
	/// [NativeObjectWithChildren#registerChild(NativeObject)] to detect "parent already closed"
	/// as a plain check rather than via exception-driven control flow. Reads the same
	/// [AtomicReference] [#close()] atomically nulls, so this observes a close a moment before
	/// [#tryClose(MemorySegment)] itself even starts running.
	///
	/// @return `true` if [#ptr()] would currently throw
	final boolean isClosed() {
		return MemorySegment.NULL.equals(owningPointer.get());
	}

	@Override
	public final void close() {
		if (immortal || ownedByParent) {
			return;
		}
		release();
	}

	/// @return `true` if [#makeImmortal()] was called, so [#close()] does nothing
	final boolean isImmortal() {
		return immortal;
	}

	/// Declares that this object borrows native memory from each of `owners` (usually one; a [RocksIterator] has its
	/// database and its [ReadOptions]): it is registered as a child of each, so their close releases it first, and
	/// [#release()] unregisters it when it is closed on its own, so a long-lived owner does not accumulate every
	/// child it ever produced. Call it once, last in the constructor, once every field
	/// [#tryClose(MemorySegment)] reads is assigned, because registering makes this object visible to a concurrent
	/// sweep by an owner.
	///
	/// An immortal owner (a shared `DEFAULTS` instance) is never swept, so registering would only add a
	/// pointless reference and it is skipped.
	///
	/// @param owners the objects whose native memory this one borrows
	final void borrowedFrom(NativeObjectWithChildren... owners) {
		int live = 0;
		for (NativeObjectWithChildren owner : owners) {
			if (!owner.isImmortal()) {
				live++;
			}
		}
		if (live == 0) {
			return;
		}
		NativeObjectWithChildren[] mine = new NativeObjectWithChildren[live];
		int next = 0;
		for (NativeObjectWithChildren owner : owners) {
			if (!owner.isImmortal()) {
				mine[next++] = owner;
			}
		}
		parents = mine;
		for (NativeObjectWithChildren owner : mine) {
			owner.registerChild(this);
		}
	}

	/// Releases the native resource exactly once, whatever [#close()] would otherwise do: the
	/// entry point for the owning [NativeObjectWithChildren] to free a child that opted out of
	/// being closed by its holder (see [#markOwnedByParent()]). Idempotent and never throws.
	///
	/// A call that loses the race to another thread already releasing this object blocks until that
	/// release has finished, instead of returning at once: a parent's sweep calls this on each child
	/// and then frees its own pointer, so returning early would let the parent be destroyed while a
	/// child, released by another thread, is still using it. Parents are always locked before their
	/// children and [#tryClose(MemorySegment)] never locks a parent, so the wait cannot deadlock.
	final void release() {
		// A private monitor (this field is already allocated per object): `synchronized (this)` would let user code
		// that locks a wrapper it holds block, or deadlock with, a parent's close.
		synchronized (owningPointer) {
			MemorySegment ptr = owningPointer.getAndSet(MemorySegment.NULL);
			if (!MemorySegment.NULL.equals(ptr)) {
				try {
					tryClose(ptr);
				} catch (Throwable throwable) {
					// close() must never throw — a failure here must not stop the rest of a
					// try-with-resources chain from closing — but a failure to release a native
					// resource is still worth knowing about.
					LOG.log(System.Logger.Level.ERROR, getClass().getSimpleName() + ".close() failed", throwable);
				} finally {
					// Unregister last: a parent that starts closing while this release is still running must
					// still find this object in its children, so its sweep waits on the monitor above instead of
					// freeing the parent under a native release in progress. When this runs because that parent's
					// own sweep is releasing its children, its pointer stays valid until the sweep ends.
					for (NativeObjectWithChildren owner : parents) {
						owner.unregisterChild(this);
					}
				}
			}
		}
	}

	/// Called exactly once with the non-NULL pointer when this object is closed.
	/// Implementations must release the native resource.
	///
	/// @param ptr the non-NULL native pointer to release
	/// @throws Throwable if the native destroy call fails; [#close()] catches it, logs it, and
	///                    does not rethrow, so the rest of a try-with-resources chain still closes
	protected abstract void tryClose(MemorySegment ptr) throws Throwable;


	/// Makes [#close()] a no-op because the owning [NativeObjectWithChildren] releases this object
	/// when it closes itself, through [#release()]. For a resource the holder only borrows, like a
	/// [ColumnFamilyHandle] looked up from its database: closing it early would destroy it for every
	/// other user of that database. Unlike [#makeImmortal()] the object is still freed, and
	/// [#transferOwnership()] still works (the parent's late-registration path relies on it).
	///
	/// @param <T> the wrapper type, inferred from the assignment target
	/// @return this object
	@SuppressWarnings("unchecked")
	<T extends NativeObject> T markOwnedByParent() {
		this.ownedByParent = true;
		return (T) this;
	}

	/// Makes this object immortal: it lives for the whole JVM, [#close()] is a no-op, and [#ptr()] keeps working.
	///
	/// Many convenience overloads need a default options object only to hand it to a native call
	/// (`beginTransaction(WriteOptions)`, `ColumnFamilyDescriptor.of(name)`, `ingestExternalFile(files)`, ...).
	/// Creating and destroying one per call is a native allocation round-trip for nothing; keeping a single
	/// immortal instance in a package-private `static final` field removes it. Making it immortal, rather than
	/// merely never closing it, means a stray `close()` (a `try`-with-resources, a cleanup path) cannot
	/// free the pointer out from under every later caller.
	///
	/// The native resource is intentionally never released; for a default that is not a leak, because there is
	/// exactly one instance per process. (Two deliberate exceptions are created by callers and so do leak once per
	/// instance, which their javadoc says: an attached [CompactionFilter] and [Env#memEnv()]. A native holder keeps
	/// a raw pointer to each that outlives everything able to release it.) Only use it for a default that every native call it is passed to
	/// merely reads or copies. Never for an object a native call retains, mutates or takes ownership of,
	/// and verify that against `db/c.cc` first; [#transferOwnership()] on an immortal object throws
	/// rather than silently breaking every other user. E.g. `rocksdb_create_column_family` copies its [Options]
	/// into its own `ColumnFamilyOptions`. Not to be confused with [#transferOwnership()], where the
	/// native side takes over the pointer, or with shared ownership, where both sides release independently.
	///
	/// @param <T> the wrapper type, inferred from the assignment target
	/// @return this object, so it chains off the factory: `static final X DEFAULTS = newX().makeImmortal();`
	@SuppressWarnings("unchecked")
	<T extends NativeObject> T makeImmortal() {
		this.immortal = true;
		return (T) this;
	}


	///
	/// To be invoked manually when this object does not have anymore the ownership of the pointer.
	/// How to know when to call this?
	/// - RocksDB C API source (db/c.cc) — look at what rocksdb_block_based_options_destroy does: if it calls delete options->rep.filter_policy, ownership was transferred.
	/// - RocksDB documentation — the C API docs sometimes state it explicitly.
	/// - The Java JNI bindings (RocksDB official Java library) — they've already solved this; look at how BlockBasedTableConfig handles filter policy lifecycle.
	///
	/// Also (re)used by [NativeObjectWithChildren#registerChild(NativeObject)] for a second,
	/// unrelated reason: to abandon a child registered too late (its owning parent already
	/// started closing) without invoking its normal [#tryClose(MemorySegment)] — that release
	/// call would itself be a use-after-free against a pointer the parent may have already
	/// freed. There is no ownership transfer in that case, just the same net effect this method
	/// already provides: the pointer is dropped and further use throws [IllegalStateException]
	/// instead of double-freeing or touching freed memory.
	void transferOwnership() {
		if (immortal) {
			throw new IllegalStateException("immortal object cannot transfer ownership: it is shared process-wide");
		}
		owningPointer.set(MemorySegment.NULL);
	}
}
