# ADR 0006: How far to centralize MethodHandle call sites and their try/catch boilerplate

- **Status:** Proposed
- **Date:** 2026-08-22
- **Deciders:** project maintainer
- **Supersedes:** —
- **Superseded by:** —

## Context

`TransactionDB.java` (1132 lines) and `Transaction.java` (928 lines) each hand-roll their own
`MH_PUT`/`MH_PUT_CF`/`MH_GET_PINNED`/`MH_DELETE_CF`/… fields and their own copy of the
`try (Arena arena = ...) { errHolder → toNative → invokeExact → checkError } catch (Throwable
t) { wrapInvokeFailure }` shape, once per tier (byte[]/`ByteBuffer`/`MemorySegment`) per
CF-or-not. Six other DB types — `ReadWriteDB`, `TtlDB`, `BlobDB`, `ReadOnlyDB`, `SecondaryDB`,
`OptimisticTransactionDB` — share one Java body per operation instead, via
`RocksDBReadOperations`/`RocksDBWriteOperations` default methods forwarding to package-private
statics on `RocksDB` (`putBytes`, `mergeSegment`, `deleteCfBytes`, …), each hardcoding one
`static final MethodHandle` field. That works for those six because they all call the identical
C symbol (`rocksdb_put`, `rocksdb_get_pinned`, …) on a plain `rocksdb_t*`. `TransactionDB`'s
direct ops call a second symbol family (`rocksdb_transactiondb_put`, …); `Transaction`'s
transactional ops call a third (`rocksdb_transaction_put`, …) — a transactional put has to go
through the lock manager, a plain put never does.

CLAUDE.md's stated reason for keeping these separate: "always keep the MethodHandles private
static final … never pass a `MethodHandle` as a method parameter, even internally: `invokeExact`
on a `static final` field lets the JIT treat the target as a compile-time constant; routed
through a parameter, that constant-folding is lost." Taken literally, this forbids the one
mechanical change that would collapse `TransactionDB`/`Transaction`'s three symbol families into
shared helpers the way the other six types already share theirs: a generic `put(MethodHandle mh,
...)` taking whichever `MH_` field the caller has.

That reason traces back to a real attempt, not an unchecked assertion: commit `460b9d7`
(2026-08-15, the `RocksIterator`/`PinnableSlice` dedup) tried collapsing `readKey()`/`readValue()`
— which call two different symbols, `rocksdb_iter_key` and `rocksdb_iter_value` — into one shared
method taking the target `MethodHandle` as a parameter, reverted it, and documented the rule in
CLAUDE.md specifically so it would not be reintroduced. In this ADR's own vocabulary, that is a
real `degree2` case the project had already hit and backed out of, months before this ADR existed.
What that commit does not carry forward is a number: "tried … reverted" is the language of an
observed regression, not a theory, but no benchmark was captured or committed alongside it, so
neither its magnitude nor its exact scenario survived for anyone to check or cite later — which is
what `MethodHandleParameterBenchmark` newly provides, and, per the `degree2` results below, is
consistent with what that original revert would have found.

Separately, the same shape of duplication exists for a second, independent reason that
*is* already fully understood: every `invokeExact` call is followed by its own
`try`/`catch (Throwable t) { throw RocksDB.wrapInvokeFailure(...); }`, because `invokeExact`'s
declared `throws Throwable` forces a `try`/`catch` at every syntactic call site. ADR 0004 already
centralized the *classification* logic inside `wrapInvokeFailure` and explicitly rejected
composing it into the `MethodHandle` itself via `MethodHandles.catchException`, on the grounds
that doing so "doesn't actually reduce call-site boilerplate: `invokeExact`'s `throws Throwable`
still forces a `try`/`catch` at every syntactic call site regardless of what the specific handle
instance does at runtime." What ADR 0004 didn't settle is whether the `try`/`catch` *syntax*
itself — as opposed to the classification logic inside it — can be centralized behind an
ordinary functional-interface helper instead. At last count, 485 `catch (Throwable` blocks and
478 `wrapInvokeFailure` calls exist across 41 files in `core/src/main/java`. That number is not
specific to `TransactionDB`/`Transaction` — it is the project's single largest source of
repeated syntax, and this ADR's second question.

