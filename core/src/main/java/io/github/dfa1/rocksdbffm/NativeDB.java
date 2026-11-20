package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/// Base of every database type: a [NativeObjectParent] that exposes the `rocksdb_t*` the shared
/// capability interfaces ([RocksDBReadOperations], [RocksDBWriteOperations], ...) operate on.
///
/// For the types whose `rocksdb_t*` is their own [#ptr()] (everything but the transaction databases)
/// that is the default here; [NativeDBWithBaseDB] overrides it for the types that carry a separate
/// base pointer.
///
/// It also owns the database's [ColumnFamilyHandle]s: the ones returned by the open, and any created
/// afterward. They are children of this object, so closing the database releases them first, and
/// [ColumnFamilyHandle#close()] is a no-op. See
/// [ADR 0009](https://github.com/dfa1/rocksdb-ffm/blob/main/docs/adr/0009-db-owned-column-family-handles.md).
abstract class NativeDB extends NativeObjectParent {

	/// `void rocksdb_close(rocksdb_t* db);`
	private static final MethodHandle MH_CLOSE = NativeLibrary.downcall(
		"rocksdb_close", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);

	/// In open order, then creation order. An immutable snapshot replaced wholesale on every change
	/// (copy-on-write): lookups are lock-free, and changes happen only on open, create and drop.
	private final AtomicReference<SequencedMap<ColumnFamilyName, ColumnFamilyHandle>> columnFamilies;

	/// Calls in flight on this database, counted in per-thread stripes so threads sharing the database do not
	/// contend on one counter. Each stripe is padded to its own cache lines. An iterator counts its own calls.
	private final AtomicLongArray inFlight;

	private static final int STRIPES = 64;
	private static final int STRIPE_PADDING = 16;

	private static int stripe() {
		return (((int) Thread.currentThread().threadId() & (STRIPES - 1)) + 1) * STRIPE_PADDING;
	}

	/// [#enter()] on `db`, which is always a [NativeDB]: every implementor of the capability interfaces extends it.
	static NativeDB enter(Object db) {
		NativeDB guard = (NativeDB) db;
		guard.enter();
		return guard;
	}

	/// Marks a native call as in flight; pair with [#exit()] in a `finally`. The count is raised before the
	/// closed check, and [#close()] swaps the pointer before reading the counts, so one side always sees the
	/// other: either this throws, or the close waits for [#exit()].
	///
	/// @throws IllegalStateException if this database has been closed
	final void enter() {
		int stripe = stripe();
		inFlight.getAndIncrement(stripe);
		if (isClosed()) {
			inFlight.getAndDecrement(stripe);
			throw new IllegalStateException("native object is closed or ownership has been transferred");
		}
	}

	final void exit() {
		inFlight.getAndDecrement(stripe());
	}

	/// Waits until every call [#enter()] let through has returned. Called first in `tryClose`, after the pointer
	/// swap and before anything is released.
	final void awaitInFlightCalls() {
		while (inFlightCalls() != 0) {
			LockSupport.parkNanos(100_000);
		}
	}

	private long inFlightCalls() {
		long sum = 0;
		for (int i = 1; i <= STRIPES; i++) {
			sum += inFlight.get(i * STRIPE_PADDING);
		}
		return sum;
	}

	protected NativeDB(MemorySegment ptr) {
		super(ptr);
		this.columnFamilies = new AtomicReference<>(Collections.unmodifiableSequencedMap(new LinkedHashMap<>()));
		this.inFlight = new AtomicLongArray((STRIPES + 2) * STRIPE_PADDING);
	}

	/// The classes whose frames run a caller-supplied callback while RocksDB, or a pinned value, still uses the
	/// database: the upcall dispatchers and the scoped zero-copy readers. See [#close()].
	private static final Set<Class<?>> CALLBACK_HOSTS = Set.of(
			EventNotifierBridge.class, MergeOperator.Custom.class, CompactionFilter.class, CompactionFilterFactory.class,
			Logger.class, PinnableHandle.class, PinnableSlice.class, RocksIterator.class);

