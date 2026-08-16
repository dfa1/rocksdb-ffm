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

	/// Wraps `ptr`, which must not be `MemorySegment.NULL` — the `get_pinned`/
	/// `get_pinned_cf` downcalls return NULL for NotFound or error (the two are
	/// distinguished only by `errptr`), so callers must check for NULL themselves before
	/// calling this.
	///
	/// @param ptr the raw, non-NULL `rocksdb_pinnableslice_t*`
	/// @return a wrapper owning `ptr`
	static PinnableSlice wrap(MemorySegment ptr) {
		return new PinnableSlice(ptr);
	}

	/// Copies this slice's value into a freshly allocated array.
	///
	/// @param vallenOut native `size_t*` scratch slot to receive the value's length; the
	///                  caller's already-checked `errptr` slot can be reused here, since
	///                  it is dead by the time a [PinnableSlice] exists
	/// @return the value's bytes, copied into a new array
	byte[] toByteArray(MemorySegment vallenOut) {
		MemorySegment data = value(vallenOut);
		return RocksDB.toByteArray(data, vallenOut.get(ValueLayout.JAVA_LONG, 0));
	}

	/// Copies this slice's value into `dest`, or reports insufficient capacity without
	/// copying anything.
	///
	/// @param dest         destination segment to copy into
	/// @param destCapacity `dest`'s usable capacity in bytes
	/// @param vallenOut    native `size_t*` scratch slot to receive the value's length; the
	///                     caller's already-checked `errptr` slot can be reused here, since
	///                     it is dead by the time a [PinnableSlice] exists
	/// @return [CopyResult.Copied] on success, [CopyResult.NotEnoughCapacity] if `dest` is too small
	CopyResult copyInto(MemorySegment dest, long destCapacity, MemorySegment vallenOut) {
		MemorySegment data = value(vallenOut);
		long len = vallenOut.get(ValueLayout.JAVA_LONG, 0);
		if (len > destCapacity) {
			return new CopyResult.NotEnoughCapacity(len);
		}
		dest.copyFrom(data.reinterpret(len));
		return CopyResult.Copied.INSTANCE;
	}

	/// [ByteBuffer] counterpart of [#copyInto(MemorySegment, long, MemorySegment)]; also
	/// advances `dest`'s position by the copied length on success.
	///
	/// @param dest      direct [ByteBuffer] to copy into
	/// @param vallenOut native `size_t*` scratch slot to receive the value's length; the
	///                  caller's already-checked `errptr` slot can be reused here, since
	///                  it is dead by the time a [PinnableSlice] exists
	/// @return [CopyResult.Copied] on success, [CopyResult.NotEnoughCapacity] if `dest` is too small
	CopyResult copyInto(ByteBuffer dest, MemorySegment vallenOut) {
		MemorySegment data = value(vallenOut);
		long len = vallenOut.get(ValueLayout.JAVA_LONG, 0);
		if (len > dest.remaining()) {
			return new CopyResult.NotEnoughCapacity(len);
		}
		MemorySegment.ofBuffer(dest).copyFrom(data.reinterpret(len));
		dest.position(dest.position() + (int) len);
		return CopyResult.Copied.INSTANCE;
	}

	/// Maps this slice's value to a result via `fn`, with no intermediate copy. The view
	/// passed to `fn` is bound to `arena` and read-only; it must not be retained beyond
	/// the call, per [Mapper]'s contract.
	///
	/// @param arena     arena to bind the view to
	/// @param fn        callback invoked with a zero-copy view of this slice's value
	/// @param vallenOut native `size_t*` scratch slot to receive the value's length; the
	///                  caller's already-checked `errptr` slot can be reused here, since
	///                  it is dead by the time a [PinnableSlice] exists
	/// @param <R>       the type produced by `fn`
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the non-null result of `fn`
	<R> R map(Arena arena, Mapper<R> fn, MemorySegment vallenOut) {
		MemorySegment data = value(vallenOut);
		// The `null` cleanup is deliberate: this view borrows from the slice, it does
		// not own the memory, so closing `arena` must not attempt to free it.
		MemorySegment view = data.reinterpret(vallenOut.get(ValueLayout.JAVA_LONG, 0), arena, null).asReadOnly();
		R result = fn.map(view);
		Objects.requireNonNull(result, "Mapper.map(MemorySegment) must not return null");
		return result;
	}

	/// Invokes `rocksdb_pinnableslice_value`. Returns the raw, unsized value pointer —
	/// every caller reinterprets it to `vallenOut`'s length itself, at whichever arity it
	/// needs (plain, or arena-bound for [#map]), rather than this method doing a
	/// reinterpret that a caller would immediately reinterpret again.
	///
	/// @param vallenOut native `size_t*` scratch slot to receive the value's length
	/// @return the raw, unsized value pointer
	private MemorySegment value(MemorySegment vallenOut) {
		try {
			return (MemorySegment) MH_VALUE.invokeExact(ptr(), vallenOut);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("pinnableslice value failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
