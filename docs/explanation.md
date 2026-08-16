# Explanation

Why the library is built the way it is: the constraints it accepts, the trade-offs behind the API
shape, and how the native library gets loaded.

Nothing here is needed to use the library — for that see the [tutorial](tutorial.md) and the
[how-to guides](how-to.md); for what exists, see [reference.md](reference.md).

- [Why FFM instead of JNI](#why-ffm-instead-of-jni)
- [The C API is the whole contract](#the-c-api-is-the-whole-contract)
- [Lifecycle and ownership](#lifecycle-and-ownership)
- [Only valid operations](#only-valid-operations)
- [Errors are always loud](#errors-are-always-loud)
- [Domain types instead of raw scalars](#domain-types-instead-of-raw-scalars)
- [Three access tiers](#three-access-tiers)
- [Static factories, no public constructors](#static-factories-no-public-constructors)
- [Native library loading](#native-library-loading)
- [Building with Zig](#building-with-zig)
- [What is not wrapped yet](#what-is-not-wrapped-yet)

---

## Why FFM instead of JNI

The official Java binding, `rocksdbjni`, is a JNI wrapper: every mapped function needs hand-written
C++ glue, compiled per platform, kept in sync with the C++ core. That glue is the bottleneck. New
features land in the RocksDB C++ core well before they reach the Java API — at the time of writing,
`rocksdbjni` has published no 11.x release at all, while the C API has been at 11.x for a while.

With `java.lang.foreign`, a new function is a `MethodHandle` and a `FunctionDescriptor` in a Java
file. There is no second language in the build, no per-platform glue to compile, and adding a
mapping is a normal Java code review.

The second reason is safety, with an honest boundary. On the Java side FFM is strictly better than
JNI: reading a closed or out-of-bounds `MemorySegment` throws instead of silently corrupting memory,
and segment bounds are checked. But FFM does not sandbox native execution — a bad pointer handed to
RocksDB can still take down the JVM. That is exactly why the ownership model below is not optional
bookkeeping.

The third is performance. FFM downcall stubs are JIT-compiled directly into the caller, with none of
JNI's frame setup or thread-state transitions. On reads that is worth roughly 2× — see
[benchmarks.md](benchmarks.md) for the measurements and their caveats.

The cost is the floor: `java.lang.foreign` is only stable from JDK 22 (JEP 454), and this project
requires **JDK 25+** so that no preview flags are involved and the baseline is an LTS release.

## The C API is the whole contract

This library maps `rocksdb/include/rocksdb/c.h` and nothing else. It never links C++ symbols
directly.

That is a deliberate constraint, not a limitation waiting to be lifted. The C API is a stable ABI
with a maintained deprecation policy; C++ symbols are mangled, compiler-dependent, and change shape
between releases. Binding to C means one `librocksdb` build works across compilers and platforms,
and the FFM mappings do not need to model C++ object layout.

The price is that when the C API does not expose something, neither can this library. Persistent
cache and wide columns are the current examples: they exist in C++ only, and reaching them requires
a PR to `facebook/rocksdb` adding the shim to `c.h`/`c.cc` before any Java work is possible. The
full breakdown of "C API exists, wrapper missing" versus "no C API yet" is in
[c-api-gaps.md](c-api-gaps.md).

## Lifecycle and ownership

Every native pointer is owned by a Java object extending `NativeObject`, which implements
`AutoCloseable`. There is no finalizer, no cleaner, and no GC-driven release: you close it, or it
leaks.

The interesting part is what happens when close is called *twice*, or from two threads. RocksDB's
destroy functions are not idempotent, and a double free crashes the JVM — a failure mode with no
Java-level stack trace and no exception to catch. `NativeObject` holds the pointer in an
`AtomicReference` and atomically swaps it to `MemorySegment.NULL` on close, so `tryClose` runs
exactly once no matter how many times or from how many threads `close()` is called. Using the
pointer afterwards throws `IllegalStateException` instead of dereferencing freed memory.

The second hazard is *ownership transfer*. Several C API calls hand a pointer's ownership to another
native object: `rocksdb_block_based_options_set_filter_policy` makes the table options responsible
for destroying the filter policy. Closing the Java wrapper afterwards would be a double free. The
library models this explicitly — the setter that takes ownership calls `transferOwnership()` on the
argument, which nulls its pointer, making its own `close()` a no-op. This is why the following is
correct rather than a bug:

```java
try (var policy = FilterPolicy.newBloom(10);
     var tableConfig = BlockBasedTableOptions.newBlockBasedConfig().setFilterPolicy(policy);
     var options = Options.newOptions().setTableFormatConfig(tableConfig)) {
	// policy.close() and tableConfig.close() are no-ops; the chain is freed once, at the top
}
```

Callers get to write ordinary try-with-resources everywhere and never think about which C function
consumed which pointer. Determining *which* calls transfer ownership is not guesswork: it is read
off `db/c.cc` (does `rocksdb_block_based_options_destroy` delete the filter policy?), the C API
docs, or how `rocksdbjni` handles the same object.

## Only valid operations

Each way of opening a database gets its own Java type, exposing only the operations that are
meaningful for it: `ReadOnlyDB`, `SecondaryDB`, `TtlDB`, `BlobDB`, `TransactionDB`,
`OptimisticTransactionDB`, `ReadWriteDB`.

`rocksdbjni` uses a single `RocksDB` type for every open mode. Calling `put()` on a read-only
instance compiles and runs; underneath, the C++ object is a `DBImplReadOnly` whose write methods all
return `Status::NotSupported`, which JNI converts into a thrown exception. The mistake is only
visible in production.

Here `ReadOnlyDB` has no `put`, `delete`, `merge`, or `write` method, so the same mistake is a
compile error. The cost is more types and some duplicated method signatures across them; the benefit
is that the type system, not a runtime status code, carries the constraint. The full capability
matrix is in [reference.md#db-types](reference.md#db-types).

## Errors are always loud

Every operation that can fail throws `RocksDBException`, unchecked. There is no `Status` object to
inspect, no `-1` return, no error code a caller can forget to check.

`null` survives in exactly one place: `byte[] get(byte[])` returns `null` for a missing key, because
a miss is a normal outcome on the hot read path and boxing it in an `Optional` would allocate on
every read. The buffer and segment tiers do better — they return a sealed `CopyResult`, so "the key
is absent" and "the value did not fit in your buffer" are different cases the compiler makes you
handle:

```java
switch (db.get(key, dst)) {
	case CopyResult.Copied() -> dst.flip();
	case CopyResult.NotEnoughCapacity(long required) -> retryWithCapacity(required);
	case CopyResult.NotFound() -> handleMiss();
}
```

That replaced an earlier `int` return carrying the value length, or `-1` for not-found. The old
encoding let a too-small destination silently truncate while still reporting the full length, and a
value larger than `Integer.MAX_VALUE` collided with the `-1` sentinel.

## Domain types instead of raw scalars

Raw numbers carry no unit and cannot be validated where they are created.

| Concept              | `rocksdbjni`             | `rocksdbffm`          |
|:---------------------|:-------------------------|:----------------------|
| Cache / buffer sizes | `long` (bytes, silently) | `MemorySize.ofMB(64)` |
| Snapshot position    | `long`                   | `SequenceNumber`      |
| Backup identity      | `int` (native `uint32`)  | `BackupId`            |
| Filesystem locations | `String`                 | `java.nio.file.Path`  |
| Durations            | `long` (seconds)         | `java.time.Duration`  |

All are immutable, `Comparable`, and reject invalid values at construction — an illegal value cannot
be built, so it cannot be passed anywhere. `MemorySize.ofMB(64)` also removes the class of bug where
a caller passes megabytes to an API expecting bytes.

`Path` additionally rules out passing a non-path string, keeps absolute/relative handling in the NIO
layer, and composes with `@TempDir` in tests.

`SequenceNumber` has one wrinkle worth knowing: RocksDB sequence numbers are `uint64`, so comparison
uses `Long.compareUnsigned`, but the factory rejects negative `long` values. Values above
`Long.MAX_VALUE` are therefore not constructible from Java — in practice unreachable, since it would
take on the order of 10^19 writes.

## Three access tiers

Every read and write comes in `byte[]`, `ByteBuffer`, and `MemorySegment` flavors. This is not
API bloat for its own sake — the three answer different questions about who owns the bytes:

- `byte[]` — the convenience tier. Allocates and copies in both directions. Documented as the slow
  one, and still the right default for application code that already has arrays.
- `ByteBuffer` — for code already built on NIO. Direct buffers are wrapped with
  `MemorySegment.ofBuffer(...)`, so no copy happens on the way in; the value is copied once into the
  caller's buffer.
- `MemorySegment` — the native-first tier. The caller supplies the arena, so the library allocates
  nothing and the JIT sees a plain memory access. Segments from a confined arena carry no GC
  scope-check overhead, which is why this is the fastest read path in the benchmarks.

Reads use `rocksdb_get_pinned` (a `PinnableSlice`) underneath, so the value is read straight out of
the block cache without an intermediate copy on the native side. Iterators go further: `key(Mapper)`
and `value(Mapper)` hand the callback a view pointing directly into RocksDB's own memory, with zero
copies. The trade-off is a lifetime rule enforced at runtime rather than compile time — the view is
bound to an arena that closes the moment the callback returns, so using it afterward throws instead
of silently reading whatever the next positioning call left behind.

## Static factories, no public constructors

Nothing in the library has a public constructor; objects come from named statics
(`Options.newOptions()`, `RocksDB.openReadOnly(path)`, `Checkpoint.newCheckpoint(db)`).

Two reasons. A constructor must call `super(...)` before anything else, which is impossible when the
pointer has to be obtained from a native call that can fail and needs error handling — a static
factory can do the work, check the error holder, and only then construct. And a named factory says
which *kind* of thing you are getting: `RocksDB.openReadWrite` versus `openReadOnly` versus `openTtl`
would otherwise all be constructor overloads distinguished only by argument types.

## Native library loading

The model is: **one build, all platforms, runtime dispatch.**

```
build (Zig cross-compiles every target)
  └── native/osx-aarch64/     → librocksdb.dylib → JAR resource /native/osx-aarch64/librocksdb.dylib
  └── native/linux-x86_64/    → librocksdb.so    → JAR resource /native/linux-x86_64/librocksdb.so
  └── native/windows-x86_64/  → librocksdb.dll   → JAR resource /native/windows-x86_64/librocksdb.dll

runtime (NativeLibrary.java)
  └── detect OS + arch → classifier → extract resource → load
```

`NativeLibrary` detects the platform on startup:

```java
String osName   = os.contains("mac") ? "osx" : os.contains("win") ? "windows" : "linux";
String archName = arch.equals("aarch64") || arch.equals("arm64") ? "aarch64" : "x86_64";
// → e.g. "linux-x86_64", "windows-x86_64"
```

It then loads `/native/<classifier>/librocksdb.<ext>` from the classpath, extracts it to a temp
file, and calls `SymbolLookup.libraryLookup()`. If no bundled library matches the platform, it
throws `UnsatisfiedLinkError` with a message naming the classifier it looked for.

There is **no** fallback to a Homebrew or system-installed `librocksdb`, by design: a system library
is a different build at a different version, and silently binding to it turns a missing-dependency
error into an undefined-behavior bug. The one supported override is explicit:

```
-Drocksdb.lib.path=/path/to/librocksdb.so
```

All native modules are ordinary unconditional dependencies — no Maven profiles. The build always
produces every platform artifact, applications declare the ones they ship to, and the loader ignores
the rest. Declaring all five is a normal thing to do for a cross-platform application.

## Building with Zig

The native library is compiled with `zig cc` / `zig c++` acting as drop-in C/C++ compilers. Zig
bundles clang, libc++, and the macOS/Linux/MinGW-w64 sysroots for every target, so a single macOS or
Linux machine can cross-compile all five classifiers hermetically, with no separate sysroot or
system toolchain.

macOS and Linux go through RocksDB's POSIX `Makefile` (`PORTABLE=1 make shared_lib`), driven by
`scripts/build-rocksdb.sh`:

| Classifier      | Zig target triple   | Library            |
|:----------------|:--------------------|:-------------------|
| `osx-aarch64`   | `aarch64-macos`     | `librocksdb.dylib` |
| `linux-x86_64`  | `x86_64-linux-gnu`  | `librocksdb.so`    |
| `linux-aarch64` | `aarch64-linux-gnu` | `librocksdb.so`    |

RocksDB's Makefile has no Windows target, so Windows goes through RocksDB's **CMake** build instead
(`CMAKE_SYSTEM_NAME=Windows`), driven by `scripts/build-rocksdb-windows.sh`. There, `zig cc` /
`zig c++` are wrapped in thin scripts to act as a MinGW-w64-compatible cross compiler, because CMake
requires `CC`/`CXX`/`AR`/`RANLIB` to each be a single executable — unlike `make`, which accepts
`CC="zig cc -target ..."` directly:

| Classifier        | Zig target triple     | Library          |
|:------------------|:----------------------|:-----------------|
| `windows-x86_64`  | `x86_64-windows-gnu`  | `librocksdb.dll` |
| `windows-aarch64` | `aarch64-windows-gnu` | `librocksdb.dll` |

The reverse does not work: `build-rocksdb.sh` cannot build *any* classifier — not even a
macOS/Linux one — from a native Windows host, because RocksDB's `build_detect_platform` relies on
POSIX shell and `uname` semantics, and `make` is absent from `windows-latest` runners altogether. It
detects a Windows host and exits cleanly with no output rather than failing the build. CI therefore
splits the work: the macOS/Linux legs build and validate the three POSIX classifiers, and the
Windows leg builds and tests the two Windows ones.

Each `native/<classifier>` Maven module runs the appropriate script at `generate-resources` and
packages the result as a classpath resource. Because `exec-maven-plugin` cannot launch a `.sh`
directly on native Windows (`CreateProcess error=193`), every native module invokes the script
through `bash` explicitly, which works uniformly on macOS, Linux, and Windows (via Git Bash on
`windows-latest`).

## What is not wrapped yet

Two different kinds of gap, and the distinction decides who can fix them:

- **Type A** — the C API exposes it, this library has no Java wrapper yet. Actionable here and now
  (MultiGet, CompactionFilter, EventListener, custom comparators, …).
- **Type B** — the C API does not expose it at all. Needs an upstream PR to `facebook/rocksdb`
  (`c.h` + `c.cc` + `c_test.c`) before any Java work is possible (persistent cache, wide columns,
  SST file reader, …).

The catalogue of both, with the specific C functions each would need, is in
[c-api-gaps.md](c-api-gaps.md). Current parity status against `rocksdbjni` is in
[reference.md#feature-status](reference.md#feature-status).
