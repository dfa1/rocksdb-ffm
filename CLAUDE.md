# AGENTS.md: Project Context & AI-Driven Guidelines

## 🤖 AI-Driven Project Mandate

This project is heavily AI-driven. As an agent, your goal is to:

- **Be Autonomous:** Research C headers (rocksdb/include/rocksdb/c.h) and identify the best mapping to Java FFM.
- **Stay Technical:** Prioritize performance, zero-copy, and manual memory safety.
- **Maintain Consistency:** Follow established naming and ownership patterns.

## 🛠 Tech Stack

- **Language:** Java 25+.
- **Core API:** `java.lang.foreign` (Foreign Function & Memory API).
- **Native Library:** RocksDB (C API via `include/rocksdb/c.h`), built from the `rocksdb/` git submodule (pinned to
  v11.8.1).
- **Native Compiler:** `zig cc` / `zig c++` — used as a drop-in C/C++ compiler via
  `CC="zig cc" CXX="zig c++" PORTABLE=1 make shared_lib`. Zig bundles clang + libc++ for every target, enabling
  cross-compilation without a separate sysroot.
- **Build System:** Maven Wrapper (`./mvnw`). `./mvnw generate-resources` (or `test`, `compile`, ...) auto-detects the
  host OS/arch and cross-compiles RocksDB for just that one `native/*` classifier via zig cc — a plain local build no
  longer compiles all 5 targets. Add `-Pall-natives` to build every classifier regardless of host (what CI and
  releases use). Use `./mvnw` (not `mvn`) to ensure the correct Maven version is used.
  **NEVER run `mvn install` or `./mvnw install`** — it pollutes `~/.m2` with local artifacts. Use `compile`, `test`, or `package` instead.
- **Testing:** JUnit 5, AssertJ.
- **Benchmarking:** JMH (Java Microbenchmark Harness).

## 🏗 Architectural Standards

### 1. Manual Memory Management & Lifecycle

Every class wrapping a native pointer **must** implement `AutoCloseable`.

- **Zero Leaks:** Native resources must be destroyed in `close()`.
- **Ownership Transfer:** When one native object takes ownership of another (e.g., `FilterPolicy` →
  `BlockBasedTableOptions`), the transferred object must be marked so its `close()` becomes a no-op and cannot
  double-free.
- **Transfer Marker:** Call `transferOwnership()` (package-private on `NativeObject`) inside the setter that takes
  ownership. It sets the held pointer to `MemorySegment.NULL`, which makes `close()` a no-op and any later `ptr()`
  throw `IllegalStateException`.

### 2. Data Types & Path Handling

To ensure type safety and consistent units across the API:

- **C API Only:** We use the RocksDB C interface (`rocksdb/c.h`). Do not attempt to link directly to C++ symbols.
- **Read-only headers:** NEVER modify system include files (e.g. `/opt/homebrew/...`, `/usr/include/...`). They are
  read-only references; all mappings live in Java source.
