package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/// Base class for all Java wrappers around native RocksDB pointers.
///
/// Holds the native pointer in an [AtomicReference]. On [#close()],
/// it is atomically swapped to [MemorySegment#NULL], ensuring
/// [#tryClose(MemorySegment)] is called exactly once even under
/// concurrent or repeated close() calls. A `close()` that loses that race returns at once, possibly
/// before the winning thread's native release has finished.
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

	/// Set by [#makeImmortal()] and never cleared. Usually set before the object is published (a `DEFAULTS` field),
	/// but a [CompactionFilter] sets it on every attach, hence atomic.
	private final AtomicBoolean immortal = new AtomicBoolean();

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
	/// [NativeObjectParent#registerChild(NativeObjectChild)] to detect "parent already closed"
	/// as a plain check rather than via exception-driven control flow. Reads the same
	/// field [#close()] atomically nulls, so this observes a close a moment before
	/// [#tryClose(MemorySegment)] itself even starts running.
	///
	/// @return `true` if [#ptr()] would currently throw
	final boolean isClosed() {
		return MemorySegment.NULL.equals(owningPointer.get());
	}

	/// Releases the native resource, unless this object is immortal. A wrapper that is released by its owner and
	/// not by whoever holds it (a [ColumnFamilyHandle]) overrides this as a no-op; `release()` still frees it.
	///
	/// Returns at once if another thread is already releasing this object, instead of waiting for it: only a
	/// parent's sweep needs that wait, and a [NativeObjectChild] gives it that in `release()`. A native release can
	/// block on a callback thread (`rocksdb_close` draining an event listener), and a `close()` from that callback
	/// would otherwise deadlock.
	@Override
	public void close() {
		if (immortal.get() || isClosed()) {
			return;
		}
		release();
	}

	/// @return `true` if [#makeImmortal()] was called, so [#close()] does nothing
	final boolean isImmortal() {
		return immortal.get();
	}

	/// Swaps the pointer to NULL and, if this call won that swap, runs [#tryClose(MemorySegment)], logging
	/// instead of throwing. Idempotent and never throws. Whatever [#close()] would otherwise do: the entry point for
	/// an owner that frees an object whose `close()` is a no-op. A [NativeObjectChild] overrides it to add the
	/// monitor a parent's sweep waits on, and its unregistration from the parents.
	void release() {
		MemorySegment ptr = owningPointer.getAndSet(MemorySegment.NULL);
		if (!MemorySegment.NULL.equals(ptr)) {
			try {
				tryClose(ptr);
			} catch (Throwable throwable) {
				// close() must never throw — a failure here must not stop the rest of a
				// try-with-resources chain from closing — but a failure to release a native
				// resource is still worth knowing about.
				LOG.log(System.Logger.Level.ERROR, getClass().getSimpleName() + ".close() failed", throwable);
			}
		}
	}

	/// Called exactly once with the non-NULL pointer when this object is closed. Implementations release everything
	/// this object owns, in the order it needs: a [NativeObjectParent] starts with `releaseChildren()`, so no child's
	/// release runs against memory this object has already freed.
	///
	/// A [NativeObjectChild] runs it while holding its own monitor, so an implementation must never close, release or
	/// lock another [NativeObject] that could be one of its owners: owners are locked before the objects that
	/// borrow from them, and taking them in the other order here can deadlock against an owner's close.
	///
	/// @param ptr the non-NULL native pointer to release
	/// @throws Throwable if the native destroy call fails; [#close()] catches it, logs it, and
	///                    does not rethrow, so the rest of a try-with-resources chain still closes
	protected abstract void tryClose(MemorySegment ptr) throws Throwable;


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
		immortal.set(true);
		return (T) this;
	}

	///
	/// To be invoked manually when this object does not have anymore the ownership of the pointer.
	/// How to know when to call this?
	/// - RocksDB C API source (db/c.cc) — look at what rocksdb_block_based_options_destroy does: if it calls delete options->rep.filter_policy, ownership was transferred.
	/// - RocksDB documentation — the C API docs sometimes state it explicitly.
	/// - The Java JNI bindings (RocksDB official Java library) — they've already solved this; look at how BlockBasedTableConfig handles filter policy lifecycle.
	///
	/// Also (re)used by [NativeObjectParent#registerChild(NativeObjectChild)] for a second,
	/// unrelated reason: to abandon a child registered too late (its owning parent already
	/// started closing) without invoking its normal [#tryClose(MemorySegment)] — that release
	/// call would itself be a use-after-free against a pointer the parent may have already
	/// freed. There is no ownership transfer in that case, just the same net effect this method
	/// already provides: the pointer is dropped and further use throws [IllegalStateException]
	/// instead of double-freeing or touching freed memory.
	void transferOwnership() {
		if (immortal.get()) {
			throw new IllegalStateException("immortal object cannot transfer ownership: it is shared process-wide");
		}
		owningPointer.set(MemorySegment.NULL);
	}
}
