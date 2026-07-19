#!/usr/bin/env bash
# Build RocksDB shared library for Windows using CMake + zig cc/c++ and
# install it into the caller's resources directory so Maven bundles it in
# the JAR.
#
# RocksDB's POSIX Makefile (used by build-rocksdb.sh) has no Windows target,
# so Windows goes through RocksDB's CMake build instead, with zig cc/c++
# acting as a MinGW-w64-compatible cross compiler (target triple
# <arch>-windows-gnu). CMake requires CC/CXX/AR/RANLIB to be single
# executables (unlike `make`, which accepts `CC="zig cc -target ..."`
# directly); see the comments below for how each is resolved differently on
# a POSIX vs. a native Windows host.
#
# Usage:
#   ./scripts/build-rocksdb-windows.sh <output-resources-dir> <target-classifier>
#
# target-classifier: windows-x86_64 | windows-aarch64
#
# Example (Maven exec plugin):
#   ./scripts/build-rocksdb-windows.sh /path/to/native/windows-x86_64/src/main/resources windows-x86_64
set -euo pipefail

if [ $# -lt 2 ]; then
    echo "Usage: $0 <output-resources-dir> <target-classifier>" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"   # multi-module root
ROCKSDB_DIR="$PROJECT_DIR/rocksdb"
# Resolve to absolute path before any cd changes the working directory
mkdir -p "$1"
OUTPUT_RESOURCES="$(cd "$1" && pwd)"
CLASSIFIER="$2"
JOBS="${ROCKSDB_BUILD_JOBS:-$(sysctl -n hw.logicalcpu 2>/dev/null || nproc 2>/dev/null || echo "${NUMBER_OF_PROCESSORS:-4}")}"
LIB_NAME="librocksdb.dll"

# ---------------------------------------------------------------------------
# Map classifier → (zig target triple, CMake system processor)
# ---------------------------------------------------------------------------
case "$CLASSIFIER" in
    windows-x86_64)
        ZIG_TARGET="x86_64-windows-gnu"
        CMAKE_PROCESSOR="AMD64"
        ;;
    windows-aarch64)
        ZIG_TARGET="aarch64-windows-gnu"
        CMAKE_PROCESSOR="ARM64"
        ;;
    *)
        echo "Unsupported classifier: $CLASSIFIER" >&2
        exit 1
        ;;
esac

DEST_DIR="$OUTPUT_RESOURCES/native/$CLASSIFIER"
mkdir -p "$DEST_DIR"

# Skip if already built (CI cache or repeated local builds)
if [ -f "$DEST_DIR/$LIB_NAME" ]; then
    echo "[build-rocksdb-windows] $DEST_DIR/$LIB_NAME already exists, skipping build."
    exit 0
fi

echo "[build-rocksdb-windows] Building RocksDB $CLASSIFIER with CMake + zig cc/c++ (jobs=$JOBS)..."

# ---------------------------------------------------------------------------
# zig invokes its C/C++ compiler as a subcommand ("zig cc"/"zig c++"), but
# CMAKE_C_COMPILER/CMAKE_CXX_COMPILER must each be a single executable with
# no baked-in subcommand.
#
# When this script itself runs on a native Windows host (e.g. building
# windows-x86_64 on a windows-latest runner), avoid a wrapper *script*
# entirely for CC/CXX: a batch-file wrapper hits a longstanding cmd.exe
# quoting bug where `cmd /C` mis-parses a command line with more than one
# quoted segment, silently dropping the opening quote around the .bat path
# and gluing it to the next argument ("cxx.bat" "-DNOMINMAX" ... becomes one
# unrecognized token) — reproduced in CI regardless of whether the
# underlying generator is Ninja or GNU Make, so it's cmd.exe itself, not the
# build tool. CMake's CMAKE_<LANG>_COMPILER_ARG1 (originally meant for
# ccache/distcc-style wrapping) sidesteps this: it inserts a fixed first
# argument after the compiler on every invocation, so CMAKE_C_COMPILER can
# point straight at the real zig(.exe) binary with no intermediary script
# for CreateProcess to launch. Off Windows, keep the proven POSIX
# shell-script wrapper (unaffected by any of this).
# ---------------------------------------------------------------------------
IS_WINDOWS_HOST=false
case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) IS_WINDOWS_HOST=true ;;
esac

