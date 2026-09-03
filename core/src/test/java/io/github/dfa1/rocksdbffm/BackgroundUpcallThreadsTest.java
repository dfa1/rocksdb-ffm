package io.github.dfa1.rocksdbffm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BackgroundUpcallThreadsTest {

	@Test
	void awaitTermination_emptySet_returnsImmediately() {
		// Given
		Set<Thread> threads = Set.of();

		// When
		long start = System.nanoTime();
		BackgroundUpcallThreads.awaitTermination(threads, Duration.ofSeconds(5));
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

		// Then
		assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
	}

	@Test
	void awaitTermination_alreadyTerminatedThreads_returnsWithoutWaitingOutTheTimeout() throws InterruptedException {
		// Given
		Thread first = new Thread(() -> { });
		Thread second = new Thread(() -> { });
		first.start();
		second.start();
		first.join();
		second.join();
		Set<Thread> threads = new LinkedHashSet<>(Set.of(first, second));

		// When
		long start = System.nanoTime();
		BackgroundUpcallThreads.awaitTermination(threads, Duration.ofSeconds(5));
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

		// Then
		assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
	}

	@Test
	void awaitTermination_timeoutElapses_stopsWaitingForAStillRunningThread() throws InterruptedException {
		// Given
		CountDownLatch neverCounts = new CountDownLatch(1);
		Thread blocked = new Thread(() -> awaitUninterruptibly(neverCounts));
		blocked.setDaemon(true);
		blocked.start();

		// When
		long start = System.nanoTime();
		BackgroundUpcallThreads.awaitTermination(Set.of(blocked), Duration.ofMillis(100));
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

		// Then
		assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(100)).isLessThan(Duration.ofSeconds(5));
		assertThat(blocked.isAlive()).isTrue();

		// Cleanup: release the thread this test parked so it doesn't outlive the test.
		blocked.interrupt();
		blocked.join(Duration.ofSeconds(1));
	}

	@Test
	void awaitTermination_callerInterrupted_returnsAndPreservesInterruptFlag() throws InterruptedException {
		// Given
		CountDownLatch neverCounts = new CountDownLatch(1);
		Thread blocked = new Thread(() -> awaitUninterruptibly(neverCounts));
		blocked.setDaemon(true);
		blocked.start();

		AtomicBoolean interruptFlagAfterReturn = new AtomicBoolean();
		Thread caller = new Thread(() -> {
			BackgroundUpcallThreads.awaitTermination(Set.of(blocked), Duration.ofSeconds(5));
			interruptFlagAfterReturn.set(Thread.currentThread().isInterrupted());
		});

		// When
		caller.start();
		caller.interrupt();
		caller.join(Duration.ofSeconds(5));

		// Then
		assertThat(caller.isAlive()).isFalse();
		assertThat(interruptFlagAfterReturn).isTrue();

		// Cleanup: release the thread this test parked so it doesn't outlive the test.
		blocked.interrupt();
		blocked.join(Duration.ofSeconds(1));
	}

	private static void awaitUninterruptibly(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
