package io.github.dfa1.rocksdbffm.sstdump;

import io.github.dfa1.rocksdbffm.NativeToolSupport;

import java.nio.file.Path;
import java.util.Arrays;

/// Command-line entry point forwarding `java ... io.github.dfa1.rocksdbffm.sstdump.Main <args>`
/// straight to the bundled `sst_dump` binary, inheriting this JVM's standard
/// input, output, and error streams and exiting with the same exit code.
///
/// Requires a `rocksdbffm-native-<platform>` dependency matching the current
/// platform on the classpath alongside this jar.
public final class Main {

	private Main() {
		// no instances
	}

	/// Runs `sst_dump` with the given command-line arguments and exits the JVM
	/// with its exit code.
	///
	/// @param args arguments to forward verbatim to `sst_dump`
	public static void main(String[] args) {
		Path toolDirectory = NativeToolSupport.extractToolDirectory();
		int exitCode = NativeToolSupport.runInherited(toolDirectory, "sst_dump", Arrays.asList(args));
		System.exit(exitCode);
	}
}
