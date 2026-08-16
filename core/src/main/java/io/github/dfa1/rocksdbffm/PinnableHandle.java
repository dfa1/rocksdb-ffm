package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/// FFM wrapper for `rocksdb_pinnable_handle_t` — the pinned-value handle returned by the
/// newer `rocksdb_get_pinned_v2`/`_cf_v2` family. Not the same native type as
/// `rocksdb_pinnableslice_t` (see [PinnableSlice]): `Transaction` and `TransactionDB` have
/// no `_v2` equivalent in `rocksdb/include/rocksdb/c.h`, so they still go through the older
/// API and its own wrapper.
///
/// Package-private: purely internal plumbing for [RocksDB]'s `withPinned`/`withPinnedCf`
/// (the `Mapper`-based scoped get) and `getBytes`/`getCfBytes` — never returned to callers.
/// Mirrors [PinnableSlice]'s shape so both native handle kinds get the same treatment:
/// this is the single mapping of `rocksdb_pinnable_handle_get_value`/`_destroy`, and it
/// owns every way a pinned value gets consumed.
final class PinnableHandle extends NativeObject {

	/// `const char* rocksdb_pinnable_handle_get_value(const rocksdb_pinnable_handle_t* handle, size_t* vallen);`
	private static final MethodHandle MH_GET_VALUE;
	/// `void rocksdb_pinnable_handle_destroy(rocksdb_pinnable_handle_t* handle);`
	private static final MethodHandle MH_DESTROY;

	static {
		// get_value/destroy are pure in-memory pointer arithmetic — safe and worth
		// marking critical(false) (no heap-array access) to skip transition overhead.
		MH_GET_VALUE = NativeLibrary.lookup("rocksdb_pinnable_handle_get_value",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
				Linker.Option.critical(false));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_pinnable_handle_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
				Linker.Option.critical(false));
	}

	private PinnableHandle(MemorySegment ptr) {
		super(ptr);
	}

	/// Wraps `ptr`, which must not be `MemorySegment.NULL` — the `get_pinned_v2`/
	/// `get_pinned_cf_v2` downcalls return NULL for NotFound or error (the two are
	/// distinguished only by `errptr`), so callers must check for NULL themselves before
	/// calling this.
	///
	/// @param ptr the raw, non-NULL `rocksdb_pinnable_handle_t*`
	/// @return a wrapper owning `ptr`
	static PinnableHandle wrap(MemorySegment ptr) {
		return new PinnableHandle(ptr);
	}

	/// Copies this handle's value into a freshly allocated array.
	///
	/// @param vallenOut native `size_t*` scratch slot to receive the value's length; the
	///                  caller's already-checked `errptr` slot can be reused here, since
	///                  it is dead by the time a [PinnableHandle] exists
	/// @return the value's bytes, copied into a new array
	byte[] toByteArray(MemorySegment vallenOut) {
		MemorySegment data = value(vallenOut);
		return RocksDB.toByteArray(data, vallenOut.get(ValueLayout.JAVA_LONG, 0));
	}

	/// Maps this handle's value to a result via `fn`, with no intermediate copy. The view
	/// passed to `fn` is bound to `arena` and read-only; it must not be retained beyond
	/// the call, per [Mapper]'s contract.
	///
	/// @param arena     arena to bind the view to
	/// @param fn        callback invoked with a zero-copy view of this handle's value
	/// @param vallenOut native `size_t*` scratch slot to receive the value's length; the
	///                  caller's already-checked `errptr` slot can be reused here, since
	///                  it is dead by the time a [PinnableHandle] exists
	/// @param <R>       the type produced by `fn`
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the non-null result of `fn`
	<R> R map(Arena arena, Mapper<R> fn, MemorySegment vallenOut) {
		MemorySegment data = value(vallenOut);
		// The `null` cleanup is deliberate: this view borrows from the handle, it does
		// not own the memory, so closing `arena` must not attempt to free it.
		MemorySegment view = data.reinterpret(vallenOut.get(ValueLayout.JAVA_LONG, 0), arena, null).asReadOnly();
		R result = fn.map(view);
		Objects.requireNonNull(result, "Mapper.map(MemorySegment) must not return null");
		return result;
	}

	/// Invokes `rocksdb_pinnable_handle_get_value`. Returns the raw, unsized value
	/// pointer — every caller reinterprets it to `vallenOut`'s length itself, at whichever
	/// arity it needs (plain, or arena-bound for [#map]), rather than this method doing a
	/// reinterpret that a caller would immediately reinterpret again.
	///
	/// @param vallenOut native `size_t*` scratch slot to receive the value's length
	/// @return the raw, unsized value pointer
	private MemorySegment value(MemorySegment vallenOut) {
		try {
			return (MemorySegment) MH_GET_VALUE.invokeExact(ptr(), vallenOut);
		} catch (Throwable t) {
			throw RocksDBException.wrap("pinnable_handle_get_value failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