- **Library loading:** `NativeLibrary.java` loads the native library from the classpath resource
  `/native/<os>-<arch>/librocksdb.<ext>` (bundled by each `native/*` module's `exec-maven-plugin` execution). There is
  no brew/system fallback. NEVER add hardcoded system paths back.
- **Paths:** Never use raw `String` for file system paths. Always use `java.nio.file.Path` for any API surface that
  accepts paths (open, backup, checkpoint).
- **Memory Sizes:** Never use raw `long` for byte counts (e.g., cache size, write buffer size). Always use the project's
  `MemorySize` type.
- **Sequence Numbers:** Never use raw `long` for RocksDB sequence numbers. Always use the project's `SequenceNumber`
  type.
- **BackupId:**: Never use raw uint32, use a wrapper Java type that hides this from the user.
- **Timeouts:** Never use raw `long` for a timeout field with a negative-sentinel meaning in the C API (e.g. `-1` =
  wait forever/disabled). Use `Duration`, with `null` — not `Duration.ZERO` — as the sentinel, since `ZERO` usually
  already means something else ("fail immediately"). Reject a non-null negative `Duration`. Verify sentinel semantics
  against the actual `rocksdb/utilities/**/*.h`/`.cc` source, not just existing javadoc (it can be wrong); if the C++
  side documents no negative-sentinel meaning, still convert to `Duration` but require it non-null.

### 3. API Surface Design

For every feature, provide three tiers of access:

1. **`MemorySegment` Version:** Native-first, for performance-critical usage.
2. **`ByteBuffer` Version:** For compatibility with existing NIO-based clients.
3. **`byte[]` Version:** Quick access for convenience (explicitly documented as slower).

## ⚡ FFM Performance & Patterns

### 1. Centralized Error Handling

**NEVER use ThreadLocals for error pointers.** Use the shared helpers on `RocksDB` with the caller's `Arena`:

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment err = RocksDB.errHolder(arena);
    MH_DO_SOMETHING.invokeExact(handle, ..., err);
    RocksDB.checkError(err);
} catch (Throwable t) {
    throw RocksDB.wrapInvokeFailure("doSomething failed", t);
}
```

`RocksDB.errHolder`, `RocksDB.checkError`, `RocksDB.toNative`, and `RocksDB.wrapInvokeFailure` are the shared FFM
plumbing used by every wrapper class.

**`RocksDBException` is only ever constructed by `RocksDB.checkError`**, for a genuine `errptr`-reported RocksDB
error. Never construct it — or call something you wrote yourself that would — from an `invokeExact` catch block;
use `RocksDB.wrapInvokeFailure(message, t)` there instead, which rethrows any `RuntimeException` (including a
`RocksDBException` `checkError` already threw earlier in the same `try`) unwrapped, an `IOException` as
`UncheckedIOException`, and anything else — which should never happen for a correctly configured downcall handle —
as `AssertionError`. See [ADR 0004](docs/adr/0004-error-handling.md) for why: a bug in this library's own FFM
plumbing must never be indistinguishable from a genuine RocksDB error.

### 2. Zero-Copy Patterns

- **PinnableSlice:** Use `rocksdb_get_pinned` for reads to avoid intermediate copies from the block cache.
- **Direct Buffers:** Use `MemorySegment.ofBuffer(directByteBuffer)` to wrap existing native memory without copies.

## 🧪 Validation & Workflow

### 1. Comparative Testing

For every new feature:

1. Write unit tests in JUnit 5 using `@TempDir`.
2. **Always** follow the `// Given / // When / // Then` structure — every test, no exceptions.
   - `// Given` sets up state.
   - `// When` performs the action under test — **never combine with `// Then`**. Extract the result into a local variable:
     ```java
     // When
     var result = db.get("k".getBytes());

     // Then
     assertThat(result).isEqualTo("v".getBytes());
     ```
   - `// Then` asserts the outcome. The assertion always operates on the variable captured in `// When`, never inline.
   - For void actions (`flush`, `put`, …) there is no return value to capture; just place the call under `// When` and put assertions (if any) under `// Then`.
   - For tests with no meaningful setup, use `// Given` with a blank line or a comment explaining why there is none.
3. **Run tests:** `./mvnw test`

### 3. Javadoc

Every public method must have complete Javadoc. The build enforces this via
`failOnError=true` + `failOnWarnings=true` in the `maven-javadoc-plugin`.

Rules:
- Every public method needs a main description, `@param` for each parameter, and `@return` (unless `void`).
- Every public record needs `@param` entries on the class-level doc (one per component).
- Cross-references use `[ClassName#method(ParamType)]` — verify the target exists before writing it. Wrong references are **errors**, not warnings.
- `@see`-only Javadoc counts as "no main description" — always add a prose sentence.

**Check:** `./mvnw package -pl core` — must produce zero output/errors. Note `./mvnw javadoc:javadoc -pl core`
(the `javadoc:javadoc` report goal) is **not equivalent** to the `attach-javadocs` execution CI actually
runs (`javadoc:jar`, bound to the `package` phase) — the report goal alone has passed clean while
`package` still failed on broken `[ClassName#method]` cross-references, so always verify with `package`,
not just the report goal.

