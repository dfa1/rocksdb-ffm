# ADR 0002: Build the native library with `zig cc`/`zig c++`

- **Status:** Accepted
- **Date:** 2026-08-16
- **Deciders:** project maintainer

## Context

The project bundles `librocksdb` for five classifiers (`osx-aarch64`, `linux-x86_64`,
`linux-aarch64`, `windows-x86_64`, `windows-aarch64`). Unlike zstd, RocksDB is a large C++ project
with its own POSIX `Makefile` and (for platforms the Makefile doesn't cover) a CMake build. The build
must produce all five from CI, and ideally from a single contributor's laptop too, without per-target
cross-toolchains or per-platform runners doing the actual compiling.

## Decision

Compile `librocksdb` with `zig cc`/`zig c++` acting as drop-in C/C++ compilers, driving RocksDB's
*own* build systems rather than bypassing them:

- macOS and Linux classifiers go through RocksDB's POSIX `Makefile` (`CC="zig cc" CXX="zig c++"
  PORTABLE=1 make shared_lib`, `scripts/build-rocksdb.sh`) — `make` accepts `CC="zig cc -target ..."`
  directly.
- Windows classifiers go through RocksDB's CMake build instead (`build_detect_platform` needs POSIX
  shell semantics the Makefile path doesn't have on Windows), driven by
  `scripts/build-rocksdb-windows.sh`. CMake requires `CC`/`CXX`/`AR`/`RANLIB` to each be a single
  executable, so these are thin wrapper scripts around `zig cc -target ...`.

Zig bundles clang, libc++, and the macOS/Linux/MinGW-w64 sysroots for every target, so any single host
cross-compiles any classifier hermetically — no separate sysroot or system toolchain install.

## Consequences

### Positive

- One host builds every classifier — each CI runner (macOS, two Linux archs, Windows) builds and
  executes all five via `zig cc`, using its own runner only to *validate* the classifier matching its
  own architecture actually runs.
- Hermetic and reproducible: no dependence on the host's installed toolchain beyond `zig` itself,
  pinned via `mlugg/setup-zig` in CI.
- A plain local build only needs the host's own classifier — see
  [explanation.md#native-library-loading](../explanation.md#native-library-loading) for the Maven
  profile that skips the other four by default.

### Negative

- Adds a Zig toolchain dependency most Java contributors will not already have installed.
- `zig cc`/`zig c++` are clang wrappers, not RocksDB's own blessed build path — RocksDB upstream
  tests against gcc/clang/MSVC, not zig.
- The reverse does not work: `build-rocksdb.sh` cannot build *any* classifier from a native Windows
  host, since RocksDB's `build_detect_platform` relies on POSIX shell and `uname` semantics that
  aren't present there.

### Risks to manage

- Zig-specific quirks become this project's problem to work around, not RocksDB's or Zig's. Two hit
  on the Windows CMake path specifically: `zig ar` must be exported explicitly (`AR="zig ar"`), or a
  host's native `ar` produces an archive in the wrong convention for the target linker; and `zig cc`'s
  COFF/Windows driver does not support compiling multiple `.c` sources in one invocation with no
  explicit `-o` (`error: coff does not support linking multiple objects into one`), which both
  RocksDB's own Makefile and lz4's vendored Makefile do — worked around with a wrapper script that
  splits such invocations into one compile per source file.
- Zig version behavior can shift between releases; pinned via `mlugg/setup-zig` in CI, upgrades
  require a re-test of both the POSIX and Windows build paths.

## Alternatives considered

- **GitHub matrix runners per target, native toolchain each:** no fragmentation risk from a
  third-party compiler wrapper, but five separate build environments to maintain instead of one, and
  no way to verify a classifier's binary actually runs except on that classifier's own runner anyway.
- **Docker-based cross-compilation (e.g. `dockcross`):** the standard answer for this class of
  problem, used by other native-library-in-a-JAR projects. Works, but means either N build images to
  maintain or a real cross-toolchain install per target — neither is "one machine, one command," and
  it does not solve the Windows-via-CMake split RocksDB itself requires.
- **CMake/Make + cross sysroots per platform, no zig:** per-target toolchain setup, the misery this
  decision exists to avoid.

## References

- [scripts/build-rocksdb.sh](../../scripts/build-rocksdb.sh)
- [scripts/build-rocksdb-windows.sh](../../scripts/build-rocksdb-windows.sh)
- [explanation.md#building-with-zig](../explanation.md#building-with-zig)
- [ADR 0001 — FFM instead of JNI](0001-ffm-instead-of-jni.md)
