package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.concurrent.atomic.AtomicReference;

/// Base of every database type: a [NativeObjectWithChildren] that exposes the `rocksdb_t*` the shared
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
abstract class NativeDB extends NativeObjectWithChildren {

	/// In open order, then creation order. An immutable snapshot replaced wholesale on every change
	/// (copy-on-write): lookups are lock-free, and changes happen only on open, create and drop.
	private final AtomicReference<SequencedMap<ColumnFamilyName, ColumnFamilyHandle>> columnFamilies =
			new AtomicReference<>(Collections.unmodifiableSequencedMap(new LinkedHashMap<>()));

	protected NativeDB(MemorySegment ptr) {
		super(ptr);
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