Two microbenchmarks — `MethodHandleParameterBenchmark` and `TryCatchWrapperBenchmark`, both in the
`benchmarks` module — were built to put a reproducible, committed number behind both questions: the
first already had real but unrecorded evidence behind it (the revert above); the second had none
at all.
Neither calls into RocksDB: `MethodHandleParameterBenchmark` binds the same native symbol
(`memcmp`) independently up to 8 times over, so what varies between its benchmark methods is
purely the *number of distinct `MethodHandle` object identities* one shared call site rotates
through — not which native function gets called, which would confound the measurement with each
function's own native-side cost. Run on this machine (Apple M5, JDK 25.0.2 Zulu, JMH 1.37,
1 fork, 3×1s warmup, 5×1s measurement, throughput mode):

| Benchmark | ops/s | vs. `direct` |
|:---|---:|---:|
| `direct` — `static final` field, read at the call site (today's rule) | 329,823,499 | — |
| `degree1` — via parameter, `% 1`, always the *same* handle | 328,069,803 | −0.5% (noise) |
| `degree2` — via parameter, rotating across 2 handles | 76,595,039 | **−76.8%** |
| `degree3` — via parameter, rotating across 3 handles | 191,921,644 | **−41.8%** |
| `degree4` | 193,235,866 | −41.4% |
| `degree5` | 181,246,196 | −45.0% |
| `degree6` | 181,100,771 | −45.1% |
| `degree7` | 170,772,571 | −48.2% |
| `degree8` | 171,287,781 | −48.1% |
| `directTryCatch` / `directTryCatchVoid` — inline `try`/`catch` | 328,609,572 / 330,093,267 | — |
| `viaWrapper` / `viaWrapperVoid` — same call, through a lambda + shared classify-and-rethrow helper | 329,916,359 / 329,328,450 | +0.4% / −0.2% (noise) |

Three results, not two, and the first one is not the shape it was expected to be. Passing the
*same* `MethodHandle` through a parameter (`degree1`) costs nothing measurable — the JIT inlines
the one-line forwarding call and re-derives the constant, so "never pass a `MethodHandle` as a
parameter, even internally," stated as an absolute, is not what the numbers show. But the cost of
passing more than one distinct target through that call site is not a step function that appears
once and then holds steady: `degree2` is the single worst point measured, at −77% — markedly worse
than `degree3`'s −42%, which is closer to what a first pass at this benchmark (rotating through
three genuinely different libc functions, an earlier version of this measurement) had reported.
From `degree3` onward the throughput keeps sliding, slowly, as more identities join the rotation
(−41% down to −48% by `degree8`), rather than settling onto a flat floor.

The likely mechanism, inferred from the shape rather than confirmed with a profiler or
`-XX:+PrintInlining`: HotSpot's inline-cache tiers for a polymorphic call site are usually
monomorphic (fast path, one check) → bimorphic (two checks, still trying to discriminate) →
megamorphic (gives up discriminating, falls back to a uniform indirect dispatch). A
`MethodHandle.invokeExact` call site that sees exactly two distinct targets may be paying for a
bimorphic check that never stabilizes, while three or more targets fall back to the — slower than
monomorphic, but more uniform — megamorphic path, which turns out cheaper than a thrashing
bimorphic one. This is a plausible reading of the data, not a proven one; it would take
`-XX:+PrintInlining` or a JIT-aware profiler to confirm, and this ADR does not go further than the
throughput numbers actually show.

**Reproducibility check.** This machine (a laptop) has an asymmetric core layout — 4 performance
cores, 6 efficiency cores (`sysctl hw.perflevel0.physicalcpu`/`hw.perflevel1.physicalcpu`) — and
macOS's scheduler can migrate a process between them with no control from user space. That alone
could produce a single-point anomaly like the `degree2` dip with no JIT story required: an unlucky
placement on an efficiency core for just that one benchmark method. To check, the full sweep was
run three more times as three independent `java` process launches (not JMH forks within one
invocation — separate shell invocations, so macOS makes an independent scheduling decision for each
one):

| | Run 1 | Run 2 | Run 3 |
|:---|---:|---:|---:|
| `degree1` | 329,866,679 | 328,112,046 | 325,012,213 |
| `degree2` | 70,802,656 | 69,873,735 | 70,361,144 |
| `degree3` | 199,857,546 | 193,811,293 | 190,400,696 |
| `degree8` | 166,177,725 | 169,791,406 | 169,771,772 |
| `direct` | 326,665,733 | 320,623,177 | 322,260,161 |

