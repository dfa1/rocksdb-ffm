# ADR 0007: A systematic study of `Linker.Option.critical(false)`

- **Status:** Proposed
- **Date:** 2026-08-28
- **Deciders:** project maintainer
- **Supersedes:** —
- **Superseded by:** —

## Context

`Linker.Option.critical(boolean allowHeapAccess)` marks a downcall `MethodHandle` as "trivial": the
JVM skips the usual thread-state transition to `_thread_in_native` for the call, which normally lets
GC treat the calling thread as safepoint-safe as long as it's blocked in native code. A critical call
instead keeps the thread in `_thread_in_vm`-equivalent state for the call's duration — cheaper per
call, but it means the call **must not block**: I/O, a contended lock, or anything that can pause for
an unbounded time will stall GC (and every other safepoint-dependent JVM activity) for as long as the
call runs.

This project already uses it in exactly one place. `PinnableHandle.java` (the newer
`rocksdb_get_pinned_v2`/`_cf_v2`-family wrapper) marks its `MH_GET_VALUE`/`MH_DESTROY` handles
`critical(false)`, with the comment "pure in-memory pointer arithmetic — safe and worth marking
critical(false) ... to skip transition overhead." `RocksDB.java` documents the opposite call directly
above it: `rocksdb_get_pinned_v2` itself is explicitly **not** marked critical, with the comment
"`get_pinned_*_v2` can block on disk I/O (a Get), so it must NOT be marked critical: a critical
downcall stalls GC for its entire duration." Two calls, one native symbol family, opposite verdicts —
already a working example of the judgment call this ADR is about.

