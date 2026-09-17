package io.github.dfa1.rocksdbffm;

import java.util.Spliterator;
import java.util.function.Consumer;

/// [Spliterator] adapter over a [RocksIterator], used only by [RocksIterator#stream(Mapper, Mapper)].
/// Package-private: callers only ever see it through the [java.util.stream.Stream] that method
/// returns.
///
/// @param <K> the mapped key type
/// @param <V> the mapped value type
final class RocksIteratorSpliterator<K, V> implements Spliterator<KeyValue<K, V>> {

	private final RocksIterator iterator;
	private final Mapper<K> keyMapper;
	private final Mapper<V> valueMapper;
	private boolean started;

	RocksIteratorSpliterator(RocksIterator iterator, Mapper<K> keyMapper, Mapper<V> valueMapper) {
		this.iterator = iterator;
		this.keyMapper = keyMapper;
		this.valueMapper = valueMapper;
	}

	@Override
	public boolean tryAdvance(Consumer<? super KeyValue<K, V>> action) {
		if (!started) {
			iterator.seekToFirst();
			started = true;
		}

		if (!iterator.isValid()) {
			return false;
		}

		K key = iterator.key(keyMapper);
		V value = iterator.value(valueMapper);
		action.accept(new KeyValue<>(key, value));

		iterator.next();
		return true;
	}

	@Override
	public Spliterator<KeyValue<K, V>> trySplit() {
		// A RocksDB iterator has no fixed-cost midpoint to seek to, so it cannot be split safely.
		return null;
	}

	@Override
	public long estimateSize() {
		// Unknown without scanning; Stream doesn't rely on this for correctness.
		return Long.MAX_VALUE;
	}

	@Override
	public int characteristics() {
		return ORDERED | NONNULL | IMMUTABLE;
	}
}
