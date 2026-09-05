# ADR 0008: Linking `ldb`/`sst_dump` dynamically against a zig-built `librocksdb.so`

- **Status:** Accepted
- **Date:** 2026-08-30
- **Deciders:** project maintainer
- **Supersedes:** —
- **Superseded by:** —

## Context

`tools/ldb`/`tools/sst-dump` wrap RocksDB's own admin/inspection CLIs (`tools/ldb.cc`,
`tools/sst_dump.cc`) — not part of `rocksdb/c.h`, so there is no FFM path for them; the only option
is to bundle and shell out to the prebuilt binaries, the same way `NativeLibrary`/`NativeTool`
already extract `librocksdb.*` from classpath resources at runtime.

`scripts/build-rocksdb.sh` builds these with `zig cc`/`zig c++` (see [ADR 0002](0002-why-zig.md)),
the same hermetic cross-compiler used for `librocksdb.*` itself. `LIB_MODE=shared` was the first
approach tried: link `ldb`/`sst_dump` against the `librocksdb.so`/`.dylib` already being built,
instead of relinking the whole (static) library into each binary — ~300KB/binary plus one shared
`librocksdb_tools.*`, versus ~15MB apiece with RocksDB's own default static link.

That shipped, then broke CI on `ubuntu-latest` and `ubuntu-24.04-arm`: every `LdbToolTest` case
returned a non-zero, non-launch-failure exit code. Reproduced locally by cross-compiling
`linux-aarch64` and running it natively in a Lima VM (an arm64 Mac host needs no emulation for that
classifier) — `ldb --help` aborted immediately with `double free or corruption (out)`, confirmed via
`gdb` against a core dump to be inside libc++'s `basic_ifstream` destructor, reached from the very
first `Options` constructed in `main` (`rocksdb::port::GenerateRfcUuid`, called while building the
default `HyperClockCache`).

Root cause: zig c++ statically bundles its own libc++/libc++abi into **every** C++ link output —
executable or shared library alike (confirmed via `-v`: `ld.lld ... libc++abi.a libc++.a ...` appears
on every invocation). So `librocksdb.so` and `ldb`/`sst_dump` each ended up with an independent copy
of libc++'s vague-linkage internals (default-visibility, weak symbols — e.g. `basic_ifstream`'s
constructor). Without linker guidance, ELF's normal symbol-interposition rules let the loading
executable's copy of those symbols preempt the shared library's own calls to them at runtime, so
`librocksdb.so` ended up half-bound to a *different*, independently-constructed libc++ instance than
the one it was compiled against — a `std::ifstream` constructed under one copy and destroyed under
the other, corrupting the heap. This never surfaced before `ldb`/`sst_dump` existed because
`librocksdb.so` was previously only ever `dlopen`'d by the JVM, which has no libc++ of its own to
interpose against.

The first fix attempt dropped `LIB_MODE=shared` for `ldb`/`sst_dump` entirely, falling back to
RocksDB's own default static link (~15-16MB/binary). That closed the crash but was rejected: a ~50x
binary-size regression is too high a price for what is, underneath, a linker configuration problem —
not a reason dynamic linking is fundamentally unsafe here.

## Decision

Keep `LIB_MODE=shared` for both `librocksdb.so` and `ldb`/`sst_dump`, and add `-Wl,-Bsymbolic` to the
`EXTRA_LDFLAGS` of both the `shared_lib` and `ldb`/`sst_dump` `make` invocations in
`scripts/build-rocksdb.sh`. `-Bsymbolic` forces `librocksdb.so` to always bind its own internal calls
to its own embedded libc++ copy, never the loading executable's — closing the interposition window
without touching a single line of RocksDB or zig source.

Two other flags were tried and rejected before landing on this one: zig's `-Wl,` passthrough is an
allowlist, not a raw forward to the linker, and rejects `-Bsymbolic-functions`, `--exclude-libs=ALL`,
and `--dynamic-list` as "unsupported linker arg." Plain `-Bsymbolic` and `--version-script=` both pass
the allowlist; `-Bsymbolic` was chosen because it needs no companion version-script file and no
RocksDB source changes.

Validated on a from-scratch cross-compile of `linux-aarch64`, run natively in the same Lima VM used
for the original repro:

- `ldb --help` / `sst_dump --help`: 20x each, 0 failures, exit 0 every time.
- A full functional cycle — `ldb put`/`get`/`compact` (forcing a real flush to an `.sst` file), then
  `sst_dump --command=scan` on that file — succeeded with correct output. This exercises the exact
  `Options`-construction/`GenerateRfcUuid` path the original crash was in, not just `--help`.
- The full core test suite (994 tests) plus `LdbToolTest`/`SstDumpToolTest` on macOS.
- Confirmed again in real CI on the actual `ubuntu-latest` and `ubuntu-24.04-arm` runners (not just
  the local VM) once merged — both green.

## Consequences

### Positive

- `ldb`/`sst_dump` stay at ~195-306KB/binary (plus one shared `librocksdb_tools.*`), not ~15MB — the
  size profile the feature originally shipped with.
- No RocksDB source patches, no companion shared `libc++.so` to build and bundle ourselves, no
  `zig cc` version-tied hacks (e.g. reaching into `~/.cache/zig`'s internal, unversioned static
  archive cache, which was considered and explicitly avoided — see Alternatives). `-Wl,-Bsymbolic` is
  a standard, documented lld/GNU-ld feature that should hold across zig upgrades.
