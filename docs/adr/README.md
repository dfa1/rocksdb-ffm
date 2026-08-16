# Architecture Decision Records

Each record follows the [Architectural Decision Records](https://adr.github.io/) format: a short
record of *why* a significant decision was made, captured close to when it was made. Unlike
[explanation.md](../explanation.md), which stays up to date with the library's current shape, an ADR
is a point-in-time snapshot: context, the decision, and its known consequences at the time. When a
later decision supersedes an earlier one, the earlier ADR stays (marked `Superseded`) rather than
being edited or deleted — the trail is the point.

Each ADR follows the same shape: **Status**, **Context**, **Decision**, **Consequences**.

| ADR                                             | Status   | Decision                                                             |
|:-------------------------------------------------|:--------|:----------------------------------------------------------------------|
| [0001](0001-ffm-instead-of-jni.md)              | Accepted | `java.lang.foreign` instead of JNI                                    |
| [0002](0002-why-zig.md)                         | Accepted | `zig cc`/`zig c++` as the cross-compiler for every native classifier |
| [0003](0003-ownership-model.md)                 | Accepted | `NativeObject` + explicit ownership transfer for native lifetimes     |
| [0004](0004-error-handling.md)                  | Proposed | Separating genuine RocksDB errors from FFM binding bugs                |
