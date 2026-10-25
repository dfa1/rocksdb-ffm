# Changelog

All notable changes to **rocksdbffm** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.15] — 2026-10-25

Snappy and zlib compression, `RocksIterator.stream`, and a cleaner published BOM.

### Added

- `RocksIterator.stream(Mapper, Mapper)`: zero-copy `Stream<KeyValue<K, V>>` from the current
  position; never closes the iterator, calls `checkError()` at end of range ([#181](https://github.com/dfa1/rocksdb-ffm/issues/181))
- `CompressionType.SNAPPY`: Snappy is now built hermetically and linked into every native classifier ([#115](https://github.com/dfa1/rocksdb-ffm/issues/115))
- `CompressionType.ZLIB`: zlib is now built hermetically and linked into every native classifier ([#116](https://github.com/dfa1/rocksdb-ffm/issues/116))

### Fixed

- `rocksdbffm-bom`: published POM no longer leaks build properties or a mangled `…/rocksdbffm-bom`
  url/scm

## [0.14] — 2026-09-13

Block cache and I/O tracing, a close-race crash fix, and a Maven Central publishing fix.

### Added

- `RocksDBTracingOperations`: block cache and I/O tracing ([#182](https://github.com/dfa1/rocksdb-ffm/pull/182))

### Fixed

- `Snapshot`/`RocksIterator` created just before DB close no longer crashes the JVM ([#184](https://github.com/dfa1/rocksdb-ffm/pull/184))
- `CompactionFilterFactory`: no leaked `CompactionFilter` when construction throws ([706f639](https://github.com/dfa1/rocksdb-ffm/commit/706f639))
- `CompactionFilter.FilterDecision.ChangeValue`: `equals`/`hashCode`/`toString` compare value content ([62d3c8a](https://github.com/dfa1/rocksdb-ffm/commit/62d3c8a))
- `benchmarks`/`integration-tests` no longer published to Maven Central ([88e96f7](https://github.com/dfa1/rocksdb-ffm/commit/88e96f7))

## [0.13] — 2026-09-06

`ldb`/`sst_dump` tool wrappers, `CompactionFilter`/`CompactionFilterFactory`, `PlainTableOptions`, and `Options` tuning.

### Added

- `Options`: background-job, file-handle, write-path and memtable-factory tuning ([34ded49](https://github.com/dfa1/rocksdb-ffm/commit/34ded49))
- `PlainTableOptions` and `Options.setTableFormatConfig(PlainTableOptions)` ([34ded49](https://github.com/dfa1/rocksdb-ffm/commit/34ded49))
- `Options`: memtable prefix bloom, whole-key filtering, huge pages, bloom locality ([e63ddd4](https://github.com/dfa1/rocksdb-ffm/commit/e63ddd4))
- `CompactionFilter` and `Options.setCompactionFilter` ([#167](https://github.com/dfa1/rocksdb-ffm/pull/167))
- `CompactionFilterFactory` and `Options.setCompactionFilterFactory` ([#167](https://github.com/dfa1/rocksdb-ffm/pull/167))
- `LdbTool`/`SstDumpTool`: bundled `ldb`/`sst_dump` CLIs as subprocesses, all five classifiers ([#135](https://github.com/dfa1/rocksdb-ffm/pull/135))

### Fixed

- `CopyResult` javadoc states the `ByteBuffer` fill-mode contract (caller must `flip()`) ([34ded49](https://github.com/dfa1/rocksdb-ffm/commit/34ded49))
- `CompactionFilter.FilterDecision` records had `@see`-only Javadoc, which broke the 0.12 release ([66e89c9](https://github.com/dfa1/rocksdb-ffm/commit/66e89c9))

## [0.12] — 2026-09-05 [YANKED]

Never published: the release build failed on `CompactionFilter` Javadoc. Re-released as [0.13](#013--2026-09-06).

## [0.11] — 2026-09-03

`EventNotifier`, tracing and replay, compaction/table/env option wrappers, `getApproximateSizes`, and a `System.exit()` deadlock fix.

### Added

- `Cache`: `getTableAddressCount`, `getOccupancyCount`, `disownData` ([5840886](https://github.com/dfa1/rocksdb-ffm/commit/5840886))
- `LRUCache`: `strictCapacityLimit`/`numShardBits` factories, matching `HyperClockCache` ([5840886](https://github.com/dfa1/rocksdb-ffm/commit/5840886))
- `BlockBasedTableOptions`: auto-readahead tuning ([#154](https://github.com/dfa1/rocksdb-ffm/pull/154))
- `BlockBasedTableOptions`: cache pinning and priority, `PinningTier` enum ([#154](https://github.com/dfa1/rocksdb-ffm/pull/154))
- `BlockBasedTableOptions`: block layout tuning ([#155](https://github.com/dfa1/rocksdb-ffm/pull/155))
- `FifoCompactionOptions` and `Options.CompactionStyle` ([#157](https://github.com/dfa1/rocksdb-ffm/pull/157))
- `UniversalCompactionOptions` and `StopStyle` ([#157](https://github.com/dfa1/rocksdb-ffm/pull/157))
- `EnvOptions`, via `SstFileWriter.newSstFileWriter(Options, EnvOptions)` ([#158](https://github.com/dfa1/rocksdb-ffm/pull/158))
- `EventNotifier`: flush/compaction/ingestion/background-error/stall/memtable-seal callbacks ([#121](https://github.com/dfa1/rocksdb-ffm/pull/121))
- Tracing and replay: `startTrace`/`endTrace`, `Replayer`, `TraceOptions`/`ReplayOptions` ([040a581](https://github.com/dfa1/rocksdb-ffm/commit/040a581))
- `CuckooTableOptions` and `Options.setTableFormatConfig(CuckooTableOptions)` ([040a581](https://github.com/dfa1/rocksdb-ffm/commit/040a581))
- `BlockBasedTableOptions`: remaining filter/index/corruption/alignment fields ([ef72ead](https://github.com/dfa1/rocksdb-ffm/commit/ef72ead))
- `Options`: basic LSM-shape tuning (write buffer, levels, L0 trigger, file/level sizes) ([989c527](https://github.com/dfa1/rocksdb-ffm/commit/989c527))
- `ReadOptions.setAutoPrefixMode` ([f5ac411](https://github.com/dfa1/rocksdb-ffm/commit/f5ac411))
- `FilterPolicy.newRibbonHybrid` ([ad67ee4](https://github.com/dfa1/rocksdb-ffm/commit/ad67ee4))
- `RocksDBMonitoringOperations.getApproximateSizes` and `Range` ([5033a4a](https://github.com/dfa1/rocksdb-ffm/commit/5033a4a))
- `Options`: `setLevel0SlowdownWritesTrigger`, `setLevel0StopWritesTrigger`, `setDisableAutoCompactions` ([ef16843](https://github.com/dfa1/rocksdb-ffm/commit/ef16843))

### Changed

- **Breaking:** `MonitoringOperations` renamed to `RocksDBMonitoringOperations` ([129c6b3](https://github.com/dfa1/rocksdb-ffm/commit/129c6b3))
- **Breaking:** `CompactionJobInfo.status()` renamed to `checkStatus()` ([4578546](https://github.com/dfa1/rocksdb-ffm/commit/4578546))

### Fixed

- `Logger` callbacks and `MergeOperator.custom` no longer deadlock `System.exit()` ([#121](https://github.com/dfa1/rocksdb-ffm/pull/121))

## [0.10] — 2026-08-30

`ReadBatch`, live-file inspection, prefix extractor/partitioner, explicit read/write options overloads, and zero-copy `MergeOperator.custom`.

### Added

- `ReadBatch`: reusable, preallocated batched multiGet, all three tiers ([#129](https://github.com/dfa1/rocksdb-ffm/pull/129))
- `LiveFiles`/`LiveFileInfo` ([#124](https://github.com/dfa1/rocksdb-ffm/pull/124))
- `LiveFilesStorageInfo`/`LiveFileStorageInfo` ([#125](https://github.com/dfa1/rocksdb-ffm/pull/125))
- `SstPartitionerFactory` (fixed-prefix) ([#124](https://github.com/dfa1/rocksdb-ffm/pull/124))
- `SliceTransform` fixed-prefix extractor, via `Options.setPrefixExtractor` ([#128](https://github.com/dfa1/rocksdb-ffm/pull/128))
- Explicit `WriteOptions` overloads for every direct write op ([#113](https://github.com/dfa1/rocksdb-ffm/pull/113))
- Explicit `ReadOptions` overloads for `ByteBuffer`/`MemorySegment` `get` ([367365f](https://github.com/dfa1/rocksdb-ffm/commit/367365f))

### Changed

- `RocksDB.close` renamed to `closeDb` ([#107](https://github.com/dfa1/rocksdb-ffm/pull/107))
- **Breaking:** `MergeOperator.custom`'s `FullMergeFn` receives zero-copy `MemorySegment`s ([fe7cc9f](https://github.com/dfa1/rocksdb-ffm/commit/fe7cc9f))
- Unlinked compression types throw `UnsupportedOperationException` instead of failing at open ([#114](https://github.com/dfa1/rocksdb-ffm/pull/114))
- **Breaking:** zero-copy `get(key, Mapper<R>)` returns nullable `R`, not `Optional<R>` ([#130](https://github.com/dfa1/rocksdb-ffm/pull/130))
- **Breaking:** `RocksDB`'s internal FFM helpers are package-private ([#132](https://github.com/dfa1/rocksdb-ffm/pull/132))

### Fixed

- `MemorySize.toString()` drops the space before the unit (`1571B`) ([#124](https://github.com/dfa1/rocksdb-ffm/pull/124))
- Forced bottommost compaction ([#124](https://github.com/dfa1/rocksdb-ffm/pull/124))

## [0.9] — 2026-08-21

Four JVM-crash fixes, full read/write surface on `OptimisticTransactionDB`, `merge()` everywhere, and `MergeOperator`.

### Added

- `Temperature` enum, wired into `Options` ([#91](https://github.com/dfa1/rocksdb-ffm/pull/91))
- `Property.COMPACTION_ABORT_COUNT` ([#90](https://github.com/dfa1/rocksdb-ffm/pull/90))
- `merge()` on every write-capable type ([#92](https://github.com/dfa1/rocksdb-ffm/pull/92))
- `MergeOperator`: `uint64Add()` and `custom(String, FullMergeFn)` ([#93](https://github.com/dfa1/rocksdb-ffm/pull/93))
- `RocksDB.openSecondary` column-family overload ([#100](https://github.com/dfa1/rocksdb-ffm/pull/100))
- `OptimisticTransactionDB` implements `RocksDBReadOperations`/`RocksDBWriteOperations` ([#105](https://github.com/dfa1/rocksdb-ffm/pull/105))

### Changed

- **Breaking:** `RateLimiter` refill period is a `Duration` ([#89](https://github.com/dfa1/rocksdb-ffm/pull/89))
- **Breaking:** `BackupInfo.timestamp` is an `Instant` ([#89](https://github.com/dfa1/rocksdb-ffm/pull/89))
- `RocksDBReadOperations`/`RocksDBWriteOperations` extracted as shared interfaces ([#100](https://github.com/dfa1/rocksdb-ffm/pull/100))
- `UpcallRegistry<T>` extracted, shared by `Logger` and `MergeOperator.Custom` ([0b4d21a](https://github.com/dfa1/rocksdb-ffm/commit/0b4d21a))
- `NativeObjectWithBaseDb`/`NativeObjectWithChildren` extracted ([#101](https://github.com/dfa1/rocksdb-ffm/pull/101))

### Fixed

- JVM crash: `Transaction.getSnapshot()` without snapshot support ([cfb0bf7](https://github.com/dfa1/rocksdb-ffm/commit/cfb0bf7))
- JVM crash: `WalIterator.getBatch()` on an exhausted iterator ([659d897](https://github.com/dfa1/rocksdb-ffm/commit/659d897))
- JVM crash: `TransactionDB`/`OptimisticTransactionDB` used after `close()`, plus a native leak ([#101](https://github.com/dfa1/rocksdb-ffm/pull/101))
- JVM crash: `Snapshot.close()` after its owning DB closed ([#106](https://github.com/dfa1/rocksdb-ffm/pull/106))
- `printStackTrace` replaced with `System.Logger` ([7d99f03](https://github.com/dfa1/rocksdb-ffm/commit/7d99f03))
- SonarCloud test-assertion findings ([#98](https://github.com/dfa1/rocksdb-ffm/pull/98))

## [0.8] — 2026-08-16

Hermetic ZSTD/LZ4 on all five platforms, host-only local native builds, ADRs, and a clean RocksDB-vs-plumbing error split.

### Added

- ZSTD and LZ4 built hermetically and statically linked, Windows included ([4062792](https://github.com/dfa1/rocksdb-ffm/commit/4062792))
- `WriteOptions`: full field set, plus `IOPriority`/`IOActivity` enums ([9fbed88](https://github.com/dfa1/rocksdb-ffm/commit/9fbed88))
- `ReadOptions`: 7 more C API setters ([6e6c2ef](https://github.com/dfa1/rocksdb-ffm/commit/6e6c2ef))
- `BlockBasedTableOptions.FormatVersion` enum ([565ce69](https://github.com/dfa1/rocksdb-ffm/commit/565ce69))
- `ConcurrentStressIntegrationTest` ([#84](https://github.com/dfa1/rocksdb-ffm/pull/84))
- `docs/adr/`: four Architecture Decision Records ([#87](https://github.com/dfa1/rocksdb-ffm/pull/87))
- Local builds compile only the host classifier; `-Pall-natives` builds all five ([#86](https://github.com/dfa1/rocksdb-ffm/pull/86))
- `FfmValueSizeBenchmark`: 64B/128B sweep points ([f16b2b8](https://github.com/dfa1/rocksdb-ffm/commit/f16b2b8))

### Changed

- **Breaking:** `TxnDBWritePolicy` renamed to `WritePolicy` ([f30aac8](https://github.com/dfa1/rocksdb-ffm/commit/f30aac8))
- **Breaking:** transaction lock/expiration timeouts use `Duration` ([5570369](https://github.com/dfa1/rocksdb-ffm/commit/5570369))
- **Breaking:** `RocksDBException` only for genuine RocksDB errors, no public constructor ([#88](https://github.com/dfa1/rocksdb-ffm/pull/88))
- Int-backed enum lookups use `switch` instead of `values()` ([c506d06](https://github.com/dfa1/rocksdb-ffm/commit/c506d06))
- `PinnableHandle` extracted from `PinnableSlice` ([1cb6c45](https://github.com/dfa1/rocksdb-ffm/commit/1cb6c45))
- `Transaction`/`TransactionDB` `get`/`getForUpdate` use `PinnableSlice` ([fedacf8](https://github.com/dfa1/rocksdb-ffm/commit/fedacf8))
- C-string-to-`String` conversion centralized ([36f1b62](https://github.com/dfa1/rocksdb-ffm/commit/36f1b62))
- `keyMayExist` error handling centralized ([3c2a8ae](https://github.com/dfa1/rocksdb-ffm/commit/3c2a8ae))

### Fixed

- Dead `null` check in `Transaction.getSnapshot()` removed ([61ba393](https://github.com/dfa1/rocksdb-ffm/commit/61ba393))
- `PinnableHandle`/`PinnableSlice` no longer reinterpret pinned values twice ([12d54b8](https://github.com/dfa1/rocksdb-ffm/commit/12d54b8))
- Stale `[#open]` javadoc ref and missing `openBlob` row ([72a6e40](https://github.com/dfa1/rocksdb-ffm/commit/72a6e40))
- Windows hermetic ZSTD/LZ4 build fixes ([9af8b8f](https://github.com/dfa1/rocksdb-ffm/commit/9af8b8f))
- `build-rocksdb.sh` exports `AR` for cross-target archives ([3b5eaa4](https://github.com/dfa1/rocksdb-ffm/commit/3b5eaa4))
- `sonar.yml`/`publish.yml` native cache keys include build scripts ([e2cf50d](https://github.com/dfa1/rocksdb-ffm/commit/e2cf50d))

### Build & Tooling

- `checkstyle` 13.9.0 → 13.10.0 ([0c40152](https://github.com/dfa1/rocksdb-ffm/commit/0c40152))
- Zig bumped in CI ([21158b4](https://github.com/dfa1/rocksdb-ffm/commit/21158b4))
- `WriteBatchSizeBenchmark`: `-Djmh.forks` override ([b214b86](https://github.com/dfa1/rocksdb-ffm/commit/b214b86))

## [0.7] — 2026-08-15

Last three-tier gaps closed, open-factory names normalized, scoped zero-copy `get(key, Mapper)`, full transaction options, and SonarCloud.

### Added

- `ByteBuffer`/`MemorySegment` tiers for `WriteBatch`, transactions, `SstFileWriter` ([#69](https://github.com/dfa1/rocksdb-ffm/pull/69))
- `WriteBatchSizeBenchmark` ([#69](https://github.com/dfa1/rocksdb-ffm/pull/69))
- SonarCloud analysis and coverage, gated on every PR ([#63](https://github.com/dfa1/rocksdb-ffm/pull/63))
- Tests for previously 0%-coverage classes ([#65](https://github.com/dfa1/rocksdb-ffm/pull/65))
- `docs/` restructured with Diataxis ([#67](https://github.com/dfa1/rocksdb-ffm/pull/67))
- `LRUCache`/`HyperClockCache` tests, `RocksIterator.refresh()` coverage ([90d54ae](https://github.com/dfa1/rocksdb-ffm/commit/90d54ae))
- Scoped zero-copy `get(MemorySegment, Mapper)` and `RocksIterator.key/value(Mapper)` ([#57](https://github.com/dfa1/rocksdb-ffm/pull/57))
- `TransactionDBOptions`/`TransactionOptions`: full C API surface, `TxnDBWritePolicy` enum ([1348f3d](https://github.com/dfa1/rocksdb-ffm/commit/1348f3d))
- `FfmScaleBenchmark`/`JniScaleBenchmark` at 10k/100k keys ([#80](https://github.com/dfa1/rocksdb-ffm/pull/80))
- `EqualsVerifier` tests for `BackupId`/`MemorySize`/`SequenceNumber` ([310238d](https://github.com/dfa1/rocksdb-ffm/commit/310238d))
- `WriteBatch`: tests for 8 uncovered overloads ([269dd6a](https://github.com/dfa1/rocksdb-ffm/commit/269dd6a))
- `Checkpoint` on every DB type, plus `exportTo(Path)` ([#75](https://github.com/dfa1/rocksdb-ffm/pull/75))
- `docs/c-api-gaps.md`: library-version query as a Type B gap ([#78](https://github.com/dfa1/rocksdb-ffm/pull/78))

### Changed

- **Breaking:** open factories drop column families from their names (`openWithColumnFamilies` → `open`, …) ([#70](https://github.com/dfa1/rocksdb-ffm/pull/70))
- **Breaking:** `RocksDB.open` renamed to `openReadWrite` ([#70](https://github.com/dfa1/rocksdb-ffm/pull/70))
- **Breaking:** `get(ByteBuffer/MemorySegment, ...)` returns a sealed `CopyResult` instead of a sentinel ([#60](https://github.com/dfa1/rocksdb-ffm/pull/60))
- `rocksdb` submodule v11.0.4 → v11.8.1 ([#56](https://github.com/dfa1/rocksdb-ffm/pull/56))
- jqwik property tests replaced with parameterized tests ([#43](https://github.com/dfa1/rocksdb-ffm/pull/43))
- `Checkpoint`/`SstFileWriter` byte counts use `MemorySize` ([616b8c0](https://github.com/dfa1/rocksdb-ffm/commit/616b8c0))
- `PinnableSlice` owns all pinned-value consumption ([#80](https://github.com/dfa1/rocksdb-ffm/pull/80))
- `getBytes()`/`getCfBytes()` use `rocksdb_get_pinned_v2` ([#81](https://github.com/dfa1/rocksdb-ffm/pull/81))
- CI actions moved off deprecated runtimes; Dependabot tracks actions ([1986333](https://github.com/dfa1/rocksdb-ffm/commit/1986333))

### Fixed

- `TransactionDB.createColumnFamily()` uses the `txn_db` handle ([626428b](https://github.com/dfa1/rocksdb-ffm/commit/626428b))
- `getBytes()` uses `rocksdb_get_into_buffer` ([#54](https://github.com/dfa1/rocksdb-ffm/pull/54))
- README CI badge ([#51](https://github.com/dfa1/rocksdb-ffm/pull/51))
- Read benchmarks use a realistic dataset ([38b32e6](https://github.com/dfa1/rocksdb-ffm/commit/38b32e6))
- Blob-size sweep decoupled from the FFM suite ([#57](https://github.com/dfa1/rocksdb-ffm/pull/57))

### Removed

- `ReadWriteDB.getSupportedCompressions` ([3e6eae3](https://github.com/dfa1/rocksdb-ffm/commit/3e6eae3))
- `ColumnFamilyDescriptor.nameAsString()` ([#75](https://github.com/dfa1/rocksdb-ffm/pull/75))

### Security

- Third-party GitHub Actions pinned to commit SHAs ([#64](https://github.com/dfa1/rocksdb-ffm/pull/64))

### Build & Tooling

- RocksDB native builds cached in `sonar.yml`/`publish.yml` ([25678c0](https://github.com/dfa1/rocksdb-ffm/commit/25678c0))
- Dependency bumps (Maven plugins, `junit-jupiter`, `equalsverifier`, actions) ([#73](https://github.com/dfa1/rocksdb-ffm/pull/73), [#76](https://github.com/dfa1/rocksdb-ffm/pull/76), [#77](https://github.com/dfa1/rocksdb-ffm/pull/77))

## [0.6] — 2026-07-20

**Windows support** lands, completing the platform matrix (macOS, Linux x86_64/aarch64, Windows
x86_64/aarch64), alongside a project-wide American-English spelling pass and routine dependency
maintenance.

### Added

- Windows support (x86_64 + aarch64) via a cross-compiled native module. ([#15](https://github.com/dfa1/rocksdb-ffm/issues/15), [#38](https://github.com/dfa1/rocksdb-ffm/pull/38))
- `RocksIterator#error()` method. ([c14bc5d](https://github.com/dfa1/rocksdb-ffm/commit/c14bc5d))

### Changed

- American English spelling adopted throughout (javadoc, comments, identifiers), matching the JDK's own convention. ([#32](https://github.com/dfa1/rocksdb-ffm/pull/32))

### Fixed

- CI cache key for RocksDB native builds now derives from `CMakeLists.txt` instead of a value that didn't reliably invalidate on submodule bumps. ([e73fee1](https://github.com/dfa1/rocksdb-ffm/commit/e73fee1))

### Build & Tooling

- Dependency bumps: `junit-jupiter` (6.0.3 → 6.1.2), `checkstyle` (13.4.0 → 13.8.0), `flatten-maven-plugin`, `maven-javadoc-plugin`, `maven-source-plugin`, `maven-gpg-plugin`, `central-publishing-maven-plugin`, `jqwik`, `maven-failsafe-plugin`, `maven-surefire-plugin`.

## [0.5] — 2026-04-27

Rounds out the Maven Central publishing story: a BOM module, a `linux-aarch64` native artifact,
and a documented catalogue of C API gaps.

### Added

- `rocksdbffm-bom` module for consistent cross-module version alignment. ([afb4c35](https://github.com/dfa1/rocksdb-ffm/commit/afb4c35))
- `linux-aarch64` native module. ([ce50869](https://github.com/dfa1/rocksdb-ffm/commit/ce50869))
- Property-based tests (jqwik) for core invariants. ([67b46ba](https://github.com/dfa1/rocksdb-ffm/commit/67b46ba))
- `docs/c-api-gaps.md` catalogues every `rocksdb/c.h` symbol not yet mapped, split into Type A (blocked on the C API) and Type B (not yet implemented). ([853f3be](https://github.com/dfa1/rocksdb-ffm/commit/853f3be))

### Changed

- Release process simplified: steps inlined into the README, `scripts/release.sh` dropped. ([38bc8d1](https://github.com/dfa1/rocksdb-ffm/commit/38bc8d1))

## [0.4] — 2026-04-25

### Documentation

- Complete Javadoc coverage across every public API class, enforced by the build (`failOnError`/`failOnWarnings`). ([adb037d](https://github.com/dfa1/rocksdb-ffm/commit/adb037d))

## [0.3] — 2026-04-24

### Fixed

- All Javadoc errors and warnings fixed; `./mvnw javadoc:javadoc` is now enforced to produce zero output. ([066dc07](https://github.com/dfa1/rocksdb-ffm/commit/066dc07))

### Build & Tooling

- `<name>` added to every submodule POM (required for Maven Central); Maven Release Plugin artifacts gitignored. ([#12](https://github.com/dfa1/rocksdb-ffm/issues/12))

## [0.2] — 2026-04-24

Same-day patch release: the GitHub release asset upload from v0.1 didn't complete correctly.

### Fixed

- GitHub release asset upload. ([76ef092](https://github.com/dfa1/rocksdb-ffm/commit/76ef092))

## [0.1] — 2026-04-24

Initial release. An FFM-based RocksDB binding built from scratch against `rocksdb/include/rocksdb/c.h`, targeting feature parity with the JNI binding.

### Added

- Core read-write path: `RocksDB.open`, `put`/`get`/`delete` across all three access tiers (`byte[]`, `ByteBuffer`, `MemorySegment`), zero-copy reads via PinnableSlice. ([fa37694](https://github.com/dfa1/rocksdb-ffm/commit/fa37694), [f19b48b](https://github.com/dfa1/rocksdb-ffm/commit/f19b48b))
- `WriteBatch` with full tier coverage. ([b5b515d](https://github.com/dfa1/rocksdb-ffm/commit/b5b515d))
- `Options`/`WriteOptions`/`ReadOptions`, `createIfMissing`, read-only open. ([aa86e1f](https://github.com/dfa1/rocksdb-ffm/commit/aa86e1f))
- Additional DB types: `TtlDB` ([d6efa3d](https://github.com/dfa1/rocksdb-ffm/commit/d6efa3d)), `TransactionDB` ([a20b25b](https://github.com/dfa1/rocksdb-ffm/commit/a20b25b)), `OptimisticTransactionDB` ([c4be4ae](https://github.com/dfa1/rocksdb-ffm/commit/c4be4ae)), `SecondaryDB` ([fd5b857](https://github.com/dfa1/rocksdb-ffm/commit/fd5b857)), `BlobDB` ([f2556dd](https://github.com/dfa1/rocksdb-ffm/commit/f2556dd)).
- Full column family support across every DB type. ([18d5181](https://github.com/dfa1/rocksdb-ffm/commit/18d5181))
- Table options: `BlockBasedTableConfig`, `LRUCache`, `HyperClockCache`, `FilterPolicy`. ([4862f8c](https://github.com/dfa1/rocksdb-ffm/commit/4862f8c), [ee7073a](https://github.com/dfa1/rocksdb-ffm/commit/ee7073a))
- `RocksIterator` with full three-tier API. ([18d35ce](https://github.com/dfa1/rocksdb-ffm/commit/18d35ce))
- Snapshots ([a029e75](https://github.com/dfa1/rocksdb-ffm/commit/a029e75)), Flush ([11236db](https://github.com/dfa1/rocksdb-ffm/commit/11236db)), DeleteRange ([16f55c2](https://github.com/dfa1/rocksdb-ffm/commit/16f55c2)), `keyMayExist` ([0e33c68](https://github.com/dfa1/rocksdb-ffm/commit/0e33c68)).
- DB property inspection (`getProperty`/`getLongProperty`) and a typed `Property` enum. ([286ae86](https://github.com/dfa1/rocksdb-ffm/commit/286ae86), [2f5de63](https://github.com/dfa1/rocksdb-ffm/commit/2f5de63))
- Statistics: `HistogramType`, `TickerType`, `StatsLevel`. ([0832102](https://github.com/dfa1/rocksdb-ffm/commit/0832102))
- Compaction control (background job control plus `WaitForCompactOptions`). ([6a7ffbd](https://github.com/dfa1/rocksdb-ffm/commit/6a7ffbd), [02a967a](https://github.com/dfa1/rocksdb-ffm/commit/02a967a))
- WAL iteration (`getUpdatesSince`) for change-data-capture. ([c998a4b](https://github.com/dfa1/rocksdb-ffm/commit/c998a4b))
- SST file ingest and `SstFileWriter`. ([d27a4e1](https://github.com/dfa1/rocksdb-ffm/commit/d27a4e1))
- `Checkpoint` API for point-in-time snapshot export. ([1943787](https://github.com/dfa1/rocksdb-ffm/commit/1943787))
- `Logger` (stderr and callback variants), `RateLimiter` (writes-only/reads-only/all-IO/auto-tuned), `SstFileManager` + `Env`. ([d9a5a1f](https://github.com/dfa1/rocksdb-ffm/commit/d9a5a1f), [7e8432b](https://github.com/dfa1/rocksdb-ffm/commit/7e8432b), [14aaca6](https://github.com/dfa1/rocksdb-ffm/commit/14aaca6))
- `BackupEngine` with full backup/restore support and a `BackupId` domain primitive. ([9e1be63](https://github.com/dfa1/rocksdb-ffm/commit/9e1be63), [7fc5e7f](https://github.com/dfa1/rocksdb-ffm/commit/7fc5e7f))
- `PerfContext`/`PerfLevel`/`PerfMetric`. ([9b94b29](https://github.com/dfa1/rocksdb-ffm/commit/9b94b29))
- `CompressionType` enum with a runtime support probe. ([bf6ccdf](https://github.com/dfa1/rocksdb-ffm/commit/bf6ccdf))
- `MemorySize` and `SequenceNumber` domain primitives, replacing raw `long` across the size and sequence-number APIs. ([a469383](https://github.com/dfa1/rocksdb-ffm/commit/a469383))
- Multi-module native distribution, cross-compiled for macOS and Linux x86_64 via Zig. ([ca9ce37](https://github.com/dfa1/rocksdb-ffm/commit/ca9ce37), [dce6303](https://github.com/dfa1/rocksdb-ffm/commit/dce6303))
- JMH benchmarks comparing FFM vs JNI for reads, writes, and batches. ([3f862b8](https://github.com/dfa1/rocksdb-ffm/commit/3f862b8), [edafeee](https://github.com/dfa1/rocksdb-ffm/commit/edafeee))
- CI: build on macOS and Linux, CodeQL analysis, Dependabot. ([f076917](https://github.com/dfa1/rocksdb-ffm/commit/f076917), [9c74a72](https://github.com/dfa1/rocksdb-ffm/commit/9c74a72))

### Changed

- All tests migrated to the `// Given / // When / // Then` + AssertJ convention. ([c8cfae5](https://github.com/dfa1/rocksdb-ffm/commit/c8cfae5))
- Error handling centralized on `RocksDB.errHolder`/`checkError`; per-class `ThreadLocal` error pointers removed in favor of a shared `Arena`-based pattern. ([736c926](https://github.com/dfa1/rocksdb-ffm/commit/736c926))

[Unreleased]: https://github.com/dfa1/rocksdb-ffm/compare/v0.15...HEAD
[0.15]: https://github.com/dfa1/rocksdb-ffm/compare/v0.14...v0.15
[0.14]: https://github.com/dfa1/rocksdb-ffm/compare/v0.13...v0.14
[0.13]: https://github.com/dfa1/rocksdb-ffm/compare/v0.12...v0.13
[0.12]: https://github.com/dfa1/rocksdb-ffm/compare/v0.11...v0.12
[0.11]: https://github.com/dfa1/rocksdb-ffm/compare/v0.10...v0.11
[0.10]: https://github.com/dfa1/rocksdb-ffm/compare/v0.9...v0.10
[0.9]: https://github.com/dfa1/rocksdb-ffm/compare/v0.8...v0.9
[0.8]: https://github.com/dfa1/rocksdb-ffm/compare/v0.7...v0.8
[0.7]: https://github.com/dfa1/rocksdb-ffm/compare/v0.6...v0.7
[0.6]: https://github.com/dfa1/rocksdb-ffm/compare/v0.5...v0.6
[0.5]: https://github.com/dfa1/rocksdb-ffm/compare/v0.4...v0.5
[0.4]: https://github.com/dfa1/rocksdb-ffm/compare/v0.3...v0.4
[0.3]: https://github.com/dfa1/rocksdb-ffm/compare/v0.2...v0.3
[0.2]: https://github.com/dfa1/rocksdb-ffm/compare/v0.1...v0.2
