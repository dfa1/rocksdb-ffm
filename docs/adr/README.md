# Architecture Decision Records

This directory contains ADRs following the
[MADR 3.0](https://adr.github.io/madr/) format (Markdown Architectural Decision Records).

## Format

Each ADR is a Markdown file named `NNNN-short-title.md`. Use `template.md` as the starting point.

**Status values:** Proposed → Accepted → Deprecated → Superseded (also: Rejected, Deferred)

`Accepted` is the decision's lifecycle state — it stays Accepted until something Supersedes it —
tracked independently of whether the code has shipped.

Most of these ADRs are retrospective: they record decisions already made and shipped, so
contributors can see *why* the project looks the way it does. Unlike
[explanation.md](../explanation.md), which stays up to date with the library's current shape, an
ADR is a point-in-time snapshot: context, the decision, and its known consequences at the time.

## Index

| ADR  | Title                                                       | Status   |
|------|--------------------------------------------------------------|----------|
| [0001](0001-ffm-instead-of-jni.md) | FFM bindings over JNI                          | Accepted |
| [0002](0002-why-zig.md)            | Build the native library with `zig cc`/`zig c++` | Accepted |
| [0003](0003-ownership-model.md)    | `NativeObject`: `AutoCloseable`, idempotent close, ownership transfer | Accepted |
| [0004](0004-error-handling.md)     | Separating genuine RocksDB errors from FFM binding bugs | Accepted |
| [0005](0005-no-jpms.md)            | Not adopting the Java Platform Module System            | Accepted |
| [0006](0006-method-handles-usage.md) | How far to centralize MethodHandle call sites and their try/catch boilerplate | Proposed |
