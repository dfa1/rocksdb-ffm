# Benchmarks

JMH results for `rocksdbffm` (FFM) against `rocksdbjni` (JNI), plus the methodology needed to read
them honestly. This page is a supplement to [explanation.md](explanation.md), which covers *why* the
FFM path is faster; see [how-to.md#run-the-benchmarks](how-to.md#run-the-benchmarks) to reproduce
them.

## Methodology

Measured 2026-10-31 on a Mac Studio (Apple M5 Max, 128 GB), JDK 25.0.5 (Homebrew OpenJDK), JMH 1.37,
with the native library built locally by zig 0.17.0 (the version CI pins). Every table below is
**3 forks** × 3×1 s warmup × 5×1 s measurement, and every cell carries JMH's 99.9% error — treat a
difference smaller than the two error bars combined as noise. A llamafile LLM server was loaded on
the machine during the run but idle (no client connections, under 1 s of CPU in total).

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
> the bundled native library built from the `rocksdb/` submodule (**v11.8.1**). The JNI column uses
> `org.rocksdb:rocksdbjni` **11.1.1** — the newest release that exists, since `rocksdbjni` lags
> behind the core. Treat the deltas as indicative of *binding* overhead rather than a
> controlled like-for-like comparison: part of any difference may come from the engine rather than
> the binding — most of all for writes, where background flush work is engine-side.

## Results

| Operation              | API tier           |      FFM (ops/s)       |      JNI (ops/s)       |   Gain    |
|:-----------------------|:-------------------|:----------------------:|:----------------------:|:---------:|
| Reads                  | `byte[]`           |  7,709,054 ± 115,921   |  3,364,538 ± 56,100    | **+129%** |
| Reads                  | `DirectByteBuffer` |  7,449,154 ± 91,869    |  3,483,877 ± 31,492    | **+114%** |
| Reads                  | `MemorySegment`    |  7,325,454 ± 683,745   |           —            |     —     |
| Writes                 | `byte[]`           |    543,543 ± 18,690    |    511,196 ± 4,515     |  **+6%**  |
| Writes, WAL disabled   | `byte[]`           |  4,107,171 ± 98,609    |  2,571,355 ± 74,324    | **+60%**  |
| Writes                 | `DirectByteBuffer` |    571,065 ± 13,061    |    501,236 ± 6,833     | **+14%**  |
| Writes                 | `MemorySegment`    |    570,536 ± 4,683     |           —            |     —     |
| Batch writes (100 ops) | `byte[]`           |     13,383 ± 154       |     10,769 ± 833       | **+24%**  |

`rocksdbjni` has no `MemorySegment` tier, hence the missing cells. `./scripts/benchmark.sh` prints
further FFM-only rows (arena reuse, `Instant` deserialization, zero-copy batches); the arena-reuse
rows land within error of their plain counterparts, since the WAL dominates a single put.

## Reading the numbers

**Reads gain ~2×** because FFM downcall stubs are JIT-compiled directly into the caller: there is no
JNI frame setup and no thread-state transition per call. At ~7–8 M ops/s the per-call overhead *is*
the benchmark.

**The three FFM read tiers are indistinguishable at this size.** `byte[]` and `DirectByteBuffer`
are within ~4% of each other, and `MemorySegment`'s ±9% error spans both. On tiny memtable-hit
values neither the `byte[]` allocation nor the copy is big enough to show; see the value-size sweep
below for where it starts to.

**The WAL is most of a write.** Turning it off makes a put ~7.5× faster on FFM (543 k → 4.1 M
ops/s) — the WAL append is ~87% of the per-put time. With it on, the binding is a small slice of the
total, so FFM's gain is small (+6–14%). With it off, what is left is mostly the memtable insert plus
the binding, and FFM's lead grows to +60% — the same call-overhead story as reads.

**Batch writes gain more than single writes** (+24%) because the per-call overhead is paid 100× per
iteration while the WAL cost is amortized across the batch.

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
|        8 B |      390 ± 8   |      423 ± 15  |       387 ± 4   |     385 ± 19  |      424 ± 84  |          0.99 |
|       64 B |      449 ± 9   |      473 ± 12  |       436 ± 8   |     418 ± 5   |      416 ± 4   |          0.97 |
|      256 B |      368 ± 10  |      382 ± 4   |       359 ± 9   |     344 ± 21  |      342 ± 7   |          0.97 |
|       1 KB |      362 ± 8   |      366 ± 5   |       339 ± 3   |     320 ± 14  |      325 ± 2   |          0.94 |
|       2 KB |      399 ± 8   |      373 ± 4   |       341 ± 2   |     312 ± 3   |      335 ± 4   |          0.85 |
|       4 KB |      450 ± 11  |      418 ± 14  |       379 ± 8   |     311 ± 3   |      377 ± 16  |          0.84 |
|       8 KB |      599 ± 67  |      470 ± 7   |       463 ± 18  |     308 ± 1   |      453 ± 18  |          0.77 |
|      16 KB |    1,079 ± 128 |      568 ± 19  |       599 ± 77  |     300 ± 4   |      546 ± 34  |          0.55 |
|      32 KB |    1,457 ± 118 |      737 ± 14  |       744 ± 50  |     298 ± 4   |      721 ± 9   |          0.51 |
|      64 KB |    1,894 ± 149 |    1,003 ± 22  |       947 ± 5   |     287 ± 4   |    1,075 ± 7   |          0.50 |
|     256 KB |    5,976 ± 52  |    3,341 ± 166 |     3,197 ± 15  |     280 ± 3   |    4,133 ± 234 |          0.53 |
|       1 MB |   21,870 ± 62  |   12,439 ± 552 |    11,517 ± 60  |     280 ± 5   |   15,713 ± 381 |          0.53 |

**Uniform keys** (ns/op):

| Value size | `byte[]`       | `ByteBuffer`   | `MemorySegment` | Mapper (size) | Mapper (copy)  | MS / `byte[]` |
|-----------:|---------------:|---------------:|----------------:|--------------:|---------------:|--------------:|
|        8 B |      497 ± 8   |      541 ± 30  |       489 ± 6   |     470 ± 3   |      472 ± 4   |          0.99 |
|       64 B |      504 ± 8   |      594 ± 22  |       503 ± 16  |     478 ± 3   |      482 ± 4   |          1.00 |
|      256 B |      553 ± 22  |      667 ± 56  |       522 ± 34  |     489 ± 3   |      510 ± 7   |          0.94 |
|       1 KB |      661 ± 193 |      663 ± 75  |       569 ± 54  |     492 ± 4   |      551 ± 9   |          0.86 |
|       2 KB |      898 ± 6   |      931 ± 7   |       878 ± 33  |     744 ± 3   |      995 ± 123 |          0.98 |
|       4 KB |    1,089 ± 49  |    1,038 ± 29  |       986 ± 38  |     824 ± 9   |    1,017 ± 13  |          0.91 |
|       8 KB |    1,197 ± 82  |      964 ± 25  |       951 ± 26  |     694 ± 36  |    1,050 ± 111 |          0.79 |
|      16 KB |    1,469 ± 74  |    1,020 ± 43  |     1,163 ± 163 |     691 ± 50  |    1,098 ± 16  |          0.79 |
|      32 KB |    1,925 ± 86  |    1,327 ± 94  |     1,403 ± 118 |     708 ± 26  |    1,308 ± 51  |          0.73 |
|      64 KB |    3,627 ± 941 |    1,643 ± 27  |     1,761 ± 80  |     747 ± 115 |    1,823 ± 69  |          0.49 |
|     256 KB |   13,500 ± 376 |    5,483 ± 88  |     5,255 ± 269 |   2,055 ± 142 |    6,421 ± 309 |          0.39 |
|       1 MB | 127,914 ± 584 | 69,284 ± 1,265 | 71,478 ± 2,926 | 51,055 ± 276 | 73,984 ± 4,488 |          0.56 |

**`byte[]` is never faster, but it only falls clearly behind after a few KB.** Up to ~1 KB every tier
is within a few percent of the others: a read costs a fixed ~340–500 ns of lookup and binding, and
the value's bytes are noise next to that. With a hot key, `MemorySegment` pulls clearly ahead from
**~2 KB** (−15%) and reaches **~2× from 16 KB** onward. With uniform keys the fixed lookup cost is
larger, which pushes the crossover out to **~8 KB** (−21%) and 2× at 64 KB.

**Why 2×:** past ~16 KB both tiers are paying per byte, and `byte[]` pays twice — the JVM zeroes
the new array, then the value is copied into it — while the buffer tiers only copy. A line fit to
the hot-key rows gives ~21 ns/KB for `byte[]` against ~11 ns/KB for `MemorySegment`.

**`ByteBuffer` vs `MemorySegment`:** same native call, same result from ~4 KB up. At small sizes
`ByteBuffer` is slower — +6–9% on a hot key, +10–30% with uniform keys — from wrapping the buffer
as a segment and updating its position on every call.

**Mapper (size)** is flat at ~280–310 ns on a hot key regardless of size — the true zero-copy floor,
and the reason to use `get(key, Mapper)` when you only need to inspect or parse part of a value.
Copying out of the Mapper (`MemorySegment.copy`) is no cheaper than letting
`rocksdb_get_into_buffer` copy, and 13–36% slower than it from 64 KB up.

**At 1 MB with uniform keys, everything is slow** (50–130 µs): the 50 MB database no longer fits
the 32 MB default block cache, so reads go to the OS page cache. That row measures the storage
path more than the binding.

## MultiGet: individual gets vs ReadBatch

`MultiGetScaleBenchmark` compares repeated single-key `get()` calls against [ReadBatch](reference.md)
for the same key count, swept from 1 to 128 keys — FFM only, no JNI side (this isolates ReadBatch's
own batching win, not a binding comparison). One fork; `byte[]` and zero-copy (`MemorySegment`) tiers.

| Batch size | Individual `byte[]` | ReadBatch `byte[]` |   Δ   | Individual 0-copy | ReadBatch 0-copy |   Δ   |
|-----------:|---------------------:|--------------------:|:-----:|--------------------:|-------------------:|:-----:|
|          1 |             2,748,186 |            1,525,635 | −44%  |            3,017,507 |           1,976,829 | −34%  |
|          2 |             1,326,508 |            1,192,747 | −10%  |            1,465,346 |           1,344,783 |  −8%  |
|          4 |               658,672 |              618,590 |  −6%  |              720,921 |             771,597 |  +7%  |
|          8 |               329,115 |              336,607 |  +2%  |              361,111 |             429,388 | +19%  |
|         16 |               160,265 |              160,705 |  +0%  |              177,530 |             212,961 | +20%  |
|         32 |                77,410 |               79,109 |  +2%  |               85,808 |              98,454 | +15%  |
|         64 |                37,435 |               40,411 |  +8%  |               41,339 |              46,852 | +13%  |
|        128 |                18,327 |               19,486 |  +6%  |               20,464 |              22,974 | +12%  |

**The crossover depends on the tier.** Zero-copy crosses between batch sizes 2 and 4 and then
holds a +12–20% lead. `byte[]` crosses later, between 4 and 8, and only edges ahead (+0–8%): its
per-call key copy into a fresh arena and per-value `byte[]` allocation are paid the same way on
both sides, diluting what `ReadBatch` saves. Below the crossover, the fixed per-call overhead of a
batched multi-get isn't amortized and individual `get()` wins — by 34–44% at a single key.

A single-fork run of the same sweep on a Linux x86_64 desktop showed the same crossover point but a
much larger and still-growing advantage past it — individual gets fell behind by up to 2× at batch
sizes ≥16, rather than plateauing at ~1.1–1.2×. The likely explanation is that this desktop's baseline
per-call `get()` overhead is proportionally larger relative to its own raw throughput than the M5 Max's,
so there is more fixed cost left for `ReadBatch` to amortize away — but that's a hypothesis, not
something confirmed with a profiler. Absolute throughput and the exact plateau are hardware-specific;
the crossover shape (small batches favor individual gets, larger batches favor `ReadBatch`) is the
part worth generalizing.

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
