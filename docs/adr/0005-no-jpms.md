# ADR 0005: Not adopting the Java Platform Module System

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** project maintainer
- **Supersedes:** —
- **Superseded by:** —

## Context

The question came up whether `core` (and the five `native/*` classifier artifacts it depends on
at runtime: `osx-aarch64`, `linux-x86_64`, `linux-aarch64`, `windows-x86_64`, `windows-aarch64`)
should ship a `module-info.java` and become a named module under the Java Platform Module System
(JPMS), rather than the plain classpath JAR it is today.

Two things make this worth writing down rather than deciding informally:

- The project's entire public surface already lives in one package,
  `io.github.dfa1.rocksdbffm`, with `package-private` and `sealed` types doing the encapsulation
  work internally (`NativeObject`, `Custom` merge-operator/logger implementations, etc. — see
  `CLAUDE.md`'s "use NativeObject as base class" and "don't expose public constructors" rules). A
  module boundary would wrap a second encapsulation mechanism around a surface that is already
  fully encapsulated by the first.
- The library uses `java.lang.foreign` throughout, which requires an explicit native-access grant
  at JVM launch. Every module today launches with a single flag,
  `--enable-native-access=ALL-UNNAMED` (`pom.xml`, `benchmarks/pom.xml`,
  `integration-tests/pom.xml`, `.github/smoke/pom.xml`, `scripts/benchmark.sh`), because the
  library and its consumers run unnamed, on the classpath. A named module changes what that flag
  needs to name — `--enable-native-access=<module-name>` instead of `ALL-UNNAMED` — and that name
  becomes something every downstream consumer's launch command has to get right, not just this
  repo's own build.

## Decision

Do not add `module-info.java` to `core` or to any `native/*` artifact. Stay on the classpath,
unnamed-module model this project has used since 0.1.

If module-path interop for consumers who *do* modularize becomes a real ask, the lightweight
follow-up is an `Automatic-Module-Name: io.github.dfa1.rocksdbffm` entry in `core`'s manifest
(one `maven-jar-plugin` config line) — it gives such consumers a stable, predictable module name
to `requires` on the module path, without this project taking on a module graph, a native-access
grant that has to be spelled out per consuming module, or a decision about which of the five
`native/*` artifacts additionally need names. That follow-up is not implemented as part of this
ADR — it stays available, not scheduled.

## Consequences

### Positive

- No new decision surface: consumers keep using the library exactly as they do today, on the
  classpath, with the single `ALL-UNNAMED` native-access flag already documented in
  `docs/how-to.md`/benchmarks.
- No `module-info.java` to keep in sync with the `native/*` classifier matrix as platforms are
  added or removed (`docs/reference.md`'s "Source Map" already tracks that matrix in prose; a
  module graph would be a second, executable copy of the same fact).
- Avoids a JPMS-specific failure mode this library is exposed to more than most: a `MethodHandles.Lookup`-based
  upcall stub (`Logger`, `MergeOperator.Custom`, `UpcallRegistry`) only needs `MethodHandles.lookup()`
  from within its own class, which JPMS does not restrict — but split-package or
  service-loader-style extension points, if ever added, would need `opens`/`exports` reasoned
  about per module. Not having a module graph removes that whole category of question today.

### Negative

- No enforced encapsulation at the JAR boundary — a consumer on the classpath can call any
  `public` method regardless of intended internal-vs-external status. In practice this is already
  true today (unnamed modules have always worked this way), so nothing regresses; JPMS would have
  been a net-new guarantee, not a restored one.
- No official module name until/unless the `Automatic-Module-Name` follow-up lands, so a consumer
  who modularizes today gets the JAR-filename-derived automatic module name
  (`rocksdbffm.core` or similar, unstable across artifact renames) rather than a maintained one.

### Risks to manage

- If a future consumer files an issue asking specifically for module-path support, re-read this
  ADR before reaching for full `module-info.java` — the `Automatic-Module-Name` manifest entry
  described above solves the common case (stable name, `requires` works) without reopening the
  native-access and five-artifact questions this ADR chose to avoid.

## Alternatives considered

- **Full JPMS**: `module-info.java` in `core`, `exports io.github.dfa1.rocksdbffm`, and a name
  per `native/*` artifact. Rejected for the reasons above — no encapsulation gain over the
  existing package-private/sealed surface, and real complexity added to native-access grants and
  the native-artifact-selection story, for consumers who are not asking for it today.
- **`Automatic-Module-Name` only** (no `module-info.java`): the middle ground described in
  Decision as a deferred follow-up. Not adopted now because nothing currently depends on it;
  revisit if asked.
- **Do nothing, don't write it down**: rejected because "would JPMS make sense here" is exactly
  the kind of question that gets re-asked periodically without a record of the tradeoff already
  having been weighed.

## References

- `CLAUDE.md` — "Manual Memory Management & Lifecycle", "don't expose public constructors"
- `docs/reference.md` — "Source Map" (the `native/*` classifier matrix this ADR avoids duplicating
  in a module graph)
- `docs/adr/0001-ffm-instead-of-jni.md` — the FFM API this project builds on, and the
  native-access requirement that motivates most of this ADR's Negative/Risks sections
- `pom.xml`, `scripts/benchmark.sh` — current `--enable-native-access=ALL-UNNAMED` usage
