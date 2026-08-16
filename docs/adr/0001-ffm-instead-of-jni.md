# ADR 0001: FFM bindings over JNI

- **Status:** Accepted
- **Date:** 2026-08-16
- **Deciders:** project maintainer

## Context

Calling the RocksDB C library from Java needs a native bridge. The established option is JNI — what
`rocksdbjni`, RocksDB's official Java binding, uses: hand-written C++ glue per mapped function,
compiled per platform, kept in sync with the C++ core by hand. That glue is JNI's inherent
bottleneck: new features land in RocksDB's C++ core well before they reach the Java API. At the time
of writing, `rocksdbjni` has published no 11.x release at all, while the C API has been at 11.x for a
while. JDK 25 ships the stable Foreign Function & Memory API (`java.lang.foreign`), which calls C
directly from Java with no C++ glue.

## Decision

Use the FFM API exclusively. No JNI, no hand-written C++, no generated stubs. Native symbols bind
directly to `MethodHandle`s, one per `rocksdb/include/rocksdb/c.h` function; the library targets
JDK 25+, where `java.lang.foreign` is stable (JEP 454) with no preview flags involved.

## Consequences

### Positive

- Zero C++ glue to maintain, review, or compile — a new mapped function is a `MethodHandle` and a
  `FunctionDescriptor` in a Java file, reviewed like any other Java change.
- Enables the zero-copy `MemorySegment` tier: `rocksdb_get_pinned` reads a value straight out of the
  block cache, and iterator `key(Mapper)`/`value(Mapper)` hand callbacks a view directly into
  RocksDB's own memory. JNI must copy across the boundary; FFM can pass native addresses directly.
- On the Java side, FFM is strictly safer than JNI: reading a closed or out-of-bounds `MemorySegment`
  throws instead of silently corrupting memory, and segment bounds are checked.
- Roughly 2× throughput on reads — FFM downcall stubs are JIT-compiled directly into the caller, with
  none of JNI's frame setup or thread-state transitions. See [benchmarks.md](../benchmarks.md) for the
  measurements and their caveats.

### Negative

- Hard floor at JDK 25. No JDK 17/21 support.
- FFM downcalls are a restricted operation: callers must pass `--enable-native-access=ALL-UNNAMED`.
- The C API is the only surface this library can bind to — anything that exists only in RocksDB's
  C++ core (persistent cache, wide columns, …) needs an upstream PR to `facebook/rocksdb` adding a C
  shim first. See [c-api-gaps.md](../c-api-gaps.md).

### Risks to manage

- FFM does not sandbox native execution the way a fully managed runtime would — a bad pointer handed
  to RocksDB can still crash the JVM. Managed by the ownership model in
  [0003](0003-ownership-model.md), which is not optional bookkeeping.
- A real native compiler is still needed to produce `librocksdb` itself — FFM removes the glue layer,
  not the underlying native library. See [0002](0002-why-zig.md).

## Alternatives considered

- **JNI (`rocksdbjni`'s approach):** mature and what the official binding uses, but ships hand-written
  C++ glue per function and copies at the boundary, foreclosing zero-copy.
- **JNA / JNR:** reflection-based FFI, removes the C++ glue requirement the same way FFM does, but
  its reflection-based dynamic dispatch is markedly slower per call than JNI, let alone FFM's
  JIT-compiled downcall stubs — and it is a third-party dependency rather than a JDK-standard API.

## References

- [explanation.md#why-ffm-instead-of-jni](../explanation.md#why-ffm-instead-of-jni)
- [ADR 0002 — build with zig](0002-why-zig.md)
- [ADR 0003 — ownership model](0003-ownership-model.md)