### 4. Releasing

```bash
./mvnw --batch-mode release:clean release:prepare \
    -DreleaseVersion=<version> \
    -DdevelopmentVersion=<next>-SNAPSHOT
git push && git push --tags
```

GitHub Actions picks up the tag and deploys to Maven Central.

### 5. Benchmark First

Performance gains are a primary goal. Use `JMH` to validate changes.

- **Run benchmarks:**
  ```bash
  ./mvnw test-compile -q
  ./scripts/benchmark.sh
  ```
  This builds everything, runs both FFM and JNI suites, and prints a side-by-side comparison table.

## 🗺 Architecture Notes

For which file implements which feature, see `docs/reference.md#feature-status` (class names are
named inline in its Notes column) or grep/Explore the repo directly. This section only records
decisions that aren't visible from the code or that table — the "why", not the "where".

`RocksDB.java` holds **only** the static open/list factories; every instance method lives on the DB
type (`ReadWriteDB`, `TtlDB`, …) or its capability interface, not on `RocksDB`.

- **LSM shape:** multiplier and other secondary level-sizing knobs are a deliberate Type A gap (see `docs/c-api-gaps.md`) — only the base memtable/level-0/file-size tuning surface is wrapped.
- **Memtable factory setters** (`setHashSkipListMemTableFactory`, etc.) have no getters, same as `setTableFormatConfig`.
- **`PlainTableOptions`** is a plain value holder with no native counterpart — `rocksdb_options_set_plain_table_factory` takes 8 scalars directly, unlike the opaque options types the other two table formats use.
- **`ReadBatch`** is the sole multi-get entry point: one CF fixed at `create` time, reusable across calls with no per-call bookkeeping allocation for the array shape — though `byte[]` keys still get copied into a fresh call-scoped `Arena` every call, since their content changes call to call. There is deliberately no one-shot `multiGet()` — a single-batch read is `try (var batch = ReadBatch.create(db, keys.size())) { return batch.get(keys, fn); }`. Legacy `rocksdb_multi_get`/`_cf`/`_with_ts` and `TransactionDB`/`Transaction` multi-get are not wrapped (see `docs/c-api-gaps.md`).
- **`RocksDBMonitoringOperations.getLiveFiles()`** has its own interface rather than folding into `RocksDBReadOperations`, but is still implemented by `TransactionDB` (which implements neither read/write interface) because `rocksdb_livefiles()` has no per-type native symbol to duplicate — it isn't blocked by the no-shared-call-site rule the way direct put/get are.
- **`LiveFilesStorageInfo` vs `LiveFiles`:** the storage-info variant is broader — every file needed to reconstruct the DB (SST, WAL, MANIFEST, `CURRENT`, `OPTIONS`, blob), not just SST.
- **Shared capability interfaces** (`RocksDBReadOperations`, `RocksDBWriteOperations`, `RocksDBCompactionOperations`, `RocksDBMonitoringOperations`, `RocksDBTracingOperations`) are implemented directly by `ReadWriteDB`/`TtlDB`/`BlobDB`/`ReadOnlyDB`/`SecondaryDB`/`OptimisticTransactionDB`. `TransactionDB` never implements them — its direct put/get/etc. bind their own `MethodHandle`s per the no-shared-call-site rule, since `rocksdb_transactiondb_put` and friends are genuinely different native symbols — but it still gets compaction control and tracing through its base `rocksdb_t*` pointer, since those calls aren't per-type. See [#131](https://github.com/dfa1/rocksdbffm/issues/131) for why this is split into one `Bindings` class per interface instead of one catch-all on `RocksDB`.
- **Compaction style options are conditional:** `FifoCompactionOptions`/`UniversalCompactionOptions` only take effect under the matching `Options.CompactionStyle`.
- **Ownership transfer is inconsistent by design:** `FilterPolicy` and the prefix extractor (`SliceTransform`) transfer ownership into their owning options (`transferOwnership()`); `SstPartitionerFactory` does not — it uses shared ownership instead. `SliceTransform`'s capped-prefix variant (`NewCappedPrefixTransform`) has no C API entry point at all (tracked in `docs/c-api-gaps.md`).
- **`EnvOptions` vs `Env`:** `EnvOptions` tunes per-file I/O (mmap vs. direct I/O, fsync cadence, readahead, rate limiter) for a single `SstFileWriter`; `Env` selects the pluggable filesystem/threading environment for the whole DB. Don't confuse the two.
- **Tracing has its own interface** (`RocksDBTracingOperations`, not folded into `RocksDBWriteOperations`) because it captures reads too and must work on read-only/secondary handles.
- **Event listener subcompaction callbacks** are deliberately not exposed to Java, but still get a shared no-op stub — `db/c.cc` calls them without a null check, so removing the stub would crash.
- **Any new callback-based feature** (event listeners, custom loggers, merge operators, ...) must call both `BackgroundUpcallThreads.installShutdownDrain()` at registration and `.track()` at every upcall dispatch — otherwise a background thread RocksDB attached to the JVM can deadlock `System.exit()`.
- **`ldb`/`sst_dump` build flags are load-bearing, not stylistic:** `USE_RTTI=1` is required by `ldb_cmd.cc`'s `Customizable`-based parsing, and `-Wl,-Bsymbolic` avoids a heap-corrupting libc++ symbol-interposition crash — see [ADR 0008](docs/adr/0008-ldb-sst-dump-dynamic-linking.md). Windows builds these statically via CMake instead (`WITH_CORE_TOOLS`/`USE_RTTI`), since RocksDB's CMake only shared-links the tools off Windows.

