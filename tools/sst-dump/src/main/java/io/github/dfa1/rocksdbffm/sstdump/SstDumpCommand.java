package io.github.dfa1.rocksdbffm.sstdump;

/// The `--command` values `sst_dump` accepts, corresponding to the tool's own
/// `check`, `scan`, `raw`, `verify`, `recompress`, and `identify` modes (see
/// `sst_dump --help`).
public enum SstDumpCommand {

	/// Iterates entries, printing nothing unless a corruption is found. The default command.
	CHECK("check"),
	/// Iterates entries, printing them to standard output.
	SCAN("scan"),
	/// Dumps all table contents to `<file_name>_dump.txt`.
	RAW("raw"),
	/// Iterates all blocks, verifying checksums, printing nothing unless a corruption is found.
	VERIFY("verify"),
	/// Reports the file size if recompressed with different compression types.
	RECOMPRESS("recompress"),
	/// Reports whether a file is a valid SST file, or lists all valid SST files under a directory.
	IDENTIFY("identify");

	private final String flag;

	SstDumpCommand(String flag) {
		this.flag = flag;
	}

	String flag() {
		return flag;
	}
}
