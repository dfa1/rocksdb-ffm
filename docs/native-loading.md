# Native Library Loading

## Architecture

The native loading model is: **one build, all platforms, runtime dispatch**.

```
build (Zig cross-compiles all targets)
  └── native/osx-aarch64/     → librocksdb.dylib → JAR resource /native/osx-aarch64/librocksdb.dylib
  └── native/linux-x86_64/    → librocksdb.so    → JAR resource /native/linux-x86_64/librocksdb.so
  └── native/windows-x86_64/  → librocksdb.dll   → JAR resource /native/windows-x86_64/librocksdb.dll

runtime (NativeLibrary.java)
  └── detect OS + arch → classifier → extract resource → load
```

## Cross-compilation

`scripts/build-rocksdb.sh` accepts a `<target-classifier>` argument and uses
`zig cc / zig c++` with `-target <zig-triple>` to cross-compile for any
supported macOS/Linux platform from any host, driving RocksDB's POSIX
`Makefile` (`make shared_lib`):

| Classifier       | Zig target triple    | Library            |
|------------------|-----------------------|--------------------|
| `osx-aarch64`    | `aarch64-macos`       | `librocksdb.dylib` |
| `linux-x86_64`   | `x86_64-linux-gnu`    | `librocksdb.so`    |
| `linux-aarch64`  | `aarch64-linux-gnu`   | `librocksdb.so`    |

RocksDB's Makefile has no Windows target, so Windows goes through a separate
script, `scripts/build-rocksdb-windows.sh`, which drives RocksDB's **CMake**
build instead (`CMAKE_SYSTEM_NAME=Windows`), with `zig cc / zig c++`
wrapped in thin scripts to act as a MinGW-w64-compatible cross compiler
(CMake requires `CC`/`CXX`/`AR`/`RANLIB` to each be a single executable,
unlike `make`, which accepts `CC="zig cc -target ..."` directly):

| Classifier         | Zig target triple      | Library          |
|---------------------|--------------------------|------------------|
| `windows-x86_64`    | `x86_64-windows-gnu`     | `librocksdb.dll` |
| `windows-aarch64`   | `aarch64-windows-gnu`    | `librocksdb.dll` |

Zig bundles clang, libc++, and the macOS/Linux/MinGW-w64 sysroots — no
separate sysroot needed. A single **macOS or Linux** CI job (or local
machine) can build all 5 targets, including both Windows classifiers.

The reverse isn't true: `build-rocksdb.sh` cannot build *any* classifier —
not even a macOS/Linux one — from a native **Windows** host, since RocksDB's
`build_detect_platform` relies on POSIX shell/`uname` semantics that a
Windows host doesn't have, on top of `make` itself being absent from
`windows-latest` runners. It detects a Windows host and skips cleanly
(exit 0, no output produced) rather than fail the whole build; CI relies on
the macOS/Linux matrix legs to build and validate those 3 classifiers, while
the `windows-latest` leg only builds/tests the 2 Windows ones.

Each `native/<classifier>` Maven module invokes the appropriate script at the
`generate-resources` phase and packages the resulting library as a classpath
resource. Because `exec-maven-plugin` cannot launch a `.sh` script directly
on native Windows (`CreateProcess error=193`), every native module's
`exec-maven-plugin` configuration invokes the script through `bash`
explicitly, which works uniformly across macOS, Linux, and Windows (via Git
Bash on `windows-latest` runners).

## Runtime dispatch (`NativeLibrary.java`)

On startup, `NativeLibrary` detects the current platform:

```java
String osName  = os.contains("mac") ? "osx" : os.contains("win") ? "windows" : "linux";
String archName = arch.equals("aarch64") || arch.equals("arm64") ? "aarch64" : "x86_64";
// → e.g. "linux-x86_64", "windows-x86_64"
```

It then loads `/native/<classifier>/librocksdb.<ext>` from the classpath,
extracts it to a temp file, and calls `SymbolLookup.libraryLookup()`. If no
bundled library matches the current platform, an `UnsatisfiedLinkError` is thrown
with a clear message.

An override is available for testing or custom builds:

```
-Drocksdb.lib.path=/path/to/librocksdb.so
```

## Distribution

All native modules are declared as unconditional dependencies (no Maven profiles).
The build always produces all platform artifacts; `NativeLibrary` loads the one
matching the current platform and ignores the rest.

```xml
<dependency>
    <groupId>io.github.dfa1</groupId>
    <artifactId>rocksdbffm-native-osx-aarch64</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.github.dfa1</groupId>
    <artifactId>rocksdbffm-native-linux-x86_64</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.github.dfa1</groupId>
    <artifactId>rocksdbffm-native-windows-x86_64</artifactId>
    <scope>runtime</scope>
</dependency>
```
