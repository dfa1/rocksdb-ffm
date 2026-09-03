package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// LRU block cache (`rocksdb_cache_t`).
/// ```
/// try (LRUCache cache = LRUCache.newLRUCache(MemorySize.ofMB(64))) {
///     BlockBasedTableOptions tbl = BlockBasedTableOptions.newBlockBasedTableOptions()
///         .setBlockCache(cache);
///     ...
/// }
/// ```
public final class LRUCache extends Cache {

	/// `rocksdb_cache_t* rocksdb_cache_create_lru(size_t capacity);`
	private static final MethodHandle MH_CREATE;
	/// `rocksdb_cache_t* rocksdb_cache_create_lru_with_strict_capacity_limit(size_t capacity);`
	private static final MethodHandle MH_CREATE_STRICT;
	/// `rocksdb_lru_cache_options_t* rocksdb_lru_cache_options_create(void);`
	private static final MethodHandle MH_OPTS_CREATE;
	/// `void rocksdb_lru_cache_options_destroy(rocksdb_lru_cache_options_t*);`
	private static final MethodHandle MH_OPTS_DESTROY;
	/// `void rocksdb_lru_cache_options_set_capacity(rocksdb_lru_cache_options_t*, size_t);`
	private static final MethodHandle MH_OPTS_SET_CAPACITY;
	/// `void rocksdb_lru_cache_options_set_num_shard_bits(rocksdb_lru_cache_options_t*, int);`
	private static final MethodHandle MH_OPTS_SET_NUM_SHARD_BITS;
	/// `rocksdb_cache_t* rocksdb_cache_create_lru_opts(const rocksdb_lru_cache_options_t*);`
	private static final MethodHandle MH_CREATE_OPTS;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_cache_create_lru",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_CREATE_STRICT = NativeLibrary.lookup("rocksdb_cache_create_lru_with_strict_capacity_limit",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_OPTS_CREATE = NativeLibrary.lookup("rocksdb_lru_cache_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_OPTS_DESTROY = NativeLibrary.lookup("rocksdb_lru_cache_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_OPTS_SET_CAPACITY = NativeLibrary.lookup("rocksdb_lru_cache_options_set_capacity",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_OPTS_SET_NUM_SHARD_BITS = NativeLibrary.lookup("rocksdb_lru_cache_options_set_num_shard_bits",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_CREATE_OPTS = NativeLibrary.lookup("rocksdb_cache_create_lru_opts",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private LRUCache(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates an LRU block cache with the given capacity.
	///
	/// @param capacity total cache capacity
	/// @return a new [LRUCache]; caller must close it
	public static LRUCache newLRUCache(MemorySize capacity) {
		try {
			return new LRUCache((MemorySegment) MH_CREATE.invokeExact(capacity.toBytes()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("LRUCache create failed", t);
		}
	}

	/// Creates an LRU block cache with the given capacity and, optionally, a strict capacity
	/// limit.
	///
	/// @param capacity            total cache capacity
	/// @param strictCapacityLimit if `true`, an insert fails outright once the cache is full of
	///                            pinned (referenced) entries rather than evicting only
	///                            unreferenced ones; if `false` (the default under
	///                            [#newLRUCache(MemorySize)]), inserts never fail
	/// @return a new [LRUCache]; caller must close it
	public static LRUCache newLRUCache(MemorySize capacity, boolean strictCapacityLimit) {
		try {
			if (strictCapacityLimit) {
				return new LRUCache((MemorySegment) MH_CREATE_STRICT.invokeExact(capacity.toBytes()));
			}
			return new LRUCache((MemorySegment) MH_CREATE.invokeExact(capacity.toBytes()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("LRUCache create failed", t);
		}
	}

	/// Creates an LRU block cache with an explicit shard count. Note: the underlying C API has
	/// no combined "strict capacity limit and explicit shard count" constructor — this overload
	/// and [#newLRUCache(MemorySize, boolean)] are independent knobs that cannot both be set on
	/// the same cache.
	///
	/// @param capacity     total cache capacity
	/// @param numShardBits number of shard bits (`shards = 1 << numShardBits`); pass `-1` to let
	///                     RocksDB choose automatically
	/// @return a new [LRUCache]; caller must close it
	public static LRUCache newLRUCache(MemorySize capacity, int numShardBits) {
		MemorySegment opts = null;
		try {
			opts = (MemorySegment) MH_OPTS_CREATE.invokeExact();
			MH_OPTS_SET_CAPACITY.invokeExact(opts, capacity.toBytes());
			MH_OPTS_SET_NUM_SHARD_BITS.invokeExact(opts, numShardBits);
			return new LRUCache((MemorySegment) MH_CREATE_OPTS.invokeExact(opts));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("LRUCache create failed", t);
		} finally {
			if (opts != null) {
				try {
					MH_OPTS_DESTROY.invokeExact(opts);
				} catch (Throwable ignored) {
				}
			}
		}
	}
}
