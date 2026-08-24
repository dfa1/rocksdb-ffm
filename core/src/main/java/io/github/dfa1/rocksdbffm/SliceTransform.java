package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_slicetransform_t`.
///
/// Use inside a try-with-resources block. If the transform is passed to
/// [Options#setPrefixExtractor(SliceTransform)], ownership transfers to
/// RocksDB's internal reference counting and [#close()] becomes a no-op —
/// it is safe (and recommended) to still call it via try-with-resources.
///
/// ```
/// try (var prefix = SliceTransform.newFixedPrefix(8);
///      var opts = Options.newOptions().setCreateIfMissing(true).setPrefixExtractor(prefix)) {
///     // prefix.close() called automatically — no-op because ownership transferred
/// }
/// ```
public final class SliceTransform extends NativeObject {

	/// `rocksdb_slicetransform_t* rocksdb_slicetransform_create_fixed_prefix(size_t);`
	private static final MethodHandle MH_CREATE_FIXED_PREFIX;
	/// `void rocksdb_slicetransform_destroy(rocksdb_slicetransform_t*);`
	private static final MethodHandle MH_DESTROY;

	static {
		MH_CREATE_FIXED_PREFIX = NativeLibrary.lookup("rocksdb_slicetransform_create_fixed_prefix",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_slicetransform_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	private SliceTransform(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a fixed-length prefix transform: the prefix of every key is its
	/// leading `prefixLen` bytes. Keys shorter than `prefixLen` are outside the
	/// transform's domain — RocksDB skips prefix-based filtering/bloom entries
	/// for them entirely, so this is only safe when every key is at least
	/// `prefixLen` bytes long.
	///
	/// @param prefixLen number of leading key bytes that make up the prefix
	/// @return a new [SliceTransform]; caller must close it (or transfer ownership via [Options#setPrefixExtractor(SliceTransform)])
	/// @throws IllegalArgumentException if `prefixLen` is negative
	public static SliceTransform newFixedPrefix(long prefixLen) {
		if (prefixLen < 0) {
			throw new IllegalArgumentException("prefixLen must not be negative: " + prefixLen);
		}
		try {
			return new SliceTransform((MemorySegment) MH_CREATE_FIXED_PREFIX.invokeExact(prefixLen));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("SliceTransform.newFixedPrefix failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
