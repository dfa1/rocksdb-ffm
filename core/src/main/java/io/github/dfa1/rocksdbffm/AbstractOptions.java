package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/// Shared plumbing for the cold-path, no-arg-getter/single-primitive-setter shape common to every
/// `*Options`-style native wrapper (`Options`, `ReadOptions`, `WriteOptions`, `CompactOptions`, ...).
///
/// This is a documented, scoped exception to CLAUDE.md's "never pass a MethodHandle as a method
/// parameter" rule: that rule protects hot-path `invokeExact` call sites (`Get`/`Put`/iterator),
/// where losing the JIT's `static final`-target constant-folding is measurable. Options getters and
/// setters are called a handful of times during setup/inspection, never in a per-operation loop, so
/// the lost constant-folding is immaterial — and consolidating dozens of near-identical
/// `catch (Throwable t)` blocks (permanently unreachable per ADR 0004) into one meaningfully shrinks
/// the amount of dead defensive code across the option classes. Do not use this pattern outside a
/// `*Options` class, and do not use it for a call site with more than one native-side value (a plain
/// getter taking only `ptr()`, or a plain setter taking `ptr()` plus exactly one primitive).
public abstract class AbstractOptions extends NativeObject {

	/// Initializes this options wrapper with the given native pointer.
	///
	/// @param ptr the non-NULL native pointer this object now owns
	protected AbstractOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Invokes a no-arg, `int`-returning getter `MethodHandle` against [#ptr()].
	///
	/// @param mh the `(ADDRESS)int` getter handle to invoke
	/// @return the native `int` value
	protected final int getInt(MethodHandle mh) {
		try {
			return (int) mh.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Options int getter failed", t);
		}
	}

	/// Invokes a single-`int`-argument, `void` setter `MethodHandle` against [#ptr()].
	///
	/// @param mh    the `(ADDRESS, JAVA_INT)void` setter handle to invoke
	/// @param value the native `int` value to set
	protected final void setInt(MethodHandle mh, int value) {
		try {
			mh.invokeExact(ptr(), value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Options int setter failed", t);
		}
	}

	/// Invokes a no-arg, `long`-returning getter `MethodHandle` against [#ptr()].
	///
	/// @param mh the `(ADDRESS)long` getter handle to invoke
	/// @return the native `long` value
	protected final long getLong(MethodHandle mh) {
		try {
			return (long) mh.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Options long getter failed", t);
		}
	}

	/// Invokes a single-`long`-argument, `void` setter `MethodHandle` against [#ptr()].
	///
	/// @param mh    the `(ADDRESS, JAVA_LONG)void` setter handle to invoke
	/// @param value the native `long` value to set
	protected final void setLong(MethodHandle mh, long value) {
		try {
			mh.invokeExact(ptr(), value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Options long setter failed", t);
		}
	}

	/// Invokes a no-arg, `double`-returning getter `MethodHandle` against [#ptr()].
	///
	/// @param mh the `(ADDRESS)double` getter handle to invoke
	/// @return the native `double` value
	protected final double getDouble(MethodHandle mh) {
		try {
			return (double) mh.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Options double getter failed", t);
		}
	}

	/// Invokes a single-`double`-argument, `void` setter `MethodHandle` against [#ptr()].
	///
	/// @param mh    the `(ADDRESS, JAVA_DOUBLE)void` setter handle to invoke
	/// @param value the native `double` value to set
	protected final void setDouble(MethodHandle mh, double value) {
		try {
			mh.invokeExact(ptr(), value);
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Options double setter failed", t);
		}
	}

	/// Invokes a no-arg, `unsigned char`-returning getter `MethodHandle` against [#ptr()], converting
	/// the native `0`/`1` byte to a Java `boolean` (see [RocksDB#fromByte(byte)]).
	///
	/// @param mh the `(ADDRESS)JAVA_BYTE` getter handle to invoke
	/// @return the decoded `boolean` value
	protected final boolean getBoolean(MethodHandle mh) {
		try {
			return RocksDB.fromByte((byte) mh.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Options boolean getter failed", t);
		}
	}

	/// Invokes a single-`unsigned char`-argument, `void` setter `MethodHandle` against [#ptr()],
	/// converting the Java `boolean` to the native `0`/`1` byte (see [RocksDB#toByte(boolean)]).
	///
	/// @param mh    the `(ADDRESS, JAVA_BYTE)void` setter handle to invoke
	/// @param value the `boolean` value to set
	protected final void setBoolean(MethodHandle mh, boolean value) {
		try {
			mh.invokeExact(ptr(), RocksDB.toByte(value));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("Options boolean setter failed", t);
		}
	}

	/// Invokes a no-arg, `size_t`-returning getter `MethodHandle` against [#ptr()], wrapping the
	/// native `long` byte count as a [MemorySize].
	///
	/// @param mh the `(ADDRESS)long` getter handle to invoke
	/// @return the decoded [MemorySize] value
	protected final MemorySize getMemorySize(MethodHandle mh) {
		return MemorySize.ofBytes(getLong(mh));
	}

	/// Invokes a single-`size_t`-argument, `void` setter `MethodHandle` against [#ptr()], unwrapping
	/// the [MemorySize] to its native byte count.
	///
	/// @param mh    the `(ADDRESS, JAVA_LONG)void` setter handle to invoke
	/// @param value the [MemorySize] value to set
	protected final void setMemorySize(MethodHandle mh, MemorySize value) {
		setLong(mh, value.toBytes());
	}
}