## Documentation

- Javadoc is written in the Markdown format to keep same format everywhere
- `docs/` follows [Diataxis](https://diataxis.fr/) — put new prose in the page matching its mode, and cross-link
  rather than repeat:

  | File                   | Put here                                                                |
  |:-----------------------|:------------------------------------------------------------------------|
  | `docs/tutorial.md`     | The single linear newcomer walkthrough                                  |
  | `docs/how-to.md`       | Task recipes ("how do I take a backup?")                                |
  | `docs/reference.md`    | Artifacts, API surface by area, options/enum tables, feature status     |
  | `docs/explanation.md`  | Design rationale, ownership model, native loading, build decisions      |
  | `docs/benchmarks.md`   | Benchmark numbers and methodology                                        |
  | `docs/c-api-gaps.md`   | Type A/B gap catalogue against `rocksdb/c.h`                            |

- `README.md` links to `docs/` and must not duplicate their content: it holds badges, a minimal quickstart, the docs
  index, contributing, and releasing only
- Java snippets in `docs/` must compile against `core` before being committed

## Code

- American English everywhere (javadoc, comments, identifiers): recognize/optimize/finalize/serialize/normalize/behavior/color — never -ise/-isation/-our. Matches the JDK (Object.finalize, Serializable).
- code is indented with tabs (enforced by checkstyle)
- always keep the MethodHandles private static final
    - never pass a `MethodHandle` as a method parameter, even internally: `invokeExact` on a `static final` field lets the JIT treat the target as a compile-time constant; routed through a parameter, that constant-folding is lost. Each call site must invoke its own `MH_` field directly, even if that means near-duplicate call sites instead of one shared helper
    - **Documented exception:** any class with the same cold-path, no-arg-getter/single-primitive-setter shape — every `*Options` class, plus [StatisticsHistogramData], [SstFileManager], [Env], [Cache], [Replayer], [ColumnFamilyHandle], [Snapshot], and the read-only event-listener payload views ([CompactionJobInfo], [FlushJobInfo], [MemTableInfo], [ExternalFileIngestionInfo]) that have at least one plain-shape field — routes through the static helpers on `NativeFields` instead of inlining its own `try { mh.invokeExact(...) } catch (Throwable t) { ... }` at every call site: `NativeFields.getInt(mh, ptr())`, `NativeFields.setInt(mh, ptr(), value)`, and the same pattern for `getLong`/`setLong`, `getDouble`/`setDouble`, `getBoolean`/`setBoolean`, `getMemorySize`/`setMemorySize`. The payload views hold a raw `MemorySegment ptr` field rather than extending `NativeObject`, which is fine: `NativeFields`'s helpers take `ptr` as a plain parameter, so nothing about them requires a `NativeObject` receiver. No per-instance delegate object either — `NativeFields` is stateless shared FFM plumbing, the same shape as `RocksDB.errHolder`/`checkError`/`wrapInvokeFailure` are already static on `RocksDB`. These are cold-path calls — made once (or once per read/write call in `ReadOptions`/`WriteOptions`, still nowhere near `Get`/`Put`/iterator internals) — so the JIT constant-folding the base rule protects is immaterial here, and consolidating the near-identical `catch (Throwable t)` blocks meaningfully shrinks the permanently-unreachable defensive code cataloged in ADR 0004. Scope stays tight: only a plain no-arg getter or a setter taking `ptr()` plus exactly one primitive qualifies. A pure, always-succeeding value transform in the argument expression is still in scope — e.g. `RocksDB.toByte(value)`, a null-to-sentinel ternary (`x == null ? -1L : x.toBytes()`), or a bitmask encode/decode (`TraceFilter.toMask(filters)`) — since none of those can throw, so there is no risk of a validation failure being misattributed to `RocksDB.wrapInvokeFailure`'s "native setter failed" message. What stays a raw, per-call-site `MethodHandle` invocation is a pointer-argument call, a multi-arg call, or a value expression that can itself throw (e.g. `TransactionDBOptions`'s `toMillisOrNoTimeout`, which rejects a negative `Duration`) or that maps onto more than a direct value substitution (e.g. a boolean mapped onto a non-0/1 native enum). Do not route a hot-path class (`WriteBatch`, `Transaction(DB)`, `RocksIterator`, `SstFileWriter`, `PinnableSlice`/`PinnableHandle`, `WalIterator`, ...) through `NativeFields` without the same justification
- every `MH_` field must have a `/// \`<c prototype>\`` comment on the line immediately above it, copied verbatim from `rocksdb/include/rocksdb/c.h` (strip the `extern ROCKSDB_LIBRARY_API` prefix); no duplicate comment in the `static` block
- a shared capability interface (`RocksDBReadOperations`, `RocksDBWriteOperations`, `RocksDBMonitoringOperations`, `RocksDBTracingOperations`, ...) never holds its own `MH_` fields directly — an interface field is implicitly `public static final`, so `private static final MethodHandle` (the rule above) cannot live on the interface itself. Instead its default methods delegate to a package-private sibling class named `<InterfaceName>Bindings` (e.g. `RocksDBTracingOperationsBindings`) that holds the `MH_` fields plus the invoke/error-handling logic. This is the shape [#131](https://github.com/dfa1/rocksdbffm/issues/131) used to split `RocksDB.java`'s read/write/compaction/monitoring helpers out — one `Bindings` class per capability interface, not one shared catch-all
- don't map multiple times the same symbol from C library of rocksdb
    - try to create always a java wrapper for that (i.e. PinnableSlice)
- use NativeObject as base class for all managed objects
    - this is needed to avoid double close() crashing the JVM
- don't expose public constructors, like CompactOptions.newCompactOptions(), CompactOptions.newCompactOptions()
    - why? to be able to call super in the private constructor and to have more freedom in the static factory method
- int-backed enums map native int → enum via a package-private `fromValue(int)` using a `switch` expression (see
  `LogLevel`), never a `for (X x : X.values())` loop — `values()` allocates a fresh array on every call. Unmapped
  input either falls back to a documented default constant or throws `IllegalArgumentException`, matching whatever
  the existing enum already did — `fromValue` changes the lookup mechanism, not the fallback behavior.
