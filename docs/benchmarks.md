# Benchmarks

JMH results for `rocksdbffm` (FFM) against `rocksdbjni` (JNI), plus the methodology needed to read
them honestly. This page is a supplement to [explanation.md](explanation.md), which covers *why* the
FFM path is faster; see [how-to.md#run-the-benchmarks](how-to.md#run-the-benchmarks) to reproduce
them.

## Methodology

Measured 2026-11-02 on a Mac Studio (Apple M5 Max, 128 GB), JDK 25.0.5 (Homebrew OpenJDK), JMH 1.37,
with the native library built locally by zig 0.17.0 (the version CI pins). Every table below is
**3 forks** × 3×1 s warmup × 5×1 s measurement, and every cell carries JMH's 99.9% error — treat a
difference smaller than the two error bars combined as noise. The machine was otherwise idle, apart
from two short Maven builds of another project: one during the value-size sweep, one early in the
MultiGet sweep (the few wider error bars there, on the `ByteBuffer` rows at 1–4 keys, may be it).

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
> the bundled native library built from the `rocksdb/` submodule (**v11.12**). The JNI column uses
> `org.rocksdb:rocksdbjni` **11.8.1** — the newest release that exists, since `rocksdbjni` lags
> behind the core. Treat the deltas as indicative of *binding* overhead rather than a
> controlled like-for-like comparison: part of any difference may come from the engine rather than
> the binding — most of all for writes, where background flush work is engine-side.

## Results

| Operation              | API tier           |      FFM (ops/s)       |      JNI (ops/s)       |   Gain    |
|:-----------------------|:-------------------|:----------------------:|:----------------------:|:---------:|
| Reads                  | `byte[]`           |  6,701,912 ± 657,415   |  3,391,860 ± 55,672    | **+98%**  |
| Reads                  | `DirectByteBuffer` |  6,993,286 ± 151,586   |  3,506,591 ± 13,513    | **+99%**  |
| Reads                  | `MemorySegment`    |  7,221,090 ± 55,375    |           —            |     —     |
| Writes                 | `byte[]`           |    565,643 ± 8,393     |    501,065 ± 4,243     | **+13%**  |
| Writes, WAL disabled   | `byte[]`           |  3,924,879 ± 75,964    |  2,590,036 ± 33,799    | **+52%**  |
| Writes                 | `DirectByteBuffer` |    576,395 ± 5,493     |    499,151 ± 7,468     | **+15%**  |
| Writes                 | `MemorySegment`    |    571,519 ± 4,687     |           —            |     —     |
| Batch writes (100 ops) | `byte[]`           |     13,427 ± 131       |     11,084 ± 100       | **+21%**  |

`rocksdbjni` has no `MemorySegment` tier, hence the missing cells. `./scripts/benchmark.sh` prints
further FFM-only rows (arena reuse, `Instant` deserialization, zero-copy batches); the arena-reuse
rows land within error of their plain counterparts, since the WAL dominates a single put.

## Reading the numbers

**Reads gain ~2×** because FFM downcall stubs are JIT-compiled directly into the caller: there is no
JNI frame setup and no thread-state transition per call. At ~7 M ops/s the per-call overhead *is*
the benchmark.

**The three FFM read tiers are close at this size.** `MemorySegment` leads `DirectByteBuffer` by
~3%, and `byte[]`'s ±10% error spans `DirectByteBuffer`. On tiny memtable-hit
values neither the `byte[]` allocation nor the copy is big enough to show; see the value-size sweep
below for where it starts to.

**The WAL is most of a write.** Turning it off makes a put ~6.9× faster on FFM (566 k → 3.9 M
ops/s) — the WAL append is ~86% of the per-put time. With it on, the binding is a small slice of the
total, so FFM's gain is small (+13–15%). With it off, what is left is mostly the memtable insert plus
the binding, and FFM's lead grows to +52% — the same call-overhead story as reads.

**Batch writes gain more than single writes** (+21%) because the per-call overhead is paid 100× per
iteration while the WAL cost is amortized across the batch.

**FFM reads are slower than on v11.8.1.** The previous run (2026-10-31) had both sides on 11.8.1.
Since the submodule moved to v11.12, FFM memtable reads are 6–13% slower (`byte[]` 7.71 M → 6.70 M,
`DirectByteBuffer` 7.45 M → 6.99 M; `MemorySegment` is within error), and WAL-disabled writes 4%
slower, while JNI, still on 11.8.1, didn't move. An earlier, noisier run on the same build showed the
same drop. That points at the engine upgrade rather than the binding, but it hasn't been confirmed by
benchmarking the same binding against both engine versions.

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
|        8 B |      404 ± 2   |      435 ± 3   |       404 ± 5   |     393 ± 4   |      392 ± 2   |          1.00 |
|       64 B |      465 ± 3   |      487 ± 6   |       466 ± 7   |     452 ± 3   |      456 ± 19  |          1.00 |
|      256 B |      377 ± 1   |      390 ± 5   |       368 ± 3   |     356 ± 5   |      358 ± 3   |          0.98 |
|       1 KB |      375 ± 5   |      374 ± 4   |       366 ± 23  |     351 ± 3   |      345 ± 2   |          0.97 |
|       2 KB |      410 ± 4   |      380 ± 2   |       359 ± 4   |     352 ± 10  |      354 ± 4   |          0.87 |
|       4 KB |      475 ± 4   |      453 ± 2   |       402 ± 10  |     346 ± 10  |      387 ± 8   |          0.85 |
|       8 KB |      581 ± 5   |      500 ± 7   |       469 ± 8   |     326 ± 6   |      481 ± 5   |          0.81 |
|      16 KB |      923 ± 8   |      624 ± 33  |       544 ± 25  |     324 ± 2   |      570 ± 7   |          0.59 |
|      32 KB |    1,314 ± 17  |      838 ± 39  |       699 ± 5   |     318 ± 1   |      762 ± 8   |          0.53 |
|      64 KB |    1,862 ± 16  |    1,062 ± 46  |       944 ± 9   |     316 ± 8   |    1,142 ± 10  |          0.51 |
|     256 KB |    6,153 ± 32  |    3,231 ± 29  |     3,246 ± 18  |     303 ± 3   |    3,498 ± 93  |          0.53 |
|       1 MB |   22,396 ± 922 |   11,851 ± 293 |    11,923 ± 199 |     304 ± 3   |   12,332 ± 126 |          0.53 |

**Uniform keys** (ns/op):

| Value size | `byte[]`       | `ByteBuffer`   | `MemorySegment` | Mapper (size) | Mapper (copy)  | MS / `byte[]` |
|-----------:|---------------:|---------------:|----------------:|--------------:|---------------:|--------------:|
|        8 B |      523 ± 3   |      532 ± 6   |       515 ± 5   |     510 ± 6   |      506 ± 3   |          0.99 |
|       64 B |      531 ± 3   |      544 ± 4   |       544 ± 6   |     514 ± 4   |      521 ± 3   |          1.02 |
|      256 B |      572 ± 5   |      574 ± 6   |       600 ± 6   |     544 ± 2   |      558 ± 3   |          1.05 |
|       1 KB |      597 ± 5   |      614 ± 5   |       671 ± 15  |     545 ± 4   |      593 ± 4   |          1.12 |
|       2 KB |    1,173 ± 36  |      920 ± 8   |     1,008 ± 9   |     836 ± 6   |      964 ± 33  |          0.86 |
|       4 KB |    1,284 ± 6   |    1,020 ± 18  |     1,089 ± 47  |     881 ± 6   |    1,049 ± 36  |          0.85 |
|       8 KB |    1,435 ± 34  |      984 ± 29  |     1,048 ± 33  |     777 ± 39  |    1,061 ± 13  |          0.73 |
|      16 KB |    1,693 ± 74  |    1,062 ± 29  |     1,167 ± 48  |     770 ± 3   |    1,118 ± 22  |          0.69 |
|      32 KB |    2,214 ± 31  |    1,343 ± 48  |     1,405 ± 18  |     754 ± 56  |    1,396 ± 27  |          0.63 |
|      64 KB |    3,052 ± 14  |    1,812 ± 17  |     1,816 ± 19  |     783 ± 4   |    1,908 ± 76  |          0.59 |
|     256 KB |    9,012 ± 51  |    5,489 ± 139 |     5,534 ± 280 |   2,029 ± 144 |    5,905 ± 235 |          0.61 |
|       1 MB |   88,658 ± 630 |   65,264 ± 457 |    67,199 ± 407 |  52,354 ± 449 |   69,980 ± 332 |          0.76 |

**`byte[]` only falls clearly behind after a few KB.** Up to ~1 KB every tier is within a few
percent of the others: a read costs a fixed ~350–670 ns of lookup and binding, and the value's bytes
are noise next to that — with uniform keys at 256 B–1 KB, `byte[]` even beats `MemorySegment` by
5–12% (`ByteBuffer` stays level with `byte[]`). From **~2 KB** `MemorySegment` pulls clearly ahead
on both access patterns (−13% hot, −14% uniform). With a hot key it reaches ~1.7× at 16 KB and
**~2× from 32 KB** onward; with uniform keys the larger fixed lookup cost caps it at ~1.6–1.7× from
32 KB.

**Why 2×:** past ~16 KB both tiers are paying per byte, and `byte[]` pays twice — the JVM zeroes
the new array, then the value is copied into it — while the buffer tiers only copy. A line fit to
the hot-key rows from 64 KB up gives ~21 ns/KB for `byte[]` against ~11 ns/KB for `MemorySegment`.

**`ByteBuffer` vs `MemorySegment`:** same native call, same result from 256 KB up. With a hot key
`ByteBuffer` is 5–20% slower below that, from wrapping the buffer as a segment and updating its
position on every call. With uniform keys the two swap places from 256 B to 32 KB (`ByteBuffer`
4–10% faster); since both make the same downcall, treat that as a run-order artifact of the sweep
rather than a real difference.

**Mapper (size)** is flat at ~300–350 ns on a hot key from 256 B up, regardless of size — the true
zero-copy floor, and the reason to use `get(key, Mapper)` when you only need to inspect or parse
part of a value. Copying out of the Mapper (`MemorySegment.copy`) is no cheaper than letting
`rocksdb_get_into_buffer` copy, and 3–21% slower than it from 64 KB up.

**At 1 MB with uniform keys, everything is slow** (52–89 µs): the 50 MB database no longer fits
the 32 MB default block cache, so reads go to the OS page cache. That row measures the storage
path more than the binding.

## MultiGet: individual gets vs ReadBatch

`MultiGetScaleBenchmark` compares repeated single-key `get()` calls against [ReadBatch](reference.md)
for the same key count, swept from 1 to 128 keys — FFM only, no JNI side (this isolates ReadBatch's
own batching win, not a binding comparison). 3 forks; `byte[]` and zero-copy (`MemorySegment`) tiers.

| Batch size | Individual `byte[]` | ReadBatch `byte[]` |   Δ   | Individual 0-copy | ReadBatch 0-copy |   Δ   |
|-----------:|--------------------:|-------------------:|:-----:|------------------:|-----------------:|:-----:|
|          1 |           2,520,555 |          1,646,113 | −35%  |         2,836,386 |        1,916,584 | −32%  |
|          2 |           1,236,810 |          1,059,151 | −14%  |         1,362,930 |        1,270,107 |  −7%  |
|          4 |             608,909 |            557,470 |  −8%  |           646,965 |          728,606 | +13%  |
|          8 |             303,919 |            301,360 |  −1%  |           317,381 |          411,855 | +30%  |
|         16 |             149,510 |            140,011 |  −6%  |           157,007 |          207,086 | +32%  |
|         32 |              71,475 |             71,534 |  +0%  |            75,542 |           95,986 | +27%  |
|         64 |              34,582 |             36,405 |  +5%  |            36,582 |           45,770 | +25%  |
|        128 |              16,880 |             17,509 |  +4%  |            18,107 |           21,884 | +21%  |

**The crossover depends on the tier.** Zero-copy crosses between batch sizes 2 and 4 and then
holds a +21–32% lead. `byte[]` barely crosses at all: it trails up to 16 keys and only edges ahead
(+4–5%) from 64: its per-call key copy into a fresh arena and per-value `byte[]` allocation are paid
the same way on both sides, diluting what `ReadBatch` saves. `ByteBuffer` (not shown) sits between
the two, crossing between 2 and 8 and leading by +13–26% from 8 keys up. Below the crossover, the
fixed per-call overhead of a batched multi-get isn't amortized and individual `get()` wins — by
32–35% at a single key.

A single-fork run of the same sweep on a Linux x86_64 desktop showed the same crossover point but a
much larger and still-growing advantage past it — individual gets fell behind by up to 2× at batch
sizes ≥16, rather than plateauing at ~1.0–1.3×. The likely explanation is that this desktop's baseline
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
