package io.github.dfa1.rocksdbffm;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/// Shared id-registry for smuggling a Java object through a native `void*` state pointer.
///
/// RocksDB's callback-based C APIs (loggers, merge operators, ...) take a `void* state` that is
/// handed back unchanged on every upcall. A real Java object cannot be encoded as a raw address, so
/// each registered value is assigned a small integer id, and that id — not real memory — is what
/// gets passed as `state`. The global upcall stub then calls [#get(MemorySegment)] to resolve the id
/// back to the Java-side callback.
///
/// @param <T> the type of value being registered (a callback, or a bundle of callback state)
final class UpcallRegistry<T> {

	private final ConcurrentHashMap<Long, T> registry = new ConcurrentHashMap<>();
	private final AtomicLong nextId = new AtomicLong(1);

	/// Registers `value` and returns the `state` pointer to pass to the native call.
	///
	/// @param value the value to register
	/// @return a `MemorySegment` encoding the registry id as an address; not real memory
	MemorySegment register(T value) {
		long id = nextId.getAndIncrement();
		registry.put(id, value);
		return MemorySegment.ofAddress(id);
	}

	/// Looks up the value registered for `state`, without removing it.
	///
	/// @param state the `state` pointer received on an upcall
	/// @return the registered value, or `null` if not found (e.g. after [#unregister])
	T get(MemorySegment state) {
		return registry.get(state.address());
	}

	/// Removes and returns the value registered for `state`.
	///
	/// @param state the `state` pointer received on an upcall, or reconstructed from an id held by
	///              the Java wrapper
	/// @return the removed value, or `null` if not found
	T unregister(MemorySegment state) {
		return registry.remove(state.address());
	}
}
