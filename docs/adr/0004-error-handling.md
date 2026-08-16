# 0004: Separating genuine RocksDB errors from FFM binding bugs

## Status

Proposed

## Context

`RocksDBException` (see [explanation.md#errors-are-always-loud](../explanation.md#errors-are-always-loud))
is currently built two different ways that get treated as the same thing:

1. **`RocksDBException(String)`** — package-private, used by `RocksDB.checkError(MemorySegment)` when
   a native call's `errptr` out-parameter comes back non-null. This is a genuine operational failure
   RocksDB itself reported: corruption, an IO error, an invalid argument at the DB level. There is no
   Java `Throwable` to wrap — the message is the C string from `errptr`.
2. **`RocksDBException.wrap(String, Throwable)`** — public, called at essentially every
   `catch (Throwable t)` block wrapping an `invokeExact` call (~431 sites across 39 files, since
   `invokeExact`'s declared `throws Throwable` has to be handled somewhere). This wraps *anything*
   `invokeExact` throws, uniformly, into the same `RocksDBException` type as path 1.

The problem: for a downcall `MethodHandle` obtained via `Linker.downcallHandle`, `invokeExact` only
throws a small, fixed set of unchecked exceptions in practice, and every one of them indicates a bug
in this library's own binding code, never a legitimate RocksDB failure (those are reported via
`errptr`, not by `invokeExact` throwing):

- `WrongMethodTypeException` — `invokeExact`'s static types don't match the handle's actual
  `FunctionDescriptor`.
- `IllegalStateException` (including `WrongThreadException`, a subtype) — a `MemorySegment` argument's
  arena was already closed, or it's a confined arena accessed from the wrong thread.
- `NullPointerException` — a required argument was an actual Java `null` reference (not
  `MemorySegment.NULL`, which is a valid non-null segment).
- `ClassCastException` — from the explicit cast on `invokeExact`'s return value.

Because path 2 wraps all of these the same way as path 1, `catch (RocksDBException e)` is not a
meaningful thing for a caller to do today: it conflates "RocksDB reported a real error, this might be
worth retrying or logging" with "this library has a bug," which is never something calling code should
be handling — the fix belongs in this library, not the caller's `catch` block.

This surfaced concretely in `RocksDB.withPinned`/`withPinnedCf`: an earlier attempt to widen their
`catch (Throwable t)` to cover the whole method body (rather than just the `invokeExact` call)
accidentally started wrapping exceptions thrown by the *caller-supplied* `Mapper` callback too —
including the `NullPointerException` `PinnableHandle#map` deliberately throws when a mapper returns
`null`. `PinnedGetTest` caught the regression before it landed. That incident is what prompted this
ADR: the callback case is a symptom of the same underlying issue — nothing currently stops "wrap
literally everything" from being applied somewhere it silently changes an exception's public type.

## Decision (direction agreed, exact shape still open)

- The four "binding bug" types above should propagate **unwrapped, with their original type
  preserved** — not replaced with a new type, not wrapped as `RocksDBException`. A caller who somehow
  needs to see `NullPointerException` from a misused API should see exactly that, not a
  `RocksDBException` with an `NullPointerException` cause three lines down. This directly reflects the
  `withPinned` incident above: the caller-supplied `Mapper`'s own `NullPointerException` must be
  indistinguishable from a `NullPointerException` this library itself would throw for the same
  contract violation.
- The wrapping/rethrowing decision should move out of `RocksDBException` and onto `RocksDB`, alongside
  the other shared FFM plumbing already there (`errHolder`, `checkError`, `toNative` — see
  `CLAUDE.md`'s "Centralized Error Handling" section). `RocksDBException` itself becomes reserved
  exclusively for the `errHolder`/`checkError` path — genuine `errptr`-reported errors — and is no
  longer constructible from any `invokeExact` catch site at all.
- Because all ~431 existing call sites already funnel through one static method
  (`RocksDBException.wrap(...)`), moving the decision logic into its replacement is a single-file
  change in effect, even though every call site's text changes (`RocksDBException.wrap(` →
  `RocksDB.<newName>(`) — a mechanical, verifiable-by-compilation rename, not a redesign repeated 431
  times.

### Open questions

- **Name of the new `RocksDB`-hosted helper.** Candidates discussed: `RocksDB.wrapInvokeFailure`
  (matches the `checkError`-style verb+noun naming already established) vs. `RocksDB.wrapNativeCall`
  (more generic). Not yet settled.
- **What a genuinely unexpected `Throwable` becomes** — i.e., anything `invokeExact` throws that is
  *not* one of the four known binding-bug types, which by the analysis above should never actually
  happen for a correctly configured downcall handle. Candidates discussed:
  - `AssertionError` — signals "this should be impossible," distinct from both `RocksDBException`
    (real DB errors) and an ordinary `RuntimeException` (which reads more like "expected, just
    unhandled").
  - A plain `RuntimeException` wrapping it — less emphatic, avoids pulling `Error`-hierarchy semantics
    into a library's normal exception path.
  - Keeping `RocksDBException` as this one remaining catch-all — rejected in discussion, since it
    reopens exactly the "catching `RocksDBException` is worthless because it's always a bug" problem
    this ADR exists to close, just for a narrower, rarer case.
- **Execution plan for the ~431-site sweep** once the above two are settled: mechanical rename,
  verified by a full compile and test run (not by inspection of individual sites, given the count).

## Consequences (anticipated, pending the open questions above)

- `catch (RocksDBException e)` becomes meaningful again: it will only ever mean "RocksDB itself
  reported an operational error," never "this library has a bug that happened to surface here."
- Binding bugs (wrong arena lifetime, wrong thread, `WrongMethodTypeException` from a mismatched
  `FunctionDescriptor`) become *more visible*, not less — they surface as their natural exception type
  immediately, instead of being flattened into the same `RocksDBException` shape as a corruption error
  three call frames later.
- A large, mechanical diff (~431 call sites across 39 files) lands in one change, which needs the full
  test suite as the verification story rather than per-site review.
