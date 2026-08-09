# Tutorial: your first RocksDB database

This tutorial walks you through a working Maven project that opens a RocksDB database, writes and
reads keys, batches writes atomically, and scans the keyspace with an iterator. Follow it top to
bottom; every step builds on the previous one.

**Prerequisites:** JDK 25+ (`java.lang.foreign` is required), Maven 3.9+.

When you are done, see [how-to.md](how-to.md) for task-oriented recipes,
[reference.md](reference.md) for the full API surface, and [explanation.md](explanation.md) for the
design rationale.

---

## 1. Create a Maven project

```bash
mvn archetype:generate \
  -DgroupId=com.example \
  -DartifactId=rocksdb-demo \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DarchetypeVersion=1.5 \
  -DinteractiveMode=false
cd rocksdb-demo
```

Set the compiler release to 25 in `pom.xml`:

```xml
<properties>
  <maven.compiler.release>25</maven.compiler.release>
</properties>
```

---

## 2. Add the dependencies

You need two things: `rocksdbffm-core` (pure Java, the API) and **one native artifact per platform
you want to run on**. The BOM manages the versions so you never repeat them.

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
  <!-- pick the classifier(s) matching the machines you deploy to -->
  <dependency>
    <groupId>io.github.dfa1</groupId>
    <artifactId>rocksdbffm-native-osx-aarch64</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

Adding more than one native artifact is fine and expected for cross-platform distribution: at
startup the library loads only the one matching the current OS and architecture and ignores the
rest. The full list of classifiers is in [reference.md#artifacts](reference.md#artifacts), and the
loading mechanism is explained in
[explanation.md#native-library-loading](explanation.md#native-library-loading).

---

## 3. Open a database and write your first key

Replace `src/main/java/com/example/App.java` with:

```java
package com.example;

import io.github.dfa1.rocksdbffm.RocksDB;

import java.nio.file.Path;

public class App {
	public static void main(String[] args) {
		Path dbPath = Path.of("target/demo-db");

		try (var db = RocksDB.open(dbPath)) {
			db.put("user:1".getBytes(), "alice".getBytes());

			byte[] value = db.get("user:1".getBytes());
			System.out.println(new String(value));   // alice

			db.delete("user:1".getBytes());
			System.out.println(db.get("user:1".getBytes()));   // null
		}
	}
}
```

Three things are happening here.

`RocksDB.open(Path)` creates the database if the directory does not exist. It returns a
`ReadWriteDB`, which is `AutoCloseable` — the try-with-resources block is what releases the native
handle. **Every** type in this library that owns native memory works that way; see
[explanation.md#lifecycle-and-ownership](explanation.md#lifecycle-and-ownership).

`get` returns `null` for a missing key — there is no `Optional` on the hot read path, and no
exception for a normal miss. Errors that are *not* misses (corrupt DB, I/O failure, closed handle)
throw `RocksDBException`, which is unchecked.

Keys and values are opaque bytes. `byte[]` is the convenience tier; there are faster `ByteBuffer`
and `MemorySegment` tiers you will meet in step 6.

---

## 4. Run it

```bash
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java --enable-native-access=ALL-UNNAMED -cp "target/classes:$(cat cp.txt)" com.example.App
```

`--enable-native-access=ALL-UNNAMED` suppresses the JDK's restricted-method warning. Without it the
program still runs, but every launch prints a warning to stderr.

You should see:

```
alice
null
```

---

## 5. Control how the database is opened

`RocksDB.open(Path)` is a shorthand for "create if missing". Anything beyond that goes through
`Options`, which is itself a native object and must be closed:

```java
try (var options = Options.newOptions()
		.setCreateIfMissing(true)
		.setCompression(CompressionType.ZSTD);
     var db = RocksDB.open(options, dbPath)) {
	db.put("user:1".getBytes(), "alice".getBytes());
}
```

Setters return `this`, so they chain. `Options` is only read during `open` — closing it afterwards
does not affect the open database.

Sizes are never raw `long`. A block cache of 64 MB is `MemorySize.ofMB(64)`, not `67108864`:

```java
try (var cache = LRUCache.newLRUCache(MemorySize.ofMB(64));
     var tableConfig = BlockBasedTableOptions.newBlockBasedConfig()
		     .setBlockCache(cache)
		     .setFilterPolicy(FilterPolicy.newBloom(10)); // 10 bits per key
     var options = Options.newOptions()
		     .setCreateIfMissing(true)
		     .setTableFormatConfig(tableConfig);
     var db = RocksDB.open(options, dbPath)) {
	// ...
}
```

`setFilterPolicy` **transfers ownership** of the filter policy to the table options, so the
policy's own `close()` becomes a no-op and there is no double free. Why the library models it that
way is covered in [explanation.md#lifecycle-and-ownership](explanation.md#lifecycle-and-ownership).

---

## 6. Write atomically, then scan

A `WriteBatch` collects operations and applies them in a single atomic write:

```java
try (var db = RocksDB.open(dbPath);
     var batch = WriteBatch.create()) {
	batch.put("user:1".getBytes(), "alice".getBytes());
	batch.put("user:2".getBytes(), "bob".getBytes());
	batch.put("user:3".getBytes(), "carol".getBytes());
	db.write(batch);           // all three, or none

	try (var it = db.newIterator()) {
		for (it.seekToFirst(); it.isValid(); it.next()) {
			System.out.println(new String(it.key()) + " = " + new String(it.value()));
		}
		it.checkError();       // surfaces an iteration failure as RocksDBException
	}
}
```

Iteration is ordered by key, so this prints `user:1`, `user:2`, `user:3`. Always call `checkError()`
after the loop: a loop simply stops when `isValid()` turns false, and that happens both at the
natural end of the keyspace *and* on an I/O error.

`it.key()` and `it.value()` allocate a fresh `byte[]` per call. The zero-copy tier avoids that:

```java
for (it.seekToFirst(); it.isValid(); it.next()) {
	MemorySegment key = it.keySegment();     // no copy — points into RocksDB's own memory
	long keyLength = key.byteSize();
}
```

The catch is lifetime: a segment returned by `keySegment()`/`valueSegment()` is valid **only until
the next positioning call** (`next`, `seek`, `prev`, …). Read it, or copy out of it, before moving
the iterator.

---

## 7. Reopen and confirm durability

Run the program a second time with the `put` calls removed. The values are still there — RocksDB
recovered them from the write-ahead log on open. To force the memtable to disk explicitly:

```java
try (var flushOptions = FlushOptions.newFlushOptions()) {
	db.flush(flushOptions);
}
```

---

## Where to go next

| You want to…                                                     | Read                                                                 |
|:-----------------------------------------------------------------|:---------------------------------------------------------------------|
| Solve a specific task (snapshots, backups, transactions, TTL, …) | [how-to.md](how-to.md)                                               |
| Look up a class, an option, or a supported feature                | [reference.md](reference.md)                                         |
| Understand why the API looks the way it does                      | [explanation.md](explanation.md)                                     |
| Compare performance against `rocksdbjni`                          | [benchmarks.md](benchmarks.md)                                       |