CMAKE_COMPILER_ARGS=()
if [ "$IS_WINDOWS_HOST" = true ]; then
    ZIG_EXE="$(command -v zig)"
    # Git Bash's `command -v` resolves zig.exe but reports the path without
    # the extension; CMake checks for a literal existing file at the given
    # path (no PATHEXT-style resolution the way a shell does), so append it
    # back when present.
    if [ -f "${ZIG_EXE}.exe" ]; then
        ZIG_EXE="${ZIG_EXE}.exe"
    fi
    CC_COMPILER="$ZIG_EXE"
    CXX_COMPILER="$ZIG_EXE"
    CMAKE_COMPILER_ARGS=(
        -DCMAKE_C_COMPILER_ARG1="cc -target $ZIG_TARGET"
        -DCMAKE_CXX_COMPILER_ARG1="c++ -target $ZIG_TARGET"
    )
    # AR *is* invoked even for the shared-lib target: CMake's Makefile
    # generator bundles objects into an intermediate CMakeFiles/.../objects.a
    # via `ar qc ... @objects1.rsp`, then links the shared lib with
    # `-Wl,--whole-archive objects.a`. There's no CMAKE_AR_ARG1 equivalent to
    # point straight at zig(.exe) the way CC/CXX do, and a .bat wrapper hits
    # the same cmd.exe quoting problem CC/CXX had (confirmed in CI — the
    # ar.bat invocation failed with "The system cannot find the path
    # specified"). So compile a tiny native .exe stub instead: `_execvp`
    # (from the C runtime, not a hand-rolled batch script) constructs the
    # Windows command line using the same well-tested quoting logic every
    # native compiler/linker already relies on.
    #
    # TODO(cleanup): compiling a C stub from a shell script is more machinery
    # than this really deserves. Revisit if CMake ever grows a CMAKE_AR_ARG1
    # (or equivalent) for archivers, or if zig starts shipping standalone
    # llvm-ar/llvm-ranlib binaries alongside zig(.exe) that could be pointed
    # at directly with no wrapper at all.
    # $ZIG_EXE is in Git Bash's MSYS path form (e.g. /c/hostedtoolcache/...).
    # Bash auto-translates that to a native Windows path when it's passed as
    # a *command-line argument* to a native executable (which is why
    # CMAKE_C_COMPILER="$ZIG_EXE" above works fine) — but here it gets
    # embedded as a plain string literal inside a generated C *source file*,
    # which isn't argv, so that translation never happens. Confirmed in CI:
    # the compiled wrapper's own _spawnv failed with ENOENT on the raw MSYS
    # path baked into the binary. cygpath -m converts to the Windows-native
    # "mixed" form (drive letter, forward slashes) — safe to drop straight
    # into a C string with no backslash-escaping needed.
    ZIG_EXE_WIN="$(cygpath -m "$ZIG_EXE")"
    WRAPPER_DIR="$(mktemp -d)"
    trap 'rm -rf "$WRAPPER_DIR"' EXIT
    AR_WRAPPER="$WRAPPER_DIR/ar.exe"
    RANLIB_WRAPPER="$WRAPPER_DIR/ranlib.exe"
    for pair in "ar:$AR_WRAPPER" "ranlib:$RANLIB_WRAPPER"; do
        subcommand="${pair%%:*}"
        out="${pair#*:}"
        src="$WRAPPER_DIR/${subcommand}_wrapper.c"
        # Use _spawnv (not _execv): CI showed the ar invocation failing
        # completely silently — no output at all, not even a zig error —
        # which is consistent with the process-replace call itself never
        # succeeding, and _execv gives no way to see why since it doesn't
        # return on success and this code printed nothing on failure
        # either. _spawnv (wait for the child, keep running) lets the
        # wrapper report exactly what went wrong instead of a bare exit
        # code with zero diagnostic output.
        cat > "$src" <<EOF
#include <process.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
int main(int argc, char** argv) {
    char** newargv = (char**)malloc(sizeof(char*) * (size_t)(argc + 2));
    newargv[0] = "$ZIG_EXE_WIN";
    newargv[1] = "$subcommand";
    for (int i = 1; i < argc; i++) newargv[i + 1] = argv[i];
    newargv[argc + 1] = NULL;
    intptr_t status = _spawnv(_P_WAIT, "$ZIG_EXE_WIN", (const char* const*)newargv);
    if (status == -1) {
        fprintf(stderr, "$subcommand wrapper: _spawnv failed for $ZIG_EXE_WIN: %s (errno=%d)\n", strerror(errno), errno);
        return 1;
    }
    return (int)status;
}
EOF
        "$ZIG_EXE" cc -o "$out" "$src"
    done
