# 0001: `java.lang.foreign` instead of JNI

## Status

Accepted

## Context

RocksDB's official Java binding, `rocksdbjni`, is a JNI wrapper. Every mapped function needs
hand-written C++ glue, compiled per platform, kept in sync with the C++ core by hand — and that glue
is JNI's inherent bottleneck: new features land in RocksDB's C++ core well before they reach the Java
API. At the time of writing, `rocksdbjni` has published no 11.x release at all, while the C API has
been at 11.x for a while.

The alternative was building this library the same way: a `RocksDB`-style JNI wrapper, hand-written
C++ glue per mapped function, one native module per platform compiled with a C++ toolchain. That path
was rejected in favor of `java.lang.foreign` (the Foreign Function & Memory API, stable since JDK 22 /
JEP 454).

## Decision

Build on `java.lang.foreign` instead of JNI. A new native function is a `MethodHandle` and a
`FunctionDescriptor` in a Java file — no second language in the build, no per-platform glue to
compile, and adding a mapping is a normal Java code review rather than a C++ change plus a
recompile-and-repackage step.

Three reasons, in order:

1. **No glue language.** Removes JNI's entire maintenance burden — hand-written C++ per function,
   kept in sync with the C++ core by hand.
2. **Safety, with an honest boundary.** On the Java side, FFM is strictly better than JNI: reading a
   closed or out-of-bounds `MemorySegment` throws instead of silently corrupting memory, and segment
   bounds are checked. It does not sandbox native execution, though — a bad pointer handed to RocksDB
   can still crash the JVM. See [0003](0003-ownership-model.md) for how that residual risk is
   contained.
3. **Performance.** FFM downcall stubs are JIT-compiled directly into the caller, with none of JNI's
   frame setup or thread-state transitions — roughly 2× on reads. See
   [benchmarks.md](../benchmarks.md) for the measurements and their caveats.

## Consequences

- **JDK 25+ is the floor.** `java.lang.foreign` is only stable from JDK 22; requiring 25+ keeps the
  baseline on an LTS release with no preview flags involved anywhere in the build.
- **The C API is the only surface this library can bind to.** FFM downcalls map C ABI functions;
  reaching anything that exists only in RocksDB's C++ core (persistent cache, wide columns, …) needs
  an upstream PR to `facebook/rocksdb` adding a C shim first — see
  [explanation.md#the-c-api-is-the-whole-contract](../explanation.md#the-c-api-is-the-whole-contract).
  This was accepted as the trade-off for having no C++ glue of our own to maintain.
- **A real native compiler is still needed to produce `librocksdb` itself** — FFM removes the *glue*
  layer, not the underlying native library. See [0002](0002-why-zig.md) for how that got solved
  without falling back to a per-platform build matrix.
