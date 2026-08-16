# ADR 0004: Separating genuine RocksDB errors from FFM binding bugs

- **Status:** Proposed
- **Date:** 2026-08-16
- **Deciders:** project maintainer

## Context

`RocksDBException` (see [explanation.md#errors-are-always-loud](../explanation.md#errors-are-always-loud))
is currently built two different ways that get treated as the same thing. `RocksDBException(String)`
(package-private) is used by `RocksDB.checkError(MemorySegment)` when a native call's `errptr`
out-parameter comes back non-null — a genuine operational failure RocksDB itself reported (corruption,
an IO error, an invalid argument at the DB level), with no Java `Throwable` to wrap. Separately,
`RocksDBException.wrap(String, Throwable)` (public) is called at essentially every
`catch (Throwable t)` block wrapping an `invokeExact` call, across every wrapper class in the library,
since `invokeExact`'s declared `throws Throwable` has to be handled somewhere. It wraps *anything*
`invokeExact` throws, uniformly, into the same `RocksDBException` type as the `errptr` path.

For a downcall `MethodHandle` obtained via `Linker.downcallHandle`, `invokeExact` only throws a small,
fixed set of unchecked exceptions in practice, and every one of them indicates a bug in this library's
own binding code, never a legitimate RocksDB failure: `WrongMethodTypeException` (static types don't
match the handle's `FunctionDescriptor`), `IllegalStateException`/`WrongThreadException` (a
`MemorySegment` argument's arena was already closed, or accessed from the wrong thread), and
`NullPointerException` (a required argument was an actual Java `null`, not `MemorySegment.NULL`).
Because the current `wrap()` treats these the same as a genuine `errptr` error, `catch (RocksDBException e)`
is not meaningful today — it conflates "RocksDB reported a real error, worth retrying or logging" with
"this library has a bug," which is never something calling code should be handling.

This surfaced concretely in `RocksDB.withPinned`/`withPinnedCf`: an earlier attempt to widen their
`catch (Throwable t)` to cover the whole method body (rather than just the `invokeExact` call)
accidentally started wrapping exceptions thrown by the *caller-supplied* `Mapper` callback too —
including the `NullPointerException` `PinnableHandle#map` deliberately throws when a mapper returns
`null`. `PinnedGetTest` caught the regression before it landed, and is what prompted this ADR.

## Decision

*(Proposed — not yet implemented.)* Move the wrap/rethrow decision off `RocksDBException` and onto
`RocksDB`, as `RocksDB.wrapInvokeFailure(String, Throwable)` — alongside the other shared FFM plumbing
already there (`errHolder`, `checkError`, `toNative`; see `CLAUDE.md`'s "Centralized Error Handling"
section):

- `NullPointerException`, `IllegalStateException` (including `WrongThreadException`),
  `WrongMethodTypeException`, and `ClassCastException` propagate **unwrapped, with their original
  type preserved** — a caller who somehow sees `NullPointerException` from a misused API sees exactly
  that, never a `RocksDBException` with an `NullPointerException` cause three lines down.
- Anything else reaching this method — which, per the analysis above, should never actually happen
  for a correctly configured downcall handle — becomes an `AssertionError`, not a `RocksDBException`.
  `AssertionError` signals "this should be impossible," distinct from both `RocksDBException` (real DB
  errors) and an ordinary `RuntimeException` (which reads more like "expected, just unhandled").
- `RocksDBException` becomes reserved exclusively for the `errHolder`/`checkError` path and is no
  longer constructible from any `invokeExact` catch site at all.

Because every existing call site already funnels through one static method
(`RocksDBException.wrap(...)`), landing this is a mechanical rename across the codebase
(`RocksDBException.wrap(` → `RocksDB.wrapInvokeFailure(`), verified by a full compile and test run —
not a redesign repeated at every site.

## Consequences

### Positive

- `catch (RocksDBException e)` becomes meaningful: it will only ever mean "RocksDB itself reported an
  operational error," never "this library has a bug that happened to surface here."
- Binding bugs become *more visible*, not less — they surface as their natural exception type
  immediately, instead of being flattened into the same `RocksDBException` shape as a corruption error
  three call frames later.
- Caller-supplied callbacks (`Mapper`, `EventListener`-style hooks if added later) can safely throw
  without their exceptions being mistaken for native-call failures, as long as they sit outside the
  narrow `invokeExact`-only catch — the exact bug this ADR's motivating incident exposed.

### Negative

- A large, mechanical diff across the whole codebase lands in one change, which needs the full test
  suite as the verification story rather than per-site review.
- `AssertionError` in a library's exception path is unusual for callers not expecting `Error`-hierarchy
  types from a supposedly `RuntimeException`-only API surface — worth calling out prominently in
  `RocksDBException`'s and `RocksDB.wrapInvokeFailure`'s own Javadoc once implemented.

### Risks to manage

- Every one of the ~60+ files with an `invokeExact` catch site needs the identical mechanical edit;
  missing one leaves that site still wrapping binding bugs as `RocksDBException`, silently
  reintroducing the exact problem this ADR exists to close. Mitigated by making the old
  `RocksDBException.wrap(...)` inaccessible once the sweep lands (remove the method, let the compiler
  find every straggler) rather than leaving both paths available.
- Any future caller-supplied callback added to the library (beyond `Mapper`) must be reviewed for the
  same widened-catch mistake `withPinned`/`withPinnedCf` made — this is a pattern to watch for in
  review, not something the type system prevents by itself.

## Alternatives considered

- **Touch every call site individually** with its own explicit rethrow clause before
  `catch (Throwable t)`, instead of centralizing in one helper: same end behavior, but ~60+ places to
  get right instead of one, with no benefit over centralizing given a single chokepoint already
  exists.
- **Keep `RocksDBException` as the catch-all**, just also let the four binding-bug types propagate
  unwrapped as a special case inside `RocksDBException.wrap` itself: rejected because it keeps the
  wrap/rethrow decision — squarely FFM plumbing concern — living on an exception type that should be
  a plain data class for RocksDB's own errors, not where the decision logic belongs.
- **Wrap the four binding-bug types too, just in a different type than `RocksDBException`** (e.g. a
  new `RocksDBBindingException`): rejected — preserving the original exception's exact type (a bare
  `NullPointerException` stays a `NullPointerException`) carries more information than any wrapper
  would, and matches how a caller would expect these mistakes to surface if FFM had thrown them
  directly with no library in between.

## References

- [explanation.md#errors-are-always-loud](../explanation.md#errors-are-always-loud)
- `RocksDBException.java`, `RocksDB.java` (`errHolder`/`checkError`/`toNative`)
