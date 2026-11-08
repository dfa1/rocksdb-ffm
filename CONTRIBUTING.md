# Contributing to rocksdb-ffm

Thanks for your interest. This guide covers everything needed to build, test, and change the code.
Design rationale lives in [docs/explanation.md](docs/explanation.md); the full conventions agents and
humans follow are in [AGENTS.md](AGENTS.md).

## Prerequisites

- JDK 25+ (`java.lang.foreign` is final; no preview flags)
- [Zig](https://ziglang.org/) 0.17.x on `PATH` — compiles RocksDB via `zig cc`/`zig c++`
- [CMake](https://cmake.org/); on Windows also `make` or [Ninja](https://ninja-build.org/)
- Maven wrapper (`./mvnw`) is included — always use it, never `mvn`

Step-by-step first build and troubleshooting:
[docs/how-to.md#build-the-native-library-from-source](docs/how-to.md#build-the-native-library-from-source).

## Get the code

```bash
git clone --recurse-submodules https://github.com/dfa1/rocksdb-ffm.git
cd rocksdb-ffm
```

Cloned without `--recurse-submodules`? Run `git submodule update --init --recursive`.

## Build and test

```bash
# Build + test (cross-compiles RocksDB for your host platform only)
./mvnw test

# Single module / test class / test method
./mvnw test -pl core
./mvnw test -pl core -Dtest=MyTest
./mvnw test -pl core -Dtest=MyTest#myMethod

# Every native classifier, as CI and releases do
./mvnw package -Pall-natives
```

**Never run `./mvnw install`** — it pollutes `~/.m2` with local artifacts. Use `compile`, `test`, or
`package`.

## Making a change

- Code is indented with **tabs** (enforced by checkstyle); American English everywhere.
- Every class wrapping a native pointer extends `NativeObject`; no public constructors, use static
  factories. See [docs/explanation.md#lifecycle-and-ownership](docs/explanation.md#lifecycle-and-ownership).
- Only the C API (`rocksdb/c.h`) is bound. A missing wrapper is a Type A gap, a missing C function a
  Type B gap — see [docs/c-api-gaps.md](docs/c-api-gaps.md).
- New features come in three tiers: `MemorySegment`, `ByteBuffer`, `byte[]`.
- `MethodHandle`s are `private static final`, each with a `/// \`<c prototype>\`` comment copied from
  `c.h`. Errors go through `NativeCalls.errHolder`/`checkError`/`wrapInvokeFailure`.

## Testing

JUnit 5 + AssertJ, with `@TempDir` for database directories. Every test follows
`// Given` / `// When` / `// Then`; the `// Then` assertions operate on a variable captured in
`// When`, never inline.

## Javadoc

Every public method needs complete Javadoc; the build fails on errors and warnings. Verify with the
same goal CI runs:

```bash
./mvnw package -pl core -am -DskipTests
```

## Documentation

Docs follow [Diátaxis](https://diataxis.fr/): put prose in the page matching its mode (tutorial,
how-to, reference, explanation) and cross-link rather than repeat. Java snippets in `docs/` must
compile against `core`. The mapping is in [AGENTS.md](AGENTS.md#documentation).

## Changelog

User-visible changes get a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md); the section is
published as the GitHub release notes. Link the pull request that introduces the change, so readers can
find the context, and the issue it closes if any. Contributions from outside the maintainer end with
`— thanks [@your-handle](https://github.com/your-handle)`; the release notes show a bare `@your-handle`, which notifies the contributor when the release is published.

## Benchmarks

Performance claims need JMH numbers:

```bash
./mvnw test-compile -q
./scripts/benchmark.sh
```

Methodology and results: [docs/benchmarks.md](docs/benchmarks.md).
