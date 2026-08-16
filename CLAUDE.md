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
- **Build System:** Maven Wrapper (`./mvnw`). Run `./mvnw generate-resources -Pnative-build` once to build the native lib; `./mvnw test`
  thereafter. Use `./mvnw` (not `mvn`) to ensure the correct Maven version is used.
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
  `/native/<os>-<arch>/librocksdb.<ext>` (bundled by the `native-build` Maven profile). There is no brew/system
  fallback. NEVER add hardcoded system paths back.
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
}
```

`RocksDB.errHolder`, `RocksDB.checkError`, and `RocksDB.toNative` are the shared FFM plumbing used by every wrapper
class.

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

**Check:** `./mvnw javadoc:javadoc -pl core` — must produce zero output.

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

## 🗺 Source Map

For the full feature status and roadmap see `docs/reference.md#feature-status`.

`RocksDB.java` holds **only** the static open/list factories plus the shared FFM helpers; every
instance method listed below lives on the DB type (`ReadWriteDB`, `TtlDB`, …), not on `RocksDB`.

| Feature                 | Java source files                                                                                                         |
|:------------------------|:--------------------------------------------------------------------------------------------------------------------------|
| DB Open/Close/Put/Get/Delete | `RocksDB.java`                                                                                                       |
| Options                 | `Options.java`, `ReadOptions.java`, `WriteOptions.java`                                                                   |
| WriteBatch              | `WriteBatch.java`                                                                                                         |
| Transactions            | `Transaction.java`, `TransactionDB.java`, `TransactionDBOptions.java`, `TransactionOptions.java`                          |
| Optimistic Transactions | `OptimisticTransactionDB.java`, `OptimisticTransactionOptions.java`                                                       |
| Checkpoints             | `Checkpoint.java`                                                                                                         |
| Table Options           | `BlockBasedTableOptions.java`, `Cache.java`, `LRUCache.java`, `HyperClockCache.java`, `FilterPolicy.java`                 |
| Compression             | `CompressionType.java`; `Options.setCompression`, `Options.getCompression`                                                |
| Iterators               | `RocksIterator.java`                                                                                                      |
| Snapshots               | `Snapshot.java`; `ReadOptions.setSnapshot`; `ReadWriteDB.getSnapshot`, `TransactionDB.getSnapshot`, `Transaction.getSnapshot` |
| Flush                   | `FlushOptions.java`; `ReadWriteDB.flush`, `ReadWriteDB.flushWal`, `TransactionDB.flush`, `TransactionDB.flushWal`                 |
| KeyMayExist             | `ReadWriteDB.keyMayExist` — byte[], ByteBuffer, MemorySegment, ReadOptions overload                                           |
| DB Properties           | `Property.java` (enum of well-known names); `ReadWriteDB.getProperty`, `ReadWriteDB.getLongProperty`, same on `TransactionDB`     |
| Statistics              | `HistogramType.java`, `TickerType.java`, `StatsLevel.java`, `StatisticsHistogramData.java`                                |
| Shared utilities        | `RocksDB.java` statics (`errHolder`, `checkError`, `toNative`), `NativeObject.java`, `NativeLibrary.java`, `MemorySize.java`, `SequenceNumber.java`, `RocksDBException.java` |
| Compaction control      | `CompactOptions.java`, `WaitForCompactOptions.java`; `ReadWriteDB.compactRange`, `suggestCompactRange`, `disableFileDeletions`, `enableFileDeletions`, `ReadWriteDB.waitForCompact` |
| DeleteRange             | `ReadWriteDB.deleteRange` (all three tiers), `WriteBatch.deleteRange`                                                     |
| SST File Ingest         | `SstFileWriter.java`, `IngestExternalFileOptions.java`; `ReadWriteDB.ingestExternalFile`                                     |
| WAL Iterator            | `WalIterator.java`, `WalBatchResult.java`; `ReadWriteDB.getUpdatesSince`, `getLatestSequenceNumber`                          |
| Read-only DB            | `ReadOnlyDB.java`                                                                                                         |
| TTL DB                  | `TtlDB.java`; `RocksDB.openTtl(Path, Duration)`                                                                       |
| Logger                  | `Logger.java`, `LogLevel.java`; `Logger.newStderrLogger`, `Logger.newCallbackLogger`                                      |
| Secondary DB            | `SecondaryDB.java`                                                                                                        |
| Blob DB                 | `BlobDB.java`; blob options in `Options.java`; `PrepopulateBlobCache.java`; `RocksDB.openBlob`                   |
| Rate Limiter            | `RateLimiter.java`; `Options.setRateLimiter`                                                                              |
| SST File Manager        | `SstFileManager.java`, `Env.java`; `Options.setSstFileManager`, `Options.setEnv`                                          |
| Backup Engine           | `BackupEngine.java`, `BackupEngineOptions.java`, `RestoreOptions.java`, `BackupInfo.java`, `BackupId.java`                |
| Column Families         | `ColumnFamilyHandle.java`, `ColumnFamilyDescriptor.java`; `RocksDB.openReadWrite`, `listColumnFamilies`; CF overloads on `ReadWriteDB` and `WriteBatch`; CF overloads on `ReadOnlyDB`, `TtlDB`, `TransactionDB`, `OptimisticTransactionDB`; `Transaction` CF put/delete/get/getForUpdate/newIterator; multi-CF open for all DB types |
| Perf Context            | `PerfContext.java`, `PerfLevel.java`, `PerfMetric.java`; thread-local; `setPerfLevel`, `reset`, `metric`, `report`       |

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
- every `MH_` field must have a `/// \`<c prototype>\`` comment on the line immediately above it, copied verbatim from `rocksdb/include/rocksdb/c.h` (strip the `extern ROCKSDB_LIBRARY_API` prefix); no duplicate comment in the `static` block
- don't map multiple times the same symbol from C library of rocksdb
    - try to create always a java wrapper for that (i.e. PinnableSlice)
- use NativeObject as base class for all managed objects
    - this is needed to avoid double close() crashing the JVM
- don't expose public constructors, like CompactOptions.newCompactOptions(), CompactOptions.newCompactOptions()
    - why? to be able to call super in the private constructor and to have more freedom in the static factory method
