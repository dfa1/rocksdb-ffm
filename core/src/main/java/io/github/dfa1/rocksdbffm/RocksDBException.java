package io.github.dfa1.rocksdbffm;

/// Unchecked exception thrown when a RocksDB native operation fails.
///
/// No public constructors: every call site goes through [#wrap(String, Throwable)] or
/// [#of(String)], so a [RocksDBException] can never be wrapped by itself and every
/// construction site is one of these two, greppable entry points.
public class RocksDBException extends RuntimeException {

	private RocksDBException(String message) {
		super(message);
	}

	private RocksDBException(String message, Throwable cause) {
		super(message, cause);
	}

	/// Re-throws `t` as-is if it is already a [RocksDBException],
	/// otherwise wraps it. Use at the bottom of every `catch (Throwable t)` block:
	/// <pre>
	/// <code><jbr-internal-inline></jbr-internal-inline></code> catch (Throwable t) {
	///     throw RocksDBException.wrap("operation failed", t);
	/// }
	/// }
	/// </pre>
	///
	/// @param message description used if `t` is not already a [RocksDBException]
	/// @param t       the throwable to promote or wrap
	/// @return `t` cast to [RocksDBException], or a new one wrapping `t`
	public static RocksDBException wrap(String message, Throwable t) {
		return (t instanceof RocksDBException r) ? r : new RocksDBException(message, t);
	}

	/// Constructs an exception from a native-reported error message, with no underlying Java
	/// [Throwable] to wrap — used for RocksDB's own `errptr`-reported errors (see
	/// [RocksDB#checkError(java.lang.foreign.MemorySegment)]), as opposed to a failure caught
	/// from an FFM downcall itself (see [#wrap(String, Throwable)] for that case).
	///
	/// @param message the native error message
	/// @return a new [RocksDBException] with no cause
	public static RocksDBException of(String message) {
		return new RocksDBException(message);
	}
}
