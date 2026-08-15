# Changelog

All notable changes to **rocksdbffm** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.7] — 2026-08-15

Closes out the last remaining `byte[]`/`ByteBuffer`/`MemorySegment` tier gaps across the whole
library, normalizes `RocksDB`'s open-mode factory names, adds a scoped zero-copy `get(key, Mapper)`
read path to every DB type, completes the `TransactionDBOptions`/`TransactionOptions` surface,
consolidates `PinnableSlice` into a single reusable wrapper, and adds SonarCloud + coverage
infrastructure.

### Added

- `ByteBuffer`/`MemorySegment` access tiers for the last gaps in the three-tier API: `WriteBatch`'s no-CF `put`/`delete`, `TransactionDB`'s CF-scoped `get`, `OptimisticTransactionDB`'s no-CF `deleteRange`, `SstFileWriter`'s `put`/`delete`/`deleteRange`, and all of `Transaction`'s `put`/`delete`/`get`/`getForUpdate` (previously `byte[]`-only, the largest gap). ([#68](https://github.com/dfa1/rocksdbffm/issues/68), [#69](https://github.com/dfa1/rocksdbffm/pull/69))
- `WriteBatchSizeBenchmark` sweeps batch size (1–400) across all four write tiers; benchmark test data switched from tiny repeated placeholder strings to realistic 16-byte keys / 1KB values, randomly generated and unique per entry. ([#69](https://github.com/dfa1/rocksdbffm/pull/69))
- SonarCloud static analysis and coverage reporting, gated on every PR. ([#62](https://github.com/dfa1/rocksdbffm/issues/62), [#63](https://github.com/dfa1/rocksdbffm/pull/63))
- Test coverage for previously 0%-coverage classes; the `integration-tests` module now feeds the aggregate coverage report instead of running outside it. ([#65](https://github.com/dfa1/rocksdbffm/pull/65))
- `docs/` restructured using the [Diataxis](https://diataxis.fr/) framework (tutorial / how-to / reference / explanation), replacing one long README. ([#66](https://github.com/dfa1/rocksdbffm/issues/66), [#67](https://github.com/dfa1/rocksdbffm/pull/67))
- Dedicated `LRUCache`/`HyperClockCache` tests; `RocksIterator.refresh()` snapshot-semantics coverage restored. ([90d54ae](https://github.com/dfa1/rocksdbffm/commit/90d54ae))
- `ReadWriteDB.get(MemorySegment, Mapper)` and equivalents across every DB type (`ReadOnlyDB`, `TtlDB`, `SecondaryDB`, `BlobDB`, `OptimisticTransactionDB`, `TransactionDB`, `Transaction`), plus `RocksIterator.key(Mapper)`/`value(Mapper)` — a scoped, zero-copy read callback built on `rocksdb_pinnable_handle_t`/`rocksdb_pinnableslice_t`, for hot read paths where a `byte[]` copy isn't wanted. ([#57](https://github.com/dfa1/rocksdbffm/pull/57))
- `TransactionDBOptions`/`TransactionOptions` now map the complete C API option surface: 10 new option pairs on `TransactionDBOptions` (`maxNumDeadlocks`, `transactionLockTimeout`, `defaultLockTimeout`, `writePolicy`, `rollbackMergeOperands`, `usePerKeyPointLockMgr`, `skipConcurrencyControl`, `defaultWriteBatchFlushThreshold`, `enableUdtValidation`, `txnCommitBypassMemtableThreshold`) and 12 on `TransactionOptions`, plus getters for options that already existed and a new `TxnDBWritePolicy` enum. ([1348f3d](https://github.com/dfa1/rocksdbffm/commit/1348f3d))
- `FfmScaleBenchmark`/`JniScaleBenchmark`/`ScaleBenchmarkRunner`: FFM-vs-JNI throughput and allocation comparison across `get`/iteration, zero-copy vs copy tiers, at realistic key counts (10k/100k) instead of a near-empty database. ([#80](https://github.com/dfa1/rocksdbffm/pull/80))
- `EqualsVerifier` contract tests for the three domain primitives with hand-rolled `equals`/`hashCode` (`BackupId`, `MemorySize`, `SequenceNumber`), replacing spot-check tests with a full-contract check. ([310238d](https://github.com/dfa1/rocksdbffm/commit/310238d))
- `WriteBatch`: the 8 previously-uncovered overloads (`put(Arena, byte[], byte[])`, `clear()`, and the CF-scoped `ByteBuffer`/`MemorySegment` tiers for `put`/`delete`/`deleteRange`) now have tests. ([269dd6a](https://github.com/dfa1/rocksdbffm/commit/269dd6a))
- `Checkpoint` coverage extended to `BlobDB`, `TtlDB`, `ReadOnlyDB`, and `SecondaryDB` (previously only `ReadWriteDB`), plus the `exportTo(Path)` convenience overload. ([#75](https://github.com/dfa1/rocksdbffm/pull/75))
- `docs/c-api-gaps.md`: documented library-version-query as a Type B gap — `rocksdb/c.h` has no runtime way to ask the loaded native library its version. ([#78](https://github.com/dfa1/rocksdbffm/pull/78))

### Changed

- **Breaking:** `RocksDB`'s open-mode factory methods no longer bake column-family support into the method name — it's now an overload of the base factory. `openWithColumnFamilies` → `open(..., descriptors, handles)`, `openReadOnlyWithColumnFamilies` → `openReadOnly(..., descriptors, handles[, boolean])`, `openWithTtl`/`openWithColumnFamiliesAndTtl` → `openTtl(...)`, `openWithBlobFiles` → `openBlob(...)`, `openTransactionWithColumnFamilies` → `openTransaction(..., descriptors, handles)`, `openOptimisticWithColumnFamilies` → `openOptimistic(..., descriptors, handles)`. ([#70](https://github.com/dfa1/rocksdbffm/pull/70))
- **Breaking:** `RocksDB.open` renamed to `RocksDB.openReadWrite`, matching the descriptive naming of every other factory (`openReadOnly`, `openTtl`, `openBlob`, `openSecondary`, `openTransaction`, `openOptimistic`). ([#70](https://github.com/dfa1/rocksdbffm/pull/70))
- **Breaking:** `ReadWriteDB#get(ByteBuffer, ByteBuffer)` and `#get(MemorySegment, MemorySegment)` return a sealed `CopyResult` (`Copied` / `NotEnoughCapacity(long)` / `NotFound`) instead of an `int`/`long` sentinel — the old encoding let a too-small destination silently truncate the value while still reporting the full length, and let a value above `Integer.MAX_VALUE` collide with the `-1` not-found sentinel. ([#44](https://github.com/dfa1/rocksdbffm/issues/44), [#47](https://github.com/dfa1/rocksdbffm/issues/47), [#54](https://github.com/dfa1/rocksdbffm/pull/54), [#60](https://github.com/dfa1/rocksdbffm/pull/60))
- `rocksdb` submodule upgraded from v11.0.4 to v11.8.1. ([#53](https://github.com/dfa1/rocksdbffm/issues/53), [#56](https://github.com/dfa1/rocksdbffm/pull/56))
- Property-based tests (jqwik) replaced with parameterized invariant tests. ([#43](https://github.com/dfa1/rocksdbffm/pull/43))
- `Checkpoint` and `SstFileWriter` byte-count APIs use `MemorySize` instead of raw `long`. ([616b8c0](https://github.com/dfa1/rocksdbffm/commit/616b8c0))
- `PinnableSlice` now owns all pinned-value consumption (`toByteArray`, `copyInto`, `map`) instead of every `get(...)` overload hand-rolling copy/map logic around it — 12 call sites across `RocksDB`, `Transaction`, and `TransactionDB` migrated, no behavior change. ([#58](https://github.com/dfa1/rocksdbffm/issues/58), [#80](https://github.com/dfa1/rocksdbffm/pull/80))
- `getBytes()`/`getCfBytes()` moved from `rocksdb_get_pinned` to `rocksdb_get_pinned_v2`, the same zero-copy core the `Mapper` tier uses — cuts a `PinnableSlice` allocation (104 B/op, measured across an 8 B–1 MB sweep) at every value size. ([#81](https://github.com/dfa1/rocksdbffm/pull/81))
- CI: `goto-bus-stop/setup-zig` (unmaintained) migrated to the maintained `mlugg/setup-zig`; `actions/checkout`/`setup-java`/`cache` bumped off the deprecated Node 20 runtime, and `dependabot.yml` now tracks `github-actions` updates. ([1986333](https://github.com/dfa1/rocksdbffm/commit/1986333), [7c13009](https://github.com/dfa1/rocksdbffm/commit/7c13009))

### Fixed

- `TransactionDB.createColumnFamily()` now creates the column family on the `txn_db` handle instead of one the transaction DB can't see. ([#61](https://github.com/dfa1/rocksdbffm/issues/61), [626428b](https://github.com/dfa1/rocksdbffm/commit/626428b))
- `getBytes()` now delegates to `rocksdb_get_into_buffer` instead of allocating an intermediate PinnableSlice. ([#52](https://github.com/dfa1/rocksdbffm/issues/52), [#54](https://github.com/dfa1/rocksdbffm/pull/54))
- CI badge no longer renders "no status" on the README. ([#51](https://github.com/dfa1/rocksdbffm/pull/51))
- Read benchmarks no longer measure against a 1–2 key database (every SST/block-cache/bloom-filter/LSM-level path was bypassed): `FfmBlobSizeBenchmark` renamed to `FfmValueSizeBenchmark` with a real dataset sized off a 32 MB target, `FfmBenchmark`/`JniBenchmark` documented as per-call-overhead microbenchmarks rather than read throughput. ([38b32e6](https://github.com/dfa1/rocksdbffm/commit/38b32e6), [55a2d34](https://github.com/dfa1/rocksdbffm/commit/55a2d34))
- Blob-size benchmark sweep decoupled from the rest of the FFM suite — it previously reran (and polluted the block cache for) every other benchmark in the class once per sweep size. ([#57](https://github.com/dfa1/rocksdbffm/pull/57))

### Removed

- `ReadWriteDB.getSupportedCompressions` dropped (unused, not part of the documented tier surface). ([3e6eae3](https://github.com/dfa1/rocksdbffm/commit/3e6eae3))
- `ColumnFamilyDescriptor.nameAsString()` dropped (unused, no callers or tests). ([#75](https://github.com/dfa1/rocksdbffm/pull/75))

### Security

- Third-party GitHub Actions pinned to a full commit SHA instead of a floating tag. ([#64](https://github.com/dfa1/rocksdbffm/pull/64))

### Build & Tooling

- RocksDB native builds cached in the `sonar.yml` and `publish.yml` workflows; `publish.yml` gains a `workflow_dispatch` ref input. ([25678c0](https://github.com/dfa1/rocksdbffm/commit/25678c0), [6e3ec0d](https://github.com/dfa1/rocksdbffm/commit/6e3ec0d), [ee2c7d1](https://github.com/dfa1/rocksdbffm/commit/ee2c7d1))
- Dependency bumps: `flatten-maven-plugin`, `checkstyle` (13.8.0 → 13.9.0), `maven-jar-plugin`, `jacoco-maven-plugin` (0.8.13 → 0.8.15), `junit-jupiter` (6.1.2 → 6.1.3, [#77](https://github.com/dfa1/rocksdbffm/pull/77)), `equalsverifier` (3.17.5 → 4.5, dev scope, [#76](https://github.com/dfa1/rocksdbffm/pull/76)), `advanced-security/maven-dependency-submission-action` (4.1.3 → 5.0.0, [#73](https://github.com/dfa1/rocksdbffm/pull/73)).

## [0.6] — 2026-07-20

**Windows support** lands, completing the platform matrix (macOS, Linux x86_64/aarch64, Windows
x86_64/aarch64), alongside a project-wide American-English spelling pass and routine dependency
maintenance.

### Added

- Windows support (x86_64 + aarch64) via a cross-compiled native module. ([#15](https://github.com/dfa1/rocksdbffm/issues/15), [#38](https://github.com/dfa1/rocksdbffm/pull/38))
- `RocksIterator#error()` method. ([c14bc5d](https://github.com/dfa1/rocksdbffm/commit/c14bc5d))

### Changed

- American English spelling adopted throughout (javadoc, comments, identifiers) — matches the JDK's own convention (`Object.finalize`, `Serializable`). ([#32](https://github.com/dfa1/rocksdbffm/pull/32))

### Fixed

- CI cache key for RocksDB native builds now derives from `CMakeLists.txt` instead of a value that didn't reliably invalidate on submodule bumps. ([e73fee1](https://github.com/dfa1/rocksdbffm/commit/e73fee1))

### Build & Tooling

- Dependency bumps: `junit-jupiter` (6.0.3 → 6.1.2), `checkstyle` (13.4.0 → 13.8.0), `flatten-maven-plugin`, `maven-javadoc-plugin`, `maven-source-plugin`, `maven-gpg-plugin`, `central-publishing-maven-plugin`, `jqwik`, `maven-failsafe-plugin`, `maven-surefire-plugin`.

## [0.5] — 2026-04-27

Rounds out the Maven Central publishing story: a BOM module, a `linux-aarch64` native artifact,
and a documented catalogue of C API gaps.

### Added

- `rocksdbffm-bom` module for consistent cross-module version alignment. ([afb4c35](https://github.com/dfa1/rocksdbffm/commit/afb4c35))
- `linux-aarch64` native module. ([ce50869](https://github.com/dfa1/rocksdbffm/commit/ce50869))
- Property-based tests (jqwik) for core invariants. ([67b46ba](https://github.com/dfa1/rocksdbffm/commit/67b46ba))
- `docs/c-api-gaps.md` catalogues every `rocksdb/c.h` symbol not yet mapped, split into Type A (blocked on the C API) and Type B (not yet implemented). ([853f3be](https://github.com/dfa1/rocksdbffm/commit/853f3be))

### Changed

- Release process simplified: steps inlined into the README, `scripts/release.sh` dropped. ([38bc8d1](https://github.com/dfa1/rocksdbffm/commit/38bc8d1))

## [0.4] — 2026-04-25

### Documentation

- Complete Javadoc coverage across every public API class, enforced by the build (`failOnError`/`failOnWarnings`). ([adb037d](https://github.com/dfa1/rocksdbffm/commit/adb037d))

## [0.3] — 2026-04-24

### Fixed

- All Javadoc errors and warnings fixed; `./mvnw javadoc:javadoc` is now enforced to produce zero output. ([066dc07](https://github.com/dfa1/rocksdbffm/commit/066dc07))

### Build & Tooling

- `<name>` added to every submodule POM (required for Maven Central); Maven Release Plugin artifacts gitignored. ([#12](https://github.com/dfa1/rocksdbffm/issues/12))

## [0.2] — 2026-04-24

Same-day patch release: the GitHub release asset upload from v0.1 didn't complete correctly.

### Fixed

- GitHub release asset upload. ([76ef092](https://github.com/dfa1/rocksdbffm/commit/76ef092))

## [0.1] — 2026-04-24

Initial release. An FFM-based RocksDB binding built from scratch against `rocksdb/include/rocksdb/c.h`, targeting feature parity with the JNI binding.

### Added

- Core read-write path: `RocksDB.open`, `put`/`get`/`delete` across all three access tiers (`byte[]`, `ByteBuffer`, `MemorySegment`), zero-copy reads via PinnableSlice. ([fa37694](https://github.com/dfa1/rocksdbffm/commit/fa37694), [f19b48b](https://github.com/dfa1/rocksdbffm/commit/f19b48b))
- `WriteBatch` with full tier coverage. ([b5b515d](https://github.com/dfa1/rocksdbffm/commit/b5b515d))
- `Options`/`WriteOptions`/`ReadOptions`, `createIfMissing`, read-only open. ([aa86e1f](https://github.com/dfa1/rocksdbffm/commit/aa86e1f))
- Additional DB types: `TtlDB` ([d6efa3d](https://github.com/dfa1/rocksdbffm/commit/d6efa3d)), `TransactionDB` ([a20b25b](https://github.com/dfa1/rocksdbffm/commit/a20b25b)), `OptimisticTransactionDB` ([c4be4ae](https://github.com/dfa1/rocksdbffm/commit/c4be4ae)), `SecondaryDB` ([fd5b857](https://github.com/dfa1/rocksdbffm/commit/fd5b857)), `BlobDB` ([f2556dd](https://github.com/dfa1/rocksdbffm/commit/f2556dd)).
- Full column family support across every DB type. ([18d5181](https://github.com/dfa1/rocksdbffm/commit/18d5181))
- Table options: `BlockBasedTableConfig`, `LRUCache`, `HyperClockCache`, `FilterPolicy`. ([4862f8c](https://github.com/dfa1/rocksdbffm/commit/4862f8c), [ee7073a](https://github.com/dfa1/rocksdbffm/commit/ee7073a))
- `RocksIterator` with full three-tier API. ([18d35ce](https://github.com/dfa1/rocksdbffm/commit/18d35ce))
- Snapshots ([a029e75](https://github.com/dfa1/rocksdbffm/commit/a029e75)), Flush ([11236db](https://github.com/dfa1/rocksdbffm/commit/11236db)), DeleteRange ([16f55c2](https://github.com/dfa1/rocksdbffm/commit/16f55c2)), `keyMayExist` ([0e33c68](https://github.com/dfa1/rocksdbffm/commit/0e33c68)).
- DB property inspection (`getProperty`/`getLongProperty`) and a typed `Property` enum. ([286ae86](https://github.com/dfa1/rocksdbffm/commit/286ae86), [2f5de63](https://github.com/dfa1/rocksdbffm/commit/2f5de63))
- Statistics: `HistogramType`, `TickerType`, `StatsLevel`. ([0832102](https://github.com/dfa1/rocksdbffm/commit/0832102))
- Compaction control (background job control plus `WaitForCompactOptions`). ([6a7ffbd](https://github.com/dfa1/rocksdbffm/commit/6a7ffbd), [02a967a](https://github.com/dfa1/rocksdbffm/commit/02a967a))
- WAL iteration (`getUpdatesSince`) for change-data-capture. ([c998a4b](https://github.com/dfa1/rocksdbffm/commit/c998a4b))
- SST file ingest and `SstFileWriter`. ([d27a4e1](https://github.com/dfa1/rocksdbffm/commit/d27a4e1))
- `Checkpoint` API for point-in-time snapshot export. ([1943787](https://github.com/dfa1/rocksdbffm/commit/1943787))
- `Logger` (stderr and callback variants), `RateLimiter` (writes-only/reads-only/all-IO/auto-tuned), `SstFileManager` + `Env`. ([d9a5a1f](https://github.com/dfa1/rocksdbffm/commit/d9a5a1f), [7e8432b](https://github.com/dfa1/rocksdbffm/commit/7e8432b), [14aaca6](https://github.com/dfa1/rocksdbffm/commit/14aaca6))
- `BackupEngine` with full backup/restore support and a `BackupId` domain primitive. ([9e1be63](https://github.com/dfa1/rocksdbffm/commit/9e1be63), [7fc5e7f](https://github.com/dfa1/rocksdbffm/commit/7fc5e7f))
- `PerfContext`/`PerfLevel`/`PerfMetric`. ([9b94b29](https://github.com/dfa1/rocksdbffm/commit/9b94b29))
- `CompressionType` enum with a runtime support probe. ([bf6ccdf](https://github.com/dfa1/rocksdbffm/commit/bf6ccdf))
- `MemorySize` and `SequenceNumber` domain primitives, replacing raw `long` across the size and sequence-number APIs. ([a469383](https://github.com/dfa1/rocksdbffm/commit/a469383))
- Multi-module native distribution, cross-compiled for macOS and Linux x86_64 via Zig. ([ca9ce37](https://github.com/dfa1/rocksdbffm/commit/ca9ce37), [dce6303](https://github.com/dfa1/rocksdbffm/commit/dce6303))
- JMH benchmarks comparing FFM vs JNI for reads, writes, and batches. ([3f862b8](https://github.com/dfa1/rocksdbffm/commit/3f862b8), [edafeee](https://github.com/dfa1/rocksdbffm/commit/edafeee))
- CI: build on macOS and Linux, CodeQL analysis, Dependabot. ([f076917](https://github.com/dfa1/rocksdbffm/commit/f076917), [9c74a72](https://github.com/dfa1/rocksdbffm/commit/9c74a72))

### Changed

- All tests migrated to the `// Given / // When / // Then` + AssertJ convention. ([c8cfae5](https://github.com/dfa1/rocksdbffm/commit/c8cfae5))
- Error handling centralized on `RocksDB.errHolder`/`checkError`; per-class `ThreadLocal` error pointers removed in favor of a shared `Arena`-based pattern. ([736c926](https://github.com/dfa1/rocksdbffm/commit/736c926))

[Unreleased]: https://github.com/dfa1/rocksdbffm/compare/v0.7...HEAD
[0.7]: https://github.com/dfa1/rocksdbffm/compare/v0.6...v0.7
[0.6]: https://github.com/dfa1/rocksdbffm/compare/v0.5...v0.6
[0.5]: https://github.com/dfa1/rocksdbffm/compare/v0.4...v0.5
[0.4]: https://github.com/dfa1/rocksdbffm/compare/v0.3...v0.4
[0.3]: https://github.com/dfa1/rocksdbffm/compare/v0.2...v0.3
[0.2]: https://github.com/dfa1/rocksdbffm/compare/v0.1...v0.2
