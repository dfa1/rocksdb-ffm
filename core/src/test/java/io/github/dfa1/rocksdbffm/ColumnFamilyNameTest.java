package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ColumnFamilyNameTest {

	@Test
	void of_sameString_isEqual() {
		// Given
		var a = ColumnFamilyName.of("users");

		// When
		var b = ColumnFamilyName.of("users");

		// Then
		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).hasToString("users");
	}

	@Test
	void of_differentStrings_areNotEqual() {
		// Given
		var a = ColumnFamilyName.of("users");

		// When
		var b = ColumnFamilyName.of("orders");

		// Then
		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void default_isTheDefaultColumnFamilyName() {
		// Given
		var name = ColumnFamilyName.DEFAULT;

		// When
		var result = name.toString();

		// Then
		assertThat(result).isEqualTo("default");
		assertThat(name).isEqualTo(ColumnFamilyName.of("default"));
	}

	@Test
	void of_null_throwsNullPointerException() {
		// Given
		String name = null;

		// When
		var thrown = catchThrowable(() -> ColumnFamilyName.of(name));

		// Then
		assertThat(thrown).isInstanceOf(NullPointerException.class);
	}

	@Test
	void of_nameContainingNul_throwsIllegalArgumentException() {
		// Given
		var name = "bad\0name";

		// When
		var thrown = catchThrowable(() -> ColumnFamilyName.of(name));

		// Then
		assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nonUtf8Name_roundTripsThroughCreateListAndReopen(@TempDir Path dir) {
		// Given — a name another binding could have written: not valid UTF-8
		var raw = ColumnFamilyName.ofBytes(new byte[]{'c', 'f', (byte) 0xFF, (byte) 0xFE});
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var _ = db.createColumnFamily(ColumnFamilyDescriptor.of(raw))) {
			// created
		}

		// When
		List<ColumnFamilyName> listed;
		try (var opts = Options.newOptions()) {
			listed = RocksDB.listColumnFamilies(dir, opts);
		}

		// Then — the listed name is byte-identical, so it can be passed straight back to open
		assertThat(listed).containsExactlyInAnyOrder(ColumnFamilyName.DEFAULT, raw);
		var descriptors = listed.stream().map(ColumnFamilyDescriptor::of).toList();
		try (var opts = Options.newOptions();
		     var _ = RocksDB.openReadWrite(dir, opts, descriptors)) {
			// reopened without a name mismatch
		}
	}

	// -----------------------------------------------------------------------
	// equality
	// -----------------------------------------------------------------------

	@Test
	void equals_isCaseSensitive() {
		// Given
		var lower = ColumnFamilyName.of("users");

		// When
		var upper = ColumnFamilyName.of("Users");

		// Then
		assertThat(lower).isNotEqualTo(upper);
	}

	@Test
	void equals_nullAndOtherTypes_areNotEqual() {
		// Given
		var name = ColumnFamilyName.of("users");

		// When
		var sameAsString = name.equals("users");
		var sameAsNull = name.equals(null);

		// Then
		assertThat(sameAsString).isFalse();
		assertThat(sameAsNull).isFalse();
		assertThat(name).isEqualTo(name);
	}

	@Test
	void hashMapLookup_withAnEqualButSeparateInstance_findsTheEntry() {
		// Given
		var map = new HashMap<ColumnFamilyName, String>();
		map.put(ColumnFamilyName.of("users"), "value");

		// When
		var result = map.get(ColumnFamilyName.of("users"));

		// Then
		assertThat(result).isEqualTo("value");
	}

	@Test
	void default_equalsTheNameBuiltFromRawBytes() {
		// Given
		var raw = ColumnFamilyName.ofBytes("default".getBytes(StandardCharsets.UTF_8));

		// When
		var result = ColumnFamilyName.DEFAULT.equals(raw);

		// Then
		assertThat(result).isTrue();
		assertThat(ColumnFamilyName.DEFAULT).hasSameHashCodeAs(raw);
	}

	// -----------------------------------------------------------------------
	// encoding
	// -----------------------------------------------------------------------

	@Test
	void of_multiByteUtf8_roundTripsThroughToString() {
		// Given
		var text = "caf\u00e9-\u65e5\u672c-\uD83D\uDE00";

		// When
		var name = ColumnFamilyName.of(text);

		// Then
		assertThat(name).hasToString(text);
		assertThat(name).isEqualTo(ColumnFamilyName.ofBytes(text.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void toString_ofNonUtf8Bytes_doesNotThrowAndIsLossy() {
		// Given
		var first = ColumnFamilyName.ofBytes(new byte[]{(byte) 0xFF});
		var second = ColumnFamilyName.ofBytes(new byte[]{(byte) 0xFE});

		// When
		var sameDisplay = first.toString().equals(second.toString());

		// Then — two different names can display identically, which is why callers must compare
		// ColumnFamilyNames and never their toString()
		assertThat(sameDisplay).isTrue();
		assertThat(first).isNotEqualTo(second);
	}

	// -----------------------------------------------------------------------
	// toNative
	// -----------------------------------------------------------------------

	@Test
	void toNative_isNulTerminatedUtf8Copy() {
		// Given
		var name = ColumnFamilyName.of("caf\u00e9");

		try (Arena arena = Arena.ofConfined()) {
			// When
			var segment = name.toNative(arena);

			// Then
			var expected = "caf\u00e9".getBytes(StandardCharsets.UTF_8);
			assertThat(segment.byteSize()).isEqualTo(expected.length + 1L);
			assertThat(segment.asSlice(0, expected.length).toArray(ValueLayout.JAVA_BYTE)).isEqualTo(expected);
			assertThat(segment.get(ValueLayout.JAVA_BYTE, expected.length)).isZero();
		}
	}

	@Test
	void toNative_nonUtf8Bytes_areCopiedVerbatim() {
		// Given
		var raw = new byte[]{'c', 'f', (byte) 0xFF, (byte) 0xFE};
		var name = ColumnFamilyName.ofBytes(raw);

		try (Arena arena = Arena.ofConfined()) {
			// When
			var segment = name.toNative(arena);

			// Then
			assertThat(segment.asSlice(0, raw.length).toArray(ValueLayout.JAVA_BYTE)).isEqualTo(raw);
			assertThat(segment.get(ValueLayout.JAVA_BYTE, raw.length)).isZero();
		}
	}

	@Test
	void toNative_readBackThroughToBorrowedBytes_isTheSameName() {
		// Given
		var name = ColumnFamilyName.ofBytes(new byte[]{'a', (byte) 0x80, 'b'});

		try (Arena arena = Arena.ofConfined()) {
			// When
			var back = ColumnFamilyName.ofBytes(NativeCalls.toBorrowedBytes(name.toNative(arena)));

			// Then
			assertThat(back).isEqualTo(name);
		}
	}

	// -----------------------------------------------------------------------
	// validation
	// -----------------------------------------------------------------------

	@Test
	void of_nulAtAnyPosition_isRejected() {
		// Given
		var names = List.of("\0abc", "ab\0c", "abc\0");

		// When
		var thrown = names.stream()
				.map(n -> catchThrowable(() -> ColumnFamilyName.of(n)))
				.toList();

		// Then
		assertThat(thrown).isNotEmpty().allSatisfy(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class));
	}

	// -----------------------------------------------------------------------
	// against a real database
	// -----------------------------------------------------------------------

	@Test
	void handleGetName_returnsTheNameTheFamilyWasCreatedWith_evenForNonUtf8(@TempDir Path dir) {
		// Given
		var raw = ColumnFamilyName.ofBytes(new byte[]{'x', (byte) 0xC3, (byte) 0x28});
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of(raw))) {

			// When
			var name = cf.getName();

			// Then
			assertThat(name).isEqualTo(raw);
		}
	}

	@Test
	void descriptorName_returnsTheNameItWasBuiltWith() {
		// Given
		var name = ColumnFamilyName.of("users");

		// When
		var result = ColumnFamilyDescriptor.of(name).name();

		// Then
		assertThat(result).isEqualTo(name);
	}

	@Test
	void loadLatestOptions_keepsNonUtf8NamesLossless(@TempDir Path dir) {
		// Given
		var raw = ColumnFamilyName.ofBytes(new byte[]{'k', (byte) 0xFF});
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(dir, opts);
		     var _ = db.createColumnFamily(ColumnFamilyDescriptor.of(raw))) {
			// created
		}

		// When
		try (var loaded = RocksDB.loadLatestOptions(dir, false)) {
			var names = loaded.columnFamilies().stream().map(ColumnFamilyDescriptor::name).toList();

			// Then
			assertThat(names).contains(raw);
		}
	}
}