Every run agrees to within about 5% (tighter for most rows — `degree3` is the widest spread), and
`degree2` is the worst point in all three. If this were core-migration noise, independent process
launches should disagree with each other — sometimes
`degree2` unlucky, sometimes `degree3`, sometimes neither, with swings closer to the 2–3× a
performance/efficiency core difference would produce on a tight compute loop. They don't: the
same shape reproduces every time. That rules out random scheduling noise on *this* machine as the
explanation. It does not, on its own, establish that the shape — particularly the `degree2`-specific
dip — generalizes to a different architecture, OS scheduler, or HotSpot build; that remains the
open question the Risks section below already flags, now with reproducibility evidence behind the
"real on this machine" half of the claim rather than just an assertion.

Separately, wrapping the same `invokeExact` call in a lambda passed to a shared
classify-and-rethrow helper costs nothing at all, for both `int`-returning and `void`-returning
shapes: the `invokeExact` call still lives in the lambda's own compiled body, which still reads
the `static final` field directly, so nothing about the JIT property this project cares about is
affected by moving the surrounding `try`/`catch` outward.

`TransactionDB`/`Transaction`/the six shared-symbol-family types merged into one call site would
land at `degree3` — three symbol families — which is where the ~40% figure this ADR's Decision
relies on comes from (−42% in the primary run, −39% to −41% across the three reproducibility
runs), not the peak ~77% at `degree2`. That ~40% is specifically the cost of the `invokeExact`
dispatch layer in isolation, isolated from RocksDB's other per-call fixed costs (arena allocation,
`toNative` copies, `errptr` decoding). A real `TransactionDB.put()` merged this way would show a
smaller relative regression than that, because those other costs are unaffected and would dilute
it — this benchmark deliberately does not put a number on that diluted figure,
since it depends on tier and payload size in ways a synthetic call cannot represent honestly. What
it does establish is that the underlying mechanism is real, not noise, worse at the boundary
(2 targets) than a simple "megamorphic and done" model would predict, and continues to worsen
slowly rather than plateau as more targets are added.

## Decision

Two independent questions, two different answers, both grounded in the measurements above.

**Should `TransactionDB`/`Transaction`'s three symbol families be collapsed into shared,
`MethodHandle`-parameterized helpers?** No — the option was seriously on the table (Option B
below) and is rejected on measured evidence, not assumption. Keep the duplication: three symbol
families, each with its own hand-written call sites per tier, exactly as they exist today.

**Should the 485-site `try`/`catch (Throwable t) { throw wrapInvokeFailure(...); }` boilerplate be
centralized behind a shared helper?** Yes, in principle — the measurement shows no cost, and
ADR 0004 already put the classification logic in one place; only the syntax around each call
site remains duplicated for no remaining reason. This ADR records the decision and the evidence
that makes it safe; it does not itself carry out the refactor, which touches every `invokeExact`
call site in the codebase and deserves its own change, reviewed on its own.

### Options considered

- **Option A — status quo.** Keep both the `MethodHandle` rule and the per-site `try`/`catch` as
  written, with no measurement backing either. Rejected: no reason to leave a real, cost-free
  simplification (the `try`/`catch` wrapper) undone once it's been checked, and no reason to keep
  asserting the `MethodHandle` rule as an absolute when the evidence shows a narrower, truer
  version of it.
