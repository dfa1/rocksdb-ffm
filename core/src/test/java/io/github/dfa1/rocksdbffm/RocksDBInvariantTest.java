package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Core key/value invariants exercised over byte sequences that stress the
/// native boundary: empty arrays, embedded NUL bytes, sequences that are not
/// valid UTF-8, and payloads that cross a page.
///
/// These cases are deliberately not expressible as `String` keys — encoding a
/// Java `String` to UTF-8 can never produce an unpaired surrogate or a bare
/// `0xFF` byte, so a `String`-based generator only ever explores the subset of
/// the key space that round-trips through UTF-16.
///
/// Each case is checked twice: once served from the memtable, and once after a
/// flush so the same bytes go through SST encoding (prefix compression, bloom
/// filter, block cache) on the way back.
class RocksDBInvariantTest {

	/// Fixed seed so the generated key set is the same from run to run. Only
	/// `Random` methods with a specified algorithm are used, so the sequence is
	/// stable across JDK implementations.
	private static final long SEED = 20260807L;

	/// A key/value pair together with a label used as the JUnit display name.
	private record Case(String name, byte[] key, byte[] value) {

		@Override
		public String toString() {
			return name;
		}
	}

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	private static byte[] filled(int length, int value) {
		byte[] result = new byte[length];
		Arrays.fill(result, (byte) value);
		return result;
	}

	/// Every distinct byte value, so no single octet can be treated as a terminator.
	private static byte[] allByteValues() {
		byte[] result = new byte[256];
		for (int i = 0; i < 256; i++) {
			result[i] = (byte) i;
		}
		return result;
	}

	static Stream<Case> cases() {
		return Stream.of(
				new Case("ascii", "hello".getBytes(), "world".getBytes()),
				new Case("empty key", new byte[0], "value".getBytes()),
				new Case("empty value", "key".getBytes(), new byte[0]),
				new Case("empty key and value", new byte[0], new byte[0]),
				new Case("single NUL", bytes(0x00), bytes(0x00)),
				new Case("embedded NUL", bytes('a', 0x00, 'b'), bytes(0x00, 'v', 0x00)),
				new Case("trailing NUL", bytes('k', 0x00), bytes('v', 0x00)),
				new Case("invalid UTF-8", bytes(0xFF, 0xFE, 0xC0, 0x80), bytes(0xED, 0xA0, 0x80)),
				new Case("all byte values", allByteValues(), allByteValues()),
				new Case("multi-page payload", filled(64 * 1024, 'k'), filled(256 * 1024, 'v')));
	}

	private static void flush(ReadWriteDB db) {
		try (FlushOptions flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
			db.flush(flushOptions);
		}
	}

	// -----------------------------------------------------------------------
	// put / get / delete — byte[] tier
	// -----------------------------------------------------------------------

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void put_thenGet_roundTripsUnchanged(Case testCase, @TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put(testCase.key(), testCase.value());

			// When
			var result = db.get(testCase.key());

			// Then
			assertThat(result).as(testCase.name()).isEqualTo(testCase.value());
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void put_thenGet_roundTripsUnchanged_afterFlush(Case testCase, @TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put(testCase.key(), testCase.value());
			flush(db);

			// When
			var result = db.get(testCase.key());

			// Then
			assertThat(result).as(testCase.name()).isEqualTo(testCase.value());
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void get_returnsNull_afterDelete(Case testCase, @TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put(testCase.key(), testCase.value());
			db.delete(testCase.key());

			// When
			var result = db.get(testCase.key());

			// Then
			assertThat(result).as(testCase.name()).isNull();
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void put_overwritesExistingKey(Case testCase, @TempDir Path dir) {
		// Given
		byte[] replacement = filled(testCase.value().length + 1, 'z');
		try (var db = RocksDB.open(dir)) {
			db.put(testCase.key(), testCase.value());

			// When
			db.put(testCase.key(), replacement);

			// Then
			var result = db.get(testCase.key());
			assertThat(result).as(testCase.name()).isEqualTo(replacement);
		}
	}

	// -----------------------------------------------------------------------
	// put / get — MemorySegment tier
	// -----------------------------------------------------------------------

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void putSegment_thenGetSegment_roundTripsUnchanged(Case testCase, @TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir); Arena arena = Arena.ofConfined()) {
			MemorySegment key = arena.allocateFrom(ValueLayout.JAVA_BYTE, testCase.key());
			MemorySegment value = arena.allocateFrom(ValueLayout.JAVA_BYTE, testCase.value());
			db.put(key, value);
			MemorySegment readBuffer = arena.allocate(testCase.value().length);

			// When
			long length = db.get(key, readBuffer);

			// Then
			assertThat(length).as(testCase.name()).isEqualTo(testCase.value().length);
			assertThat(readBuffer.toArray(ValueLayout.JAVA_BYTE)).as(testCase.name()).isEqualTo(testCase.value());
		}
	}

	// -----------------------------------------------------------------------
	// Iterator ordering
	// -----------------------------------------------------------------------

	/// The adversarial keys above plus a deterministic pseudo-random spread, so
	/// the ordering invariant is checked across a few hundred keys of varying
	/// length rather than a handful of hand-picked ones.
	private static List<byte[]> orderingKeys() {
		List<byte[]> keys = new ArrayList<>();
		cases().forEach(testCase -> keys.add(testCase.key()));

		Random random = new Random(SEED);
		for (int i = 0; i < 256; i++) {
			byte[] key = new byte[random.nextInt(23) + 1];
			random.nextBytes(key);
			keys.add(key);
		}
		return keys;
	}

	@Test
	void iterator_visitsEveryDistinctKeyInLexicographicOrder(@TempDir Path dir) {
		// Given
		List<byte[]> keys = orderingKeys();
		var expected = new TreeSet<byte[]>(Arrays::compareUnsigned);
		expected.addAll(keys);

		try (var db = RocksDB.open(dir)) {
			for (byte[] key : keys) {
				db.put(key, "v".getBytes());
			}
			flush(db);

			// When
			List<byte[]> observed = new ArrayList<>();
			try (RocksIterator it = db.newIterator()) {
				for (it.seekToFirst(); it.isValid(); it.next()) {
					observed.add(it.key());
				}
				it.checkError();
			}

			// Then — TreeSet iterates in ascending unsigned order, and
			// containsExactlyElementsOf is order-sensitive, so this asserts both
			// "same distinct keys" and "ascending unsigned order" in one step.
			assertThat(observed)
					.as("iterator must visit each distinct key exactly once, in order")
					.usingElementComparator(Arrays::compareUnsigned)
					.containsExactlyElementsOf(expected);
		}
	}
}