That established precedent motivated an experiment: `PinnableSlice.java` — the older
`rocksdb_pinnableslice_value`/`_destroy` pair `ReadBatch` uses, structurally identical to
`PinnableHandle`'s pair — did **not** have the same `critical(false)` marking. A JMH stack profile of
`MultiGetScaleBenchmark.readBatchByteArray` (see [reference.md#multiget](../reference.md), the
`ReadBatch` feature row) showed roughly 6-7% of RUNNABLE samples inside
`PinnableSlice.tryClose`/`value`'s downcall stubs, which looked like exactly the kind of short,
frequent, pure-pointer-arithmetic call `PinnableHandle`'s comment already argues for.

Applying the same marking to `PinnableSlice` and measuring it turned into a lesson about
measurement, not about the optimization itself:

- An initial before/after comparison, run as two separate `java` process launches roughly 20 minutes
  apart (the first following a long JMH sweep), showed **both** the touched benchmark
  (`readBatchByteArray`) and an untouched **control** (`individualGetsByteArray`, which goes through
  `PinnableHandle` — already `critical(false)` before this experiment, never touched by the change)
  drop by a similar 3-7%. Since the control has no code path affected by the change, that whole drop
  was session-level noise (thermal drift after sustained load, background CPU contention — a video
  was running on the machine at the time), not a regression from the change.
- A same-session, back-to-back A/B (stash the change, benchmark, restore it, benchmark again,
  `-f 3`) removed the cross-session gap but still showed the control drifting 3-5% between its own
  two "identical code" runs — the noise floor on this laptop, at this fork count, was comparable to
  the effect being measured.
- At `-f 1`, several cells had error bars of 25-50% of their own mean — single-fork JMH runs have no
  second fork to average a bad JIT/GC/scheduling event against, and are unusable for an effect this
  small.
- At `-f 2`, one run finally landed with a genuinely quiet control (±0.1-0.3% drift, well inside its
  own error bars) and showed the touched benchmark's delta (+0.1% to +0.6%) sitting at the same tiny
  magnitude — a clean null result, not a positive one.

The change was reverted (not merged) on that basis: this project's own "Benchmark First" rule means
an unproven change shouldn't ride along, and the one clean measurement obtained says `critical(false)`
made no measurable difference for `PinnableSlice`, contrary to the profiler-motivated hypothesis. That
leaves an open question this single experiment cannot answer: is `critical(false)` a real, exploitable
win *anywhere else* in this codebase (48 `NativeObject` subclasses, most with their own `_destroy`
call and several with cheap getters), or does its per-call saving sit below the noise floor of
anything this project's native calls actually cost, full stop? One noisy, single-candidate experiment
is not evidence for either answer.

## Decision

Propose — not yet execute — a systematic study, structured the way [ADR 0006](0006-method-handles-usage.md)
studied `MethodHandle` call-site sharing: isolate the mechanism with a synthetic microbenchmark before
drawing any conclusion from a RocksDB-shaped one.

1. **Establish the ceiling first, with no RocksDB involved.** A JMH microbenchmark (in the
  `benchmarks` module, alongside `MethodHandleParameterBenchmark`) that binds a trivially short,
  guaranteed-non-blocking native function (e.g. libc `memcmp`/`strlen`, as ADR 0006 already does) once
  plain and once `critical(false)`, and measures the per-call delta directly — no PinnableSlice, no
  RocksDB, no I/O, nothing that can introduce the kind of session-to-session noise this experiment
  just hit. This answers "what does `critical(false)` save, at all, on this project's JDK and
  hardware" as a standalone number, the same way `MethodHandleParameterBenchmark` answered "what does
  a shared call site cost" before ADR 0006 drew any conclusion about `TransactionDB`/`Transaction`.
2. **Catalogue candidates against that ceiling**, not against intuition. Every `NativeObject`
  subclass's native calls, classified by reading the actual `db/c.cc` implementation (not assumed
  from the C header) for blocking behavior — matching the rigor `RocksDB.java`'s existing
  `get_pinned_v2` comment already applies to one call:
   - **Likely safe, worth measuring:** simple option-struct `_destroy` calls (`WriteOptions`,
    `ReadOptions`, `FlushOptions`, `CompactOptions`, …) and cheap accessors (`RocksIterator#valid`,
    `#key`/`#value` where already zero-copy) — pure allocation/pointer-arithmetic, no shared state.
   - **Definitely unsafe:** anything that can reach disk I/O, a contended mutex, or unbounded work —
    `get`/`put`/`write`/iterator seek, `ReadWriteDB#close` (can flush/wait), `Cache` destroy (can
    evict a large working set), `BackupEngine`/`Checkpoint` (filesystem I/O), `Snapshot` release
    (touches DB-internal state).
   - **Needs case-by-case native-source review:** anything in between — e.g. `RocksIterator#close`
    if it can unmap SST file mappings, `ColumnFamilyHandle` destroy if it touches shared CF metadata.
3. **Only then, for whatever survives step 2 as both safe and above the noise floor**, benchmark the
  real `NativeObject` call the way this experiment tried to for `PinnableSlice` — but paired within a
  single controlled run (ideally alternating trials within one JMH invocation rather than two
  separate `java` launches), at `-f 3` minimum per what this experiment found necessary to get a
  readable signal, and only trusted if an untouched control benchmark in the same run shows the
  measurement was actually quiet.

## Consequences

### Positive

- Turns one ambiguous, already-repeated experiment into a reusable answer: a measured ceiling for
  `critical(false)`'s saving on this project's JDK, plus a documented per-class safety classification
  future contributors can extend instead of re-litigating from scratch.
- Prevents repeating this session's exact mistake — chasing a specific class's number before knowing
  whether the mechanism can produce a measurable effect at all on calls this cheap.
- Gives `CLAUDE.md`'s `critical`/trivial-downcall guidance (currently just the two inline comments in
  `PinnableHandle.java`/`RocksDB.java`) a place to point to for the full reasoning, the way ADR 0004
  and ADR 0006 already anchor their respective inline rules.

### Negative

- Real upfront investigation work before any further `critical(false)` marking lands — this ADR
  authorizes a study, not a rollout. Until it's done, `critical(false)` stays exactly where it already
  was before this experiment: `PinnableHandle` only.
