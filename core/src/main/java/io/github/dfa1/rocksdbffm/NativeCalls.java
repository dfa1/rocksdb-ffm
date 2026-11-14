package io.github.dfa1.rocksdbffm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/// Low-level FFM plumbing shared by every wrapper class: error-holder/`errptr` handling, native
/// string/byte marshaling, and `rocksdb_t*` lifecycle (`close`/`free`). Used well beyond
/// [RocksDBReadOperations]/[RocksDBWriteOperations] -- by [TransactionDB], [Transaction],
/// [WriteBatch], every `*Options` class, and effectively every other wrapper in the package --
/// so it lives in its own class rather than on any single capability interface's `Bindings`
/// companion. See [#131](https://github.com/dfa1/rocksdb-ffm/issues/131).
final class NativeCalls {

	/// `void rocksdb_close(rocksdb_t* db);`
	private static final MethodHandle MH_CLOSE;
	/// `void rocksdb_free(void* ptr);`
	static final MethodHandle MH_FREE = NativeLibrary.lookup("rocksdb_free",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

	static {
		MH_CLOSE = NativeLibrary.lookup("rocksdb_close",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	private NativeCalls() {
	}

	static void closeDb(MemorySegment db) throws Throwable {
		MH_CLOSE.invokeExact(db);
	}

	/// Creates a pre-zeroed error holder in the given arena.
	/// Use this for RocksDB C calls that take `char** errptr`.
	///
	/// @param arena arena to allocate the holder from
	/// @return a zeroed `char**` segment suitable for RocksDB error-out parameters
	static MemorySegment errHolder(Arena arena) {
		MemorySegment holder = arena.allocate(ValueLayout.ADDRESS);
		holder.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
		return holder;
	}

	/// Copies `bytes` into a new native memory segment allocated from `arena`.
	/// No null-terminator is appended; use the byte length when passing to C functions.
	///
	/// @param arena arena to allocate the segment from
	/// @param bytes source bytes to copy
	/// @return native segment containing a copy of `bytes`
	static MemorySegment toNative(Arena arena, byte[] bytes) {
		MemorySegment seg = arena.allocate(bytes.length);
		MemorySegment.copy(bytes, 0, seg, ValueLayout.JAVA_BYTE, 0, bytes.length);
		return seg;
	}

	/// Frees a malloc'd pointer returned by the RocksDB C API.
	///
	/// @param ptr pointer to free; must have been allocated by RocksDB
	static void free(MemorySegment ptr) {
		try {
			MH_FREE.invokeExact(ptr);
		} catch (Throwable _) {
			// ignore errors as this is used in destructor-like code
		}
	}

	/// Checks if the error holder contains a non-NULL pointer.
	/// If so, throws a [RocksDBException] and frees the C string.
	///
	/// @param errHolder the `char**` segment previously passed to a RocksDB C call
	static void checkError(MemorySegment errHolder) {
		MemorySegment errPtr = errHolder.get(ValueLayout.ADDRESS, 0);
		if (!MemorySegment.NULL.equals(errPtr)) {
			String msg = toJavaString(errPtr);
			throw new RocksDBException(msg);
		}
	}

	/// Classifies a `Throwable` caught from a `catch (Throwable t)` block wrapping an
	/// `invokeExact` call on a downcall [MethodHandle]. RocksDB itself reports operational
	/// failures via `errptr`, checked separately by [#checkError(MemorySegment)] -- typically
	/// called right after `invokeExact` inside the same `try`, so a genuine [RocksDBException]
	/// it throws reaches this method too, alongside whatever `invokeExact` itself might throw.
	/// See [ADR 0004](https://github.com/dfa1/rocksdbffm/blob/main/docs/adr/0004-error-handling.md).
	///
	/// Every `RuntimeException` -- a [RocksDBException] from `checkError`, or one of the small,
	/// fixed set `invokeExact` itself throws in practice for a downcall handle
	/// (`NullPointerException`, `IllegalStateException` including `WrongThreadException`,
	/// `WrongMethodTypeException`, `ClassCastException`, each indicating a concrete binding bug:
	/// wrong argument, closed/wrong-thread arena, mismatched `FunctionDescriptor`, bad cast) --
	/// propagates unwrapped, with its original type preserved. An [IOException] (not from
	/// `invokeExact` itself, which never throws a checked exception, but possible from other
	/// code sharing the same `try` block, e.g. file access) becomes an [UncheckedIOException],
	/// the standard idiom for surfacing it as unchecked. Anything else reaching this method
	/// should never actually happen for a correctly configured downcall handle, and becomes an
	/// [AssertionError].
	///
	/// @param message description used for the [UncheckedIOException]/[AssertionError] fallbacks
	/// @param t       the throwable caught from the `invokeExact` call's `try` block
	/// @return never returns; declared non-void so callers can write `throw wrapInvokeFailure(...)`
	static RuntimeException wrapInvokeFailure(String message, Throwable t) {
		if (t instanceof RuntimeException e) {
			throw e;
		}
		if (t instanceof IOException e) {
			throw new UncheckedIOException(message, e);
		}
		throw new AssertionError(message, t);
	}

	/// Copies `len` bytes out of a length-prefixed, non-owned native pointer (e.g. a `const
	/// char*` + separate `size_t*` out-param) into a new Java array. Unlike [#toJavaString],
	/// this does not free `ptr` -- use it for borrowed views the C API still owns, such as a
	/// pointer into an internal `std::string` that stays alive only as long as its parent object.
	///
	/// @param ptr non-NULL native pointer to a borrowed buffer
	/// @param len number of bytes to copy
	/// @return a new array containing a copy of the bytes
	static byte[] toByteArray(MemorySegment ptr, long len) {
		return ptr.reinterpret(len).toArray(ValueLayout.JAVA_BYTE);
	}

	/// Reads a NUL-terminated, non-owned `const char*` into a Java [String]. Unlike
	/// [#toJavaString], this does not free `ptr` -- use it for the same kind of borrowed view
	/// [#toByteArray] does, e.g. a pointer into an internal `std::string` that stays alive only
	/// as long as its parent object.
	///
	/// @param ptr non-NULL native pointer to a borrowed, NUL-terminated string
	/// @return the decoded string
	static String toBorrowedJavaString(MemorySegment ptr) {
		return ptr.reinterpret(Long.MAX_VALUE).getString(0);
	}

	/// Copies the raw bytes of a NUL-terminated, non-owned `const char*` into a new array, without
	/// decoding them. Unlike [#toBorrowedJavaString(MemorySegment)] this is lossless for bytes that
	/// are not valid UTF-8, e.g. a column family name written by another binding.
	///
	/// @param ptr non-NULL native pointer to a borrowed, NUL-terminated string
	/// @return the bytes before the terminating NUL
	static byte[] toBorrowedBytes(MemorySegment ptr) {
		MemorySegment all = ptr.reinterpret(Long.MAX_VALUE);
		long len = 0;
		while (all.get(ValueLayout.JAVA_BYTE, len) != 0) {
			len++;
		}
		return all.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
	}

	/// Guards against handing a native call an array with an unfilled (NULL) slot — every C
	/// API this backs (`rocksdb_open_column_families`, `rocksdb_ingest_external_file`, ...)
	/// dereferences each entry as a pointer with no null check of its own, so a slot a
	/// marshalling loop failed to populate would otherwise crash the JVM instead of failing
	/// in Java. Cheap (a handful of pointer comparisons at DB-open/ingest time, not a hot
	/// per-key path) insurance against that class of bug, whatever causes it.
	///
	/// @param arr  native `ADDRESS[n]` array to check
	/// @param n    number of entries to check
	/// @param what description of the array's contents, for the error message
	/// @throws AssertionError if any of the first `n` entries is `MemorySegment.NULL`
	static void requireNoNullEntries(MemorySegment arr, int n, String what) {
		for (int i = 0; i < n; i++) {
			if (MemorySegment.NULL.equals(arr.getAtIndex(ValueLayout.ADDRESS, i))) {
				throw new AssertionError(what + " has an unpopulated (NULL) entry at index " + i);
			}
		}
	}

	/// Decodes a borrowed, non-owned `const char*` + separate `size_t*` out-param pair as a
	/// UTF-8 [String], without freeing `ptr`. Same ownership contract as
	/// [#toByteArray(MemorySegment, long)]; use it for read-only accessors that hand back a view
	/// into a native `std::string` (e.g. event-listener job-info column family names and paths).
	///
	/// @param ptr native pointer to a borrowed buffer
	/// @param len number of bytes to decode
	/// @return the decoded string
	public static String toJavaString(MemorySegment ptr, long len) {
		return new String(toByteArray(ptr, len), StandardCharsets.UTF_8);
	}

	/// Converts a malloc'd, NUL-terminated `char*` returned by the RocksDB C API into a
	/// Java [String], then frees it.
	///
	/// @param ptr non-NULL `char*` allocated by RocksDB
	/// @return the decoded string
	static String toJavaString(MemorySegment ptr) {
		String s = ptr.reinterpret(Long.MAX_VALUE).getString(0);
		free(ptr);
		return s;
	}

	/// [#toJavaString(MemorySegment)] for C APIs that return NULL instead of a value.
	///
	/// @param ptr `char*` allocated by RocksDB, or `MemorySegment.NULL`
	/// @return the decoded string, or [Optional#empty()] if `ptr` is NULL
	static Optional<String> toOptionalString(MemorySegment ptr) {
		if (MemorySegment.NULL.equals(ptr)) {
			return Optional.empty();
		}
		return Optional.of(toJavaString(ptr));
	}

	/// Converts a Java `boolean` to the `unsigned char` (0 or 1) the C API expects.
	///
	/// @param value the boolean to convert
	/// @return `(byte) 1` if `value` is `true`, `(byte) 0` otherwise
	static byte toByte(boolean value) {
		return value ? (byte) 1 : (byte) 0;
	}

	/// [#toByte(boolean)] in reverse: converts a C API `unsigned char` result back to a Java `boolean`.
	///
	/// @param value the native byte to convert
	/// @return `false` if `value` is `0`, `true` otherwise
	static boolean fromByte(byte value) {
		return value != 0;
	}
}
