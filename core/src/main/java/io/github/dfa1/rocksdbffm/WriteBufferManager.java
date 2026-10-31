package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_write_buffer_manager_t`.
///
/// Controls the memory budget for write buffers across multiple column families
/// and databases. Enables shared write-buffer memory management.
///
/// ```
/// try (var wbm = WriteBufferManager.create(MemorySize.ofMB(100), false);
///      var opts = Options.newOptions()
///          .setCreateIfMissing(true)
///          .setWriteBufferManager(wbm)) {
///     var db = RocksDB.openReadWrite(opts, path);
/// }
/// ```
///
/// The write buffer manager uses shared ownership: passing it to [Options#setWriteBufferManager]
/// does not transfer ownership — both objects may be closed independently.
public final class WriteBufferManager extends NativeObject {

	/// `rocksdb_write_buffer_manager_t* rocksdb_write_buffer_manager_create(size_t buffer_size, bool allow_stall);`
	private static final MethodHandle MH_CREATE;
	/// `rocksdb_write_buffer_manager_t* rocksdb_write_buffer_manager_create_with_cache(size_t buffer_size, rocksdb_cache_t* cache);`
	private static final MethodHandle MH_CREATE_WITH_CACHE;
	/// `void rocksdb_write_buffer_manager_destroy(rocksdb_write_buffer_manager_t* wbm);`
	private static final MethodHandle MH_DESTROY;
	/// `bool rocksdb_write_buffer_manager_enabled(rocksdb_write_buffer_manager_t* wbm);`
	private static final MethodHandle MH_ENABLED;
	/// `bool rocksdb_write_buffer_manager_cost_to_cache(rocksdb_write_buffer_manager_t* wbm);`
	private static final MethodHandle MH_COST_TO_CACHE;
	/// `size_t rocksdb_write_buffer_manager_memory_usage(rocksdb_write_buffer_manager_t* wbm);`
	private static final MethodHandle MH_MEMORY_USAGE;
	/// `size_t rocksdb_write_buffer_manager_mutable_memtable_memory_usage(rocksdb_write_buffer_manager_t* wbm);`
	private static final MethodHandle MH_MUTABLE_MEMTABLE_MEMORY_USAGE;
	/// `size_t rocksdb_write_buffer_manager_dummy_entries_in_cache_usage(rocksdb_write_buffer_manager_t* wbm);`
	private static final MethodHandle MH_DUMMY_ENTRIES_IN_CACHE_USAGE;
	/// `size_t rocksdb_write_buffer_manager_buffer_size(rocksdb_write_buffer_manager_t* wbm);`
	private static final MethodHandle MH_BUFFER_SIZE;
	/// `void rocksdb_write_buffer_manager_set_buffer_size(rocksdb_write_buffer_manager_t* wbm, size_t new_size);`
	private static final MethodHandle MH_SET_BUFFER_SIZE;
	/// `void rocksdb_write_buffer_manager_set_allow_stall(rocksdb_write_buffer_manager_t* wbm, bool new_allow_stall);`
	private static final MethodHandle MH_SET_ALLOW_STALL;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_write_buffer_manager_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.JAVA_LONG,
						ValueLayout.JAVA_BYTE));

		MH_CREATE_WITH_CACHE = NativeLibrary.lookup("rocksdb_write_buffer_manager_create_with_cache",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_write_buffer_manager_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_ENABLED = NativeLibrary.lookup("rocksdb_write_buffer_manager_enabled",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_COST_TO_CACHE = NativeLibrary.lookup("rocksdb_write_buffer_manager_cost_to_cache",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_MEMORY_USAGE = NativeLibrary.lookup("rocksdb_write_buffer_manager_memory_usage",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_MUTABLE_MEMTABLE_MEMORY_USAGE = NativeLibrary.lookup("rocksdb_write_buffer_manager_mutable_memtable_memory_usage",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_DUMMY_ENTRIES_IN_CACHE_USAGE = NativeLibrary.lookup("rocksdb_write_buffer_manager_dummy_entries_in_cache_usage",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_BUFFER_SIZE = NativeLibrary.lookup("rocksdb_write_buffer_manager_buffer_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_BUFFER_SIZE = NativeLibrary.lookup("rocksdb_write_buffer_manager_set_buffer_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_SET_ALLOW_STALL = NativeLibrary.lookup("rocksdb_write_buffer_manager_set_allow_stall",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));
	}

	private WriteBufferManager(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a write buffer manager with the specified buffer size and stall behavior.
	///
	/// @param bufferBytes   total memory budget for write buffers in bytes
	/// @param allowStall    whether to stall writes when buffer limit is reached
	/// @return a new [WriteBufferManager]; caller must close it
	public static WriteBufferManager create(MemorySize bufferBytes, boolean allowStall) {
		try {
			MemorySegment ptr = (MemorySegment) MH_CREATE.invokeExact(
					bufferBytes.toBytes(), NativeCalls.toByte(allowStall));
			return new WriteBufferManager(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("WriteBufferManager create failed", t);
		}
	}

	/// Creates a write buffer manager with the specified buffer size, using a cache
	/// for dummy entries.
	///
	/// @param bufferBytes   total memory budget for write buffers in bytes
	/// @param cache         cache for dummy entries (ownership not transferred)
	/// @return a new [WriteBufferManager]; caller must close it
	public static WriteBufferManager createWithCache(MemorySize bufferBytes, Cache cache) {
		try {
			MemorySegment ptr = (MemorySegment) MH_CREATE_WITH_CACHE.invokeExact(
					bufferBytes.toBytes(), cache.ptr());
			return new WriteBufferManager(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("WriteBufferManager createWithCache failed", t);
		}
	}

	/// Returns whether the write buffer manager is enabled.
	///
	/// @return `true` if enabled, `false` otherwise
	public boolean isEnabled() {
		try {
			return NativeCalls.fromByte((byte) MH_ENABLED.invokeExact(ptr()));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("isEnabled failed", t);
		}
	}

	/// Returns whether the cost to cache is enabled.
	///
	/// @return `true` if cost to cache is enabled, `false` otherwise
	public boolean costToCache() {
		try {
			return NativeCalls.fromByte((byte) MH_COST_TO_CACHE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("costToCache failed", t);
		}
	}

	/// Returns the total memory usage of the write buffer manager.
	///
	/// @return memory usage in bytes
	public MemorySize memoryUsage() {
		try {
			return MemorySize.ofBytes((long) MH_MEMORY_USAGE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("memoryUsage failed", t);
		}
	}

	/// Returns the memory usage of mutable memtables.
	///
	/// @return mutable memtable memory usage in bytes
	public MemorySize mutableMemtableMemoryUsage() {
		try {
			return MemorySize.ofBytes((long) MH_MUTABLE_MEMTABLE_MEMORY_USAGE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("mutableMemtableMemoryUsage failed", t);
		}
	}

	/// Returns the memory usage of dummy entries in cache.
	///
	/// @return dummy entries memory usage in bytes
	public MemorySize dummyEntriesInCacheUsage() {
		try {
			return MemorySize.ofBytes((long) MH_DUMMY_ENTRIES_IN_CACHE_USAGE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("dummyEntriesInCacheUsage failed", t);
		}
	}

	/// Returns the configured buffer size.
	///
	/// @return buffer size in bytes
	public MemorySize bufferSize() {
		try {
			return MemorySize.ofBytes((long) MH_BUFFER_SIZE.invokeExact(ptr()));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("bufferSize failed", t);
		}
	}

	/// Sets the buffer size.
	///
	/// @param newSize new buffer size in bytes
	public void setBufferSize(MemorySize newSize) {
		try {
			MH_SET_BUFFER_SIZE.invokeExact(ptr(), newSize.toBytes());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("setBufferSize failed", t);
		}
	}

	/// Sets whether writes should be stalled when buffer limit is reached.
	///
	/// @param allowStall whether to allow stalling
	public void setAllowStall(boolean allowStall) {
		try {
			MH_SET_ALLOW_STALL.invokeExact(ptr(), NativeCalls.toByte(allowStall));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("setAllowStall failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
