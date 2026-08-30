package io.github.dfa1.rocksdbffm;

/// The captured outcome of running one of the bundled RocksDB command-line
/// tools (`ldb`, `sst_dump`) as a subprocess.
///
/// A non-zero [#exitCode()] is a normal, expected outcome for these tools —
/// e.g. `ldb checkconsistency` exiting non-zero means the database really is
/// inconsistent, not that something went wrong in this library — so it is
/// reported as a value here rather than thrown as an exception. Only a
/// failure to launch or wait for the subprocess itself throws
/// [ToolLaunchException].
///
/// @param exitCode the subprocess exit code
/// @param stdout   everything the subprocess wrote to standard output
/// @param stderr   everything the subprocess wrote to standard error
public record ToolResult(int exitCode, String stdout, String stderr) {

	/// Returns whether the subprocess exited with status `0`.
	///
	/// @return `true` if [#exitCode()] is `0`, `false` otherwise
	public boolean isSuccess() {
		return exitCode == 0;
	}
}
