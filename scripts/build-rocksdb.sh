#!/usr/bin/env bash
# Build RocksDB shared library using zig cc/c++ and install it into the
# caller's resources directory so Maven bundles it in the JAR.
#
# Supports cross-compilation: runs on any host but can produce a binary
# for any supported target by passing a TARGET_CLASSIFIER.
#
# Usage:
#   ./scripts/build-rocksdb.sh <output-resources-dir> <target-classifier>
#
# target-classifier: osx-aarch64 | osx-x86_64 | linux-x86_64 | linux-aarch64
#
# Example (Maven exec plugin):
#   ./scripts/build-rocksdb.sh /path/to/native/osx-aarch64/src/main/resources osx-aarch64
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

# ---------------------------------------------------------------------------
# Map classifier → (zig target triple, library name, RocksDB platform)
# ---------------------------------------------------------------------------
case "$CLASSIFIER" in
    osx-aarch64)
        ZIG_TARGET="aarch64-macos"
        LIB_NAME="librocksdb.dylib"
        TARGET_OS="Darwin"
        ;;
    osx-x86_64)
        ZIG_TARGET="x86_64-macos"
        LIB_NAME="librocksdb.dylib"
        TARGET_OS="Darwin"
        ;;
    linux-x86_64)
        ZIG_TARGET="x86_64-linux-gnu"
        LIB_NAME="librocksdb.so"
        TARGET_OS="Linux"
        ;;
    linux-aarch64)
        ZIG_TARGET="aarch64-linux-gnu"
        LIB_NAME="librocksdb.so"
        TARGET_OS="Linux"
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
    echo "[build-rocksdb] $DEST_DIR/$LIB_NAME already exists, skipping build."
    exit 0
fi

# ---------------------------------------------------------------------------
# Detect whether we are cross-compiling
# ---------------------------------------------------------------------------
HOST_OS=$(uname -s)
HOST_ARCH=$(uname -m)
case "$HOST_OS" in
    Darwin) HOST_OS_NAME="osx" ;;
    Linux) HOST_OS_NAME="linux" ;;
    MINGW* | MSYS* | CYGWIN*) HOST_OS_NAME="windows" ;;
    *) HOST_OS_NAME="unknown" ;;
esac
case "$HOST_ARCH" in
    arm64 | aarch64) HOST_ARCH_NAME="aarch64" ;;
    x86_64) HOST_ARCH_NAME="x86_64" ;;
    *) HOST_ARCH_NAME="unknown" ;;
esac
HOST_CLASSIFIER="${HOST_OS_NAME}-${HOST_ARCH_NAME}"

# RocksDB's POSIX Makefile has no Windows target at all (see
# build-rocksdb-windows.sh), so this script cannot build ANY classifier —
# not even a macOS/Linux one — from a native Windows host: RocksDB's own
# build_detect_platform relies on POSIX uname/shell semantics, and
# windows-latest CI runners additionally have no GNU Make on PATH. Skip
# cleanly rather than fail the whole `mvn verify`; other CI matrix legs
# (macOS/Linux hosts) still build and validate this classifier normally.
if [ "$HOST_OS_NAME" = "windows" ]; then
    echo "[build-rocksdb] Skipping $CLASSIFIER on a Windows host: RocksDB's POSIX Makefile has no Windows build path. Use build-rocksdb-windows.sh for windows-* classifiers." >&2
    exit 0
fi

CROSS=""
if [ "$CLASSIFIER" != "$HOST_CLASSIFIER" ]; then
    CROSS=" (cross from $HOST_CLASSIFIER)"
fi

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
echo "[build-rocksdb] Building RocksDB $CLASSIFIER$CROSS with zig cc/c++ (jobs=$JOBS)..."

export CC="zig cc -target $ZIG_TARGET"
export CXX="zig c++ -target $ZIG_TARGET"
export PORTABLE=1
# TODO: to have hermetic zig build, disable external libs for now
export ROCKSDB_DISABLE_SNAPPY=1
export ROCKSDB_DISABLE_BZ2=1
export ROCKSDB_DISABLE_ZLIB=1
export TARGET_OS=$TARGET_OS
cd "$ROCKSDB_DIR"

# zig cc/c++ treats some warnings as errors that RocksDB's own build does not
# expect (e.g. -Wunused-parameter in util/compression.cc). Suppress them for
# all builds so the Makefile does not abort on RocksDB's own code.
EXTRA_FLAGS="-Wno-error"

# Cross-compilation: existing .o files and make_config.mk are for the host
# architecture. Remove them so RocksDB's build_detect_platform regenerates
# the config and Make recompiles everything with the cross target.
rm -f make_config.mk
make clean -j"$JOBS" 2>/dev/null || true

# ---------------------------------------------------------------------------
# ZSTD/LZ4: build hermetically instead of relying on host-installed libs.
#
# RocksDB's own Makefile already knows how to fetch pinned,
# checksum-verified source tarballs for these and build each as a static
# archive (`libzstd.a`, `liblz4.a`) — the same recipe upstream's
# rocksdbjavastatic pipeline uses to ship codec libraries with no host
# dependency in the published JNI jars. Reuse those targets with our zig
# cross-compiler, then point the shared_lib build's -I/-L at the results so
# build_detect_platform's probe finds them and statically links them in.
#
# (zig cc's linker has no dynamic-library search fallback the way host cc
# does via LIBRARY_PATH, so the plain `-lzstd`/`-llz4` probes against a
# brew/apt-installed copy would silently fail to find it anyway — this step
# is a genuine capability, not a behavior change for a case that used to
# work.)
#
# ALLOW_BUILD_PARAMETER_CHANGE=1: `make libzstd.a liblz4.a` below runs with a
# bare CC/CXX, while the `make shared_lib` after it adds -I/-L for the
# archives just built — different strings trip RocksDB's
# build-parameter-signature guard even though these targets never touch
# RocksDB's object dir. The `make clean` above already guarantees a clean
# tree, so the guard has nothing to protect here.
export ALLOW_BUILD_PARAMETER_CHANGE=1
make libzstd.a liblz4.a -j"$JOBS"
ZSTD_SRC_DIR="$(ls -d zstd-*/ | head -1)"
LZ4_SRC_DIR="$(ls -d lz4-*/ | head -1)"

export CC="zig cc -target $ZIG_TARGET -I$ROCKSDB_DIR/${ZSTD_SRC_DIR}lib -I$ROCKSDB_DIR/${LZ4_SRC_DIR}lib -L$ROCKSDB_DIR"
export CXX="zig c++ -target $ZIG_TARGET -I$ROCKSDB_DIR/${ZSTD_SRC_DIR}lib -I$ROCKSDB_DIR/${LZ4_SRC_DIR}lib -L$ROCKSDB_DIR"

make shared_lib EXTRA_LDFLAGS="-s" EXTRA_CXXFLAGS="$EXTRA_FLAGS" EXTRA_CFLAGS="$EXTRA_FLAGS" -j"$JOBS"

# ---------------------------------------------------------------------------
# Install
# ---------------------------------------------------------------------------
cp "$ROCKSDB_DIR/$LIB_NAME" "$DEST_DIR/$LIB_NAME"
echo "[build-rocksdb] Installed: $DEST_DIR/$LIB_NAME"
