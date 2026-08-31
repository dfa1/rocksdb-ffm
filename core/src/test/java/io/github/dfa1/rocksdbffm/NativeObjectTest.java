package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers [NativeObject] in isolation, without any real native pointer — the double-close
/// idempotency and ownership-transfer contract described in `docs/explanation.md` is testable
/// directly against a fabricated address and a fake [#tryClose(MemorySegment)].
class NativeObjectTest {

	private static final class TestNativeObject extends NativeObject {

		final AtomicInteger tryCloseCount = new AtomicInteger();
		volatile MemorySegment lastClosedPtr;
		volatile RuntimeException throwFromTryClose;

		TestNativeObject(MemorySegment ptr) {
			super(ptr);
		}

		@Override
		protected void tryClose(MemorySegment ptr) {
			tryCloseCount.incrementAndGet();
			lastClosedPtr = ptr;
			if (throwFromTryClose != null) {
				throw throwFromTryClose;
			}
		}
	}

	private static MemorySegment fakePtr(long address) {
		return MemorySegment.ofAddress(address);
	}

	@Test
	void ptr_returnsTheConstructedPointer() {
		// Given
		var obj = new TestNativeObject(fakePtr(0x1234));

		// When
		var result = obj.ptr();

		// Then
		assertThat(result).isEqualTo(fakePtr(0x1234));
	}

	@Test
	void ptr_afterClose_throwsIllegalStateException() {
		// Given
		var obj = new TestNativeObject(fakePtr(0x1234));
		obj.close();

		// When / Then
		assertThatThrownBy(obj::ptr).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void close_callsTryCloseExactlyOnceWithTheOriginalPointer() {
		// Given
		var obj = new TestNativeObject(fakePtr(0xABCD));

		// When
		obj.close();

		// Then
		assertThat(obj.tryCloseCount).hasValue(1);
		assertThat(obj.lastClosedPtr).isEqualTo(fakePtr(0xABCD));
	}

	@Test
	void close_calledTwice_callsTryCloseOnlyOnce() {
		// Given
		var obj = new TestNativeObject(fakePtr(0x1));
		obj.close();

		// When
		obj.close();

		// Then
		assertThat(obj.tryCloseCount).hasValue(1);
	}

	@Test
	void close_calledConcurrentlyFromManyThreads_callsTryCloseExactlyOnce() {
		// Given — the documented guarantee: tryClose runs exactly once no matter how many
		// times or from how many threads close() is called concurrently
		var obj = new TestNativeObject(fakePtr(0x1));
		ExecutorService executor = Executors.newFixedThreadPool(8);

		// When
		var futures = IntStream.range(0, 200)
				.mapToObj(i -> CompletableFuture.runAsync(obj::close, executor))
				.toList();
		futures.forEach(CompletableFuture::join);
		executor.shutdown();

		// Then
		assertThat(obj.tryCloseCount).hasValue(1);
	}

	@Test
	void close_whenTryCloseThrows_doesNotPropagateButStillMarksClosed() {
		// Given — close() must never throw: a failure here must not stop the rest of a
		// try-with-resources chain from closing
		var obj = new TestNativeObject(fakePtr(0x1));
		obj.throwFromTryClose = new RuntimeException("native destroy failed");

		// When
		obj.close();

		// Then
		assertThat(obj.tryCloseCount).hasValue(1);
		assertThatThrownBy(obj::ptr).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void close_afterTryCloseThrew_isStillIdempotent() {
		// Given
		var obj = new TestNativeObject(fakePtr(0x1));
		obj.throwFromTryClose = new RuntimeException("native destroy failed");
		obj.close();

		// When
		obj.close();

		// Then — the failed first attempt still consumed the pointer; a second close() is a
		// pure no-op, not a retry
		assertThat(obj.tryCloseCount).hasValue(1);
	}

	@Test
	void transferOwnership_makesCloseANoOp() {
		// Given
		var obj = new TestNativeObject(fakePtr(0x1));

		// When
		obj.transferOwnership();
		obj.close();

		// Then
		assertThat(obj.tryCloseCount).hasValue(0);
	}

	@Test
	void transferOwnership_thenPtr_throwsIllegalStateException() {
		// Given
		var obj = new TestNativeObject(fakePtr(0x1));

		// When
		obj.transferOwnership();

		// Then
		assertThatThrownBy(obj::ptr).isInstanceOf(IllegalStateException.class);
	}
}