- **Option B — relax the `MethodHandle` rule for `TransactionDB`/`Transaction`,** passing the
  handle as a parameter to shared `put`/`get`/`merge`/`delete` helpers the way the try/catch
  wrapper is adopted for everything else. Rejected on the `degree3` measurement (three symbol
  families, one call site): a ~40% cost at the `invokeExact` layer, on a project whose stated
  reason for existing over `rocksdbjni` is exactly this kind of per-call overhead
  (`docs/benchmarks.md`: "Reads gain ~2× because FFM downcall stubs are JIT-compiled directly into
  the caller"). The full sweep (`degree1` through `degree8`) also rules out the narrower version of
  this option someone might propose after reading only a two-point comparison — "surely a smaller
  number of shared targets is fine" — since `degree2` alone is worse than `degree3`, at ~−77%; there
  is no safe non-trivial degree between "exactly one handle" and "pay a large, and slowly
  worsening, cost." Giving that back here, permanently, on the read/write hot path, in exchange for
  a line-count reduction, is the wrong trade for this project.
- **Option C — adopt the functional-interface wrapper for the `try`/`catch`/classify
  boilerplate,** leaving every `MethodHandle` exactly where it is (`static final`, one per symbol,
  read directly at its own call site). Accepted: `viaWrapper`/`viaWrapperVoid` show no cost,
  the classification logic is already centralized per ADR 0004, and this removes real, measured
  duplication (485 sites) with no measured downside. Left as a follow-up refactor, not performed
  in this ADR.
- **Option D — source-level code generation** for the `put`/`get`/`merge`/`delete` × tier ×
  symbol-family matrix, emitting independent `static final` fields and independent call sites per
  symbol family from one template, as real `.java` files a normal `javac` compiles. Would remove
  the *human*-maintenance cost of Option A without paying Option B's measured cost, since
  generated code can still satisfy "one field, one call site, no parameter" by construction. Not
  attempted here — no such generator exists in this project today, and building one is a larger
  undertaking than this ADR's scope — but it is the option that would let a future change actually
  remove `TransactionDB`/`Transaction`'s duplication without the megamorphic-dispatch cost Option B
  pays. Left as a named, unstarted alternative.
- **Option E — bytecode manipulation (weaving),** rather than generating Java source: compile one
  hand-written template method once, then have a build step (ASM/ByteBuddy, or a javac plugin
  operating after lowering) clone its `.class` bytecode into each sibling method/class, relinking
  only the constant-pool operand of its `getstatic` instruction from `MH_PUT` to
  `MH_TRANSACTIONDB_PUT` to `MH_TRANSACTION_PUT` — never emitting or maintaining N textual copies,
  generated or hand-written, at all. The premise was checked, not assumed: two methods
  identical except for which `static final MethodHandle` field they read compile to bytecode
  identical in every respect but that one `getstatic` operand —

  ```
  0: getstatic #43   // Field MH_MEMCMP:Ljava/lang/invoke/MethodHandle;
  vs.
  0: getstatic #60   // Field MH_BCMP:Ljava/lang/invoke/MethodHandle;
  ```

  (full `javap -c` diff below). Since HotSpot draws no distinction between a `.class` file javac
  emitted from hand-written source and one a weaver assembled after the fact, a woven call site is
  exactly as monomorphic as a hand-written one — this option does not reintroduce Option B's
  megamorphic-dispatch cost any more than Option D does; the cost here is entirely on the
  software-engineering side, not the
  JIT side. And it is a real cost, larger than Option D's: no generated `.java` ever exists to read
  against the `c.h` prototype it implements, which breaks the audit path this project's whole
  documentation style depends on (every ADR to date treats "each method is a self-contained,
  auditable translation of one C prototype" as load-bearing); stack traces and step-through
  debugging need line-number-table handling a source generator gets for free from javac; and it
  adds a build-time bytecode-manipulation dependency this project has never needed before, for a
  problem (three short, mechanical method bodies) that doesn't obviously need bytecode-level
  tooling to solve when Option D solves the identical "one field, one call site" goal at the source
  level, with an artifact a reviewer can actually read. Not attempted here, and not preferred over
  Option D unless a future version of this problem grows enough near-identical, painstakingly
  parameterized template text that keeping it as reviewable source becomes the greater cost.

  ```
  static int callA(MemorySegment, MemorySegment, long);          static int callB(MemorySegment, MemorySegment, long);
    0: getstatic  #43  // Field MH_MEMCMP:...MethodHandle;         0: getstatic  #60  // Field MH_BCMP:...MethodHandle;
    3: aload_0                                                     3: aload_0
    4: aload_1                                                     4: aload_1
    5: lload_2                                                     5: lload_2
    6: invokevirtual #47 // MethodHandle.invokeExact:(...)I        6: invokevirtual #47 // MethodHandle.invokeExact:(...)I
    9: ireturn                                                     9: ireturn
    (identical exception-table and catch body below, omitted)
  ```

## Consequences

### Positive

- The full `degree1`–`degree8` sweep replaces an assumption with a measurement, on this project's
  actual JDK — the next person who proposes collapsing these three symbol families has a curve to
  check against instead of a rule to take on faith, including the counterintuitive part (`degree2`
  is worse than `degree3`) a single before/after number would have hidden.
- `viaWrapper`'s result opens a concrete, evidence-backed simplification (Option C) that removes
  485 duplicated `try`/`catch` sites without touching the property the project's whole
  performance story depends on.
- The `MethodHandle` rule in CLAUDE.md can be stated more precisely than "never pass as a
  parameter" — the real constraint is "never let one `invokeExact` call site see more than one
  distinct target in practice," which is both narrower (a same-handle parameter is fine) and
  clearer about why it exists.

### Negative

- `TransactionDB`/`Transaction`'s ~2060 combined lines of near-duplicate call sites stay exactly
  as they are; this ADR closes the question of *why*, it does not shrink the files.
- Option C is decided but not implemented here — 485 call sites is a large, mechanical,
  whole-codebase change (the same shape of risk ADR 0004 flagged for its own sweep: "missing one
  leaves that site still [unconverted], silently reintroducing" the duplication this ADR exists
  to close), and it is being deferred rather than landed alongside the decision.
- The benchmark's libc stand-ins are a deliberately clean proxy, not `rocksdb_put` itself; the
  ~40% figure (`degree3`) is specific to the `invokeExact` dispatch layer in isolation and should
  not be quoted as "put() gets 40% slower" — the Context section says why, and future citations of
  this number should carry that caveat forward.

### Risks to manage

- If Option C is implemented later without re-reading this ADR, someone could reasonably try to
  extend the *same* wrapper pattern to also parameterize the `MethodHandle` itself (since the
  wrapper already takes a lambda) — that would silently reintroduce Option B's rejected shape
  inside what was supposed to be a safe refactor. The wrapper's contract is "same handle, moved
  `try`/`catch`," not "any handle, moved dispatch," and that distinction needs to survive into
  whatever change implements Option C.
- Numbers this way are JVM- and machine-specific (JMH's own output already carries this caveat:
  "comparisons between different JVMs are already problematic"). The reproducibility check in the
  Context section only rules out *this machine's* scheduling noise as the explanation for the
  `degree2` dip — it does not establish that the dip, or the shape of the curve generally, holds on
  a different architecture, OS scheduler, or HotSpot build. A future JDK's escape analysis or
  inline-cache tuning could change the shape of the whole curve, not just its magnitude. If
  `TransactionDB`/`Transaction` come up again, re-run `MethodHandleParameterBenchmark` — ideally on
  more than one architecture — before assuming this ADR's numbers still hold, rather than citing
  them indefinitely.
- The `degree2`-worse-than-`degree3` inversion is inferred to be a bimorphic-vs-megamorphic
  inline-cache effect, not confirmed with a profiler. If this ADR is revisited, that inference —
  not just the raw numbers — is the part most worth re-checking with `-XX:+PrintInlining` or an
  async-profiler run before being repeated as established fact.

## Alternatives considered

Covered inline above as Options A–E; no additional alternatives were evaluated beyond those five.

## References

- `CLAUDE.md`, "Code" section — the `static final MethodHandle` / no-parameter-passing rule this
  ADR narrows
- [ADR 0004](0004-error-handling.md) — centralized the classification logic
  (`RocksDB.wrapInvokeFailure`) this ADR's Option C builds on, and already rejected composing it
  *into* the `MethodHandle` itself, for a related but distinct reason
- `benchmarks/src/test/java/io/github/dfa1/rocksdbffm/benchmark/MethodHandleParameterBenchmark.java`,
  `TryCatchWrapperBenchmark.java` — the two measurements this ADR is built on; run with
  `./scripts/benchmark.sh MethodHandleParameterBenchmark` / `TryCatchWrapperBenchmark`
- `TransactionDB.java`, `Transaction.java` — the duplicated call sites in question
- `RocksDBReadOperations.java`, `RocksDBWriteOperations.java`, `RocksDB.java` (`putBytes`,
  `mergeSegment`, `deleteCfBytes`, …) — the shared-symbol-family sharing this ADR contrasts with
- `docs/benchmarks.md`, "Reading the numbers" — the ~2× JIT-inlining measurement Option B would
  have partially given back
