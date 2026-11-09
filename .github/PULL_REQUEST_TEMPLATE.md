## What and why

<!-- What this changes and why. "Closes #NNN" if it fixes an issue. -->

## Checklist

See [CONTRIBUTING.md](https://github.com/dfa1/rocksdb-ffm/blob/main/CONTRIBUTING.md) for details.

- [ ] Tests use JUnit 5 + AssertJ with `@TempDir`, and every test follows `// Given` / `// When` / `// Then`
- [ ] `./mvnw test` passes
- [ ] `./mvnw package -pl core -am -DskipTests` passes (Javadoc fails the build on any error or warning)
- [ ] New native bindings: `private static final` `MH_` fields, each with its ``/// `<c prototype>` `` comment from `c.h`
- [ ] `CHANGELOG.md` has a line under `## [Unreleased]` linking this PR (and the issue, if any), ending with `— thanks [@your-handle](https://github.com/your-handle)` if you're an external contributor
- [ ] Docs updated in the matching [Diátaxis](https://diataxis.fr/) page (tutorial / how-to / reference / explanation), snippets compile against `core`
