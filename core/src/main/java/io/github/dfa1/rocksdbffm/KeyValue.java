package io.github.dfa1.rocksdbffm;

/// A single mapped key/value pair produced by [RocksIterator#stream(Mapper, Mapper)].
///
/// @param <K>   the mapped key type
/// @param <V>   the mapped value type
/// @param key   the mapped key
/// @param value the mapped value
public record KeyValue<K, V>(K key, V value) {
}
