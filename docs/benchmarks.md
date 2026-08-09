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

## Reproducing

```bash
./mvnw test-compile -q
./scripts/benchmark.sh
```

The script builds everything, runs both the FFM and JNI suites, and prints a side-by-side table.
Expect absolute numbers to differ on other hardware — a thermally throttled laptop depresses every
absolute figure while leaving the ratios roughly intact.
