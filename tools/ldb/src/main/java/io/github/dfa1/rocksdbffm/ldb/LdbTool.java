package io.github.dfa1.rocksdbffm.ldb;

import io.github.dfa1.rocksdbffm.NativeTool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Java wrapper around RocksDB's bundled `ldb` command-line tool — offline
/// database inspection and administration (consistency checks, MANIFEST
/// dumps, repair, and everything else `ldb` supports) that has no equivalent
/// in `rocksdb/include/rocksdb/c.h` and therefore no FFM-based counterpart
/// anywhere else in this library.
///
/// `ldb` has around three dozen subcommands, each with its own evolving flag
/// dialect (see `ldb --help`). Rather than chase that surface with a typed
/// Java method per subcommand, this class provides typed wrappers only for a
/// handful of high-value, stable operations, plus [#run(Path, String...)] as
/// an escape hatch for everything else.
///
/// A non-zero exit code from `ldb` (e.g. an inconsistent database) is a
/// legitimate answer from the tool, not a failure of this library, so it is
/// reported via [NativeTool.Result#exitCode()] rather than thrown. Only a
/// failure to launch or wait for the subprocess itself throws
/// [java.io.UncheckedIOException].
public final class LdbTool {

	private LdbTool() {
		// no instances
	}

	/// Runs `ldb checkconsistency` against the given database.
	///
	/// @param dbPath path to the RocksDB database directory
	/// @return the captured result; a non-zero exit code means the database is inconsistent
	public static NativeTool.Result checkConsistency(Path dbPath) {
		return run(dbPath, "checkconsistency");
	}

	/// Runs `ldb manifest_dump` against the given database, printing the
	/// contents of its current MANIFEST file.
	///
	/// @param dbPath path to the RocksDB database directory
	/// @param verbose whether to pass `--verbose` for more detailed output
	/// @return the captured result, with the MANIFEST dump in [NativeTool.Result#stdout()]
	public static NativeTool.Result manifestDump(Path dbPath, boolean verbose) {
		return verbose ? run(dbPath, "manifest_dump", "--verbose") : run(dbPath, "manifest_dump");
	}

	/// Runs `ldb repair` against the given database. Must not be run against a
	/// database that is currently open elsewhere.
	///
	/// @param dbPath path to the RocksDB database directory
	/// @param verbose whether to pass `--verbose` for more detailed output
	/// @return the captured result
	public static NativeTool.Result repair(Path dbPath, boolean verbose) {
		return verbose ? run(dbPath, "repair", "--verbose") : run(dbPath, "repair");
	}

	/// Runs `ldb` with an arbitrary subcommand and flags — the escape hatch
	/// for the many `ldb` subcommands not given a dedicated method above.
	///
	/// @param dbPath path to the RocksDB database directory, passed as `ldb`'s `--db` flag
	/// @param args    the subcommand and any flags, e.g. `"list_column_families"`
	/// @return the captured exit code, standard output, and standard error
	/// @throws java.io.UncheckedIOException if the `ldb` subprocess could not be started or waited for
	public static NativeTool.Result run(Path dbPath, String... args) {
		List<String> fullArgs = new ArrayList<>(args.length + 1);
		fullArgs.add("--db=" + dbPath);
		fullArgs.addAll(Arrays.asList(args));
		return NativeTool.run(NativeTool.extractToolDirectory(), "ldb", fullArgs);
	}
}
