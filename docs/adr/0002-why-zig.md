# 0002: `zig cc`/`zig c++` as the native cross-compiler

## Status

Accepted

## Context

This library ships a real, compiled `librocksdb` for five platform classifiers
(`osx-aarch64`, `linux-x86_64`, `linux-aarch64`, `windows-x86_64`, `windows-aarch64`) — [0001](0001-ffm-instead-of-jni.md)
removes the *glue* layer, but a real native compiler is still needed to produce `librocksdb` itself.
Getting a `librocksdb.{dylib,so,dll}` for one platform is ordinary; getting all five, reproducibly,
from a single CI runner or a single contributor's laptop is the actual problem, and it has one
standard answer and one non-standard one:

- **The standard answer** is a per-target sysroot and cross toolchain: `binutils`/`gcc` triples,
  `osxcross` for macOS targets, MinGW-w64 for Windows, a matching glibc/musl for each Linux arch. This
  is what most native-library-in-a-JAR projects do (Docker images per target, `dockcross`, or a matrix
  of native runners each building only its own platform). It works, but it means either N build
  environments to maintain, or a real cross-toolchain install per target — neither is "one machine, one
  command."
- **The non-standard answer** is `zig cc`/`zig c++`: Zig's compiler driver bundles clang, libc++, and
  the macOS/Linux/MinGW-w64 sysroots for every target it supports, as part of the Zig download itself.
  `zig cc -target <triple>` is a drop-in C compiler for that triple, no separate sysroot or system
  toolchain install required.

## Decision

Compile `librocksdb` with `zig cc`/`zig c++` for every classifier, from any single host.

- macOS and Linux classifiers go through RocksDB's POSIX `Makefile` (`CC="zig cc" CXX="zig c++"
  PORTABLE=1 make shared_lib`), driven by `scripts/build-rocksdb.sh` — `make` accepts
  `CC="zig cc -target ..."` directly, so this needed no wrapper.
- Windows classifiers go through RocksDB's CMake build instead (`build_detect_platform` needs POSIX
  shell semantics the Makefile path doesn't have on Windows), driven by
  `scripts/build-rocksdb-windows.sh`. CMake requires `CC`/`CXX`/`AR`/`RANLIB` to each be a single
  executable, unlike `make`, so these are thin wrapper scripts around `zig cc -target ...`.

See [explanation.md#building-with-zig](../explanation.md#building-with-zig) for the exact target
triples and the current split of what builds where.

## Consequences

- **One host builds every classifier — including the CI matrix.** Each CI runner (macOS, two Linux
  archs, Windows) builds and executes all five classifiers via `zig cc`, using its own runner only to
  *validate* the classifier matching its own architecture actually runs; cross-compiled classifiers
  from that same runner are trusted on build success, not re-executed elsewhere.
- **A real, uncommon toolchain dependency.** Contributors need `zig` on `PATH` (pinned via
  `mlugg/setup-zig` in CI); this is a smaller ask than N cross-toolchains, but it is still a
  non-default tool most Java contributors will not already have.
- **Zig-specific quirks become this project's problem.** Two were hit and fixed during the Windows
  CMake path specifically: `zig ar` must be exported explicitly (`AR="zig ar"`) or a host's native
  `ar` produces an archive in the wrong convention for the target linker; and `zig cc`'s COFF/Windows
  driver does not support compiling multiple `.c` sources in one invocation with no explicit `-o`
  (`error: coff does not support linking multiple objects into one`), which RocksDB's own Makefile
  and lz4's vendored Makefile both do — worked around with a wrapper script that splits such
  invocations into one compile per source file.
- **A plain local build no longer needs to touch the other four classifiers.** Because any host can
  build any target, a local `mvn compile`/`mvn test` was, until it was fixed, hermetically
  cross-compiling RocksDB five times over — once per classifier — even though local development only
  ever needs the host's own. Maven profiles now default every non-host classifier's native build to
  skipped, with `-Pall-natives` (used by CI and releases) forcing the full set.
