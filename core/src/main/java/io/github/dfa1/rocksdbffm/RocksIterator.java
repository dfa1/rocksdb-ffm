package io.github.dfa1.rocksdbffm;

import javax.annotation.CheckReturnValue;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/// FFM wrapper for `rocksdb_iterator_t`.
///
/// Always close after use. Closing the owner first is safe: an iterator registers itself as a
/// [NativeObjectParent] child of the database that created it, or of the [Transaction] it
/// came from (`Transaction.newIterator`), the same way [Snapshot] does, so the owner closes any
/// still-open iterator itself, synchronously, before destroying its own native handle. A closed
/// iterator throws [IllegalStateException] on use.
///
/// ```
/// try (RocksIterator it = db.newIterator()) {
///     for (it.seekToFirst(); it.isValid(); it.next()) {
///         byte[] key   = it.key();
///         byte[] value = it.value();
///     }
///     it.checkError();
/// }
/// ```
public final class RocksIterator extends NativeObjectChild {

	/// `rocksdb_iterator_t* rocksdb_create_iterator(rocksdb_t* db, const rocksdb_readoptions_t* options);`
	private static final MethodHandle MH_CREATE = NativeLibrary.downcall(
		"rocksdb_create_iterator", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
	);
	/// `void rocksdb_iter_destroy(rocksdb_iterator_t*);`
	private static final MethodHandle MH_DESTROY = NativeLibrary.downcall(
		"rocksdb_iter_destroy", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);
	/// `unsigned char rocksdb_iter_valid(const rocksdb_iterator_t*);`
	private static final MethodHandle MH_VALID = NativeLibrary.downcall(
		"rocksdb_iter_valid", FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS)
	);
	/// `void rocksdb_iter_seek_to_first(rocksdb_iterator_t*);`
	private static final MethodHandle MH_SEEK_TO_FIRST = NativeLibrary.downcall(
		"rocksdb_iter_seek_to_first", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);
	/// `void rocksdb_iter_seek_to_last(rocksdb_iterator_t*);`
	private static final MethodHandle MH_SEEK_TO_LAST = NativeLibrary.downcall(
		"rocksdb_iter_seek_to_last", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);
	/// `void rocksdb_iter_seek(rocksdb_iterator_t*, const char* k, size_t klen);`
	private static final MethodHandle MH_SEEK = NativeLibrary.downcall(
		"rocksdb_iter_seek", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, NativeCalls.SIZE_T)
	);
	/// `void rocksdb_iter_seek_for_prev(rocksdb_iterator_t*, const char* k, size_t klen);`
	private static final MethodHandle MH_SEEK_FOR_PREV = NativeLibrary.downcall(
		"rocksdb_iter_seek_for_prev", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, NativeCalls.SIZE_T)
	);
	/// `void rocksdb_iter_next(rocksdb_iterator_t*);`
	private static final MethodHandle MH_NEXT = NativeLibrary.downcall(
		"rocksdb_iter_next", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);
	/// `void rocksdb_iter_prev(rocksdb_iterator_t*);`
	private static final MethodHandle MH_PREV = NativeLibrary.downcall(
		"rocksdb_iter_prev", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);
	// key/value only read the current entry's Slice (db/c.cc), so they are critical, with heap access for the
	// on-heap lenSegment.
	/// `const char* rocksdb_iter_key(const rocksdb_iterator_t*, size_t* klen);`
	private static final MethodHandle MH_KEY = NativeLibrary.downcall(
		"rocksdb_iter_key", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS), Linker.Option.critical(true)
	);
	/// `const char* rocksdb_iter_value(const rocksdb_iterator_t*, size_t* vlen);`
	private static final MethodHandle MH_VALUE = NativeLibrary.downcall(
		"rocksdb_iter_value", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS), Linker.Option.critical(true)
	);
	/// `void rocksdb_iter_get_error(const rocksdb_iterator_t*, char** errptr);`
	private static final MethodHandle MH_GET_ERROR = NativeLibrary.downcall(
		"rocksdb_iter_get_error", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
	);
	/// `void rocksdb_iter_refresh(const rocksdb_iterator_t* iter, char** errptr);`
	private static final MethodHandle MH_REFRESH = NativeLibrary.downcall(
		"rocksdb_iter_refresh", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
	);

	// The key/value length out-param, reused across calls. On the heap (a long[] is size_t-aligned), so there is no
	// native memory to free, from whichever thread a parent's close releases this iterator.
	private final MemorySegment lenSegment;

	/// Calls in flight on this iterator, so a close from another thread (its own, or a parent's sweep) waits for
	/// them instead of destroying the iterator under them. One plain counter: an iterator is used by one thread at
	/// a time, so there is nothing to stripe.
	private final AtomicInteger inFlight;

	/// The thread that made the latest [#enter()]. Written before the count is raised, so a close that sees the
	/// count also sees the writer: it tells a close from inside this iterator's own [Mapper], which must not wait
	/// for itself, from a close on another thread.
	private Thread lastCaller;

	/// Package-private: created via [#create].
	private RocksIterator(NativeObjectParent owningDb, ReadOptions readOptions, MemorySegment ptr) {
		super(ptr);
		this.lenSegment = MemorySegment.ofArray(new long[1]);
		this.inFlight = new AtomicInteger();
		// Two parents: the database or transaction, and the options, whose iterate bounds the native iterator keeps raw
		// pointers into. Holding them also keeps the options' bounds memory reachable.
		childOf(owningDb, readOptions);
	}

	/// Package-private factory called by RocksDB.
	static RocksIterator create(RocksDBReadOperations db, ReadOptions readOptions) {
		NativeDB guard = NativeDB.enter(db);
		try {
			MemorySegment iterPtr = (MemorySegment) MH_CREATE.invokeExact(db.dbPtr(), readOptions.readPtr());
			// Every implementor extends NativeObjectParent (see RocksDBReadOperations#getSnapshot()).
			return new RocksIterator((NativeObjectParent) db, readOptions, iterPtr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("iterator create failed", t);
		} finally {
			guard.exit();
		}
	}

	/// Package-private factory for a pre-created iterator pointer scoped to a column family
	/// (e.g. from `rocksdb_create_iterator_cf`), registered with `owningDb`.
	static RocksIterator create(NativeObjectParent owningDb, ReadOptions readOptions, MemorySegment iterPtr) {
		return new RocksIterator(owningDb, readOptions, iterPtr);
	}

	// -----------------------------------------------------------------------
	// Positioning
	// -----------------------------------------------------------------------

	/// Positions the iterator at the first key in the database.
	public void seekToFirst() {
		enter();
		try {
			MH_SEEK_TO_FIRST.invokeExact(ptr());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("seekToFirst failed", t);
		} finally {
			exit();
		}
	}

	/// Positions the iterator at the last key in the database.
	public void seekToLast() {
		enter();
		try {
			MH_SEEK_TO_LAST.invokeExact(ptr());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("seekToLast failed", t);
		} finally {
			exit();
		}
	}

	/// Positions the iterator at the first key >= `target`. Slow path: copies key.
	///
	/// @param target the seek target key
	public void seek(byte[] target) {
		enter();
		try (Arena arena = Arena.ofConfined()) {
			MH_SEEK.invokeExact(ptr(), NativeCalls.toNative(arena, target), (long) target.length);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("seek failed", t);
		} finally {
			exit();
		}
	}

	/// Positions the iterator at the first key >= `target`. Zero-copy for direct buffers.
	///
	/// @param target direct [ByteBuffer] containing the seek target key
	public void seek(ByteBuffer target) {
		enter();
		try {
			MH_SEEK.invokeExact(ptr(), MemorySegment.ofBuffer(target), (long) target.remaining());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("seek failed", t);
		} finally {
			exit();
		}
	}

	/// Positions the iterator at the first key >= `target`. Zero-copy.
	///
	/// @param target native segment containing the seek target key
	public void seek(MemorySegment target) {
		enter();
		try {
			MH_SEEK.invokeExact(ptr(), target, target.byteSize());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("seek failed", t);
		} finally {
			exit();
		}
	}

	/// Positions the iterator at the last key <= `target`. Slow path: copies key.
	///
	/// @param target the seek target key
	public void seekForPrev(byte[] target) {
		enter();
		try (Arena arena = Arena.ofConfined()) {
			MH_SEEK_FOR_PREV.invokeExact(ptr(), NativeCalls.toNative(arena, target), (long) target.length);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("seekForPrev failed", t);
		} finally {
			exit();
		}
	}

	/// Positions the iterator at the last key <= `target`. Zero-copy for direct buffers.
	///
	/// @param target direct [ByteBuffer] containing the seek target key
	public void seekForPrev(ByteBuffer target) {
		enter();
		try {
			MH_SEEK_FOR_PREV.invokeExact(ptr(), MemorySegment.ofBuffer(target), (long) target.remaining());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("seekForPrev failed", t);
		} finally {
			exit();
		}
	}

	/// Positions the iterator at the last key <= `target`. Zero-copy.
	///
	/// @param target native segment containing the seek target key
	public void seekForPrev(MemorySegment target) {
		enter();
		try {
			MH_SEEK_FOR_PREV.invokeExact(ptr(), target, target.byteSize());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("seekForPrev failed", t);
		} finally {
			exit();
		}
	}

	/// Moves to the next key. Only call when [#isValid()] is true.
	public void next() {
		enter();
		try {
			MH_NEXT.invokeExact(ptr());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("next failed", t);
		} finally {
			exit();
		}
	}

	/// Moves to the previous key. Only call when [#isValid()] is true.
	public void prev() {
		enter();
		try {
			MH_PREV.invokeExact(ptr());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("prev failed", t);
		} finally {
			exit();
		}
	}

	// -----------------------------------------------------------------------
	// State
	// -----------------------------------------------------------------------

	/// Returns true if the iterator is positioned at a valid key.
	///
	/// @return `true` if the iterator has a valid current position
	public boolean isValid() {
		enter();
		try {
			return NativeCalls.fromByte((byte) MH_VALID.invokeExact(ptr()));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("isValid failed", t);
		} finally {
			exit();
		}
	}

	/// Checks for any I/O error encountered during iteration.
	/// Always call after an iteration loop to detect background errors.
	/// Throws [RocksDBException] if an error occurred.
	public void checkError() {
		enter();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_GET_ERROR.invokeExact(ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getError failed", t);
		} finally {
			exit();
		}
	}

	/// Returns the iterator's current error without throwing.
	/// Returns [Optional#empty()] if the iterator is healthy, or an [Optional] containing
	/// the error message if an I/O or background error was encountered.
	/// Use [#checkError()] instead when you want an exception on error.
	///
	/// @return empty if no error, otherwise the error message
	public Optional<String> error() {
		enter();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_GET_ERROR.invokeExact(ptr(), err);
			MemorySegment errPtr = err.get(ValueLayout.ADDRESS, 0);
			return NativeCalls.toOptionalString(errPtr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("error failed", t);
		} finally {
			exit();
		}
	}

	/// Refreshes the iterator to reflect the latest DB state after mutations.
	/// The iterator is invalidated by this call regardless of the outcome; a subsequent
	/// `seek*` call is required to reposition it before further navigation.
	public void refresh() {
		enter();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_REFRESH.invokeExact(ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("refresh failed", t);
		} finally {
			exit();
		}
	}

	// -----------------------------------------------------------------------
	// Key/Value access — MemorySegment (zero-copy, scoped to the callback)
	// -----------------------------------------------------------------------

	/// Scoped zero-copy read of the current key. `fn` receives a read-only view valid
	/// only for the duration of this call.
	///
	/// The view is bound to an arena that closes the moment `fn` returns. [#next()],
	/// [#prev()], [#seek(byte[])], and friends reuse the iterator's internal buffer in
	/// place rather than allocating a new one, so a raw view that escaped past the next
	/// positioning call would not fail — it would silently start reporting a different
	/// key's bytes. Scoping the view to this call turns that into a loud failure instead:
	/// `IllegalStateException` if used after this call returns, `WrongThreadException` if
	/// handed to another thread.
	///
	/// Only call when [#isValid()] is true.
	///
	/// @param <R> the type produced by `fn`
	/// @param fn  callback invoked with a zero-copy view of the current key
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`
	public <R> R key(Mapper<R> fn) {
		enter();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment raw = readKey();
			MemorySegment view = raw.reinterpret(lenSegment.get(NativeCalls.SIZE_T, 0), arena, null).asReadOnly();
			R result = fn.map(view);
			Objects.requireNonNull(result, "Mapper.map(MemorySegment) must not return null");
			return result;
		} finally {
			exit();
		}
	}

	/// Scoped zero-copy read of the current value. See [#key(Mapper)] for the
	/// lifetime contract on the view passed to `fn`.
	///
	/// Only call when [#isValid()] is true.
	///
	/// @param <R> the type produced by `fn`
	/// @param fn  callback invoked with a zero-copy view of the current value
	/// @throws NullPointerException if `fn` returns `null`
	/// @return the result of `fn`
	public <R> R value(Mapper<R> fn) {
		enter();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment raw = readValue();
			MemorySegment view = raw.reinterpret(lenSegment.get(NativeCalls.SIZE_T, 0), arena, null).asReadOnly();
			R result = fn.map(view);
			Objects.requireNonNull(result, "Mapper.map(MemorySegment) must not return null");
			return result;
		} finally {
			exit();
		}
	}

	// -----------------------------------------------------------------------
	// Internal iteration — Stream
	// -----------------------------------------------------------------------

	/// Returns a [Stream] over the remaining entries from the current position, mapping each
	/// key and value through `keyMapper`/`valueMapper`. This has the same positioning contract
	/// as [#next()]: it never repositions the iterator on its own, so call [#seekToFirst()],
	/// [#seekToLast()], or [#seek(byte[])] (or an overload) first, exactly as the manual
	/// `for (it.seekToFirst(); it.isValid(); it.next())` loop requires. Do not call [#next()],
	/// [#prev()], or any positioning method while consuming the stream.
	///
	/// The returned stream does not take ownership of this iterator: closing the stream —
	/// directly, or via try-with-resources, since [Stream] is [AutoCloseable] — never closes
	/// this iterator. The iterator remains open and positioned wherever the stream stopped
	/// (at the end of the range, or wherever a short-circuiting operation like `.limit(n)`
	/// or `.findFirst()` left it), so it can be reseeked and streamed again, or closed by the
	/// caller when truly done. Reaching the natural end of the range calls [#checkError()]
	/// before signalling completion, so a background error during the scan surfaces as a
	/// [RocksDBException] from the stream itself instead of silently looking like a shorter,
	/// complete result; a stream abandoned early via a short-circuiting operation does not
	/// reach that check, so call [#checkError()] directly in that case if an error needs to be
	/// ruled out.
	///
	/// Both mappers run under the same scoped, zero-copy lifetime as [#key(Mapper)] and
	/// [#value(Mapper)]: nothing stops a mapper from returning the underlying
	/// [MemorySegment] view itself rather than a copy, but that view is only
	/// valid for the duration of the mapper call — retaining it past the call is the same
	/// use-after-scope bug [#key(Mapper)] already documents, just reached through the stream
	/// instead of a direct call.
	///
	/// @param <K>         the type produced from mapping each key
	/// @param <V>         the type produced from mapping each value
	/// @param keyMapper   callback invoked with a zero-copy view of each key
	/// @param valueMapper callback invoked with a zero-copy view of each value
	/// @return a stream of the mapped key/value pairs, in iteration order
	public <K, V> Stream<KeyValue<K, V>> stream(Mapper<K> keyMapper, Mapper<V> valueMapper) {
		RocksIteratorSpliterator<K, V> spliterator = new RocksIteratorSpliterator<>(this, keyMapper, valueMapper);
		return StreamSupport.stream(spliterator, false);
	}

	// -----------------------------------------------------------------------
	// Key/Value access — ByteBuffer (single copy into caller's buffer)
	// -----------------------------------------------------------------------

	/// Copies the current key into `dst`. Copies nothing when `dst`'s remaining capacity is
	/// too small. Only call when [#isValid()] is true.
	///
	/// See [CopyResult]'s class doc for the fill-mode contract on `dst`: on success only its
	/// position advances, so call `dst.flip()` before reading — a required extra step for
	/// callers migrating from `rocksdbjni`, whose equivalent flips for you.
	///
	/// @param dst destination buffer to copy the key into
	/// @return [CopyResult.Copied] if copied, or [CopyResult.NotEnoughCapacity] if `dst` is too small
	@CheckReturnValue
	public CopyResult key(ByteBuffer dst) {
		enter();
		try {
			MemorySegment raw = readKey();
			long len = lenSegment.get(NativeCalls.SIZE_T, 0);
			if (len > dst.remaining()) {
				return new CopyResult.NotEnoughCapacity(len);
			}
			MemorySegment.ofBuffer(dst).copyFrom(raw.reinterpret(len));
			dst.position(dst.position() + (int) len);
			return CopyResult.Copied.INSTANCE;
		} finally {
			exit();
		}
	}

	/// Copies the current value into `dst`. Copies nothing when `dst`'s remaining capacity is
	/// too small. Only call when [#isValid()] is true.
	///
	/// See [CopyResult]'s class doc for the fill-mode contract on `dst`: on success only its
	/// position advances, so call `dst.flip()` before reading — a required extra step for
	/// callers migrating from `rocksdbjni`, whose equivalent flips for you.
	///
	/// @param dst destination buffer to copy the value into
	/// @return [CopyResult.Copied] if copied, or [CopyResult.NotEnoughCapacity] if `dst` is too small
	@CheckReturnValue
	public CopyResult value(ByteBuffer dst) {
		enter();
		try {
			MemorySegment raw = readValue();
			long len = lenSegment.get(NativeCalls.SIZE_T, 0);
			if (len > dst.remaining()) {
				return new CopyResult.NotEnoughCapacity(len);
			}
			MemorySegment.ofBuffer(dst).copyFrom(raw.reinterpret(len));
			dst.position(dst.position() + (int) len);
			return CopyResult.Copied.INSTANCE;
		} finally {
			exit();
		}
	}

	// -----------------------------------------------------------------------
	// Key/Value access — byte[] (convenience, always copies)
	// -----------------------------------------------------------------------

	/// Returns a copy of the current key as a byte array.
	/// Slower than the MemorySegment or ByteBuffer variants due to heap allocation.
	/// Only call when [#isValid()] is true.
	///
	/// @return current key as a newly allocated byte array
	public byte[] key() {
		enter();
		try {
			MemorySegment raw = readKey();
			return NativeCalls.toByteArray(raw, lenSegment.get(NativeCalls.SIZE_T, 0));
		} finally {
			exit();
		}
	}

	/// Returns a copy of the current value as a byte array.
	/// Slower than the MemorySegment or ByteBuffer variants due to heap allocation.
	/// Only call when [#isValid()] is true.
	///
	/// @return current value as a newly allocated byte array
	public byte[] value() {
		enter();
		try {
			MemorySegment raw = readValue();
			return NativeCalls.toByteArray(raw, lenSegment.get(NativeCalls.SIZE_T, 0));
		} finally {
			exit();
		}
	}

	/// Invokes `MH_KEY`. Returns the raw, unsized key pointer — every caller reinterprets
	/// it to `lenSegment`'s length itself, at whichever arity it needs (plain, or
	/// arena-bound for [#key(Mapper)]), rather than this method doing a reinterpret that a
	/// caller would immediately reinterpret again.
	///
	/// @return the raw, unsized key pointer
	private MemorySegment readKey() {
		try {
			return (MemorySegment) MH_KEY.invokeExact(ptr(), lenSegment);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("key failed", t);
		}
	}

	/// Invokes `MH_VALUE`. Returns the raw, unsized value pointer — every caller
	/// reinterprets it to `lenSegment`'s length itself, at whichever arity it needs (plain,
	/// or arena-bound for [#value(Mapper)]), rather than this method doing a reinterpret
	/// that a caller would immediately reinterpret again.
	///
	/// @return the raw, unsized value pointer
	private MemorySegment readValue() {
		try {
			return (MemorySegment) MH_VALUE.invokeExact(ptr(), lenSegment);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("value failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// AutoCloseable
	// -----------------------------------------------------------------------

	/// Marks a call as in flight; pair with [#exit()] in a `finally`. The count is raised before the body reads
	/// [#ptr()], and a close swaps the pointer before reading the count, so one side always sees the other: either
	/// `ptr()` throws, or the close waits for [#exit()].
	private void enter() {
		lastCaller = Thread.currentThread();
		inFlight.getAndIncrement();
	}

	private void exit() {
		inFlight.getAndDecrement();
	}

	/// Closes this iterator, waiting for a call in flight on another thread to return first.
	///
	/// Must not be called from inside this iterator's own [Mapper]: the mapper is still reading the entry the
	/// close would free. Close it once the mapper has returned.
	///
	/// @throws IllegalStateException if called from inside this iterator's own [Mapper]
	@Override
	public void close() {
		if (inFlight.get() != 0 && lastCaller == Thread.currentThread()) {
			throw new IllegalStateException("an iterator cannot be closed from inside its own Mapper: "
					+ "close it once the Mapper has returned");
		}
		super.close();
	}

	/// Waits for calls in flight on other threads, then destroys the iterator. A parent closed from inside this
	/// iterator's own [Mapper] (its [ReadOptions] or transaction; [#close()] and a database close refuse earlier)
	/// cannot wait for itself: it throws, which the release logs, and the iterator is left undestroyed rather
	/// than freed under the mapper.
	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		while (inFlight.get() != 0) {
			if (lastCaller == Thread.currentThread()) {
				throw new IllegalStateException("an iterator cannot be closed from inside its own Mapper");
			}
			LockSupport.parkNanos(100_000);
		}
		MH_DESTROY.invokeExact(ptr);
	}
}
