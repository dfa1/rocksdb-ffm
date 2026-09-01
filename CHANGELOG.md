# Changelog

All notable changes to **rocksdbffm** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `BlockBasedTableOptions`: auto-readahead tuning — `setMaxAutoReadaheadSize`/`getMaxAutoReadaheadSize`,
  `setInitialAutoReadaheadSize`/`getInitialAutoReadaheadSize`, `setNumFileReadsForAutoReadahead`/
  `getNumFileReadsForAutoReadahead`.
- `BlockBasedTableOptions`: cache pinning and priority — new `PinningTier` enum plus
  `setTopLevelIndexPinningTier`, `setPartitionPinningTier`, `setUnpartitionedPinningTier`
  (setter-only in `c.h`, no getters), and `setCacheIndexAndFilterBlocksWithHighPriority`/
  `setPinL0FilterAndIndexBlocksInCache`/`setPinTopLevelIndexAndFilter` with their getters.
- `BlockBasedTableOptions`: block layout tuning — `setBlockRestartInterval`/
  `getBlockRestartInterval`, `setIndexBlockRestartInterval`/`getIndexBlockRestartInterval`,
  `setMetadataBlockSize`/`getMetadataBlockSize`, `setBlockSizeDeviation`/`getBlockSizeDeviation`,
  `setUseDeltaEncoding`/`getUseDeltaEncoding`, `setSeparateKeyValueInDataBlock`/
  `getSeparateKeyValueInDataBlock`.
- `FifoCompactionOptions`: new wrapper for FIFO compaction, attached via `Options#setCompactionStyle`
  (new `Options.CompactionStyle` enum) and `Options#setFifoCompactionOptions`.
- `UniversalCompactionOptions`: new wrapper for universal compaction (new `StopStyle` enum),
  attached via `Options#setCompactionStyle` and `Options#setUniversalCompactionOptions`.
- `EnvOptions`: new wrapper for `rocksdb_envoptions_t` (mmap/direct I/O, sync cadence, fallocate,
  readahead, rate limiting), previously completely unmapped. Attached via a new
  `SstFileWriter.newSstFileWriter(Options, EnvOptions)` overload; the existing single-arg
  overload keeps using RocksDB's defaults internally.
