package io.github.dfa1.rocksdbffm;

/// Result of a copy-into-buffer get, e.g. [ReadWriteDB#get(java.nio.ByteBuffer, java.nio.ByteBuffer)].
///
/// Replaces the previous `int` return (value length, or `-1` if not found): that encoding let a
/// too-small destination silently truncate the value while still reporting the full length, and let
/// a value above `Integer.MAX_VALUE` collide with the `-1` not-found sentinel.
///
/// On [Copied], the destination `ByteBuffer` is left in fill mode: only its position advances, its
/// limit is untouched. This matches `java.nio.channels.ReadableByteChannel#read(ByteBuffer)`, not
/// `rocksdbjni`'s `RocksIterator`/`RocksDB` `ByteBuffer` methods, which flip the destination for you.
/// A caller migrating from `rocksdbjni`, especially one reusing a pooled buffer whose limit is still
/// at its old capacity, must call `dst.flip()` before reading — otherwise the read past `position()`
/// silently returns stale bytes from the buffer's previous contents instead of failing loudly.
///
/// ```
/// switch (db.get(key, dst)) {
///     case CopyResult.Copied() -> dst.flip();
///     case CopyResult.NotEnoughCapacity(long required) -> retryWithCapacity(required);
///     case CopyResult.NotFound() -> handleMiss();
/// }
/// ```
public sealed interface CopyResult {

	/// The value fit and was copied in full; the destination's position was advanced accordingly.
	record Copied() implements CopyResult {
		// Stateless; every call site shares this instance instead of allocating a fresh one.
		static final Copied INSTANCE = new Copied();
	}

	/// The value exists but does not fit in the destination. Nothing was copied and the
	/// destination is unchanged.
	///
	/// @param required the value's actual length in bytes
	record NotEnoughCapacity(long required) implements CopyResult {
	}

	/// The key does not exist. The destination is unchanged.
	record NotFound() implements CopyResult {
		// Stateless; every call site shares this instance instead of allocating a fresh one.
		static final NotFound INSTANCE = new NotFound();
	}
}
