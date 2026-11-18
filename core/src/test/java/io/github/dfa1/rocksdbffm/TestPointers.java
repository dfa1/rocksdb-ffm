package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;

/// Native-pointer helpers shared across tests.
final class TestPointers {

	private TestPointers() {
	}

	/// A pointer that is never dereferenced, for tests that only exercise a wrapper's lifecycle.
	static MemorySegment fakePtr(long address) {
		return MemorySegment.ofAddress(address);
	}
}
