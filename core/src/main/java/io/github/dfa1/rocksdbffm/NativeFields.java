package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/// Shared plumbing for the cold-path, no-arg-getter/single-primitive-setter shape common across
/// this library's native wrappers — every `*Options` class, plus a handful of other classes with
/// the same shape ([StatisticsHistogramData], [SstFileManager], [Env], [Cache], [Replayer],
/// [ColumnFamilyHandle], [Snapshot]). A call site passes its own `ptr()` alongside the
/// `MethodHandle`, the same way [RocksDB#wrapInvokeFailure(String, Throwable)] and friends are
/// already shared static FFM plumbing rather than instance state.
///
/// This is a documented, scoped exception to CLAUDE.md's "never pass a MethodHandle as a method
/// parameter" rule: that rule protects hot-path `invokeExact` call sites (`Get`/`Put`/iterator),
/// where losing the JIT's `static final`-target constant-folding is measurable. The classes that
/// call these helpers do so a handful of times during setup/inspection, never in a per-operation
/// loop, so the lost constant-folding is immaterial — and consolidating dozens of near-identical
/// `catch (Throwable t)` blocks (permanently unreachable per ADR 0004) into one meaningfully
/// shrinks the amount of dead defensive code across those classes. Do not call these from a
/// hot-path class (`WriteBatch`, `Transaction(DB)`, `RocksIterator`, `SstFileWriter`,
/// `PinnableSlice`/`PinnableHandle`, ...), and do not use it for a call site with more than one
/// native-side value (a plain getter taking only a pointer, or a plain setter taking a pointer
/// plus exactly one primitive).
final class NativeFields {

	private NativeFields() {
	}

	/// Invokes a no-arg, `int`-returning getter `MethodHandle` against `ptr`.
	///
	/// @param mh  the `(ADDRESS)int` getter handle to invoke
	/// @param ptr the native pointer to invoke it against
	/// @return the native `int` value
	static int getInt(MethodHandle mh, MemorySegment ptr) {
		try {
			return (int) mh.invokeExact(ptr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("native int getter failed", t);
		}
	}

	/// Invokes a single-`int`-argument, `void` setter `MethodHandle` against `ptr`.
	///
	/// @param mh    the `(ADDRESS, JAVA_INT)void` setter handle to invoke
	/// @param ptr   the native pointer to invoke it against
	/// @param value the native `int` value to set
	static void setInt(MethodHandle mh, MemorySegment ptr, int value) {
		try {
			mh.invokeExact(ptr, value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("native int setter failed", t);
		}
	}

	/// Invokes a no-arg, `long`-returning getter `MethodHandle` against `ptr`.
	///
	/// @param mh  the `(ADDRESS)long` getter handle to invoke
	/// @param ptr the native pointer to invoke it against
	/// @return the native `long` value
	static long getLong(MethodHandle mh, MemorySegment ptr) {
		try {
			return (long) mh.invokeExact(ptr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("native long getter failed", t);
		}
	}

	/// Invokes a single-`long`-argument, `void` setter `MethodHandle` against `ptr`.
	///
	/// @param mh    the `(ADDRESS, JAVA_LONG)void` setter handle to invoke
	/// @param ptr   the native pointer to invoke it against
	/// @param value the native `long` value to set
	static void setLong(MethodHandle mh, MemorySegment ptr, long value) {
		try {
			mh.invokeExact(ptr, value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("native long setter failed", t);
		}
	}

	/// Invokes a no-arg, `double`-returning getter `MethodHandle` against `ptr`.
	///
	/// @param mh  the `(ADDRESS)double` getter handle to invoke
	/// @param ptr the native pointer to invoke it against
	/// @return the native `double` value
	static double getDouble(MethodHandle mh, MemorySegment ptr) {
		try {
			return (double) mh.invokeExact(ptr);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("native double getter failed", t);
		}
	}

	/// Invokes a single-`double`-argument, `void` setter `MethodHandle` against `ptr`.
	///
	/// @param mh    the `(ADDRESS, JAVA_DOUBLE)void` setter handle to invoke
	/// @param ptr   the native pointer to invoke it against
	/// @param value the native `double` value to set
	static void setDouble(MethodHandle mh, MemorySegment ptr, double value) {
		try {
			mh.invokeExact(ptr, value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("native double setter failed", t);
		}
	}

	/// Invokes a no-arg, `unsigned char`-returning getter `MethodHandle` against `ptr`,
	/// converting the native `0`/`1` byte to a Java `boolean` (see [RocksDB#fromByte(byte)]).
	///
	/// @param mh  the `(ADDRESS)JAVA_BYTE` getter handle to invoke
	/// @param ptr the native pointer to invoke it against
	/// @return the decoded `boolean` value
	static boolean getBoolean(MethodHandle mh, MemorySegment ptr) {
		try {
			return RocksDB.fromByte((byte) mh.invokeExact(ptr));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("native boolean getter failed", t);
		}
	}

	/// Invokes a single-`unsigned char`-argument, `void` setter `MethodHandle` against `ptr`,
	/// converting the Java `boolean` to the native `0`/`1` byte (see [RocksDB#toByte(boolean)]).
	///
	/// @param mh    the `(ADDRESS, JAVA_BYTE)void` setter handle to invoke
	/// @param ptr   the native pointer to invoke it against
	/// @param value the `boolean` value to set
	static void setBoolean(MethodHandle mh, MemorySegment ptr, boolean value) {
		try {
			mh.invokeExact(ptr, RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("native boolean setter failed", t);
		}
	}

	/// Invokes a no-arg, `size_t`-returning getter `MethodHandle` against `ptr`, wrapping the
	/// native `long` byte count as a [MemorySize].
	///
	/// @param mh  the `(ADDRESS)long` getter handle to invoke
	/// @param ptr the native pointer to invoke it against
	/// @return the decoded [MemorySize] value
	static MemorySize getMemorySize(MethodHandle mh, MemorySegment ptr) {
		return MemorySize.ofBytes(getLong(mh, ptr));
	}

	/// Invokes a single-`size_t`-argument, `void` setter `MethodHandle` against `ptr`,
	/// unwrapping the [MemorySize] to its native byte count.
	///
	/// @param mh    the `(ADDRESS, JAVA_LONG)void` setter handle to invoke
	/// @param ptr   the native pointer to invoke it against
	/// @param value the [MemorySize] value to set
	static void setMemorySize(MethodHandle mh, MemorySegment ptr, MemorySize value) {
		setLong(mh, ptr, value.toBytes());
	}
}
