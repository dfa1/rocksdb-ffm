package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// Base class for RocksDB block cache implementations (LRU, HyperClock).
///
/// Wraps a `rocksdb_cache_t*`. Pass to
/// [BlockBasedTableOptions#setBlockCache(Cache)] to share a single cache
/// across multiple column families or DB instances.
public abstract class Cache extends NativeObject {

	/// `void rocksdb_cache_destroy(rocksdb_cache_t* cache);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_cache_set_capacity(rocksdb_cache_t* cache, size_t capacity);`
	private static final MethodHandle MH_SET_CAPACITY;
	/// `size_t rocksdb_cache_get_capacity(const rocksdb_cache_t* cache);`
	private static final MethodHandle MH_GET_CAPACITY;
	/// `size_t rocksdb_cache_get_usage(const rocksdb_cache_t* cache);`
	private static final MethodHandle MH_GET_USAGE;
	/// `size_t rocksdb_cache_get_pinned_usage(const rocksdb_cache_t* cache);`
	private static final MethodHandle MH_GET_PINNED_USAGE;
	/// `size_t rocksdb_cache_get_table_address_count(const rocksdb_cache_t* cache);`
	private static final MethodHandle MH_GET_TABLE_ADDRESS_COUNT;
	/// `size_t rocksdb_cache_get_occupancy_count(const rocksdb_cache_t* cache);`
	private static final MethodHandle MH_GET_OCCUPANCY_COUNT;
	/// `void rocksdb_cache_disown_data(rocksdb_cache_t* cache);`
	private static final MethodHandle MH_DISOWN_DATA;

	static {
		MH_DESTROY = NativeLibrary.lookup("rocksdb_cache_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_CAPACITY = NativeLibrary.lookup("rocksdb_cache_set_capacity",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_CAPACITY = NativeLibrary.lookup("rocksdb_cache_get_capacity",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_GET_USAGE = NativeLibrary.lookup("rocksdb_cache_get_usage",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_GET_PINNED_USAGE = NativeLibrary.lookup("rocksdb_cache_get_pinned_usage",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_GET_TABLE_ADDRESS_COUNT = NativeLibrary.lookup("rocksdb_cache_get_table_address_count",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_GET_OCCUPANCY_COUNT = NativeLibrary.lookup("rocksdb_cache_get_occupancy_count",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_DISOWN_DATA = NativeLibrary.lookup("rocksdb_cache_disown_data",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	/// Constructs a cache wrapping the given native pointer.
	///
	/// @param ptr native `rocksdb_cache_t*`
	protected Cache(MemorySegment ptr) {
		super(ptr);
	}

	/// Dynamically resizes the cache. Excess entries are evicted as needed.
	///
	/// @param capacity new cache capacity
	public void setCapacity(MemorySize capacity) {
		NativeFields.setMemorySize(MH_SET_CAPACITY, ptr(), capacity);
	}

	/// Returns the configured capacity of the cache.
	///
	/// @return cache capacity
	public MemorySize getCapacity() {
		return NativeFields.getMemorySize(MH_GET_CAPACITY, ptr());
	}

	/// Returns the current memory usage of the cache.
	///
	/// @return current usage
	public MemorySize getUsage() {
		return NativeFields.getMemorySize(MH_GET_USAGE, ptr());
	}

	/// Returns the amount of memory currently pinned (not eligible for eviction).
	///
	/// @return pinned memory usage
	public MemorySize getPinnedUsage() {
		return NativeFields.getMemorySize(MH_GET_PINNED_USAGE, ptr());
	}

	/// Returns the number of ways the cache's hash table divides its address space, for
	/// inspecting the load factor together with [#getOccupancyCount()] (`usage / capacity` is
	/// the memory-fill ratio; `occupancy / tableAddressCount` is the hash-table load factor —
	/// the two are independent).
	///
	/// @return the hash table's address count, or `0` if this cache implementation doesn't
	///         support this statistic
	public long getTableAddressCount() {
		return NativeFields.getLong(MH_GET_TABLE_ADDRESS_COUNT, ptr());
	}

	/// Returns the number of entries currently tracked in the cache's hash table. Unlike
	/// [#getUsage()] (a memory size), this counts entries regardless of their individual size.
	///
	/// @return the number of tracked entries, or `-1` (native `SIZE_MAX`) if this cache
	///         implementation doesn't support this statistic
	public long getOccupancyCount() {
		return NativeFields.getLong(MH_GET_OCCUPANCY_COUNT, ptr());
	}

	/// Releases this cache's entries without freeing their memory, so the process can exit
	/// faster without paying for per-entry cleanup accounting.
	///
	/// **Only call this when the process is shutting down.** It deliberately leaks memory —
	/// calling it during normal operation leaks for the remaining process lifetime — and every
	/// database using this cache must already be closed first: RocksDB's own documentation
	/// warns that using the cache after this call "will fail terribly."
	public void disownData() {
		try {
			MH_DISOWN_DATA.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("disownData failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
