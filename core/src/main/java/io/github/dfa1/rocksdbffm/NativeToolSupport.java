package io.github.dfa1.rocksdbffm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// Infrastructure — extraction and execution support for the standalone
/// RocksDB command-line tools (`ldb`, `sst_dump`) bundled alongside the shared
/// library in each platform's `native/<classifier>` resources.
///
/// Unlike [NativeLibrary], which loads one shared library in-process for FFM
/// calls, these tools run as subprocesses via [ProcessBuilder]. The OS dynamic
/// linker resolves `ldb`/`sst_dump`'s own shared-library dependencies
/// (`librocksdb.*`, `librocksdb_tools.*`) by bare file name, so the whole set
/// of files for the current platform is extracted together, real file names
/// preserved, into one directory the dynamic linker is told to search.
public final class NativeToolSupport {

	private static final String[] RESOURCE_NAMES = {"ldb", "sst_dump", "librocksdb_tools.EXT", "librocksdb.EXT", "librocksdb.soname"};

	private NativeToolSupport() {
		// no instances
	}

	/// Extracts the current platform's bundled `ldb`, `sst_dump`, and their
	/// shared-library dependencies into one shared temporary directory,
	/// content-addressed and reused across calls the same way
	/// [NativeLibrary] reuses its own extracted library file.
	///
	/// @return the directory containing the extracted tool executables and libraries
	/// @throws UnsupportedOperationException if the current platform has no bundled tools (Windows)
	/// @throws UnsatisfiedLinkError           if the current platform's classifier has no bundled tool resources
	public static Path extractToolDirectory() {
		String classifier = NativeLibrary.classifier();
		if (classifier.startsWith("windows")) {
			throw new UnsupportedOperationException(
					"ldb/sst_dump are not bundled for platform " + classifier);
		}
		String ext = classifier.startsWith("osx") ? "dylib" : "so";

		String[] names = new String[RESOURCE_NAMES.length];
		byte[][] contents = new byte[RESOURCE_NAMES.length][];
		MessageDigest combined = newSha256();
		for (int i = 0; i < RESOURCE_NAMES.length; i++) {
			names[i] = RESOURCE_NAMES[i].replace("EXT", ext);
			contents[i] = readResource(classifier, names[i]);
			combined.update(contents[i]);
		}

		Path target = Path.of(System.getProperty("java.io.tmpdir"),
				"rocksdbffm-tools-" + HexFormat.of().formatHex(combined.digest()));
		if (Files.isDirectory(target)) {
			return target;
		}
		extract(target, names, contents);
		return target;
	}

	/// Runs an extracted tool executable, capturing its output instead of
	/// inheriting this JVM's standard streams — for programmatic callers that
	/// want the result as a value.
	///
	/// @param toolDirectory the directory returned by [#extractToolDirectory()]
	/// @param executable    file name of the executable inside `toolDirectory`, e.g. `"ldb"`
	/// @param args           command-line arguments to pass to the executable
	/// @return the captured exit code, standard output, and standard error
	/// @throws ToolLaunchException if the subprocess could not be started or waited for
	public static ToolResult run(Path toolDirectory, String executable, List<String> args) {
		Process process = start(toolDirectory, executable, args, false);
		// Read stdout and stderr concurrently: reading one to completion before
		// starting the other risks a deadlock if the unread stream's OS pipe
		// buffer fills up while the subprocess blocks trying to write to it.
		CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(
				() -> readAllBytesQuietly(process.getErrorStream()));
		try (InputStream out = process.getInputStream()) {
			String stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8);
			int exitCode = process.waitFor();
			return new ToolResult(exitCode, stdout, new String(stderr.join(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new ToolLaunchException(executable + " failed", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ToolLaunchException(executable + " was interrupted", e);
		}
	}

	private static byte[] readAllBytesQuietly(InputStream in) {
		try (in) {
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/// Runs an extracted tool executable with this JVM's standard input,
	/// output, and error streams inherited — for direct command-line
	/// passthrough (a `Main` class forwarding `java -jar` arguments straight
	/// to the tool).
	///
	/// @param toolDirectory the directory returned by [#extractToolDirectory()]
	/// @param executable    file name of the executable inside `toolDirectory`, e.g. `"ldb"`
	/// @param args           command-line arguments to pass to the executable
	/// @return the executable's exit code
	/// @throws ToolLaunchException if the subprocess could not be started or waited for
	public static int runInherited(Path toolDirectory, String executable, List<String> args) {
		Process process = start(toolDirectory, executable, args, true);
		try {
			return process.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ToolLaunchException(executable + " was interrupted", e);
		}
	}

	private static Process start(Path toolDirectory, String executable, List<String> args, boolean inherit) {
		List<String> command = new ArrayList<>(args.size() + 1);
		command.add(toolDirectory.resolve(executable).toString());
		command.addAll(args);
		ProcessBuilder pb = new ProcessBuilder(command);
		if (inherit) {
			pb.inheritIO();
		}
		String libraryPathVar = System.getProperty("os.name", "").toLowerCase().contains("mac")
				? "DYLD_LIBRARY_PATH" : "LD_LIBRARY_PATH";
		pb.environment().put(libraryPathVar, toolDirectory.toString());
		try {
			return pb.start();
		} catch (IOException e) {
			throw new ToolLaunchException("Failed to start " + executable, e);
		}
	}

	private static byte[] readResource(String classifier, String name) {
		String resource = "/native/" + classifier + "/" + name;
		try (InputStream in = NativeToolSupport.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new UnsatisfiedLinkError("No bundled " + name + " found for platform " + classifier);
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to extract bundled " + name, e);
		}
	}

	private static void extract(Path target, String[] names, byte[][] contents) {
		Path staging = null;
		try {
			staging = Files.createTempDirectory(target.getParent(), "rocksdbffm-tools-staging-");
			for (int i = 0; i < names.length; i++) {
				if ("librocksdb.soname".equals(names[i])) {
					continue;
				}
				Path file = staging.resolve(names[i]);
				Files.write(file, contents[i]);
				if ("ldb".equals(names[i]) || "sst_dump".equals(names[i])) {
					file.toFile().setExecutable(true);
				}
			}
			String soname = new String(contents[names.length - 1]).strip();
			Path mainLib = staging.resolve(names[names.length - 2]);
			try {
				Files.createSymbolicLink(staging.resolve(soname), mainLib.getFileName());
			} catch (FileAlreadyExistsException e) {
				// soname happens to equal the already-extracted main library name; nothing to link.
			}
			try {
				Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				// Another process already extracted (and may be executing from) the same
				// content-addressed directory; its content is byte-for-byte identical by
				// construction, so falling back to it is safe.
				if (!Files.isDirectory(target)) {
					throw e;
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to extract RocksDB tool binaries", e);
		} finally {
			if (staging != null) {
				deleteQuietly(staging);
			}
		}
	}

	private static void deleteQuietly(Path dir) {
		if (!Files.exists(dir)) {
			return;
		}
		try (var stream = Files.walk(dir)) {
			stream.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// best-effort cleanup of a staging directory
				}
			});
		} catch (IOException ignored) {
			// best-effort cleanup of a staging directory
		}
	}

	private static MessageDigest newSha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