- `EventNotifier`: wraps `rocksdb_eventlistener_create()` for flush/compaction/ingestion/
  background-error/stall/memtable-seal callbacks, attached via `Options.addEventListener`.
  ([#121](https://github.com/dfa1/rocksdbffm/pull/121))
- `Tracing & Replay`: `RocksDBTracingOperations.startTrace`/`endTrace` capture DB operations to a
  file (tuned via `TraceOptions`); `Replayer`/`ReplayOptions` reissue a captured trace against a
  target database.
- `CuckooTableOptions`: new wrapper for the hash-based Cuckoo Table SST format, attached via a new
  `Options.setTableFormatConfig(CuckooTableOptions)` overload.
- `BlockBasedTableOptions`: remaining filter/index/corruption/block-alignment tuning, plus
  `setPrepopulateBlockCache` and the string-based user-defined-index activation trio.
- `Options`: basic LSM-shape tuning — `setWriteBufferSize`, `setNumLevels`,
  `setLevel0FileNumCompactionTrigger`, `setTargetFileSizeBase`, `setMaxBytesForLevelBase`.
- `ReadOptions.setAutoPrefixMode`: lets RocksDB auto-select prefix-seek mode on `Seek()` when safe.
- `FilterPolicy.newRibbonHybrid`: Bloom for the top levels, Ribbon below, via `bloomBeforeLevel`.

### Fixed

- `Logger`'s callback path and `MergeOperator.custom` could deadlock the JVM on `System.exit()`
  once a callback had run on a RocksDB background thread. Fixed by a new
  `BackgroundUpcallThreads` shutdown hook; introduced alongside `EventNotifier`, which hits the
  same class of bug. ([#121](https://github.com/dfa1/rocksdbffm/pull/121))

## [0.10] — 2026-08-30

`ReadBatch` (a reusable, preallocated multiGet), full SST-file inspection (`LiveFiles`,
`LiveFilesStorageInfo`), `SstPartitionerFactory` and `SliceTransform` prefix wrappers, explicit
`WriteOptions`/`ReadOptions` overloads across the direct read/write surface, a zero-copy
`MergeOperator.custom`, and three breaking API cleanups.

### Added

- `ReadBatch`: batched multiGet, all three tiers (byte[], ByteBuffer, MemorySegment/zero-copy via
  `Mapper`), built on `rocksdb_batched_multi_get_cf`. Reusable and preallocated — create once for
  up to N keys, call `get(keys, ...)` repeatedly with no per-call bookkeeping-array allocation.
  There's no separate one-shot `multiGet()` method; a single-batch read is just `try (var batch =
  ReadBatch.create(db, keys.size())) { return batch.get(keys, fn); }`. The zero-copy tier's `get`
  returns `List<R>` with `null` marking a missing key, not `List<Optional<R>>`, matching `get(key,
  Mapper<R>)`'s nullable-`R` convention. (closes [#126](https://github.com/dfa1/rocksdbffm/issues/126))
- `LiveFiles`/`LiveFileInfo`: lazy, per-field view over `rocksdb_livefiles` for inspecting a
  database's current SST files. ([#124](https://github.com/dfa1/rocksdbffm/pull/124))
- `LiveFilesStorageInfo`/`LiveFileStorageInfo`: broader than `LiveFiles` — every file needed to
  reconstruct the database (SST, WAL, MANIFEST, `CURRENT`, `OPTIONS`, blob), over
  `rocksdb_get_livefiles_storage_info`. ([#125](https://github.com/dfa1/rocksdbffm/pull/125))
- `SstPartitionerFactory` (fixed-prefix), wired via a new `Options` setter, so compaction can
  split output SST files at key-prefix boundaries instead of only by size. Closes the Type A half
  of [#123](https://github.com/dfa1/rocksdbffm/issues/123); the custom callback-based partitioner
  half has no C API entry point and is recorded as a Type B gap. Also fixes forced bottommost
  compaction. ([#124](https://github.com/dfa1/rocksdbffm/pull/124))
- `SliceTransform` (fixed-prefix extractor), wired via `Options.setPrefixExtractor`, enabling
  prefix-based Bloom filters and prefix iteration.
  ([#128](https://github.com/dfa1/rocksdbffm/pull/128))
- Explicit `WriteOptions` overloads for every direct write op (put/merge/delete/deleteRange/write,
  all tiers and CF variants) — 30 new methods alongside the existing default-options methods.
  ([#113](https://github.com/dfa1/rocksdbffm/pull/113))
- Explicit `ReadOptions` overloads for `get(ByteBuffer, ByteBuffer)`, `get(MemorySegment,
  MemorySegment)`, and `get(MemorySegment, Mapper)` (plus CF-scoped equivalents), matching the
  `byte[]` tier which already had both.

### Changed

- `RocksDB.close` renamed to `closeDb` for clarity alongside the `open*` factories.
- **Breaking:** `MergeOperator.custom`'s `FullMergeFn` now receives zero-copy `MemorySegment`
  views of the key, existing value, and operands instead of copied `byte[]`s — benchmarking
  showed the copies dominating once operands exceed ~1KB, so there's no separate copying tier.
  (closes [#94](https://github.com/dfa1/rocksdbffm/issues/94))
- `Options.setCompression`/`setBlobCompressionType` now throw `UnsupportedOperationException` for
  `SNAPPY`, `ZLIB`, `BZLIB2`, `XPRESS` — not linked into the bundled native library yet — instead
  of failing opaquely at DB-open time. ([#114](https://github.com/dfa1/rocksdbffm/pull/114),
  part of [#83](https://github.com/dfa1/rocksdbffm/issues/83))
- **Breaking:** the scoped zero-copy `get(key, Mapper<R>)` (and its `ColumnFamilyHandle`/
  `ReadOptions` overloads, across `RocksDBReadOperations`, `TransactionDB`, `Transaction`) now
  returns `R` directly instead of `Optional<R>`, with `null` meaning "key not found" — matching
  the existing `byte[] get(key)` convention. `Optional` boxed a result on every present read,
  defeating the point of a zero-copy path. ([#130](https://github.com/dfa1/rocksdbffm/pull/130))
- **Breaking:** `RocksDB.errHolder`, `toNative`, `free`, `checkError`, `toByteArray`,
  `toBorrowedJavaString`, `toJavaString`, `toOptionalString`, `toByte`, `fromByte` are now
  package-private. They're internal FFM plumbing consumed only by other wrapper classes in this
  library, never meant for callers — being `public` just meant they cluttered `RocksDB`'s javadoc
  page alongside the `open*`/`listColumnFamilies` factories users actually want.
  ([#132](https://github.com/dfa1/rocksdbffm/pull/132), part of
  [#131](https://github.com/dfa1/rocksdbffm/issues/131))

### Fixed

- `MemorySize.toString()` no longer puts a space between the value and its unit ("1571B", not
  "1571 B") — matching the no-space convention used elsewhere (e.g. "10ms"), and no longer reading
  as two fields when interleaved with other key=value diagnostic output.
- Forced bottommost compaction, fixed alongside the `SstPartitionerFactory` work.
  ([#124](https://github.com/dfa1/rocksdbffm/pull/124))

## [0.9] — 2026-08-21

Four JVM-crash fixes, `OptimisticTransactionDB` gaining the full shared read/write surface,
`merge()` across every write-capable type, and a new `MergeOperator`.

### Added

- `Temperature` enum (storage-tier hint) wired into 5 `Options` setter/getter pairs. EXPERIMENTAL
  upstream; a no-op unless a custom `FileSystem` inspects it.
- `Property.COMPACTION_ABORT_COUNT`, found missing during a property-set audit against
  `rocksdb/include/rocksdb/db.h`.
- `merge()` (all three tiers + CF variants) on `ReadWriteDB`, `TtlDB`, `BlobDB`,
  `OptimisticTransactionDB`, `TransactionDB`, `Transaction`, `WriteBatch`
  (closes [#8](https://github.com/dfa1/rocksdbffm/issues/8)).
- `MergeOperator`: `uint64Add()` (built-in sum) and `custom(String, FullMergeFn)` (Java-callback),
  attached via `Options.setMergeOperator`.
- `RocksDB.openSecondary` gains a column-family-descriptor overload.
  ([52c9bbd](https://github.com/dfa1/rocksdbffm/commit/52c9bbd))
- `OptimisticTransactionDB` now implements `RocksDBReadOperations`/`RocksDBWriteOperations`
  instead of ~50 hand-duplicated methods, gaining `keyMayExist`, `write(WriteBatch)`, compaction
  control, WAL iteration, `ingestExternalFile`, and more.
  ([#105](https://github.com/dfa1/rocksdbffm/pull/105))

### Changed

- **Breaking:** `RateLimiter`'s refill period is now a `Duration` instead of a raw microsecond
  `long`.
- **Breaking:** `BackupInfo.timestamp` is now an `Instant` instead of a raw `long`.
- `RocksDBReadOperations`/`RocksDBWriteOperations` extracted: shared default-method interfaces
  for the direct DB surface, replacing per-type duplication.
  ([53416ca](https://github.com/dfa1/rocksdbffm/commit/53416ca) and follow-ups)
- `UpcallRegistry<T>` extracted: shared id-registry for native-callback state, used by `Logger`
  and `MergeOperator.Custom`. ([0b4d21a](https://github.com/dfa1/rocksdbffm/commit/0b4d21a))
- `NativeObjectWithBaseDb`/`NativeObjectWithChildren` extracted: checked access to
  `TransactionDB`/`OptimisticTransactionDB`'s base-DB pointer, and automatic snapshot cleanup on
  DB close. ([#101](https://github.com/dfa1/rocksdbffm/pull/101),
  [#106](https://github.com/dfa1/rocksdbffm/pull/106))

### Fixed

- **4 JVM crashes:** `Transaction.getSnapshot()` on a transaction without snapshot support
  ([cfb0bf7](https://github.com/dfa1/rocksdbffm/commit/cfb0bf7)); `WalIterator.getBatch()` on an
  exhausted iterator ([659d897](https://github.com/dfa1/rocksdbffm/commit/659d897),
  [ef80fd2](https://github.com/dfa1/rocksdbffm/commit/ef80fd2)); `TransactionDB`/
  `OptimisticTransactionDB` used after `close()`, also fixing a native leak found in the process
  ([#101](https://github.com/dfa1/rocksdbffm/pull/101)); `Snapshot.close()` after its owning DB
  was already closed — the DB now tracks and releases its own snapshots first
  ([#106](https://github.com/dfa1/rocksdbffm/pull/106)).
- `printStackTrace` replaced with `System.Logger` in the one place it had leaked through.
  ([7d99f03](https://github.com/dfa1/rocksdbffm/commit/7d99f03))
- SonarCloud findings: missing test assertions, non-dedicated AssertJ assertions.
  ([67ac576](https://github.com/dfa1/rocksdbffm/commit/67ac576),
  [e8ea801](https://github.com/dfa1/rocksdbffm/commit/e8ea801))

## [0.8] — 2026-08-16

Hermetic ZSTD/LZ4 on all five platforms including Windows, local builds now cross-compile only the
host's own native classifier, four Architecture Decision Records, and a clean split between genuine
RocksDB errors and bugs in this library's own FFM plumbing.

### Added

- ZSTD and LZ4 now built hermetically via `zig cc` and statically linked, replacing an unreliable host-library probe; tests confirm the codecs actually compress — including on Windows, via CMake instead of the POSIX Makefile. ([4062792](https://github.com/dfa1/rocksdbffm/commit/4062792), [06c3840](https://github.com/dfa1/rocksdbffm/commit/06c3840), [19ef44c](https://github.com/dfa1/rocksdbffm/commit/19ef44c))
- `WriteOptions` now maps its full field set (`sync`, `disableWal`, `noSlowdown`, `lowPri`, `ioActivity`, and more), plus new `IOPriority`/`IOActivity` enums. ([9fbed88](https://github.com/dfa1/rocksdbffm/commit/9fbed88))
- `ReadOptions` maps 7 more C API setters: `verifyChecksums`, `fillCache`, `pinData`, `tailing`, `totalOrderSeek`, `prefixSameAsStart`, `readaheadSize`. ([6e6c2ef](https://github.com/dfa1/rocksdbffm/commit/6e6c2ef))
- `BlockBasedTableOptions.FormatVersion` enum replaces the unvalidated raw `int` `setFormatVersion` took. ([565ce69](https://github.com/dfa1/rocksdbffm/commit/565ce69))
- `ConcurrentStressIntegrationTest`. ([#84](https://github.com/dfa1/rocksdbffm/pull/84))
- `docs/adr/` — four Architecture Decision Records covering FFM-vs-JNI, the `zig cc` build, the ownership model, and error handling. ([#87](https://github.com/dfa1/rocksdbffm/pull/87))
- Local `mvn` builds now cross-compile only the host's own native classifier by default (each other one logs `skipping execute as per configuration`) instead of all five every time; `-Pall-natives` forces the full matrix, used by CI and releases. ([#86](https://github.com/dfa1/rocksdbffm/pull/86))
- `FfmValueSizeBenchmark` gains 64B/128B sweep points. ([f16b2b8](https://github.com/dfa1/rocksdbffm/commit/f16b2b8))

### Changed

- **Breaking:** `TxnDBWritePolicy` renamed to `WritePolicy`. ([f30aac8](https://github.com/dfa1/rocksdbffm/commit/f30aac8))
- **Breaking:** Transaction/TransactionDB lock and expiration timeouts now use `Duration` (`null` = disabled/wait-forever) instead of raw `long` + `-1`. ([5570369](https://github.com/dfa1/rocksdbffm/commit/5570369))
- **Breaking:** `RocksDBException` has no public constructor and is now thrown only for a genuine RocksDB-reported error; `wrap()` is deleted. A bug in this library's own FFM plumbing now surfaces as its own exception type (`NullPointerException`, `IllegalStateException`, …) or `AssertionError` instead of masquerading as a `RocksDBException` — see [ADR 0004](docs/adr/0004-error-handling.md). ([#88](https://github.com/dfa1/rocksdbffm/pull/88))
- Int-backed enum reverse lookups now use `switch` instead of a `values()`-loop. ([c506d06](https://github.com/dfa1/rocksdbffm/commit/c506d06))
- `PinnableHandle` extracted from `PinnableSlice`; both now reuse the caller's dead `errptr` slot and check `NULL` before opening their try-scope. ([1cb6c45](https://github.com/dfa1/rocksdbffm/commit/1cb6c45), [460b9d7](https://github.com/dfa1/rocksdbffm/commit/460b9d7), [2273661](https://github.com/dfa1/rocksdbffm/commit/2273661))
- `Transaction`/`TransactionDB`'s `get`/`getForUpdate` now go through `PinnableSlice` instead of the malloc'd `rocksdb_*_get[_for_update]`. ([fedacf8](https://github.com/dfa1/rocksdbffm/commit/fedacf8))
- Malloc'd-C-string-to-`String` conversion centralized into `RocksDB.toJavaString`/`toOptionalString`. ([36f1b62](https://github.com/dfa1/rocksdbffm/commit/36f1b62))
- `keyMayExist`'s try/catch centralized into `RocksDB` static helpers, shared by `ReadWriteDB`/`TtlDB`. ([3c2a8ae](https://github.com/dfa1/rocksdbffm/commit/3c2a8ae), [b1c9ed4](https://github.com/dfa1/rocksdbffm/commit/b1c9ed4))

### Fixed

- `Transaction.getSnapshot()` never actually returned `null` — dropped the dead check and its javadoc claim. ([61ba393](https://github.com/dfa1/rocksdbffm/commit/61ba393))
- `PinnableHandle`/`PinnableSlice` reinterpreted their pinned value twice, costing throughput on small values. ([12d54b8](https://github.com/dfa1/rocksdbffm/commit/12d54b8), [cc99c1b](https://github.com/dfa1/rocksdbffm/commit/cc99c1b))
- Stale `[#open]` javadoc reference and missing `openBlob` row fixed. ([72a6e40](https://github.com/dfa1/rocksdbffm/commit/72a6e40))
- Windows hermetic ZSTD/LZ4 build: wrong `RANLIB` precedence broke `liblz4.a`, zstd's tarball shipped Windows-incompatible symlinks, a `make -n` "dry run" wasn't actually one (GNU Make always executes `$(MAKE)`-referencing recipe lines even under `-n`), and zstd/lz4 built unoptimized (`DEBUG_LEVEL=1`) inside an otherwise-release build. ([cb37b2e](https://github.com/dfa1/rocksdbffm/commit/cb37b2e)..[19ef44c](https://github.com/dfa1/rocksdbffm/commit/19ef44c), [9af8b8f](https://github.com/dfa1/rocksdbffm/commit/9af8b8f))
- `build-rocksdb.sh` never exported `AR`, so `make libzstd.a liblz4.a` used the host's native `ar`, producing archives in the wrong convention for a cross-target linker. ([3b5eaa4](https://github.com/dfa1/rocksdbffm/commit/3b5eaa4))
- `sonar.yml`/`publish.yml` native-lib cache keys were missing build-script inputs (unlike `ci.yml`), serving a stale pre-hermetic-compression native library. ([e2cf50d](https://github.com/dfa1/rocksdbffm/commit/e2cf50d))

### Build & Tooling

- `checkstyle` bumped 13.9.0 → 13.10.0. ([0c40152](https://github.com/dfa1/rocksdbffm/commit/0c40152))
- Zig version bumped in CI. ([21158b4](https://github.com/dfa1/rocksdbffm/commit/21158b4))
- `WriteBatchSizeBenchmark` gains a `-Djmh.forks` override, matching `FfmValueSizeBenchmark`/`ScaleBenchmarkRunner`. ([b214b86](https://github.com/dfa1/rocksdbffm/commit/b214b86))

## [0.7] — 2026-08-15

Closes out the last three-tier API gaps, normalizes `RocksDB`'s open-mode factory names, adds a
scoped zero-copy `get(key, Mapper)` read path to every DB type, completes the
`TransactionDBOptions`/`TransactionOptions` surface, consolidates `PinnableSlice`, and adds
SonarCloud + coverage infrastructure.

### Added

- `ByteBuffer`/`MemorySegment` tiers for the last three-tier gaps: `WriteBatch`, `TransactionDB`, `OptimisticTransactionDB`, `SstFileWriter`, and all of `Transaction` (previously `byte[]`-only). ([#68](https://github.com/dfa1/rocksdbffm/issues/68), [#69](https://github.com/dfa1/rocksdbffm/pull/69))
- `WriteBatchSizeBenchmark` sweeps batch size (1–400) across all four write tiers, with realistic 16-byte key / 1KB value data. ([#69](https://github.com/dfa1/rocksdbffm/pull/69))
- SonarCloud static analysis and coverage reporting, gated on every PR. ([#62](https://github.com/dfa1/rocksdbffm/issues/62), [#63](https://github.com/dfa1/rocksdbffm/pull/63))
- Test coverage added for previously 0%-coverage classes; `integration-tests` now feeds the aggregate coverage report. ([#65](https://github.com/dfa1/rocksdbffm/pull/65))
- `docs/` restructured using the [Diataxis](https://diataxis.fr/) framework, replacing one long README. ([#66](https://github.com/dfa1/rocksdbffm/issues/66), [#67](https://github.com/dfa1/rocksdbffm/pull/67))
- Dedicated `LRUCache`/`HyperClockCache` tests; `RocksIterator.refresh()` snapshot-semantics coverage restored. ([90d54ae](https://github.com/dfa1/rocksdbffm/commit/90d54ae))
- Scoped zero-copy `get(MemorySegment, Mapper)` added across every DB type, plus `RocksIterator.key(Mapper)`/`value(Mapper)`. ([#57](https://github.com/dfa1/rocksdbffm/pull/57))
- `TransactionDBOptions`/`TransactionOptions` now map the complete C API option surface (22 new option pairs), plus a new `TxnDBWritePolicy` enum. ([1348f3d](https://github.com/dfa1/rocksdbffm/commit/1348f3d))
- `FfmScaleBenchmark`/`JniScaleBenchmark`: FFM-vs-JNI comparison at realistic key counts (10k/100k) instead of a near-empty database. ([#80](https://github.com/dfa1/rocksdbffm/pull/80))
- `EqualsVerifier` contract tests for `BackupId`/`MemorySize`/`SequenceNumber`'s hand-rolled `equals`/`hashCode`. ([310238d](https://github.com/dfa1/rocksdbffm/commit/310238d))
- `WriteBatch`: 8 previously-uncovered overloads now have tests. ([269dd6a](https://github.com/dfa1/rocksdbffm/commit/269dd6a))
- `Checkpoint` coverage extended to `BlobDB`, `TtlDB`, `ReadOnlyDB`, `SecondaryDB`, plus an `exportTo(Path)` overload. ([#75](https://github.com/dfa1/rocksdbffm/pull/75))
- `docs/c-api-gaps.md`: documented library-version-query as a Type B gap. ([#78](https://github.com/dfa1/rocksdbffm/pull/78))

### Changed

- **Breaking:** `RocksDB`'s open-mode factories no longer bake column-family support into the name — `openWithColumnFamilies` → `open(..., descriptors, handles)`, `openReadOnlyWithColumnFamilies` → `openReadOnly(...)`, `openWithTtl` → `openTtl(...)`, `openWithBlobFiles` → `openBlob(...)`, `openTransactionWithColumnFamilies` → `openTransaction(...)`, `openOptimisticWithColumnFamilies` → `openOptimistic(...)`. ([#70](https://github.com/dfa1/rocksdbffm/pull/70))
- **Breaking:** `RocksDB.open` renamed to `RocksDB.openReadWrite`, matching every other factory's naming. ([#70](https://github.com/dfa1/rocksdbffm/pull/70))
- **Breaking:** `ReadWriteDB#get(ByteBuffer/MemorySegment, ...)` returns a sealed `CopyResult` instead of an `int`/`long` sentinel — the old encoding let a too-small destination silently truncate the value, and a value above `Integer.MAX_VALUE` collided with the not-found sentinel. ([#44](https://github.com/dfa1/rocksdbffm/issues/44), [#47](https://github.com/dfa1/rocksdbffm/issues/47), [#54](https://github.com/dfa1/rocksdbffm/pull/54), [#60](https://github.com/dfa1/rocksdbffm/pull/60))
- `rocksdb` submodule upgraded from v11.0.4 to v11.8.1. ([#53](https://github.com/dfa1/rocksdbffm/issues/53), [#56](https://github.com/dfa1/rocksdbffm/pull/56))
- Property-based tests (jqwik) replaced with parameterized invariant tests. ([#43](https://github.com/dfa1/rocksdbffm/pull/43))
- `Checkpoint` and `SstFileWriter` byte-count APIs use `MemorySize` instead of raw `long`. ([616b8c0](https://github.com/dfa1/rocksdbffm/commit/616b8c0))
- `PinnableSlice` now owns all pinned-value consumption (`toByteArray`, `copyInto`, `map`) instead of every `get(...)` overload hand-rolling it — 12 call sites migrated, no behavior change. ([#58](https://github.com/dfa1/rocksdbffm/issues/58), [#80](https://github.com/dfa1/rocksdbffm/pull/80))
- `getBytes()`/`getCfBytes()` moved to `rocksdb_get_pinned_v2`, cutting a `PinnableSlice` allocation at every value size. ([#81](https://github.com/dfa1/rocksdbffm/pull/81))
- CI: `setup-zig` action migrated off the unmaintained fork; `checkout`/`setup-java`/`cache` off the deprecated Node 20 runtime; `dependabot.yml` now tracks `github-actions`. ([1986333](https://github.com/dfa1/rocksdbffm/commit/1986333), [7c13009](https://github.com/dfa1/rocksdbffm/commit/7c13009))

### Fixed

- `TransactionDB.createColumnFamily()` now creates the column family on the `txn_db` handle instead of one it can't see. ([#61](https://github.com/dfa1/rocksdbffm/issues/61), [626428b](https://github.com/dfa1/rocksdbffm/commit/626428b))
- `getBytes()` now delegates to `rocksdb_get_into_buffer` instead of allocating an intermediate PinnableSlice. ([#52](https://github.com/dfa1/rocksdbffm/issues/52), [#54](https://github.com/dfa1/rocksdbffm/pull/54))
- CI badge no longer renders "no status" on the README. ([#51](https://github.com/dfa1/rocksdbffm/pull/51))
- Read benchmarks no longer measure against a 1–2 key database: `FfmBlobSizeBenchmark` renamed to `FfmValueSizeBenchmark` with a real 32 MB dataset; `FfmBenchmark`/`JniBenchmark` documented as per-call-overhead microbenchmarks. ([38b32e6](https://github.com/dfa1/rocksdbffm/commit/38b32e6), [55a2d34](https://github.com/dfa1/rocksdbffm/commit/55a2d34))
- Blob-size benchmark sweep decoupled from the FFM suite — it previously reran (and polluted the block cache for) every other benchmark once per sweep size. ([#57](https://github.com/dfa1/rocksdbffm/pull/57))

### Removed

- `ReadWriteDB.getSupportedCompressions` dropped (unused). ([3e6eae3](https://github.com/dfa1/rocksdbffm/commit/3e6eae3))
- `ColumnFamilyDescriptor.nameAsString()` dropped (unused). ([#75](https://github.com/dfa1/rocksdbffm/pull/75))

### Security

- Third-party GitHub Actions pinned to a full commit SHA instead of a floating tag. ([#64](https://github.com/dfa1/rocksdbffm/pull/64))

### Build & Tooling

- RocksDB native builds cached in `sonar.yml`/`publish.yml`; `publish.yml` gains a `workflow_dispatch` ref input. ([25678c0](https://github.com/dfa1/rocksdbffm/commit/25678c0), [6e3ec0d](https://github.com/dfa1/rocksdbffm/commit/6e3ec0d), [ee2c7d1](https://github.com/dfa1/rocksdbffm/commit/ee2c7d1))
- Dependency bumps: `flatten-maven-plugin`, `checkstyle` (13.8.0 → 13.9.0), `maven-jar-plugin`, `jacoco-maven-plugin` (0.8.13 → 0.8.15), `junit-jupiter` (6.1.2 → 6.1.3, [#77](https://github.com/dfa1/rocksdbffm/pull/77)), `equalsverifier` (3.17.5 → 4.5, [#76](https://github.com/dfa1/rocksdbffm/pull/76)), `maven-dependency-submission-action` (4.1.3 → 5.0.0, [#73](https://github.com/dfa1/rocksdbffm/pull/73)).

## [0.6] — 2026-07-20

**Windows support** lands, completing the platform matrix (macOS, Linux x86_64/aarch64, Windows
x86_64/aarch64), alongside a project-wide American-English spelling pass and routine dependency
maintenance.

### Added

- Windows support (x86_64 + aarch64) via a cross-compiled native module. ([#15](https://github.com/dfa1/rocksdbffm/issues/15), [#38](https://github.com/dfa1/rocksdbffm/pull/38))
- `RocksIterator#error()` method. ([c14bc5d](https://github.com/dfa1/rocksdbffm/commit/c14bc5d))

### Changed

- American English spelling adopted throughout (javadoc, comments, identifiers), matching the JDK's own convention. ([#32](https://github.com/dfa1/rocksdbffm/pull/32))

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

[Unreleased]: https://github.com/dfa1/rocksdbffm/compare/v0.10...HEAD
[0.10]: https://github.com/dfa1/rocksdbffm/compare/v0.9...v0.10
[0.9]: https://github.com/dfa1/rocksdbffm/compare/v0.8...v0.9
[0.8]: https://github.com/dfa1/rocksdbffm/compare/v0.7...v0.8
[0.7]: https://github.com/dfa1/rocksdbffm/compare/v0.6...v0.7
[0.6]: https://github.com/dfa1/rocksdbffm/compare/v0.5...v0.6
[0.5]: https://github.com/dfa1/rocksdbffm/compare/v0.4...v0.5
[0.4]: https://github.com/dfa1/rocksdbffm/compare/v0.3...v0.4
[0.3]: https://github.com/dfa1/rocksdbffm/compare/v0.2...v0.3
[0.2]: https://github.com/dfa1/rocksdbffm/compare/v0.1...v0.2