- The microbenchmark step (1) only measures the JVM-side transition cost in isolation; it cannot
  measure how much of `PinnableSlice`'s originally-observed 6-7% was genuine native-side work
  (`rocksdb_pinnableslice_destroy`'s actual `delete`) that no linker option can remove — that would
  need a native profiler (`perf`, Instruments, or async-profiler attached to `librocksdb.dylib`),
  which is out of scope for a Java-only JMH study and not attempted by this ADR. (`perf stat`/`perf
  record` are confirmed usable without root on this project's dev container — see
  [benchmarks.md#profiling-with-perf](../benchmarks.md#profiling-with-perf) — which unblocks this as
  a future step.)

### Risks to manage

- **Safety and speed are separate axes and must not get conflated.** A call being measurably free to
  mark critical does not make it safe (it might still occasionally block), and a call being safe does
  not make marking it critical measurably worth doing (this experiment's own result). Every candidate
  in step 2's "likely safe" bucket still needs the source-level check `RocksDB.java`'s existing
  `get_pinned_v2` comment already demonstrates — never promote something to critical on measured
  speed alone.
- **Cross-session, cross-process comparisons are not trustworthy at this effect size.** This
  experiment's biggest wasted effort was comparing runs separated by wall-clock time (a 20-minute gap,
  a video playing in the background) and initially mistaking machine noise for a real regression. Any
  future measurement under this ADR must include an untouched control benchmark in the *same* run and
  distrust any result where the control itself isn't quiet.
- **A negative result for `PinnableSlice` is not evidence against the mechanism generally.** One
  candidate, one workload (batch sizes 64/128, ~1KB values), one machine. The systematic study exists
  specifically because that single data point is too narrow to generalize from in either direction.

## Alternatives considered

- **Roll `critical(false)` out ad hoc to more "obviously safe" candidates**, the way this experiment
  tried for `PinnableSlice` alone. Rejected: the actual attempt cost real engineering time chasing
  noise across four separate benchmark runs before landing one clean (null) result, for a single
  candidate — repeating that per-class, un-systematically, doesn't scale and produces exactly the kind
  of ambiguous, hard-to-trust measurements this experiment struggled with.
- **Do nothing further** — leave `critical(false)` at `PinnableHandle` only, treat the `PinnableSlice`
  null result as the final word. Rejected as premature: the null result is specific to one call shape
  and one workload; a systematic study might find a genuinely different answer for, say, a
  high-frequency option-struct destroy call with a much smaller native-side cost than
  `PinnableSlice`'s (which still does real C++ object destruction, not just a `free()`).
- **Profile the native side directly** (`perf`/Instruments/async-profiler against `librocksdb.dylib`)
  instead of, or before, a Java microbenchmark. Not attempted in this experiment — JMH's stack
  profiler cannot see past the JNI/FFM call boundary into native code, only that a downcall stub was
  on-CPU, which is what motivated but couldn't resolve the original hypothesis. Worth folding into the
  study in step 1 as a complementary technique (it can show how much of a call's cost is JVM
  transition overhead vs. genuine native work, independent of `critical(false)`'s effect), not a
  replacement for the JMH-based ceiling measurement.

## References

- `PinnableHandle.java` — the one place `critical(false)` is already used, with its safety
  justification
- `RocksDB.java`'s `MH_GET_PINNED_V2` comment — the existing counter-example, already documenting why
  a blocking call must not be marked critical
- [ADR 0006](0006-method-handles-usage.md) — the methodology precedent this ADR follows: isolate the
  mechanism with a synthetic microbenchmark before drawing conclusions from a RocksDB-shaped one, and
  the "measure, don't assume" ethos generally
- `MultiGetScaleBenchmark.java`, its stack-profiler run — the observation that originally motivated
  trying `critical(false)` on `PinnableSlice`
- [`Linker.Option.critical`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.Option.html#critical(boolean))
  javadoc — the correctness constraints (no blocking, no unbounded time) any candidate must satisfy
