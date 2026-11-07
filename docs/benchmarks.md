# Benchmarks

JMH results for `rocksdbffm` (FFM) against `rocksdbjni` (JNI), plus the methodology needed to read
them honestly. This page is a supplement to [explanation.md](explanation.md), which covers *why* the
FFM path is faster; see [how-to.md#run-the-benchmarks](how-to.md#run-the-benchmarks) to reproduce
them.

## Methodology

Measured 2026-11-07 on a Mac Studio (Apple M5 Max, 128 GB), JDK 25.0.5 (Homebrew OpenJDK), JMH 1.37,
with the native library built locally by zig 0.17.0 (the version CI pins). Every table below is
**3 forks** × 3×1 s warmup × 5×1 s measurement (the blog-scenario tables use 3×3 s and 5×3 s), and every
cell carries JMH's 99.9% error — treat a difference smaller than the two error bars combined as noise.
The suites ran back to back, unattended, with no other work started on the machine.

What the numbers do and don't cover:

- **FFM vs JNI reads are memtable hits** on a 1–2 key database that is never flushed: one hot key,
  no SST, no block cache. That isolates per-call binding overhead, which is what the table is for —
  it is not read throughput.
- **Writes use default `WriteOptions`**: WAL on, `sync=false`, so a put is a `write(2)` into the page
  cache, not an fsync. Every put overwrites the same key, so the memtable keeps filling and
  background flushes run during measurement. The "WAL disabled" row removes the WAL to show its
  share.
- **FFM runs entirely before JNI** in one session, so drift over the session (thermal, background
  load) falls between the two columns; three forks narrow that, they don't remove it.

> **The two sides do not run the same RocksDB build, and cannot be made to.** The FFM column uses
> the bundled native library built from the `rocksdb/` submodule (**v11.13.2**). The JNI column uses
> `org.rocksdb:rocksdbjni` **11.8.1** — the newest release that exists, since `rocksdbjni` lags
> behind the core. Treat the deltas as indicative of *binding* overhead rather than a
> controlled like-for-like comparison: part of any difference may come from the engine rather than
> the binding — most of all for writes, where background flush work is engine-side.

## Results

| Operation              | API tier           |      FFM (ops/s)       |      JNI (ops/s)       |   Gain    |
|:-----------------------|:-------------------|:----------------------:|:----------------------:|:---------:|
| Reads                  | `byte[]`           |  8,246,436 ± 60,550    |  3,568,546 ± 23,746    | **+131%** |
| Reads                  | `DirectByteBuffer` |  7,490,131 ± 32,725    |  3,623,342 ± 25,360    | **+107%** |
| Reads                  | `MemorySegment`    |  8,084,807 ± 258,762   |           —            |     —     |
| Writes                 | `byte[]`           |    551,728 ± 9,622     |    468,222 ± 18,628    | **+18%**  |
| Writes, WAL disabled   | `byte[]`           |  4,432,516 ± 137,303   |  2,700,285 ± 35,748    | **+64%**  |
| Writes                 | `DirectByteBuffer` |    547,359 ± 22,306    |    473,510 ± 8,178     | **+16%**  |
| Writes                 | `MemorySegment`    |    527,399 ± 11,716    |           —            |     —     |
| Batch writes (100 ops) | `byte[]`           |     13,542 ± 271       |     11,243 ± 190       | **+20%**  |

`rocksdbjni` has no `MemorySegment` tier, hence the missing cells. `./scripts/benchmark.sh` prints
further FFM-only rows (arena reuse, `Instant` deserialization, zero-copy batches); the arena-reuse
rows land within error of their plain counterparts, since the WAL dominates a single put.

## Reading the numbers

**Reads gain ~2.1–2.3×** because FFM downcall stubs are JIT-compiled directly into the caller: there is no
JNI frame setup and no thread-state transition per call. At ~8 M ops/s the per-call overhead *is*
the benchmark.

**The three FFM read tiers are close at this size.** `byte[]` (8.25 M) and `MemorySegment` (8.08 M)
are within error of each other; `DirectByteBuffer` is ~9% behind (7.49 M), from wrapping the buffer as
a segment on every call. On tiny memtable-hit values neither the `byte[]` allocation nor the copy is big
enough to show; see the value-size sweep below for where it starts to.

**The WAL is most of a write.** Turning it off makes a put ~8× faster on FFM (552 k → 4.4 M
ops/s) — the WAL append is ~88% of the per-put time. With it on, the binding is a small slice of the
total, so FFM's gain is small (+16–18%). With it off, what is left is mostly the memtable insert plus
the binding, and FFM's lead grows to +64% — the same call-overhead story as reads.

**Batch writes gain more than single writes** (+20%) because the per-call overhead is paid 100× per
iteration while the WAL cost is amortized across the batch. Zero-copy batches (`DirectByteBuffer`,
`MemorySegment`, ~14.7 k ops/s) add another ~8% on top.

**FFM reads are faster than in the previous run (2026-11-02), and not for a reason we know.** That run
measured `byte[]` at 6.70 M on v11.12 (and 7.71 M on v11.8.1 before it); this one, on v11.13.2, measures
8.25 M, +23% over the v11.12 run. JNI, still on 11.8.1, moved only +5% (3.39 M → 3.57 M) between the
same two runs, so run-to-run drift on this machine is a few percent. That leaves most of the FFM gain
unexplained: it may be the engine upgrade, but it has not been confirmed by benchmarking the same binding
against both engine versions.

**Absolute write numbers are not comparable across machines.** An earlier run on an M5 laptop
measured both FFM *and* JNI writes 15–45% higher than this Mac Studio, while the FFM-over-JNI ratio
stayed in the same range — a filesystem/page-cache difference, not a binding one.

## Value size: when do `ByteBuffer`/`MemorySegment` beat `byte[]`?

`FfmValueSizeBenchmark` reads one value per call from a flushed and compacted database, sweeping
the value size from 8 B to 1 MB. It reports **ns/op (lower is better)** across five tiers:

- `byte[]` — `get(byte[])`: three downcalls (`rocksdb_get_pinned_v2`, fetch, release), then a fresh
  array and one copy into it.
- `ByteBuffer` / `MemorySegment` — `get(key, buffer)`: one `rocksdb_get_into_buffer` downcall that
  copies straight into a buffer the caller allocated once. No Java allocation, still one copy.
- Mapper (size) — `get(key, Mapper)` reading only the value's size: zero copy, and the bytes are
  never touched. A floor, not a realistic consumer.
- Mapper (copy) — the same, but copying the pinned bytes into the preallocated segment, so it is
  like-for-like with the buffer tiers.

Two access patterns: **hot** reads one key every call (block cache, CPU caches and branch
predictors all warm); **uniform** walks a shuffled order over every key in the database (20,000
keys up to ~1.6 KB values, falling to 50 at 1 MB, with the database held at ~32 MB). The heap is
pinned at 4 GB so the `byte[]` tier's allocation rate doesn't turn the result into a GC-sizing test.

**Hot key** (ns/op):

| Value size | `byte[]`       | `ByteBuffer`   | `MemorySegment` | Mapper (size) | Mapper (copy)  | MS / `byte[]` |
|-----------:|---------------:|---------------:|----------------:|--------------:|---------------:|--------------:|
|        8 B | 368 ± 3 | 380 ± 4 | 367 ± 3 | 348 ± 2 | 366 ± 3 | 1.00 |
|       64 B | 420 ± 3 | 428 ± 6 | 415 ± 1 | 401 ± 3 | 419 ± 3 | 0.99 |
|      256 B | 351 ± 11 | 347 ± 5 | 343 ± 8 | 326 ± 9 | 337 ± 11 | 0.98 |
|       1 KB | 346 ± 3 | 340 ± 3 | 327 ± 2 | 300 ± 2 | 313 ± 3 | 0.95 |
|       2 KB | 380 ± 3 | 345 ± 4 | 332 ± 2 | 303 ± 3 | 324 ± 2 | 0.87 |
|       4 KB | 442 ± 2 | 382 ± 9 | 375 ± 9 | 302 ± 3 | 356 ± 3 | 0.85 |
|       8 KB | 552 ± 3 | 489 ± 27 | 482 ± 25 | 297 ± 2 | 445 ± 21 | 0.87 |
|      16 KB | 890 ± 3 | 647 ± 63 | 648 ± 59 | 291 ± 3 | 520 ± 26 | 0.73 |
|      32 KB | 1,259 ± 7 | 837 ± 44 | 841 ± 32 | 289 ± 3 | 726 ± 32 | 0.67 |
|      64 KB | 1,782 ± 5 | 1,097 ± 36 | 1,074 ± 12 | 286 ± 4 | 1,099 ± 24 | 0.60 |
|     256 KB | 5,953 ± 41 | 3,303 ± 20 | 3,242 ± 47 | 274 ± 6 | 4,167 ± 122 | 0.54 |
|       1 MB | 21,539 ± 508 | 11,393 ± 45 | 11,358 ± 63 | 270 ± 2 | 15,796 ± 38 | 0.53 |

**Uniform keys** (ns/op):

| Value size | `byte[]`       | `ByteBuffer`   | `MemorySegment` | Mapper (size) | Mapper (copy)  | MS / `byte[]` |
|-----------:|---------------:|---------------:|----------------:|--------------:|---------------:|--------------:|
|        8 B | 478 ± 3 | 492 ± 2 | 475 ± 3 | 457 ± 1 | 458 ± 5 | 0.99 |
|       64 B | 492 ± 4 | 502 ± 3 | 483 ± 2 | 465 ± 3 | 474 ± 3 | 0.98 |
|      256 B | 530 ± 2 | 526 ± 2 | 510 ± 2 | 495 ± 2 | 508 ± 3 | 0.96 |
|       1 KB | 551 ± 5 | 553 ± 4 | 534 ± 3 | 512 ± 31 | 522 ± 4 | 0.97 |
|       2 KB | 877 ± 5 | 846 ± 9 | 826 ± 9 | 735 ± 6 | 823 ± 6 | 0.94 |
|       4 KB | 1,021 ± 4 | 935 ± 13 | 945 ± 31 | 805 ± 8 | 912 ± 4 | 0.93 |
|       8 KB | 1,162 ± 9 | 956 ± 7 | 935 ± 27 | 717 ± 28 | 944 ± 25 | 0.80 |
|      16 KB | 1,421 ± 20 | 1,030 ± 36 | 1,009 ± 35 | 686 ± 41 | 1,008 ± 30 | 0.71 |
|      32 KB | 1,858 ± 39 | 1,272 ± 49 | 1,252 ± 41 | 745 ± 6 | 1,266 ± 47 | 0.67 |
|      64 KB | 2,547 ± 66 | 1,703 ± 57 | 1,682 ± 65 | 707 ± 78 | 1,787 ± 44 | 0.66 |
|     256 KB | 7,443 ± 267 | 5,407 ± 71 | 5,290 ± 183 | 2,063 ± 71 | 5,783 ± 270 | 0.71 |
|       1 MB | 77,885 ± 208 | 63,853 ± 328 | 64,514 ± 620 | 50,598 ± 535 | 68,134 ± 1,590 | 0.83 |

**`byte[]` only falls clearly behind after a few KB.** Up to ~1 KB every tier is within a few
percent of the others: a read costs a fixed ~300–550 ns of lookup and binding, and the value's bytes
are noise next to that (`MemorySegment` is 1–5% ahead of `byte[]`, `ByteBuffer` level with it). From
**~2 KB** `MemorySegment` pulls clearly ahead on both access patterns (−13% hot, −6% uniform). With a
hot key it reaches ~1.4× at 16 KB and **~1.9× from 256 KB** onward; with uniform keys the larger fixed
lookup cost caps it at ~1.4–1.5× from 16 KB to 256 KB.

**Why ~2×:** past ~16 KB both tiers are paying per byte, and `byte[]` pays twice — the JVM zeroes
the new array, then the value is copied into it — while the buffer tiers only copy. A line fit to
the hot-key rows from 64 KB up gives ~21 ns/KB for `byte[]` against ~11 ns/KB for `MemorySegment`.

**`ByteBuffer` vs `MemorySegment`:** same native call, and level with each other from 8 KB up. With
a hot key `ByteBuffer` is 1–4% slower below that, from wrapping the buffer as a segment and updating
its position on every call; with uniform keys the gap is 1–3%.

**Mapper (size)** is flat at ~270–330 ns on a hot key from 256 B up, regardless of size — the true
zero-copy floor, and the reason to use `get(key, Mapper)` when you only need to inspect or parse
part of a value. Copying out of the Mapper (`MemorySegment.copy`) is no cheaper than letting
`rocksdb_get_into_buffer` copy, and 2–39% slower than it from 64 KB up (the gap is widest on a hot
key, at 256 KB and 1 MB).

**At 1 MB with uniform keys, everything is slow** (51–78 µs): the 50 MB database no longer fits
the 32 MB default block cache, so reads go to the OS page cache. That row measures the storage
path more than the binding.

## MultiGet: individual gets vs ReadBatch

`MultiGetScaleBenchmark` compares repeated single-key `get()` calls against [ReadBatch](reference.md)
for the same key count, swept from 1 to 128 keys — FFM only, no JNI side (this isolates ReadBatch's
own batching win, not a binding comparison). 3 forks; `byte[]` and zero-copy (`MemorySegment`) tiers.

| Batch size | Individual `byte[]` | ReadBatch `byte[]` |   Δ   | Individual 0-copy | ReadBatch 0-copy |   Δ   |
|-----------:|--------------------:|-------------------:|:-----:|------------------:|-----------------:|:-----:|
|          1 |           2,767,110 |          1,854,544 | −33%  |         3,126,953 |        2,018,446 | −35%  |
|          2 |           1,355,441 |          1,144,471 | −16%  |         1,519,884 |        1,385,418 |  −9%  |
|          4 |             623,783 |            642,139 |  +3%  |           745,610 |          803,142 |  +8%  |
|          8 |             333,706 |            325,194 |  −3%  |           370,069 |          444,381 | +20%  |
|         16 |             163,452 |            159,083 |  −3%  |           183,959 |          217,571 | +18%  |
|         32 |              77,545 |             79,917 |  +3%  |            86,935 |          100,585 | +16%  |
|         64 |              37,830 |             40,731 |  +8%  |            42,472 |           48,412 | +14%  |
|        128 |              18,372 |             20,129 | +10%  |            20,824 |           23,243 | +12%  |

**The crossover depends on the tier.** Zero-copy crosses between batch sizes 2 and 4 and then
leads by +8–20% (peaking at 8 keys). `byte[]` barely crosses at all: it trails up to 2 keys, is
within a few percent of individual gets from 4 to 32, and only reaches +8–10% at 64–128: its
per-call key copy into a fresh arena and per-value `byte[]` allocation are paid the same way on both
sides, diluting what `ReadBatch` saves. `ByteBuffer` (not shown) sits with zero-copy: it crosses
between 2 and 4 keys and leads by +12–26% from 4 keys up. Below the crossover, the fixed per-call
overhead of a batched multi-get isn't amortized and individual `get()` wins — by 28–35% at a single
key.

A single-fork run of the same sweep on a Linux x86_64 desktop showed the same crossover point but a
much larger and still-growing advantage past it — individual gets fell behind by up to 2× at batch
sizes ≥16, rather than plateauing at ~1.0–1.3×. The likely explanation is that this desktop's baseline
per-call `get()` overhead is proportionally larger relative to its own raw throughput than the M5 Max's,
so there is more fixed cost left for `ReadBatch` to amortize away — but that's a hypothesis, not
something confirmed with a profiler. Absolute throughput and the exact plateau are hardware-specific;
the crossover shape (small batches favor individual gets, larger batches favor `ReadBatch`) is the
part worth generalizing.

## The blog's `get` scenario: FFM vs JNI with 128-byte keys

RocksDB's own Java-performance post
([Java API Performance Improvements](https://rocksdb.org/blog/2023/11/06/java-jni-benchmarks.html),
2023-11-06) benchmarks `get` against a real database with `keySize=128`, `valueSize=1024,16384` and
`keyCount=1000,50000`. `BlogGetBenchmark` (FFM) and `JniBlogGetBenchmark` (JNI) reproduce that scenario with
identical state, so the two columns differ only in the API under test. Both follow upstream's
`GetBenchmarks` where it matters: default options, data in both `default` and `cf1` with reads going
through an explicit `cf1` handle, zero-padded `"key"+j` / `"value"+j` content (highly compressible),
sequential key order, no manual compaction, and a wait for background work after loading.

Deliberate differences, identical on both sides: keys are built once in setup rather than per call (so
the absolute numbers are **not** comparable to the blog's chart — only the FFM/JNI ratio is meaningful),
the JMH runs are shorter than upstream's (3 forks, 3×3 s + 5×3 s), and the heap is pinned to 4 GB. `core` has
no `get(byte[], byte[])`, so the blog's `preallocatedGet` (heap `byte[]` destination) exists only on the JNI
side, and the FFM `MemorySegment` tier is ours, not the blog's. `SSTs` means `cf1` was flushed after loading
(no compaction); `memtable` is the blog's state, except that the 50,000 × 16 KB set (~825 MB) overflows the
64 MB write buffer and is mostly in SSTs either way.

Throughput in thousands of ops/s (higher is better); every cell's 99.9% error is under 10%:

| `cf1` in | Keys   | Value | FFM `get` | JNI `get` |  Δ   | FFM `ByteBuffer` | JNI `ByteBuffer` |  Δ   | FFM `MemorySegment` | JNI `byte[]` prealloc |
|:---------|-------:|------:|----------:|----------:|:----:|-----------------:|-----------------:|:----:|--------------------:|----------------------:|
| memtable | 1,000 | 1 KB | 2,594 | 1,935 | +34% | 3,002 | 1,925 | +56% | 3,007 | 2,108 |
| memtable | 1,000 | 16 KB | 917 | 717 | +28% | 1,383 | 1,097 | +26% | 1,370 | 1,043 |
| memtable | 50,000 | 1 KB | 2,098 | 1,545 | +36% | 2,111 | 1,517 | +39% | 2,217 | 1,688 |
| memtable | 50,000 | 16 KB | 306 | 278 | +10% | 358 | 331 | +8% | 353 | 322 |
| SSTs | 1,000 | 1 KB | 2,649 | 1,809 | +46% | 2,812 | 1,859 | +51% | 2,796 | 1,971 |
| SSTs | 1,000 | 16 KB | 1,015 | 772 | +32% | 1,557 | 1,234 | +26% | 1,542 | 1,232 |
| SSTs | 50,000 | 1 KB | 1,518 | 1,178 | +29% | 1,674 | 1,182 | +42% | 1,518 | 1,259 |
| SSTs | 50,000 | 16 KB | 305 | 276 | +11% | 356 | 321 | +11% | 355 | 321 |

**FFM is ahead of JNI in every cell, by +8% to +56%.** The gap is widest where the binding is a large share
of the cost — 1 KB values over 1,000 keys, where `get` is +34–46% and the preallocated `ByteBuffer` path
+51–56% — and narrows to +8–11% on the 50,000 × 16 KB set, where each call moves 16 KB and the engine's
read and copy work dominates. This matches the blog's own conclusion that the transfer mechanism matters
less than not allocating or copying more than necessary.

**Writing into a caller-supplied buffer beats allocating the result**, as the blog recommends, and by how
much depends on the value size: on FFM the preallocated `ByteBuffer` and `MemorySegment` tiers lead
`get(byte[])` by +6–16% at 1 KB over 1,000 keys, ~1.5× at 16 KB over 1,000 keys, +17% at 16 KB over 50,000
keys, and are level with it only at 50,000 × 1 KB in the memtable (+1%; +10% from SSTs). The two buffer
tiers are within error of each other, as in the value-size sweep.

**Memtable vs SSTs matters mainly for the large 1 KB set**: with 50,000 × 1 KB the FFM `get` drops from 2.10 M
to 1.52 M ops/s (−28%) once `cf1` is flushed. Every other cell moves by 11% or less, and the 16 KB cells are
slightly *faster* from SSTs.

## Reproducing

```bash
./mvnw test-compile -q
./scripts/benchmark.sh
```

The script builds everything, runs both the FFM and JNI suites, and prints a side-by-side table.
Expect absolute numbers to differ on other hardware — a thermally throttled laptop depresses every
absolute figure while leaving the ratios roughly intact. Individual classes:

```bash
./scripts/benchmark.sh MultiGetScaleBenchmark        # 3 forks by default
./scripts/benchmark.sh FfmValueSizeBenchmark         # ~50 min; -Djmh.access=hot halves it
./scripts/benchmark.sh BenchmarkRunner -Djmh.forks=3 # the setting the tables above use
./scripts/benchmark.sh BlogGetBenchmark              # FFM side of the blog scenario
./scripts/benchmark.sh JniBlogGetBenchmark           # JNI side; run both on an idle machine
```

## Profiling with `perf`

Linux `perf` is available and usable without root on the project's dev container: `perf stat`
(hardware counters) and `perf record` (sampled call-graph profiling, including attaching to an
already-running PID with `-p`) both work as the current user — `kernel.perf_event_paranoid` is set
low enough for both. This resolves the open question in
[ADR 0007](adr/0007-critical-linker-option-study.md), which flagged native-side profiling
(`perf`/Instruments/async-profiler attached to `librocksdb.dylib`) as a complementary technique to
the JMH-based measurements but hadn't confirmed `perf` itself was usable there.

JMH 1.37 (the version this project pins) ships profilers that wrap `perf` directly —
`LinuxPerfProfiler`, `LinuxPerfNormProfiler`, `LinuxPerfAsmProfiler`, `LinuxPerfC2CProfiler` — normally
selected with `-prof perf`/`-prof perfnorm`/`-prof perfasm`/`-prof perfc2c` on JMH's own command-line
runner. This repo's benchmark classes don't expose that flag (each `main()` builds its
`OptionsBuilder` programmatically, e.g. `MultiGetScaleBenchmark`'s use of `GCProfiler` — see
`benchmarks/src/test/java/io/github/dfa1/rocksdbffm/benchmark/MultiGetScaleBenchmark.java`); add one
the same way to use it:

```java
builder.addProfiler(org.openjdk.jmh.profile.LinuxPerfNormProfiler.class);
```

To profile ad hoc without touching a benchmark's `main()`, attach directly to a running `@Fork`'s
PID (each fork is its own JVM process — find the PID with `jps` while it's warming up):

```bash
perf stat -p <pid>          # aggregate hardware counters until Ctrl-C
perf record -p <pid> -g     # sampled call-graph -> perf.data
```

For call-graph frames to resolve through JIT-compiled Java code, add `-XX:+PreserveFramePointer` to
the fork's `jvmArgsPrepend`; even then, plain `perf record` won't symbolicate JIT frames the way
`async-profiler`'s `-agentpath` integration does, so prefer async-profiler when Java-level (not just
native) stacks matter.
