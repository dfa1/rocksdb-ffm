package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.Objects;

/// FFM wrapper for `rocksdb_pinnableslice_t` — the pinned-value handle returned by the
/// byte[]-tier `rocksdb_*get_pinned[_cf]` family (`rocksdb_get_pinned[_cf]`,
/// `rocksdb_transaction_get_pinned[_cf]`, `rocksdb_transactiondb_get_pinned[_cf]`).
///
/// Not the same native type as `rocksdb_pinnable_handle_t` (see `RocksDB.withPinned`),
/// which is the newer, zero-copy `_v2` handle. `Transaction` and `TransactionDB` have no
/// `_v2` equivalent in `rocksdb/include/rocksdb/c.h`, so they still go through this
/// older API.
///
/// Package-private: purely internal plumbing for the `get_pinned`-based `get(...)`
/// overloads on [RocksDB], [Transaction], and [TransactionDB] — never returned to
/// callers. This is the single mapping of `rocksdb_pinnableslice_value`/`_destroy`;
/// those three classes used to each map the same two symbols independently. Beyond that
/// mapping, this class also owns every way a pinned value gets consumed — copy to
/// `byte[]`, copy into a caller's buffer, or a zero-copy [Mapper] callback — so callers
/// never touch the raw pointer.
final class PinnableSlice extends NativeObject {

	/// `const char* rocksdb_pinnableslice_value(const rocksdb_pinnableslice_t* t, size_t* vlen);`
	private static final MethodHandle MH_VALUE;
	/// `void rocksdb_pinnableslice_destroy(rocksdb_pinnableslice_t* v);`
	private static final MethodHandle MH_DESTROY;

	static {
		MH_VALUE = NativeLibrary.lookup("rocksdb_pinnableslice_value",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_pinnableslice_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	private PinnableSlice(MemorySegment ptr) {
		super(ptr);
	}

	/// Wraps `ptr`, or returns `null` if `ptr` is `MemorySegment.NULL` — the
	/// `get_pinned`/`get_pinned_cf` downcalls return NULL for NotFound or error (the two
	/// are distinguished only by `errptr`, which callers must check separately).
	///
	/// @param ptr the raw `rocksdb_pinnableslice_t*`, or `MemorySegment.NULL`
	/// @return a wrapper owning `ptr`, or `null` if `ptr` is `MemorySegment.NULL`
	static PinnableSlice wrapOrNull(MemorySegment ptr) {
		return MemorySegment.NULL.equals(ptr) ? null : new PinnableSlice(ptr);
	}

	/// Copies this slice's value into a freshly allocated array.
	///
	/// @param arena arena to allocate the native length scratch slot in
	/// @return the value's bytes, copied into a new array
	byte[] toByteArray(Arena arena) {
		MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);
		MemorySegment data = value(lenSeg);
		long len = lenSeg.get(ValueLayout.JAVA_LONG, 0);
		return data.reinterpret(len).toArray(ValueLayout.JAVA_BYTE);
	}

	/// Copies this slice's value into `dest`, or reports insufficient capacity without
	/// copying anything.
	///
	/// @param arena       arena to allocate the native length scratch slot in
	/// @param dest        destination segment to copy into
	/// @param destCapacity `dest`'s usable capacity in bytes
	/// @return [CopyResult.Copied] on success, [CopyResult.NotEnoughCapacity] if `dest` is too small
	CopyResult copyInto(Arena arena, MemorySegment dest, long destCapacity) {
		MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);
		MemorySegment data = value(lenSeg);
		long len = lenSeg.get(ValueLayout.JAVA_LONG, 0);
		if (len > destCapacity) {
			return new CopyResult.NotEnoughCapacity(len);
		}
		dest.copyFrom(data.reinterpret(len));
		return new CopyResult.Copied();
	}

	/// [ByteBuffer] counterpart of [#copyInto(Arena, MemorySegment, long)]; also advances
	/// `dest`'s position by the copied length on success.
	///
	/// @param arena arena to allocate the native length scratch slot in
	/// @param dest  direct [ByteBuffer] to copy into
	/// @return [CopyResult.Copied] on success, [CopyResult.NotEnoughCapacity] if `dest` is too small
	CopyResult copyInto(Arena arena, ByteBuffer dest) {
		MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);
		MemorySegment data = value(lenSeg);
		long len = lenSeg.get(ValueLayout.JAVA_LONG, 0);
		if (len > dest.remaining()) {
			return new CopyResult.NotEnoughCapacity(len);
		}
		MemorySegment.ofBuffer(dest).copyFrom(data.reinterpret(len));
		dest.position(dest.position() + (int) len);
		return new CopyResult.Copied();
	}

	/// Maps this slice's value to a result via `fn`, with no intermediate copy. The view
	/// passed to `fn` is bound to `arena` and read-only; it must not be retained beyond
	/// the call, per [Mapper]'s contract.
	///
	/// @param arena arena to allocate the native length scratch slot in and bind the view to
	/// @param fn    callback invoked with a zero-copy view of this slice's value
	/// @param <R>   the type produced by `fn`
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the non-null result of `fn`
	<R> R map(Arena arena, Mapper<R> fn) {
		MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);
		MemorySegment data = value(lenSeg);
		long len = lenSeg.get(ValueLayout.JAVA_LONG, 0);
		// The `null` cleanup is deliberate: this view borrows from the slice, it does
		// not own the memory, so closing `arena` must not attempt to free it.
		MemorySegment view = data.reinterpret(len, arena, null).asReadOnly();
		R result = fn.map(view);
		Objects.requireNonNull(result, "Mapper.map(MemorySegment) must not return null");
		return result;
	}

	/// Returns the raw value pointer and writes its length into `vallenOut`, a
	/// caller-allocated `size_t*` slot.
	///
	/// @param vallenOut native slot to receive the value's length
	/// @return pointer to the value's bytes
	private MemorySegment value(MemorySegment vallenOut) {
		try {
			return (MemorySegment) MH_VALUE.invokeExact(ptr(), vallenOut);
		} catch (Throwable t) {
			throw RocksDBException.wrap("pinnableslice value failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
