# ADR 0004: Separating genuine RocksDB errors from FFM binding bugs

- **Status:** Accepted
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

Move the wrap/rethrow decision off `RocksDBException` and onto `RocksDB`, as
`RocksDB.wrapInvokeFailure(String, Throwable)` — alongside the other shared FFM plumbing already
there (`errHolder`, `checkError`, `toNative`; see `CLAUDE.md`'s "Centralized Error Handling" section):

```java
static RuntimeException wrapInvokeFailure(String message, Throwable t) {
    if (t instanceof RuntimeException e) {
        throw e;
    }
    if (t instanceof IOException e) {
        throw new UncheckedIOException(message, e);
    }
    throw new AssertionError(message, t);
}
```

- **Every `RuntimeException` propagates unwrapped, with its original type preserved** — this is one
  check, not an enumerated list of the four binding-bug types originally proposed here, because
  `checkError` typically runs *inside the same `try` block* as `invokeExact`, right after it (see
  `getBytes` for the established shape), so the genuine `RocksDBException` it throws for a real
  `errptr` error also reaches this method — and needs to pass through unchanged too, exactly like the
  four binding-bug types. Missing this is a real bug the first implementation attempt hit: five
  existing tests broke because their expected `RocksDBException` came back as an `AssertionError`
  wrapping it, once the enumerated-type check stopped recognizing `RocksDBException` itself as
  something to pass through. `RuntimeException` alone covers `NullPointerException`,
  `IllegalStateException` (including `WrongThreadException`), `WrongMethodTypeException`,
  `ClassCastException`, *and* `RocksDBException` — no enumeration needed, and no risk of a fifth case
  being missed the same way in the future.
- **An `IOException`** — not from `invokeExact` itself, which never throws a checked exception, but
  possible from other code sharing the same `try` block (e.g. file access) — **becomes an
  `UncheckedIOException`**, the standard JDK idiom for surfacing a checked I/O failure as unchecked.
- **Anything else reaching this method** — which, per the analysis above, should never actually happen
  for a correctly configured downcall handle — **becomes an `AssertionError`**, not a
  `RocksDBException`. `AssertionError` signals "this should be impossible," distinct from both
  `RocksDBException` (real DB errors) and an ordinary `RuntimeException` (which reads more like
  "expected, just unhandled").
- `RocksDBException` becomes reserved exclusively for the `errHolder`/`checkError` path — it has no
  public constructor, and is no longer *constructed* from any `invokeExact` catch site (though a
  `RocksDBException` already thrown by `checkError` still passes through, per the point above);
  `RocksDBException.wrap(String, Throwable)` is deleted outright.

Because every existing call site already funnels through one static method
(`RocksDBException.wrap(...)`), landing this is a mechanical rename across the codebase
(`RocksDBException.wrap(` → `RocksDB.wrapInvokeFailure(`), verified by a full compile and test run —
not a redesign repeated at every site. `MethodHandles.catchException` (composing the classification
into every downcall handle itself, in `NativeLibrary.lookup`, instead of at each call site) was tried
first and rejected — see Alternatives — since `invokeExact`'s `throws Throwable` is a language-level
constraint regardless of what a given handle instance does at runtime, so javac still requires a
`try`/`catch` at every call site either way. Composing the handle bought no reduction in call-site
boilerplate over a plain shared method, for real added complexity.

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
- The real decision logic lives in exactly one place (`RocksDB.wrapInvokeFailure`), not duplicated at
  every call site — even though every call site still *invokes* it, per the `invokeExact` constraint
  below.

### Negative

- A large, mechanical diff across the whole codebase lands in one change (every
  `RocksDBException.wrap(` becomes `RocksDB.wrapInvokeFailure(`), which needs the full test suite as
  the verification story rather than per-site review.
- `AssertionError` in a library's exception path is unusual for callers not expecting `Error`-hierarchy
  types from a supposedly `RuntimeException`-only API surface — called out prominently in
  `RocksDB.wrapInvokeFailure`'s own Javadoc.
- Every call site keeps its own `try`/`catch (Throwable t)` — `invokeExact`'s `throws Throwable` is a
  language-level constraint no amount of centralizing the *logic* removes (see Alternatives). What
  moves is the classification, not the syntactic wrapper.

### Risks to manage

- Every one of the ~60+ files with an `invokeExact` catch site needs the identical mechanical edit;
  missing one leaves that site still wrapping binding bugs as `RocksDBException`, silently
  reintroducing the exact problem this ADR exists to close. Mitigated by deleting the old
  `RocksDBException.wrap(...)` outright once the sweep lands (rather than leaving both paths
  available) — the compiler finds every straggler.
- Any future caller-supplied callback added to the library (beyond `Mapper`) must be reviewed for the
  same widened-catch mistake `withPinned`/`withPinnedCf` made — this is a pattern to watch for in
  review, not something the type system prevents by itself.

## Alternatives considered

- **Compose the classification into every downcall `MethodHandle` itself**, via
  `MethodHandles.catchException`/`throwException`/`filterArguments`, applied once in
  `NativeLibrary.lookup(...)` rather than at each call site. Tried first; rejected once it became
  clear it doesn't actually reduce call-site boilerplate: `invokeExact`'s `throws Throwable` still
  forces a `try`/`catch` at every syntactic call site regardless of what the specific handle instance
  does at runtime, so the catch body just becomes a different (still mandatory) fixed line — real
  added complexity (a `classify` combinator wired through three `MethodHandles` factory methods) for
  no reduction in the thing actually being optimized for.
- **Touch every call site individually** with its own explicit rethrow clause before
  `catch (Throwable t)`, no shared helper at all: same end behavior, but ~60+ places to get right
  instead of one, with no benefit over centralizing given a single chokepoint already exists.
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
- `RocksDBException.java`, `RocksDB.java` (`errHolder`/`checkError`/`toNative`/`wrapInvokeFailure`)
