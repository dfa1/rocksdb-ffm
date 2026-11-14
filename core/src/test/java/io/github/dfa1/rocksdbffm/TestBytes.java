package io.github.dfa1.rocksdbffm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/// Byte-encoding helpers shared across tests.
final class TestBytes {

	private TestBytes() {
	}

	static ByteBuffer directBuffer(byte[] bytes) {
		return (ByteBuffer) ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
	}

	static ByteBuffer directBuffer(String s) {
		return directBuffer(s.getBytes(StandardCharsets.UTF_8));
	}

	static byte[] encodeUint64(long value) {
		return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
	}

	static long decodeUint64(byte[] bytes) {
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
	}
}
