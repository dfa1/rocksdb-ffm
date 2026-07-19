// Dependencies (io.github.dfa1:rocksdbffm-core and the per-arch native jar)
// are resolved by ../pom.xml from -Drocksdbffm.version/-Drocksdbffm.classifier,
// so the workflow can pin the released version and the matrix's native jar.

import io.github.dfa1.rocksdbffm.RocksDB;
import io.github.dfa1.rocksdbffm.RocksIterator;
import io.github.dfa1.rocksdbffm.Snapshot;
import io.github.dfa1.rocksdbffm.WriteBatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Smoke test for a published rocksdbffm release: proves the bundled native
/// library loads and links correctly on the host OS/arch, and that off-heap
/// `MemorySegment` interop (zero-copy put/get) and iterator/snapshot native
/// struct handling work there.
/// Run against a release from Maven Central, one arch per CI matrix leg, via
/// `mvn test` — a JUnit failure here fails just that one check, so a single
/// broken symbol doesn't hide every other result behind it.
///
/// This deliberately does *not* re-derive full API correctness — that's the
/// job of the module's JUnit suite, which runs once per PR and fails with a
/// precise, single-platform stack trace. A check only belongs here if a local
/// build passing it would NOT prove the released native artifact behaves the
/// same way on this specific platform (link failure, ABI mismatch, native
/// error-code marshaling).
class SmokeTest {

	private static final String PLATFORM = System.getProperty("os.name") + "/" + System.getProperty("os.arch");

	@Test
	void byteArrayRoundTrip(@TempDir Path dir) {
		try (var db = RocksDB.open(dir)) {
			db.put("key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8));

			byte[] value = db.get("key".getBytes(StandardCharsets.UTF_8));
			assertEquals("value", new String(value, StandardCharsets.UTF_8), "byte[] get() mismatch on " + PLATFORM);

			db.delete("key".getBytes(StandardCharsets.UTF_8));
			assertNull(db.get("key".getBytes(StandardCharsets.UTF_8)), "key not removed on " + PLATFORM);
		}
	}

	@Test
	void byteBufferRoundTrip(@TempDir Path dir) {
		try (var db = RocksDB.open(dir)) {
			ByteBuffer key = ByteBuffer.allocateDirect(3);
			key.put("bbk".getBytes(StandardCharsets.UTF_8)).flip();
			ByteBuffer value = ByteBuffer.allocateDirect(3);
			value.put("bbv".getBytes(StandardCharsets.UTF_8)).flip();

			db.put(key, value);

			ByteBuffer out = ByteBuffer.allocateDirect(3);
			int len = db.get(key.duplicate(), out);
			out.flip();
			byte[] read = new byte[len];
			out.get(read);
			assertEquals("bbv", new String(read, StandardCharsets.UTF_8),
					"ByteBuffer get() mismatch on " + PLATFORM);
		}
	}

	@Test
	void zeroCopyMemorySegmentRoundTrip(@TempDir Path dir) {
		try (var db = RocksDB.open(dir);
		     Arena arena = Arena.ofConfined()) {
			MemorySegment key = toNative(arena, "msk".getBytes(StandardCharsets.UTF_8));
			MemorySegment value = toNative(arena, "msv".getBytes(StandardCharsets.UTF_8));

			db.put(key, value);

			MemorySegment out = arena.allocate(3);
			long len = db.get(key, out);
			assertEquals(3L, len, "MemorySegment get() length mismatch on " + PLATFORM);
			assertEquals("msv", new String(toByteArray(out, len), StandardCharsets.UTF_8),
					"MemorySegment get() content mismatch on " + PLATFORM);
		}
	}

	@Test
	void iteratorSeesAllPuts(@TempDir Path dir) {
		try (var db = RocksDB.open(dir)) {
			for (int i = 0; i < 10; i++) {
				db.put(("k" + i).getBytes(StandardCharsets.UTF_8), ("v" + i).getBytes(StandardCharsets.UTF_8));
			}

			int count = 0;
			try (RocksIterator it = db.newIterator()) {
				for (it.seekToFirst(); it.isValid(); it.next()) {
					count++;
				}
			}
			assertEquals(10, count, "iterator did not see all puts on " + PLATFORM);
		}
	}

	@Test
	void snapshotIsolatesReads(@TempDir Path dir) {
		try (var db = RocksDB.open(dir)) {
			db.put("k".getBytes(StandardCharsets.UTF_8), "v1".getBytes(StandardCharsets.UTF_8));

			try (Snapshot snapshot = db.getSnapshot()) {
				db.put("k".getBytes(StandardCharsets.UTF_8), "v2".getBytes(StandardCharsets.UTF_8));

				var readOptions = io.github.dfa1.rocksdbffm.ReadOptions.newReadOptions().setSnapshot(snapshot);
				byte[] snapshotted = db.get(readOptions, "k".getBytes(StandardCharsets.UTF_8));
				assertEquals("v1", new String(snapshotted, StandardCharsets.UTF_8),
						"snapshot read saw a post-snapshot write on " + PLATFORM);
			}

			assertEquals("v2", new String(db.get("k".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8),
					"live read did not see the post-snapshot write on " + PLATFORM);
		}
	}

	@Test
	void writeBatchAppliesAtomically(@TempDir Path dir) {
		try (var db = RocksDB.open(dir);
		     WriteBatch batch = WriteBatch.create()) {
			batch.put("a".getBytes(StandardCharsets.UTF_8), "1".getBytes(StandardCharsets.UTF_8));
			batch.put("b".getBytes(StandardCharsets.UTF_8), "2".getBytes(StandardCharsets.UTF_8));

			db.write(batch);

			assertEquals("1", new String(db.get("a".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
			assertEquals("2", new String(db.get("b".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
		}
	}

	@Test
	void flushPersistsData(@TempDir Path dir) {
		try (var db = RocksDB.open(dir);
		     var flushOptions = io.github.dfa1.rocksdbffm.FlushOptions.newFlushOptions().setWait(true)) {
			db.put("k".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8));

			db.flush(flushOptions);

			assertTrue(java.nio.file.Files.list(dir).findAny().isPresent(),
					"expected on-disk SST/manifest files after flush on " + PLATFORM);
		} catch (java.io.IOException e) {
			throw new AssertionError("failed to list db directory on " + PLATFORM, e);
		}
	}

	private static MemorySegment toNative(Arena arena, byte[] data) {
		MemorySegment seg = arena.allocate(Math.max(data.length, 1));
		MemorySegment.copy(data, 0, seg, JAVA_BYTE, 0, data.length);
		return seg;
	}

	private static byte[] toByteArray(MemorySegment seg, long len) {
		byte[] out = new byte[Math.toIntExact(len)];
		MemorySegment.copy(seg, JAVA_BYTE, 0, out, 0, out.length);
		return out;
	}
}
