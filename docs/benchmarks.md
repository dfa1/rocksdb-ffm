# Benchmarks

JMH results for `rocksdbffm` (FFM) against `rocksdbjni` (JNI), plus the methodology needed to read
them honestly. This page is a supplement to [explanation.md](explanation.md), which covers *why* the
FFM path is faster; see [how-to.md#run-the-benchmarks](how-to.md#run-the-benchmarks) to reproduce
them.

## Methodology

Benchmarks run on JDK 25, Apple M5. Each tier uses the same pre-seeded key, so the numbers reflect
call overhead rather than cache-miss variance. Both libraries use `PinnableSlice` for reads.

> **The two sides do not run the same RocksDB build, and cannot be made to.** The FFM column uses
> the bundled native library built from the `rocksdb/` submodule (**v11.8.1**). The JNI column uses
> `org.rocksdb:rocksdbjni` **10.10.1.1** — the newest release that exists, since `rocksdbjni` has
> published no 11.x at all. Treat the deltas as indicative of *binding* overhead rather than a
> controlled like-for-like comparison: part of any difference may come from the engine rather than
> the binding.

## Results

| Operation              | API tier           | FFM (ops/s) | JNI (ops/s) |   Gain    |
|:-----------------------|:-------------------|:-----------:|:-----------:|:---------:|
| Reads                  | `byte[]`           |  7,196,554  |  3,619,125  | **+99%**  |
| Reads                  | `DirectByteBuffer` |  8,077,135  |  3,656,113  | **+121%** |
| Reads                  | `MemorySegment`    |  8,149,510  |      —      |     —     |
| Writes                 | `byte[]`           |   671,213   |   608,496   | **+10%**  |
| Writes                 | `DirectByteBuffer` |   694,166   |   590,923   | **+17%**  |
| Writes                 | `MemorySegment`    |   686,889   |      —      |     —     |
| Batch writes (100 ops) | `byte[]`           |   23,936    |   16,813    | **+42%**  |

`rocksdbjni` has no `MemorySegment` tier, hence the missing cells.

## Reading the numbers

**Reads gain ~2×** because FFM downcall stubs are JIT-compiled directly into the caller: there is no
JNI frame setup and no thread-state transition per call. At ~7–8 M ops/s the per-call overhead *is*
the benchmark.

**`MemorySegment` is the fastest read tier** because segments backed by a confined arena carry no GC
scope-check overhead on the hot path — the JIT sees a plain memory access.

**Write gains are much smaller** (+10–17%) because WAL and memtable I/O dominate the call: binding
overhead is a small fraction of the total, so removing it moves the total little.

**Batch writes gain more than single writes** (+42%) because the per-call overhead is paid 100× per
iteration while the I/O cost is amortized across the batch — exactly the shape you would expect if
the win comes from call overhead.

## MultiGet: individual gets vs ReadBatch

`MultiGetScaleBenchmark` compares repeated single-key `get()` calls against [ReadBatch](reference.md)
for the same key count, swept from 1 to 128 keys — FFM only, no JNI side (this isolates ReadBatch's
own batching win, not a binding comparison). One fork; `byte[]` and zero-copy (`MemorySegment`) tiers.

| Batch size | Individual `byte[]` | ReadBatch `byte[]` |   Δ   | Individual 0-copy | ReadBatch 0-copy |   Δ   |
|-----------:|---------------------:|--------------------:|:-----:|--------------------:|-------------------:|:-----:|
|          1 |             2,454,792 |            1,502,818 | −39%  |            2,660,649 |           1,674,289 | −37%  |
|          2 |             1,188,106 |              965,775 | −19%  |            1,283,908 |           1,113,954 | −13%  |
|          4 |               594,607 |              551,385 |  −7%  |              642,368 |             652,425 |  +2%  |
|          8 |               262,832 |              302,523 | +15%  |              316,920 |             362,836 | +14%  |
|         16 |               126,613 |              151,635 | +20%  |              160,115 |             183,974 | +15%  |
|         32 |                66,752 |               74,055 | +11%  |               76,001 |              86,178 | +13%  |
|         64 |                30,218 |               35,914 | +19%  |               35,843 |              41,882 | +17%  |
|        128 |                16,249 |               16,662 |  +3%  |               15,615 |              19,830 | +27%  |

**Crossover is between batch sizes 2 and 4.** Below that, `ReadBatch`'s upfront bookkeeping
(allocating the batched-multi-get call arrays at `create`) isn't amortized and individual `get()`
wins. From batch size 4 up, `ReadBatch` wins, plateauing around +15-20% on this hardware.

A single-fork run of the same sweep on a Linux x86_64 desktop showed the same crossover point but a
much larger and still-growing advantage past it — individual gets fell behind by up to 2× at batch
sizes ≥16, rather than plateauing at ~1.2×. The likely explanation is that this desktop's baseline
per-call `get()` overhead is proportionally larger relative to its own raw throughput than the M5's,
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
absolute figure while leaving the ratios roughly intact. For `MultiGetScaleBenchmark` specifically:

```bash
./scripts/benchmark.sh MultiGetScaleBenchmark -Djmh.forks=1
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
