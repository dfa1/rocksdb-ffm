package io.github.dfa1.rocksdbffm;

/// Unchecked exception thrown when a RocksDB native operation fails.
///
/// No public constructors. The two-arg (message, cause) constructor is private, reachable only
/// via [#wrap(String, Throwable)], so a [RocksDBException] can never be wrapped by itself. The
/// single-arg (message-only) constructor is package-private instead: used directly by
/// [RocksDB#checkError(java.lang.foreign.MemorySegment)] for RocksDB's own `errptr`-reported
/// errors, which have no Java [Throwable] to wrap in the first place, so there is no
/// double-wrap invariant to gate behind a factory method there.
public class RocksDBException extends RuntimeException {

	RocksDBException(String message) {
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
}
