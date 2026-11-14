package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/// Immutable value object representing the name of a column family.
///
/// RocksDB names are byte strings on disk, not necessarily UTF-8: a database created by another
/// binding may use any bytes except NUL. This type keeps the raw bytes, so a name read back from
/// a database round-trips unchanged into a later open, and [#toString()] only decodes them for
/// display. Names created in Java from a [String] are encoded as UTF-8.
///
/// ```
/// var users = ColumnFamilyName.of("users");
/// var descriptor = ColumnFamilyDescriptor.of(users);
/// var defaultName = ColumnFamilyName.DEFAULT;
/// ```
public final class ColumnFamilyName {

	/// The name of the column family every database has: `"default"`.
	public static final ColumnFamilyName DEFAULT = new ColumnFamilyName("default".getBytes(StandardCharsets.UTF_8));

	private final byte[] bytes;

	private ColumnFamilyName(byte[] bytes) {
		this.bytes = bytes;
	}

	/// Creates a [ColumnFamilyName] from a Java string, encoded as UTF-8.
	///
	/// @param name the column family name
	/// @return a new [ColumnFamilyName]
	/// @throws NullPointerException     if `name` is `null`
	/// @throws IllegalArgumentException if `name` contains a NUL character, which the C API cannot carry
	public static ColumnFamilyName of(String name) {
		Objects.requireNonNull(name, "name");
		if (name.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("column family name must not contain NUL: " + name);
		}
		return new ColumnFamilyName(name.getBytes(StandardCharsets.UTF_8));
	}

	/// Wraps the raw bytes of a name read from the native layer, without validating or decoding them.
	///
	/// @param bytes the name bytes, taken as-is; the caller must not modify the array afterward
	/// @return a new [ColumnFamilyName]
	static ColumnFamilyName ofBytes(byte[] bytes) {
		return new ColumnFamilyName(bytes);
	}

	/// Copies this name into `arena` as the NUL-terminated `const char*` the C API expects.
	///
	/// @param arena the arena that owns the returned segment
	/// @return a native, NUL-terminated copy of the name
	MemorySegment toNative(Arena arena) {
		MemorySegment segment = arena.allocate(bytes.length + 1L);
		MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.length);
		return segment;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ColumnFamilyName other && Arrays.equals(bytes, other.bytes);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(bytes);
	}

	/// Returns the name decoded as UTF-8, for display. Bytes that are not valid UTF-8 are
	/// replaced, so do not use the result to identify the column family; compare [ColumnFamilyName]s.
	///
	/// @return the decoded name
	@Override
	public String toString() {
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