	private static final StackWalker STACK = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	/// Closes this database, releasing everything still borrowed from it first.
	///
	/// Must not be called from inside a callback this library is running on the calling thread: an
	/// [EventNotifier], [MergeOperator], [CompactionFilter], [CompactionFilterFactory] or [Logger] callback, or a
	/// [Mapper] reading a pinned value or an iterator entry. Whatever invoked the callback is still using the
	/// database. On a RocksDB background thread the close would wait for that very thread, never return, and
	/// wedge a thread of the pool every database in the process shares; on a caller's thread it would free the
	/// database under the read, flush or pinned value in progress. Close it once the callback has returned, or
	/// hand the close to another thread.
	///
	/// @throws IllegalStateException if called from inside such a callback
	@Override
	public void close() {
		if (inCallback()) {
			throw new IllegalStateException("a database cannot be closed from inside a callback: "
					                                + "whatever invoked the callback is still using it. Close it once the callback has returned, "
					                                + "or from another thread");
		}
		super.close();
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		awaitInFlightCalls();
		releaseChildren();
		MH_CLOSE.invokeExact(ptr);
	}

	/// A stack walk rather than a flag set around each callback: `close()` is cold, while the callbacks sit on
	/// the hottest paths there are (a pinned get, a merge or compaction filter per key), which a per-call
	/// `ScopedValue` binding would tax.
	private static boolean inCallback() {
		return STACK.walk(frames -> frames.anyMatch(frame -> CALLBACK_HOSTS.contains(frame.getDeclaringClass())));
	}

	/// Returns the native `rocksdb_t*` pointer to operate on, after checking that this object has not
	/// been closed.
	///
	/// @return the native database pointer
	/// @throws IllegalStateException if this object has been closed
	public MemorySegment dbPtr() {
		return ptr();
	}

	/// Returns the handle of the column family called `name`. The handle is owned by this database:
	/// do not close it, closing it is a no-op, and it stays valid until this database is closed or the
	/// column family is dropped.
	///
	/// @param name the column family name; [ColumnFamilyName#DEFAULT] always exists
	/// @return the column family's handle
	/// @throws NoSuchElementException if this database has no column family with that name
	/// @throws IllegalStateException  if this database has been closed
	public ColumnFamilyHandle getColumnFamily(ColumnFamilyName name) {
		Objects.requireNonNull(name, "name");
		ptr();
		ColumnFamilyHandle handle = columnFamilies.get().get(name);
		if (handle == null) {
			throw new NoSuchElementException("no column family named " + name);
		}
		return handle;
	}

	/// Returns the handles of every column family of this database, in the order they were opened,
	/// followed by those created since. The map is a snapshot: later creates and drops do not show up in it.
	///
	/// @return an unmodifiable name-to-handle map
	/// @throws IllegalStateException if this database has been closed
	public SequencedMap<ColumnFamilyName, ColumnFamilyHandle> getColumnFamilies() {
		ptr();
		return columnFamilies.get();
	}

	/// Takes ownership of a freshly opened or created column family handle and registers it.
	///
	/// @param name      the column family's name
	/// @param handlePtr the native `rocksdb_column_family_handle_t*`, now owned by this database
	/// @return the wrapped handle
	final ColumnFamilyHandle adopt(ColumnFamilyName name, MemorySegment handlePtr) {
		ColumnFamilyHandle handle = ColumnFamilyHandle.wrap(this, handlePtr);
		columnFamilies.updateAndGet(current -> {
			var copy = new LinkedHashMap<>(current);
			copy.put(name, handle);
			return Collections.unmodifiableSequencedMap(copy);
		});
		return handle;
	}

	/// Checks that `handle` is one of this database's own column family handles, before anything native is done
	/// with it: passing another database's handle to a native call acts on that other database.
	///
	/// @param handle a column family handle supplied by the caller
	/// @throws IllegalArgumentException if `handle` does not belong to this database
	final void requireOwns(ColumnFamilyHandle handle) {
		if (columnFamilies.get().values().stream().noneMatch(h -> h == handle)) {
			throw new IllegalArgumentException("column family handle does not belong to this database");
		}
	}

	/// Releases a handle that was dropped natively; releasing it also unregisters it.
	///
	/// @param handle the handle of a column family this database just dropped
	/// @throws IllegalArgumentException if `handle` does not belong to this database
	final void forget(ColumnFamilyHandle handle) {
		SequencedMap<ColumnFamilyName, ColumnFamilyHandle> before;
		SequencedMap<ColumnFamilyName, ColumnFamilyHandle> after;
		do {
			before = columnFamilies.get();
			var copy = new LinkedHashMap<>(before);
			if (!copy.values().removeIf(h -> h == handle)) {
				throw new IllegalArgumentException("column family handle does not belong to this database");
			}
			after = Collections.unmodifiableSequencedMap(copy);
		} while (!columnFamilies.compareAndSet(before, after));
		handle.release();
	}
}