else
    WRAPPER_DIR="$(mktemp -d)"
    trap 'rm -rf "$WRAPPER_DIR"' EXIT
    CC_COMPILER="$WRAPPER_DIR/cc"
    CXX_COMPILER="$WRAPPER_DIR/cxx"
    AR_WRAPPER="$WRAPPER_DIR/ar"
    RANLIB_WRAPPER="$WRAPPER_DIR/ranlib"
    cat > "$CC_COMPILER" <<EOF
#!/usr/bin/env bash
exec zig cc -target $ZIG_TARGET "\$@"
EOF
    cat > "$CXX_COMPILER" <<EOF
#!/usr/bin/env bash
exec zig c++ -target $ZIG_TARGET "\$@"
EOF
    cat > "$AR_WRAPPER" <<'EOF'
#!/usr/bin/env bash
exec zig ar "$@"
EOF
    cat > "$RANLIB_WRAPPER" <<'EOF'
#!/usr/bin/env bash
exec zig ranlib "$@"
EOF
    chmod +x "$CC_COMPILER" "$CXX_COMPILER" "$AR_WRAPPER" "$RANLIB_WRAPPER"
fi

# ---------------------------------------------------------------------------
# Pick a CMake generator the host actually has. GitHub's windows-latest
# runner has Ninja preinstalled; macOS/Linux runners have `make` via their
# standard toolchain. Prefer make where available since it's already the
# tool build-rocksdb.sh relies on, and fall back to Ninja everywhere else.
# ---------------------------------------------------------------------------
if command -v make >/dev/null 2>&1; then
    CMAKE_GENERATOR="Unix Makefiles"
elif command -v ninja >/dev/null 2>&1; then
    CMAKE_GENERATOR="Ninja"
else
    echo "Neither 'make' nor 'ninja' found on PATH; cannot configure the CMake build." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Configure + build. Third-party compression libs are disabled, matching
# build-rocksdb.sh, to keep the cross-compile hermetic. Tests/tools are
# disabled since only the shared lib is needed. FAIL_ON_WARNINGS is off
# because zig/MinGW headers trigger warnings RocksDB's own CMake build does
# not expect.
# ---------------------------------------------------------------------------
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$WRAPPER_DIR" "$BUILD_DIR"' EXIT

cmake -S "$ROCKSDB_DIR" -B "$BUILD_DIR" -G "$CMAKE_GENERATOR" \
    -DCMAKE_SYSTEM_NAME=Windows \
    -DCMAKE_SYSTEM_PROCESSOR="$CMAKE_PROCESSOR" \
    -DCMAKE_C_COMPILER="$CC_COMPILER" \
    -DCMAKE_CXX_COMPILER="$CXX_COMPILER" \
    "${CMAKE_COMPILER_ARGS[@]+"${CMAKE_COMPILER_ARGS[@]}"}" \
    -DCMAKE_AR="$AR_WRAPPER" \
    -DCMAKE_RANLIB="$RANLIB_WRAPPER" \
    -DCMAKE_BUILD_TYPE=Release \
    -DPORTABLE=1 \
    -DROCKSDB_BUILD_SHARED=ON \
    -DWITH_SNAPPY=OFF -DWITH_LZ4=OFF -DWITH_ZLIB=OFF -DWITH_ZSTD=OFF -DWITH_LIBURING=OFF \
    -DWITH_TESTS=OFF -DWITH_TOOLS=OFF -DWITH_BENCHMARK_TOOLS=OFF -DWITH_CORE_TOOLS=OFF -DWITH_TRACE_TOOLS=OFF \
    -DFAIL_ON_WARNINGS=OFF

cmake --build "$BUILD_DIR" --target rocksdb-shared -j"$JOBS"

# ---------------------------------------------------------------------------
# Install. CMake's default Windows/GNU naming is "librocksdb-shared.dll"
# (RocksDB's OUTPUT_NAME override to "rocksdb.dll" only applies when
# NOT WIN32), so rename it to match the librocksdb.<ext> convention used by
# every other classifier.
# ---------------------------------------------------------------------------
cp "$BUILD_DIR/librocksdb-shared.dll" "$DEST_DIR/$LIB_NAME"
echo "[build-rocksdb-windows] Installed: $DEST_DIR/$LIB_NAME"
