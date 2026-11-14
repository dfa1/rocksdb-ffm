package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_column_family_handle_t`.
///
/// Obtained via [RocksDB#openReadWrite] or [ReadWriteDB#createColumnFamily].
/// Must be closed after use to release the native handle.
///
/// ```
/// try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dbOpts, path);
///      var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1"))) {
///     db.put(cf, "key".getBytes(), "value".getBytes());
/// }
/// ```
public final class ColumnFamilyHandle extends NativeObject {

	/// `void rocksdb_column_family_handle_destroy(rocksdb_column_family_handle_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `rocksdb_column_family_handle_t* rocksdb_get_default_column_family_handle(rocksdb_t* db);`
	private static final MethodHandle MH_GET_DEFAULT;
	/// `uint32_t rocksdb_column_family_handle_get_id(rocksdb_column_family_handle_t* handle);`
	private static final MethodHandle MH_GET_ID;
	/// `char* rocksdb_column_family_handle_get_name(rocksdb_column_family_handle_t* handle, size_t* name_len);`
	private static final MethodHandle MH_GET_NAME;

	static {
		MH_DESTROY = NativeLibrary.lookup("rocksdb_column_family_handle_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_GET_DEFAULT = NativeLibrary.lookup("rocksdb_get_default_column_family_handle",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_GET_ID = NativeLibrary.lookup("rocksdb_column_family_handle_get_id",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_GET_NAME = NativeLibrary.lookup("rocksdb_column_family_handle_get_name",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private ColumnFamilyHandle(MemorySegment ptr) {
		super(ptr);
	}

	static ColumnFamilyHandle wrap(MemorySegment ptr) {
		return new ColumnFamilyHandle(ptr);
	}

	/// Returns a new native handle to the default column family of `dbPtr`, for a database opened
	/// without column family descriptors. The only mapping of `rocksdb_get_default_column_family_handle`.
	///
	/// @param dbPtr the `rocksdb_t*` of the database
	/// @return a new handle; the caller (the database) owns it
	static MemorySegment defaultHandle(MemorySegment dbPtr) {
		try {
			return (MemorySegment) MH_GET_DEFAULT.invokeExact(dbPtr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("defaultHandle failed", t);
		}
	}

	/// Returns the numeric ID of this column family.
	///
	/// @return column family ID
	public int getId() {
		return NativeFields.getInt(MH_GET_ID, ptr());
	}

	/// Returns the name of this column family.
	///
	/// @return column family name
	public ColumnFamilyName getName() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment namePtr = (MemorySegment) MH_GET_NAME.invokeExact(ptr(), lenSeg);
			long nameLen = lenSeg.get(ValueLayout.JAVA_LONG, 0);
			// namePtr is malloc'd by CopyString() on the C side (db/c.cc) -- unlike the other
			// toByteArray() call sites in this codebase, this one owns the pointer and must free it.
			byte[] bytes = NativeCalls.toByteArray(namePtr, nameLen);
			NativeCalls.free(namePtr);
			return ColumnFamilyName.ofBytes(bytes);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getName failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
