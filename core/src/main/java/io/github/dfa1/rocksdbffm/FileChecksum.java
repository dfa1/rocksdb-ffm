package io.github.dfa1.rocksdbffm;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/// Immutable value object holding a file checksum as raw bytes, together with the name of the
/// function that computed it.
///
/// A checksum is binary, not text: it may contain `0x00` or bytes at or above `0x80`, so it is
/// never decoded as a string. [#toString()] renders it as lowercase hex.
///
/// Obtained from [LiveFileStorageInfo#fileChecksum()].
public final class FileChecksum {

	private final byte[] bytes;
	private final String functionName;

	FileChecksum(byte[] bytes, String functionName) {
		this.bytes = bytes.clone();
		this.functionName = Objects.requireNonNull(functionName, "functionName");
	}

	/// Returns a copy of the raw checksum bytes.
	///
	/// @return the checksum bytes, in the byte order RocksDB produced them
	public byte[] toBytes() {
		return bytes.clone();
	}

	/// Returns the name of the function that computed this checksum, e.g. `"FileChecksumCrc32c"`.
	///
	/// @return the checksum function name
	public String functionName() {
		return functionName;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FileChecksum other
				&& this.functionName.equals(other.functionName)
				&& Arrays.equals(this.bytes, other.bytes);
	}

	@Override
	public int hashCode() {
		return 31 * functionName.hashCode() + Arrays.hashCode(bytes);
	}

	@Override
	public String toString() {
		return functionName + ":" + HexFormat.of().formatHex(bytes);
	}
}
