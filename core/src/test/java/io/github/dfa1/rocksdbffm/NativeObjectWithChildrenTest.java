package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers [NativeObjectWithChildren] in isolation, without any real native pointer — including
/// the issues/143 TOCTOU race: a child registering itself after its parent's one-shot sweep has
/// already run must never end up silently unswept and unreleased.
class NativeObjectWithChildrenTest {

	private static final class TestParent extends NativeObjectWithChildren {

		final AtomicInteger tryCloseResourceCount = new AtomicInteger();

		TestParent(MemorySegment ptr) {
			super(ptr);
		}

		@Override
		protected void tryCloseResource(MemorySegment ptr) {
			tryCloseResourceCount.incrementAndGet();
		}
	}

	private static final class TestChild extends NativeObject {

		final AtomicInteger tryCloseCount = new AtomicInteger();

		TestChild(MemorySegment ptr) {
			super(ptr);
		}

		@Override
		protected void tryClose(MemorySegment ptr) {
			tryCloseCount.incrementAndGet();
		}
	}

	private static MemorySegment fakePtr(long address) {
		return MemorySegment.ofAddress(address);
	}

	@Test
	void registerChild_beforeParentCloses_isClosedByParentSweep() {
		// Given
		var parent = new TestParent(fakePtr(0x1));
		var child = new TestChild(fakePtr(0x2));
		parent.registerChild(child);

		// When
		parent.close();

		// Then
		assertThat(child.tryCloseCount).hasValue(1);
		assertThat(parent.tryCloseResourceCount).hasValue(1);
	}

	@Test
	void registerChild_afterParentAlreadyClosed_abandonsChildWithoutInvokingItsReleaseCall() {
		// Given — simulates the issues/143 race deterministically: the parent's one-shot sweep
		// has already run (with no children registered yet) by the time this child registers.
		var parent = new TestParent(fakePtr(0x1));
		parent.close();
		var child = new TestChild(fakePtr(0x2));

		// When
		parent.registerChild(child);

		// Then — never released via its own tryClose (that call would be a use-after-free
		// against a pointer the parent may already have freed)...
		assertThat(child.tryCloseCount).hasValue(0);
		// ...but abandoned (transferOwnership), not left as a live, unswept, dangling child —
		// any later use fails fast instead of risking a crash.
		assertThatThrownBy(child::ptr).isInstanceOf(IllegalStateException.class);

		// And closing it explicitly afterward is still a safe no-op.
		assertThatCode(child::close).doesNotThrowAnyException();
		assertThat(child.tryCloseCount).hasValue(0);
	}

	@Test
	void unregisterChild_removesFromPendingSweep() {
		// Given
		var parent = new TestParent(fakePtr(0x1));
		var child = new TestChild(fakePtr(0x2));
		parent.registerChild(child);

		// When — child released itself first, same as its own tryClose would do
		parent.unregisterChild(child);
		parent.close();

		// Then — parent's sweep never touches it again
		assertThat(child.tryCloseCount).hasValue(0);
	}

	@Test
	void concurrentRegisterAndClose_neverLeavesAChildBothUnclosedAndUnabandoned() throws Exception {
		// Given — hammers registerChild against close() from many threads, the same shape as
		// the issues/143 race timeline (a child's native "create" succeeding an instant before
		// close() runs). Without closeLock, a straggler registration landing after the sweep
		// already iterated `children` would be added to a set nothing ever iterates again.
		int childCount = 2000;
		var parent = new TestParent(fakePtr(0x1));
		List<TestChild> children = new CopyOnWriteArrayList<>();
		ExecutorService executor = Executors.newFixedThreadPool(8);
		CountDownLatch start = new CountDownLatch(1);

		// When
		var closer = executor.submit(() -> {
			await(start);
			parent.close();
		});
		var registrars = IntStream.range(0, childCount)
				.mapToObj(i -> executor.submit(() -> {
					await(start);
					var child = new TestChild(fakePtr(0x100 + i));
					children.add(child);
					parent.registerChild(child);
				}))
				.toList();

		start.countDown();
		closer.get();
		for (var f : registrars) {
			f.get();
		}
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		// Then — every single child ended up in exactly one terminal state: either the parent's
		// sweep closed it (tryClose ran once), or it was abandoned before/without ever being
		// added to `children` (tryClose never ran, but it's inert — ptr() throws). None can be
		// both "never closed" and "still live", which is the dangling state the race produced.
		for (TestChild child : children) {
			boolean closedByParent = child.tryCloseCount.get() == 1;
			boolean abandoned = child.tryCloseCount.get() == 0 && isInert(child);
			assertThat(closedByParent || abandoned)
					.as("child must be either closed-by-parent or abandoned, never left dangling")
					.isTrue();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	private static boolean isInert(TestChild child) {
		try {
			child.ptr();
			return false;
		} catch (IllegalStateException e) {
			return true;
		}
	}
}
