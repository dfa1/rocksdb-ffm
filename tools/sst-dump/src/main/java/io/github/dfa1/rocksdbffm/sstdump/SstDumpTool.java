package io.github.dfa1.rocksdbffm.sstdump;

import io.github.dfa1.rocksdbffm.NativeTool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Java wrapper around RocksDB's bundled `sst_dump` command-line tool — direct
/// inspection of a single SST file or a directory of them, with no equivalent
/// in `rocksdb/include/rocksdb/c.h` and therefore no FFM-based counterpart
/// anywhere else in this library.
///
/// Unlike `ldb`, `sst_dump`'s command surface is small and fixed (see
/// [SstDumpCommand]), so it gets a typed builder rather than an escape-hatch
/// method. [#request(Path)] starts a request against a file or directory;
/// [Request#run()] executes it.
///
/// A non-zero exit code (e.g. a corrupted SST file under `check`/`verify`) is
/// a legitimate answer from the tool, not a failure of this library, so it is
/// reported via [NativeTool.Result#exitCode()] rather than thrown. Only a
/// failure to launch or wait for the subprocess itself throws
/// [java.io.UncheckedIOException].
public final class SstDumpTool {

	private SstDumpTool() {
		// no instances
	}

	/// Starts a request against the given SST file or directory of SST files.
	/// [Request#command(SstDumpCommand)] must be called before [Request#run()].
	///
	/// @param target path to an SST file, or a directory containing SST files
	/// @return a new request, further configurable before calling [Request#run()]
	public static Request request(Path target) {
		return new Request(target);
	}

	/// Runs `sst_dump --command=identify` against the given file or directory.
	///
	/// @param target path to an SST file, or a directory containing SST files
	/// @return the captured result
	public static NativeTool.Result identify(Path target) {
		return request(target).command(SstDumpCommand.IDENTIFY).run();
	}

	/// A configurable `sst_dump` invocation against one file or directory.
	/// Obtain an instance via [SstDumpTool#request(Path)].
	public static final class Request {

		private final Path target;
		private SstDumpCommand command;
		private String from;
		private String to;
		private String prefix;
		private boolean outputHex;
		private boolean verifyChecksum;
		private boolean showProperties;
		private Integer readNum;
		private final List<String> extraArgs = new ArrayList<>();

		private Request(Path target) {
			this.target = target;
		}

		/// Sets which `sst_dump` command to run.
		///
		/// @param command the command to run
		/// @return this request, for chaining
		public Request command(SstDumpCommand command) {
			this.command = command;
			return this;
		}

		/// Sets the key to start reading from, for `check`/`scan`.
		///
		/// @param from the starting user key
		/// @return this request, for chaining
		public Request from(String from) {
			this.from = from;
			return this;
		}

		/// Sets the key to stop reading at, for `check`/`scan`.
		///
		/// @param to the ending user key
		/// @return this request, for chaining
		public Request to(String to) {
			this.to = to;
			return this;
		}

		/// Restricts `check`/`scan` to keys with the given prefix. Mutually
		/// exclusive with [#from(String)] in `sst_dump` itself.
		///
		/// @param prefix the user key prefix
		/// @return this request, for chaining
		public Request prefix(String prefix) {
			this.prefix = prefix;
			return this;
		}

		/// Prints keys and values in hex when combined with [SstDumpCommand#SCAN].
		///
		/// @return this request, for chaining
		public Request outputHex() {
			this.outputHex = true;
			return this;
		}

		/// Verifies the file checksum while executing `check`/`scan`.
		///
		/// @return this request, for chaining
		public Request verifyChecksum() {
			this.verifyChecksum = true;
			return this;
		}

		/// Prints table properties after iterating over the file.
		///
		/// @return this request, for chaining
		public Request showProperties() {
			this.showProperties = true;
			return this;
		}

		/// Caps the number of entries read while executing `check`/`scan`.
		///
		/// @param readNum the maximum number of entries to read
		/// @return this request, for chaining
		public Request readNum(int readNum) {
			this.readNum = readNum;
			return this;
		}

		/// Appends raw `sst_dump` flags not covered by a dedicated method above
		/// — e.g. `recompress`-specific flags like `--compression_types`.
		///
		/// @param args raw command-line flags to append verbatim
		/// @return this request, for chaining
		public Request extraArgs(String... args) {
			extraArgs.addAll(Arrays.asList(args));
			return this;
		}

		/// Runs `sst_dump` with the configured command and flags.
		///
		/// @return the captured exit code, standard output, and standard error
		/// @throws IllegalStateException if [#command(SstDumpCommand)] was never called
		/// @throws java.io.UncheckedIOException if the `sst_dump` subprocess could not be started or waited for
		public NativeTool.Result run() {
			if (command == null) {
				throw new IllegalStateException("command must be set via Request#command(SstDumpCommand) before run()");
			}
			List<String> args = new ArrayList<>();
			args.add("--file=" + target);
			args.add("--command=" + command.flag());
			if (from != null) {
				args.add("--from=" + from);
			}
			if (to != null) {
				args.add("--to=" + to);
			}
			if (prefix != null) {
				args.add("--prefix=" + prefix);
			}
			if (outputHex) {
				args.add("--output_hex");
			}
			if (verifyChecksum) {
				args.add("--verify_checksum");
			}
			if (showProperties) {
				args.add("--show_properties");
			}
			if (readNum != null) {
				args.add("--read_num=" + readNum);
			}
			args.addAll(extraArgs);
			return NativeTool.run(NativeTool.extractToolDirectory(), "sst_dump", args);
		}
	}
}
