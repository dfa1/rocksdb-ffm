package io.github.dfa1.rocksdbffm.ldb;

import io.github.dfa1.rocksdbffm.NativeToolSupport;

import java.nio.file.Path;
import java.util.Arrays;

/// Command-line entry point forwarding `java ... io.github.dfa1.rocksdbffm.ldb.Main <args>`
/// straight to the bundled `ldb` binary, inheriting this JVM's standard input,
/// output, and error streams and exiting with the same exit code.
///
/// Requires a `rocksdbffm-native-<platform>` dependency matching the current
/// platform on the classpath alongside this jar.
public final class Main {

	private Main() {
		// no instances
	}

	/// Runs `ldb` with the given command-line arguments and exits the JVM with
	/// its exit code.
	///
	/// @param args arguments to forward verbatim to `ldb`
	public static void main(String[] args) {
		Path toolDirectory = NativeToolSupport.extractToolDirectory();
		int exitCode = NativeToolSupport.runInherited(toolDirectory, "ldb", Arrays.asList(args));
		System.exit(exitCode);
	}
}
