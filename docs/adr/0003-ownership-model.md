# ADR 0003: `NativeObject` — `AutoCloseable`, idempotent close, ownership transfer

- **Status:** Accepted
- **Date:** 2026-08-16
- **Deciders:** project maintainer

## Context

Every native pointer this library hands out owns off-heap memory that must be freed via RocksDB's own
`rocksdb_*_destroy` calls. Java's GC does not free native memory, and RocksDB's destroy functions are
not idempotent: calling one twice is a double free, and a double free crashes the JVM with no
Java-level stack trace and no exception to catch — the worst possible failure mode for a bug to have.
Beyond simple double-close, several C API calls transfer ownership of one pointer to another:
`rocksdb_block_based_options_set_filter_policy` makes the table options responsible for destroying the
filter policy it was given. If both the original wrapper and the new owner call `close()`, that is a
double free by construction, not a caller mistake — the lifecycle model has to account for it
directly.

## Decision

All native-pointer holders extend `NativeObject`, which is `AutoCloseable` with an **idempotent**
`close()`: the pointer lives in an `AtomicReference`, atomically swapped to `MemorySegment.NULL` on
close, so the real destroy call runs exactly once no matter how many times or from how many threads
`close()` is invoked, and using the pointer afterward throws `IllegalStateException` instead of
dereferencing freed memory. Callers use try-with-resources.

For ownership transfer, the setter that takes ownership calls the package-private
`transferOwnership()` on the argument being absorbed — which nulls the argument's own pointer the
same way `close()` does, making the argument's own `close()` a no-op. Only the new owner's `close()`
(cascading through however many objects it is itself chained into) does real work. Which calls
transfer ownership is read off `db/c.cc` (does the destroy function for the owner also delete the
argument?), the C API's own docs, or how `rocksdbjni` handles the same pair of objects — not
guesswork.

## Consequences

### Positive

- Deterministic release via try-with-resources; no reliance on GC/finalizers/`Cleaner`.
- Idempotent close makes double-close safe (explicit `close()` then try-with-resources unwind, or
  concurrent `close()` from two threads).
- Try-with-resources works uniformly even across an ownership-transfer chain — callers never need to
  track which of several chained objects actually owns the native memory by the time `close()` runs.

### Negative

- Caller must manage lifetime; a `NativeObject` dropped without `close()` leaks the native resource
  for the life of the process. There is no safety net, by design — see the risks FFM's safety
  boundary does *not* cover in [ADR 0001](0001-ffm-instead-of-jni.md).
- More bookkeeping per wrapped setter: every ownership-transferring C API call needs its Java setter
  to remember `transferOwnership()`, which is easy to omit for a new wrapped setter of this shape.

### Risks to manage

- Missing a `transferOwnership()` call where the C API expects one reintroduces the double-free bug
  class this design exists to remove; adding one where the C API does *not* transfer ownership leaks
  the argument's memory instead (quieter, but still wrong). Both directions require checking the
  actual C++ source for every new wrapped setter, not just the header comment.
- This idempotent-close, ownership-transfer contract is the foundation any future object graph
  (e.g. a shared `Cache` referenced by multiple `Options`) must keep respecting.

## Alternatives considered

- **Cleaner/finalizer-based release:** non-deterministic, GC-timing-dependent native frees — directly
  unacceptable given a double free crashes the JVM with no diagnostic.
- **Manual `free()`/`destroy()` method (non-`AutoCloseable`):** loses try-with-resources and adds no
  idempotency for free by itself.
- **Reference counting instead of an ownership-transfer marker:** considered for the shared-object
  case (a `Cache` referenced by multiple `Options`), but RocksDB's own C API is not reference-counted
  for the types this library wraps — modeling counting on the Java side without native backing would
  create its own class of drift bugs. Revisit if a wrapped type's C API does add refcounting.

## References

- [explanation.md#lifecycle-and-ownership](../explanation.md#lifecycle-and-ownership)
- [ADR 0001 — FFM instead of JNI](0001-ffm-instead-of-jni.md)
