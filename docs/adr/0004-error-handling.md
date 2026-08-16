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

*(Proposed — not yet implemented.)* Move the classification logic off `RocksDBException` entirely, and
bake it into every downcall `MethodHandle` itself via `MethodHandles.catchException`, applied once,
centrally, in `NativeLibrary.lookup(...)` — the single factory every `MH_` field already goes through
— rather than repeated per call site or even per field:

```java
static MethodHandle lookup(String symbol, FunctionDescriptor fd, Linker.Option... options) {
    MethodHandle raw = /* existing Linker.downcallHandle(...) lookup */;
    MethodHandle thrower = MethodHandles.throwException(raw.type().returnType(), Throwable.class);
    MethodHandle handler = MethodHandles.filterArguments(thrower, 0, MH_CLASSIFY);
    return MethodHandles.catchException(raw, Throwable.class, handler);
}

// classify(t) returns t unchanged for the four binding-bug types, or a new
// AssertionError wrapping t for anything else -- never a RocksDBException.
private static Throwable classify(Throwable t) { ... }
```

`MethodHandles.throwException(returnType, Throwable.class)` builds a handle that always throws
whatever `Throwable` it's given, declared to "return" any type — which is what lets one `classify`
method compose with every `MH_` field regardless of that field's actual return type (`MemorySegment`,
`long`, `boolean`, `void`, an enum, …), with no per-return-type handler needed.

- `NullPointerException`, `IllegalStateException` (including `WrongThreadException`),
  `WrongMethodTypeException`, and `ClassCastException` propagate **unwrapped, with their original
  type preserved** — a caller who somehow sees `NullPointerException` from a misused API sees exactly
  that, never a `RocksDBException` with an `NullPointerException` cause three lines down.
- Anything else — which, per the analysis above, should never actually happen for a correctly
  configured downcall handle — becomes an `AssertionError`, not a `RocksDBException`. `AssertionError`
  signals "this should be impossible," distinct from both `RocksDBException` (real DB errors) and an
  ordinary `RuntimeException` (which reads more like "expected, just unhandled").
- `RocksDBException` becomes reserved exclusively for the `errHolder`/`checkError` path and is no
  longer constructible from any `invokeExact` catch site at all; `RocksDBException.wrap(String,
  Throwable)` is deleted outright.

One important limit: `invokeExact`'s signature is `throws Throwable` at the *language* level
regardless of what a given handle instance actually does at runtime, so javac still requires a
`try`/`catch` (or a `throws Throwable` on the enclosing method) at every call site — this decision
does not, and cannot, delete that syntactic wrapper. What it does remove is every call site's own
*classification* logic and custom message: since `MH_GET_VALUE` (for example) has already classified
and rethrown by the time an exception reaches the call site's `catch`, that block collapses to one
fixed, uniform, effectively-unreachable line:

```java
try {
    return (MemorySegment) MH_GET_VALUE.invokeExact(ptr(), vallenOut);
} catch (Throwable t) {
    throw new AssertionError(t); // unreachable: MH_GET_VALUE already classified via NativeLibrary.lookup
}
```

Landing this touches exactly one method (`NativeLibrary.lookup`) plus every call site's `catch` body
(mechanical, identical edit at each), rather than either the field-by-field or fully-manual sweep
originally proposed here.

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
- The real decision logic lives in exactly one place (`NativeLibrary.lookup` plus `classify`), not
  duplicated per field or per call site — every `MH_` field gets the behavior automatically the moment
  it's constructed through the existing shared factory.

### Negative

- Every call site's `catch` body still changes (to the fixed, uniform `AssertionError(t)` form) even
  though it no longer does any real classification work — a smaller, more mechanical edit than a
  full rethrow-clause sweep would have been, but still touches every file with an `invokeExact` call.
- `AssertionError` in a library's exception path is unusual for callers not expecting `Error`-hierarchy
  types from a supposedly `RuntimeException`-only API surface — worth calling out prominently in
  `RocksDBException`'s and `NativeLibrary.lookup`'s own Javadoc once implemented.
- `MethodHandles.catchException`/`throwException`-composed handles are one more LambdaForm layer over
  a raw downcall handle; expected to be negligible next to the native call itself, but not verified
  against this project's own benchmarks yet — worth a before/after run once implemented.

### Risks to manage

- The classification logic (`classify` in `NativeLibrary`) is now load-bearing for every single native
  call in the library — a bug there is a bug everywhere at once. Mitigated by it being a small, pure,
  directly unit-testable function (`Throwable -> Throwable`), unlike the ~60+ scattered catch blocks
  it replaces.
- Any future caller-supplied callback added to the library (beyond `Mapper`) must be reviewed for the
  same widened-catch mistake `withPinned`/`withPinnedCf` made — this is a pattern to watch for in
  review, not something the type system prevents by itself, since it happens inside a method body
  `NativeLibrary.lookup`'s wrapping has no visibility into.

## Alternatives considered

- **Rename every call site to a shared `RocksDB.wrapInvokeFailure(String, Throwable)` helper**, doing
  the classification at each `catch (Throwable t)` block instead of inside the `MethodHandle` itself:
  same end behavior, and still centralizes the *logic* in one method, but leaves every call site
  needing its own custom message string and doesn't get the "automatically applied to every `MH_`
  field via the existing factory" property — a real mechanical sweep instead of a single-method change
  plus a cosmetic catch-body cleanup.
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
- `RocksDBException.java`, `RocksDB.java` (`errHolder`/`checkError`/`toNative`), `NativeLibrary.java`
  (`lookup`)
- `java.lang.invoke.MethodHandles#catchException`, `#throwException`, `#filterArguments`
