# Security Policy

## Supported versions

rocksdbffm is pre-1.0: only the latest release gets security fixes. Fixes ship in a new release, not
as backports.

| Version        | Supported |
|:---------------|:----------|
| Latest release | Yes       |
| Older releases | No        |

The bundled RocksDB version is listed in the [CHANGELOG](CHANGELOG.md) and on the README badge. A
vulnerability in RocksDB itself belongs upstream at
[facebook/rocksdb](https://github.com/facebook/rocksdb/security); we pick up the fix by bumping the
`rocksdb/` submodule.

## Reporting a vulnerability

**Do not open a public issue.** Report it privately through GitHub:
[Security → Report a vulnerability](https://github.com/dfa1/rocksdb-ffm/security/advisories/new).

Please include:

- the rocksdbffm version, JDK version, and OS/arch (native classifier)
- what an attacker can do, and the minimal code or input that triggers it
- whether it is in this library's FFM plumbing or in RocksDB itself, if you know

This is a single-maintainer project, so responses are best effort: expect an acknowledgment within a
week. Once a fix is released, the advisory is published with credit to the reporter unless you ask
otherwise.
