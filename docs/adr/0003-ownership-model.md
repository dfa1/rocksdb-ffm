# 0003: `NativeObject` and explicit ownership transfer

## Status

Accepted

## Context

Every native pointer this library hands out is a liability if it outlives the memory it points to, or
if it gets freed twice. Two related problems needed one answer each:

1. **When is a native resource released?** The JVM has no reliable, timely signal for "the Java
   wrapper around this pointer is unreachable" — `finalize()` is deprecated and unpredictable,
   `java.lang.ref.Cleaner` runs on GC's schedule, not the program's. RocksDB's destroy functions are
   not idempotent, either: calling one twice is a double free, and a double free crashes the JVM with
   no Java-level stack trace and no exception to catch — the worst possible failure mode for a bug to
   have.
2. **Who owns a pointer once it has been handed to another native object?** Several C API calls
   transfer ownership: `rocksdb_block_based_options_set_filter_policy` makes the table options
   responsible for destroying the filter policy it was given. If both the original Java wrapper and
   the new owner call `close()`, that is a double free by construction, not a caller mistake.

## Decision

- Every class wrapping a native pointer extends `NativeObject`, which implements `AutoCloseable` and
  holds the pointer in an `AtomicReference`. `close()` atomically swaps the reference to
  `MemorySegment.NULL`, so the actual destroy call runs exactly once no matter how many times or from
  how many threads `close()` is invoked — later calls are no-ops, and using the pointer afterward
  throws `IllegalStateException` instead of dereferencing freed memory. No finalizer, no `Cleaner`, no
  GC-driven release: callers close it via try-with-resources, or it leaks.
- For the ownership-transfer case, the setter that takes ownership calls the package-private
  `transferOwnership()` on the argument being absorbed. That nulls the argument's own pointer the same
  way `close()` does, making the argument's own `close()` a no-op — the argument is now inert, and only
  the new owner's `close()` (cascading through whatever it itself is chained into) does real work.
  Which calls transfer ownership is not guesswork: it is read off `db/c.cc` (does the destroy function
  for the owner also delete the argument?), the C API's own docs, or how `rocksdbjni` handles the same
  pair of objects.

See [explanation.md#lifecycle-and-ownership](../explanation.md#lifecycle-and-ownership) for the
worked example (`FilterPolicy` → `BlockBasedTableOptions` → `Options`, one try-with-resources, freed
once at the top) and the current API surface.

## Consequences

- **Try-with-resources works everywhere, uniformly**, even across an ownership-transfer chain —
  callers never need to know or remember which of several chained objects actually owns the native
  memory by the time `close()` runs.
- **Every ownership-transferring setter is a place a mistake is silent until it crashes the process.**
  Missing a `transferOwnership()` call where the C API expects one reintroduces the double-free bug
  class this whole design exists to remove; adding one where the C API does *not* transfer ownership
  leaks the argument's memory instead (quieter, but still wrong). Both directions require checking the
  actual C++ source, not just the header comment, for every new wrapped setter of this shape.
- **No cleanup ever happens without an explicit `close()`.** A caller who drops a `NativeObject`
  without closing it leaks the native resource for the life of the process — there is no safety net,
  by design: [0001](0001-ffm-instead-of-jni.md) is explicit that FFM's safety boundary does not extend
  to automatic native lifetime management.
