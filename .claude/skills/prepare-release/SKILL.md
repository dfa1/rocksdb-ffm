---
name: prepare-release
description: Prepare and cut a rocksdb-ffm release - backfill CHANGELOG entries from merged PRs, finalize the version section, run pre-flight checks, run release:prepare, and verify the GitHub release. Use when asked to release, cut/prepare a version, or finalize the CHANGELOG.
disable-model-invocation: true
---

# Prepare a release

Contributors do not edit `CHANGELOG.md` (see AGENTS.md); the maintainer fills it here. Never push
or tag without explicit confirmation - the tag triggers the Maven Central deploy (irreversible).

## 1. Find what shipped

```bash
LAST=$(git tag --sort=-v:refname | head -1)
git fetch --tags origin && git status -sb          # must be on main, clean, up to date
gh pr list --state merged --base main --limit 100 --json number,title,author,mergedAt,body \
    --jq '.[] | "\(.number)\t\(.author.login)\t\(.title)"'
git log --oneline $LAST..HEAD
```

Keep only PRs merged after `$LAST` (check `mergedAt` against `git log -1 --format=%cI $LAST`).
Compare against `## [Unreleased]` in `CHANGELOG.md`: any PR with no entry gets one.

## 2. Backfill the entries

Read each missing PR's diff, not just its title. One line per user-visible change, under
`### Added` / `### Changed` / `### Fixed` (omit empty ones; skip pure CI/docs/dependabot unless notable):

- Link the PR as `([#NNN](https://github.com/dfa1/rocksdb-ffm/pull/NNN)`, plus `, closes [#MMM](.../issues/MMM)` when it closes one.
- Outside contributors (author is not `dfa1`) end with `— thanks [@handle](https://github.com/handle)`.
- Terse, one line. Match the existing entries' style.

## 3. Choose the version and finalize

Pre-1.0: new API -> next minor is fine; ask the user if unsure. Then:

- Rename `## [Unreleased]` to `## [x.y] — YYYY-MM-DD` (today) and add a one-sentence summary paragraph under it, as the previous sections do.
- Insert a fresh empty `## [Unreleased]` above it.
- Commit: `docs: finalize CHANGELOG for x.y` (this section is published verbatim as the GitHub release notes by `publish.yml`).

## 4. Pre-flight

```bash
java scripts/CheckEnums.java
java scripts/CheckPrototypes.java
./mvnw package -pl core -am -DskipTests       # Javadoc must have no ERROR lines
gh run list --branch main --limit 3           # latest CI must be green
```

Also update the released-version strings (PURL badge and `<version>` in `README.md`, `docs/tutorial.md`, the BOM line and
`pkg:maven` coordinate in `docs/reference.md`): `grep -rn "rocksdbffm\|<version>" README.md docs/*.md`. Ignore unrelated
versions such as Zig 0.17.
Stop and report on any failure.

## 5. Cut it (confirm first)

Show the user the finalized section and ask for the go-ahead, then:

```bash
./mvnw --batch-mode release:clean release:prepare \
    -DreleaseVersion=x.y -DdevelopmentVersion=<next>-SNAPSHOT
git push && git push --tags      # only after confirmation
```

## 6. Verify

```bash
gh run watch                                   # publish.yml: Central deploy + GitHub release
gh release view vX.Y                           # title "vX.Y — date", notes match the CHANGELOG section
```

Contributor mentions must appear as bare `@handle` in the notes. If the workflow fails, fix and
re-run it (it skips release creation if the release already exists); never move or delete the tag.
