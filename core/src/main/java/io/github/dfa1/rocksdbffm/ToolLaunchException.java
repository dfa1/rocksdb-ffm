package io.github.dfa1.rocksdbffm;

/// Thrown when a bundled RocksDB command-line tool (`ldb`, `sst_dump`) could
/// not be launched or waited for — a missing bundled resource, an I/O failure
/// starting the subprocess, or this thread being interrupted while waiting for
/// it to finish.
///
/// This is distinct from a non-zero [ToolResult#exitCode()], which is a normal
/// answer from the tool (e.g. "this database is inconsistent"), not a failure
/// of this library's own plumbing.
public final class ToolLaunchException extends RuntimeException {

	/// Creates a new exception wrapping the underlying failure.
	///
	/// @param message a description of what failed
	/// @param cause   the underlying failure
	public ToolLaunchException(String message, Throwable cause) {
		super(message, cause);
	}
}
