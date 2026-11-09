---
name: upgrade-rocksdb
description: Bump the rocksdb/ submodule to a new RocksDB release and re-verify everything that silently drifts across versions (enum values, c.h prototypes, the locally built native library). Use when asked to upgrade/bump/move RocksDB to a new version or tag, or after the submodule pointer changed.
---

# Upgrade RocksDB

RocksDB changes break this binding *silently*: an enum gets a value inserted mid-list, and Java
keeps compiling while it reads the wrong counter. Follow every step, even when tests pass.

## 1. Move the submodule

```bash
OLD=$(git -C rocksdb rev-parse HEAD)
git -C rocksdb fetch --tags origin
git -C rocksdb ls-remote --tags origin 'v*' | grep -v '\^{}' | awk -F/ '{print $3}' | sort -V | tail -5   # pick the tag
git -C rocksdb checkout vX.Y.Z
NEW=$(git -C rocksdb rev-parse HEAD)
git add rocksdb   # stage the new submodule pointer
```

## 2. Enums: `java scripts/CheckEnums.java`

Must exit 0. It compiles the headers with `zig c++`, prints every native enumerator's real value,
and diffs against the Java enums (CI runs it too).

- `ERROR` = drift. Renumber from the printed native values. For `TickerType`/`HistogramType`, keep the
  constants in native order, keep existing Javadoc, and take new constants' docs from the
  `statistics.h` comments. Escape `[`/`]`, and never start a line with `#` (Markdown heading).
- `warn native-only` = new upstream value. Wrap it if the C API can reach it, or leave it.
- A new int-backed enum in Java means a new entry in `ENUMS` in that script.
- Check that each value actually reaches the C++ enum. `c.h`'s own constants can be stale: `rocksdb_set_perf_level`
  `static_cast`s to the C++ `PerfLevel`, whose values had moved past `c.h`'s `rocksdb_enable_*`.
  When Java follows `c.h`, read the `db/c.cc` side to confirm.

## 3. C API: `java scripts/CheckPrototypes.java`, then the diff

Must exit 0. It compares every `MH_` field's `/// \`prototype\`` comment with `c.h`, by ABI (types
only, parameter names ignored). CI runs it too.

- **Changed prototype**: update the comment and the field's `FunctionDescriptor` together. The script
  only sees the comment, and a mismatched descriptor is memory corruption, not an exception.
- **Not declared in c.h** (removed/renamed symbol): the owning class fails at static init. Remove or
  replace the binding.

Then read what's new:

```bash
git -C rocksdb diff $OLD $NEW -- include/rocksdb/c.h
```

- **New symbol**: a candidate for `docs/c-api-gaps.md` (Type A), or wrap it now.
- New `enum { rocksdb_... }` blocks: see step 2.

## 4. Rebuild the native library and run everything

`scripts/build-rocksdb.sh` rebuilds automatically when the stamp file `rocksdb.sha` next to the
library doesn't match the submodule commit plus the script's own hash. Confirm the library is fresh:

```bash
strings native/<os>-<arch>/src/main/resources/native/<os>-<arch>/librocksdb.* | grep rocksdb_build_git_sha
git -C rocksdb rev-parse HEAD   # must match (the _tag string can be empty)
./mvnw test
./mvnw package -pl core -am -DskipTests   # javadoc: no ERROR lines
```

## 5. Docs

- `AGENTS.md`: the "pinned to vX.Y.Z" line.
- `docs/reference.md`: the ticker/histogram/metric counts and the enum constant table, for any enum that changed.
- `CHANGELOG.md`: one line for the bump, plus one per enum fix (link the PR).
- `docs/benchmarks.md` names the version its numbers were measured on. Leave it unless you rerun the benchmarks.
