# Benchmarks

JMH results for `rocksdbffm` (FFM) against `rocksdbjni` (JNI), plus the methodology needed to read
them honestly. This page is a supplement to [explanation.md](explanation.md), which covers *why* the
FFM path is faster; see [how-to.md#run-the-benchmarks](how-to.md#run-the-benchmarks) to reproduce
them.

## Methodology

Benchmarks run on a Mac Studio (Apple M5 Max), JDK 25.0.5 (Homebrew OpenJDK), measured 2026-10-31,
with the native library built locally by zig 0.17.0 (CI pins 0.16.0). Each tier uses the same pre-seeded key, so the numbers reflect
call overhead rather than cache-miss variance. Both libraries use `PinnableSlice` for reads.

> **The two sides do not run the same RocksDB build, and cannot be made to.** The FFM column uses
> the bundled native library built from the `rocksdb/` submodule (**v11.8.1**). The JNI column uses
> `org.rocksdb:rocksdbjni` **11.1.1** — the newest release that exists, since `rocksdbjni` lags
> behind the core. Treat the deltas as indicative of *binding* overhead rather than a
> controlled like-for-like comparison: part of any difference may come from the engine rather than
> the binding.

## Results

| Operation              | API tier           | FFM (ops/s) | JNI (ops/s) |   Gain    |
|:-----------------------|:-------------------|:-----------:|:-----------:|:---------:|
| Reads                  | `byte[]`           |  7,957,139  |  3,577,781  | **+122%** |
| Reads                  | `DirectByteBuffer` |  7,475,189  |  3,577,598  | **+109%** |
| Reads                  | `MemorySegment`    |  7,840,501  |      —      |     —     |
| Writes                 | `byte[]`           |   556,463   |   498,064   | **+12%**  |
| Writes                 | `DirectByteBuffer` |   547,158   |   477,926   | **+14%**  |
| Writes                 | `MemorySegment`    |   557,740   |      —      |     —     |
| Batch writes (100 ops) | `byte[]`           |   13,385    |   11,011    | **+22%**  |

`rocksdbjni` has no `MemorySegment` tier, hence the missing cells.

## Reading the numbers

**Reads gain ~2×** because FFM downcall stubs are JIT-compiled directly into the caller: there is no
JNI frame setup and no thread-state transition per call. At ~7–8 M ops/s the per-call overhead *is*
the benchmark.

**The three FFM read tiers land within ~6% of each other** (7.5–8.0 M ops/s), and in this run
`byte[]` edged out `MemorySegment`, with `DirectByteBuffer` slowest — outside the error bars, but
small. These are memtable hits on tiny values, so the one value copy the `byte[]` tier pays is too
cheap to show; the zero-copy tiers only pull ahead as values grow (`FfmValueSizeBenchmark`, printed
at the end of `./scripts/benchmark.sh`: at 64 KB, `MemorySegment` reads are ~1.5× `byte[]`).

**Write gains are much smaller** (+12–14%) because WAL and memtable I/O dominate the call: binding
overhead is a small fraction of the total, so removing it moves the total little.

**Batch writes gain more than single writes** (+22%) because the per-call overhead is paid 100× per
iteration while the I/O cost is amortized across the batch — exactly the shape you would expect if
the win comes from call overhead.

**Absolute write numbers are not comparable across machines.** They are bound by the WAL's
filesystem path, not the CPU: this Mac Studio run measured both FFM *and* JNI writes 15–45% below an
earlier run on an M5 laptop, while the FFM-over-JNI ratio stayed in the same range.

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
