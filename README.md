# RocksDB FFM

[![Maven Central](https://img.shields.io/maven-central/v/io.github.dfa1/rocksdbffm-core.svg)](https://central.sonatype.com/artifact/io.github.dfa1/rocksdbffm-core)
![PURL](https://img.shields.io/badge/purl-pkg%3Amaven%2Fio.github.dfa1%2Frocksdbffm--core%400.6-blue)
![RocksDB](https://img.shields.io/badge/RocksDB-11.8.1-green.svg)
![MacOS](https://img.shields.io/badge/macOS-fully_supported-green.svg)
![Linux](https://img.shields.io/badge/linux-fully_supported-green.svg)
![Linux aarch64](https://img.shields.io/badge/linux_aarch64-fully_supported-green.svg)
![Windows](https://img.shields.io/badge/windows-fully_supported-green.svg)
![Windows aarch64](https://img.shields.io/badge/windows_aarch64-fully_supported-green.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/dfa1/rocksdbffm/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/dfa1/rocksdbffm/actions/workflows/ci.yml?query=branch%3Amain)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dfa1_rocksdbffm&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=dfa1_rocksdbffm)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=dfa1_rocksdbffm&metric=coverage)](https://sonarcloud.io/summary/new_code?id=dfa1_rocksdbffm)

**rocksdbffm** is an experimental Java wrapper for [RocksDB](https://rocksdb.org/) built on the
**Foreign Function & Memory (FFM) API**, targeting JDK 25+.

It aims to be a more maintainable alternative to the JNI-based `rocksdbjni`: mappings are plain Java
against `rocksdb/c.h`, so new RocksDB features need no C++ glue. Reads are roughly **2× faster** than
JNI — see [docs/benchmarks.md](docs/benchmarks.md) for the numbers and their caveats, and
[docs/explanation.md](docs/explanation.md) for why.

> **AI-assisted development:** This project uses [Claude Code](https://claude.ai/code) heavily for
> implementation work — C header mapping, test generation, and documentation. **Architecture, API
> design, and all decisions are human-driven.**

## Quickstart

Import the BOM, then depend on `rocksdbffm-core` plus one native artifact per platform you ship to
(full classifier list in [docs/reference.md#artifacts](docs/reference.md#artifacts)):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.dfa1</groupId>
      <artifactId>rocksdbffm-bom</artifactId>
      <version>0.6</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.github.dfa1</groupId>
    <artifactId>rocksdbffm-core</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.dfa1</groupId>
    <artifactId>rocksdbffm-native-osx-aarch64</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

```java
try (var db = RocksDB.open(Path.of("/tmp/demo-db"))) {
    db.put("user:1".getBytes(), "alice".getBytes());
    byte[] value = db.get("user:1".getBytes());   // null if absent
    db.delete("user:1".getBytes());
}
```

Run with `--enable-native-access=ALL-UNNAMED`. Step-by-step setup — including batches, iterators, and
options — is in the [tutorial](docs/tutorial.md).

## Documentation

Docs follow the [Diátaxis](https://diataxis.fr/) framework.

| Document                                       | Mode        | Contents                                                                        |
|:-----------------------------------------------|:------------|:---------------------------------------------------------------------------------|
| [docs/tutorial.md](docs/tutorial.md)           | Tutorial    | Start to finish: project setup, open a DB, put/get/delete, batch, iterate        |
| [docs/how-to.md](docs/how-to.md)               | How-to      | Recipes: column families, snapshots, transactions, backups, TTL, WAL tailing, …  |
| [docs/reference.md](docs/reference.md)         | Reference   | Artifacts, API surface by area, options, enums, feature status                   |
| [docs/explanation.md](docs/explanation.md)     | Explanation | Why FFM over JNI, ownership model, domain types, native library loading          |
| [docs/benchmarks.md](docs/benchmarks.md)       | Explanation | FFM vs JNI throughput, methodology, how to reproduce                             |
| [docs/c-api-gaps.md](docs/c-api-gaps.md)       | Reference   | What `rocksdb/c.h` exposes but is unwrapped, and what needs an upstream PR       |

## Contributing

**Requirements:** JDK 25+, [Zig](https://ziglang.org/) 0.15.x, and — for the Windows native builds
only — [CMake](https://cmake.org/) plus `make` or [Ninja](https://ninja-build.org/).

```bash
git submodule update --init --recursive     # clone the rocksdb submodule (first time)
./mvnw generate-resources -Pnative-build    # build the native library (first time or after clean)
./mvnw test
```

Never run `./mvnw install` — it pollutes `~/.m2` with local artifacts. Use `compile`, `test`, or
`package`.

## Releasing

```bash
./mvnw --batch-mode release:clean release:prepare
git push && git push --tags
```

GitHub Actions picks up the tag and deploys to Maven Central.

## License

Licensed under the same terms as RocksDB (LevelDB/Apache 2.0).

## See also

- [Expanding RocksDB's Java FFI](https://rocksdb.org/blog/2024/02/20/foreign-function-interface.html)
- [Rocksjava: present and future](https://evolvedbinary.slides.com/adamretter/rocksjava-present-and-future#/1)
