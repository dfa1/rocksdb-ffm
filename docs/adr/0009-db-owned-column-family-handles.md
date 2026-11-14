# ADR 0009: The database owns its column family handles

- **Status:** Proposed
- **Date:** 2026-11-14
- **Deciders:** project maintainer
- **Supersedes:** —
- **Superseded by:** —

## Context

The seven multi-column-family `open*` overloads (`openReadWrite`, both `openReadOnly` forms,
`openSecondary`, `openTransaction`, `openOptimistic`, `openTtl`) fill a caller-supplied
`List<ColumnFamilyHandle>` (`RocksDB.collectCfHandles`). `openTtl` adds a third list for the TTLs.

- **Positional alignment, by convention only.** Handles match descriptors by index; a size mismatch
  between `descriptors` and `ttls` needs a runtime check (#289).
- **Caller-owned, not children of the DB.** Only `RocksIterator` and `Snapshot` register with
  `NativeObjectWithChildren`. `rocksdb_column_family_handle_destroy` deletes a
  `ColumnFamilyHandleImpl`, whose destructor locks the DB mutex and unrefs the `ColumnFamilyData`
  (`db/column_family.cc`). Destroying a handle after its DB is closed is therefore a use-after-free,
  and nothing enforces the try-with-resources order.
- **The default column family handle is mapped twice** (`ReadBatch`,
  `RocksDBWriteOperationsBindings`), against the "map each symbol once" rule, and
  `rocksdb_get_default_column_family_handle` allocates a wrapper on every call.

Nobody uses the library in production yet, so this can be fixed once, without a deprecation period.

## Decision

- Every multi-column-family open takes `List<ColumnFamilyDescriptor>` (`openTtl`:
  `List<TtlColumnFamily>`, a record pairing a descriptor with its TTL) and returns only the DB. The old
  overloads are deleted. There is no double or triple list anywhere.
- Every DB type (`NativeDB`) keeps a registry of its handles by `ColumnFamilyName`, in open order:
  `getColumnFamily(ColumnFamilyName)` (throws `NoSuchElementException` for an unknown name) and
  `getColumnFamilies()` (an unmodifiable snapshot). `ColumnFamilyName.DEFAULT` is always present.
- Handles are owned by the DB. They are registered as children, so `NativeObjectWithChildren`
  releases them before the DB's own native close. `close()` on a DB-owned handle is a **no-op**:
  only the DB's `close()` or `dropColumnFamily` releases it. This is implemented with a
  package-private "owned by parent" flag on `NativeObject` and a package-private `release()` that
  the DB's sweep calls.
- `createColumnFamily` registers the new handle (and still returns it). `dropColumnFamily` releases
  the handle and removes the entry, only after the native drop succeeded. Dropping another DB's handle
  is rejected with `IllegalArgumentException`.
- Duplicate descriptor names are rejected before the native open.
- The default column family handle gets a single mapping, `ColumnFamilyHandle.ofDefault`.

## Consequences

### Positive

- A handle can no longer be destroyed after its DB is closed, and nobody has to close handles in a
  particular order.
- Using a handle after its DB is closed throws `IllegalStateException` from `ptr()` before any
  native call, instead of touching freed memory.
- Index alignment and the #289 size check disappear: the types make a mismatch impossible.
- One mapping of `rocksdb_get_default_column_family_handle`, resolved once per `ReadBatch`, removes
  the per-call wrapper allocations.

### Negative

- `ColumnFamilyHandle.close()` is a no-op, which no other `NativeObject` does. Existing
  `try (var cf = db.createColumnFamily(...))` blocks still compile and behave.
- Every multi-column-family call site changes (about a dozen test files and the docs).

### Risks to manage

- **Drop races a concurrent user of the handle.** Dropping now releases the handle, so a thread
  using it at that moment hits the close-versus-call race described in
  [explanation.md](../explanation.md). Keeping the handle until DB close would keep the dropped
  family's files on disk until then, so releasing on drop is the better trade; document it.
- **`Transaction` is still not a child of `TransactionDB`**, so a transaction used after its DB
  closes can still crash. Out of scope here; tracked separately.
- The registry keys on names, never descriptors: a descriptor's `Options` are closed with
  `LoadedOptions`.
- Registry mutations happen only after native success (create, drop), since they cannot be undone.

## Alternatives considered

- **Keep the output list.** Keeps positional alignment, caller ownership and the use-after-free.
- **Return a result record holding the DB and a map.** Every caller unpacks two values, the map still
  has to be tied to the DB's lifetime, and it does not cover `createColumnFamily`.
- **`SequencedMap<ColumnFamilyDescriptor, Duration>` as the TTL input.** With identity equality it
  adds nothing over a list of pairs; with name-based equality `HashMap.put` keeps the first key, so a
  second descriptor's `Options` would be silently dropped while its TTL is taken.
- **Idempotent `close()` on shared handles.** IDEs flag a returned `AutoCloseable` as an unclosed
  resource, so users would write `try (var cf = db.getColumnFamily(n))`, which would destroy the
  handle for every other user of the DB.
- **`makeImmortal()` on handles.** The DB could never release them, and `transferOwnership()` throws
  on an immortal object, which `registerChild`'s late-registration path relies on (issues/143).
- **Fresh non-owning views per lookup.** A second wrapper class for no benefit.

## References

- [ADR 0003](0003-ownership-model.md); `NativeObjectWithChildren`; issues/143, #289, #302, #303, #304
