package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers [UpcallRegistry] in isolation, without going through an actual native upcall — the
/// id-smuggling contract (register -> state pointer -> get/unregister) is testable directly.
class UpcallRegistryTest {

	@Test
	void register_thenGet_returnsTheRegisteredValue() {
		// Given
		var registry = new UpcallRegistry<String>();

		// When
		MemorySegment state = registry.register("hello");

		// Then
		assertThat(registry.get(state)).isEqualTo("hello");
	}

	@Test
	void register_twice_returnsDistinctStatePointersEachResolvingToItsOwnValue() {
		// Given
		var registry = new UpcallRegistry<String>();

		// When
		MemorySegment first = registry.register("first");
		MemorySegment second = registry.register("second");

		// Then
		assertThat(first.address()).isNotEqualTo(second.address());
		assertThat(registry.get(first)).isEqualTo("first");
		assertThat(registry.get(second)).isEqualTo("second");
	}

	@Test
	void get_unknownState_returnsNull() {
		// Given
		var registry = new UpcallRegistry<String>();

		// When
		String result = registry.get(MemorySegment.ofAddress(42));

		// Then
		assertThat(result).isNull();
	}

	@Test
	void unregister_removesAndReturnsTheValue() {
		// Given
		var registry = new UpcallRegistry<String>();
		MemorySegment state = registry.register("hello");

		// When
		String removed = registry.unregister(state);

		// Then
		assertThat(removed).isEqualTo("hello");
	}

	@Test
	void get_afterUnregister_returnsNull() {
		// Given
		var registry = new UpcallRegistry<String>();
		MemorySegment state = registry.register("hello");
		registry.unregister(state);

		// When
		String result = registry.get(state);

		// Then
		assertThat(result).isNull();
	}

	@Test
	void unregister_unknownState_returnsNullWithoutThrowing() {
		// Given
		var registry = new UpcallRegistry<String>();

		// When
		String result = registry.unregister(MemorySegment.ofAddress(42));

		// Then
		assertThat(result).isNull();
	}

	@Test
	void unregister_calledTwiceForTheSameState_returnsNullOnTheSecondCall() {
		// Given
		var registry = new UpcallRegistry<String>();
		MemorySegment state = registry.register("hello");
		registry.unregister(state);

		// When
		String secondRemoval = registry.unregister(state);

		// Then
		assertThat(secondRemoval).isNull();
	}

	@Test
	void register_fromManyThreadsConcurrently_everyValueResolvesToItselfWithNoIdCollisions() {
		// Given — id generation and the backing map both need to hold up under concurrent
		// registration: upcalls can arrive from RocksDB's own background threads (flush,
		// compaction), not just the registering thread.
		var registry = new UpcallRegistry<Integer>();
		int count = 1_000;
		ExecutorService executor = Executors.newFixedThreadPool(8);

		// When
		List<CompletableFuture<MemorySegment>> futures = IntStream.range(0, count)
				.<CompletableFuture<MemorySegment>>mapToObj(i -> CompletableFuture.supplyAsync(() -> registry.register(i), executor))
				.toList();
		List<MemorySegment> states = futures.stream().map(CompletableFuture::join).toList();
		executor.shutdown();

		// Then
		Set<Long> distinctAddresses = new ConcurrentHashMap<Long, Boolean>().keySet(true);
		states.forEach(state -> distinctAddresses.add(state.address()));
		assertThat(distinctAddresses).hasSize(count);
		for (int i = 0; i < count; i++) {
			assertThat(registry.get(states.get(i))).isEqualTo(i);
		}
	}
}
