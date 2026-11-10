package io.github.dfa1.rocksdbffm;

/// Compression algorithms supported by the RocksDB C API.
///
/// Integer values match the `rocksdb_*_compression` constants in `rocksdb/c.h`.
///
/// `XPRESS` is only available in the Windows classifiers (it wraps the Windows
/// compression API, which RocksDB doesn't implement on macOS/Linux).
/// [Options#setCompression(CompressionType)] and [Options#setBlobCompressionType(CompressionType)]
/// throw [UnsupportedOperationException] for unsupported values.
///
/// ```
/// try (Options opts = Options.newOptions().setCompression(CompressionType.LZ4)) { ... }
/// ```
public enum CompressionType {

	/// No compression. Always supported.
	NO_COMPRESSION(0),

	/// Snappy compression.
	SNAPPY(1),

	/// zlib compression.
	ZLIB(2),

	/// bzip2 compression.
	BZLIB2(3),

	/// LZ4 compression.
	LZ4(4),

	/// LZ4HC (high-compression) compression.
	LZ4HC(5),

	/// Express compression (Windows only).
	XPRESS(6),

	/// Zstandard compression.
	ZSTD(7);

	private final int value;

	CompressionType(int value) {
		this.value = value;
	}

	// don't expose the raw value
	int getValue() {
		return value;
	}

	/// Whether this algorithm is supported by the bundled native on the current platform.
	///
	/// @return `true` if usable, `false` if setting it throws `UnsupportedOperationException`
   	boolean isSupported() {
   		return switch (this) {
   			case XPRESS -> NativeLibrary.classifier().startsWith("windows");
   			case NO_COMPRESSION, SNAPPY, ZLIB, BZLIB2, LZ4, LZ4HC, ZSTD -> true;
   		};
   	}

	static CompressionType fromValue(int value) {
		return switch (value) {
			case 0 -> NO_COMPRESSION;
			case 1 -> SNAPPY;
			case 2 -> ZLIB;
			case 3 -> BZLIB2;
			case 4 -> LZ4;
			case 5 -> LZ4HC;
			case 6 -> XPRESS;
			case 7 -> ZSTD;
			default -> throw new IllegalArgumentException("Unknown compression type value: " + value);
		};
	}
}