- The fix generalizes: any *future* binary that links against `librocksdb.so` in the same process
  (another CLI tool, a test harness) inherits the same protection for free, since the flag lives on
  `librocksdb.so`'s own link step.

### Negative

- Restores the SONAME/versioned-symlink plumbing the static-link attempt had removed:
  `scripts/build-rocksdb.sh` computes `librocksdb.so.$MAJOR.$MINOR` from
  `include/rocksdb/version.h` and ships it as a `librocksdb.soname` text file;
  `NativeTool`/`NativeLibrary` read that file at extraction time to symlink it next to the
  extracted library and set `DYLD_LIBRARY_PATH`/`LD_LIBRARY_PATH`. More moving parts than the (now
  rejected) static build's "just copy the binary" simplicity.
- `-Bsymbolic` does not hide `librocksdb.so`'s libc++ symbols from its dynamic symbol table — it only
  changes runtime *binding preference*. A future binary that itself statically embeds libc++ and is
  loaded into the *same* process as `librocksdb.so` (not just spawned as a separate process, the way
  `ldb`/`sst_dump` are today) would need the same analysis repeated; this ADR's fix is specific to the
  "shared lib depended on by a separate executable" shape, not a general guarantee.

### Risks to manage

- **`linux-x86_64` was validated by build only, not by execution.** The Lima VM available for testing
  is aarch64 (matching the arm64 Mac host, no emulation needed); `linux-x86_64` cross-compiles cleanly
  with the same flags but has not been run. The interposition mechanism is architecture-independent
  pure ELF/`ld.lld` semantics — nothing in the theory or the repro is aarch64-specific — but that is
  inference, not a measurement, until real CI runs on `ubuntu-latest`/`x86_64` confirm it (which this
  ADR's Decision section's "confirmed again in real CI" bullet does cover, since GitHub's
  `ubuntu-latest` runners are x86_64).
- **macOS was never affected by this bug** (Mach-O's two-level namespace symbol resolution behaves
  differently from ELF's flat interposition) and was not re-validated for the *specific* interposition
  failure mode — only that the build and existing tests still pass, which they did before this fix
  too.

## Windows

`windows-*` classifiers do not go through this decision at all. `scripts/build-rocksdb-windows.sh`
builds `ldb`/`sst_dump` via RocksDB's CMake build (see [ADR 0002](0002-why-zig.md) for why CMake
instead of the POSIX Makefile on this platform), and `rocksdb/CMakeLists.txt`'s own
`if(ROCKSDB_BUILD_SHARED AND NOT WIN32)` only ever selects the shared library as the tools' link
target off Windows — on Windows it always falls back to the static library, with no CMake option to
override that short of patching the vendored `CMakeLists.txt` (out of scope; the submodule is pinned,
not ours to edit). So `ldb.exe`/`sst_dump.exe` end up self-contained and statically linked
unconditionally, the same shape the static-linking alternative above was rejected for on POSIX — but
here it isn't a choice this project made, it's upstream CMake's own behavior, and there is no libc++
double-copy risk to begin with since there's only ever one linked copy. Accepted as a Windows-specific
tradeoff: larger binaries, but no SONAME/symlink/`LD_LIBRARY_PATH`-equivalent plumbing needed either.

## Alternatives considered

- **Static linking (RocksDB's own `LIB_MODE` default).** Shipped first, rejected: ~50x binary size
  regression (~15MB vs ~300KB per binary) to work around what turned out to be a one-flag linker
  problem, not a reason dynamic linking is fundamentally unsound with this toolchain.
- **A single shared `libc++.so`, Android-NDK style.** Build one shared libc++/libc++abi from zig's
  bundled sources and link *both* `librocksdb.so` and `ldb`/`sst_dump` against it dynamically (the
  technique Android NDK's `libc++_shared.so` uses for the identical multi-`.so`-in-one-process ODR
  problem). Not pursued once `-Bsymbolic` validated clean: it would need either building libc++ from
  zig's bundled source via a `zig c++ -shared` invocation (unconfirmed as directly supported) or
  extracting zig's internal, content-hash-keyed static archive cache under `~/.cache/zig/o/*` — the
  latter explicitly rejected as fragile and version-tied to zig's internal cache layout, not a
  documented public interface. Worth revisiting only if `-Bsymbolic` is ever found insufficient for a
  future in-process linking shape (see Consequences → Negative).
- **`-Wl,-Bsymbolic-functions` / `--exclude-libs=ALL` / `--dynamic-list`.** All standard lld/GNU-ld
  mechanisms for the same class of problem, all rejected outright by zig's `-Wl,` argument allowlist
  ("unsupported linker arg") before they could even be tested for effectiveness.

## References

- `scripts/build-rocksdb.sh` — the `-Wl,-Bsymbolic` flag on both `make shared_lib` and
  `make ldb sst_dump` invocations
- [ADR 0002](0002-why-zig.md) — why `zig cc`/`zig c++` is the project's cross-compiler, and the
  hermeticity property (no system sysroot) that ruled out relying on a system libc++
- `core/.../NativeTool.java`, `core/.../NativeLibrary.java` — the SONAME-symlink extraction logic
  this decision depends on (POSIX only; `NativeTool` skips it on Windows, see Windows section above)
- `scripts/build-rocksdb-windows.sh` — the Windows CMake build (`WITH_CORE_TOOLS`, `USE_RTTI`)
- [`-Bsymbolic`](https://sourceware.org/binutils/docs/ld/Options.html) — the lld/GNU-ld linker option
  this decision relies on
