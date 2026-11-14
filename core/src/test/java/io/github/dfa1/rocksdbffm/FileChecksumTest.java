package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileChecksumTest {

	@Test
	void toBytes_preservesNulAndHighBytes() {
		// Given
		byte[] raw = {0x00, (byte) 0x80, (byte) 0xFF, 0x00};
		var checksum = new FileChecksum(raw, "FileChecksumCrc32c");

		// When
		var result = checksum.toBytes();

		// Then
		assertThat(result).isEqualTo(raw);
	}

	@Test
	void toString_isHex() {
		// Given
		var checksum = new FileChecksum(new byte[]{0x00, (byte) 0xAB, 0x01, (byte) 0xFF}, "FileChecksumCrc32c");

		// When
		var result = checksum.toString();

		// Then
		assertThat(result).isEqualTo("FileChecksumCrc32c:00ab01ff");
	}

	@Test
	void toBytes_isDefensiveCopy() {
		// Given
		byte[] raw = {1, 2, 3, 4};
		var checksum = new FileChecksum(raw, "f");

		// When
		raw[0] = 9;
		checksum.toBytes()[1] = 9;

		// Then
		assertThat(checksum.toBytes()).isEqualTo(new byte[]{1, 2, 3, 4});
	}

	@Test
	void equals_comparesBytesAndFunctionName() {
		// Given
		var a = new FileChecksum(new byte[]{1, 2}, "f");
		var b = new FileChecksum(new byte[]{1, 2}, "f");
		var c = new FileChecksum(new byte[]{1, 2}, "g");

		// When / Then
		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c);
	}
}
