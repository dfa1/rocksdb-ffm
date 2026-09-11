package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalLong;

/// FFM plumbing for [RocksDBReadOperations]'s default methods, plus [TransactionDB]'s
/// column-family-scoped property reads (it implements neither [RocksDBReadOperations] nor
/// [RocksDBWriteOperations], but shares the CF-scoped property native symbols with them since
/// there's no separate `rocksdb_transactiondb_property_value_cf`). Kept out of [RocksDB] itself
/// so that class doesn't also have to hold every feature area's `MH_` fields -- see
/// [#131](https://github.com/dfa1/rocksdbffm/issues/131).
final class RocksDBReadOperationsBindings {

	/// `rocksdb_pinnable_handle_t* rocksdb_get_pinned_v2(rocksdb_t* db, const rocksdb_readoptions_t* options, const char* key, size_t keylen, char** errptr);`
	private static final MethodHandle MH_GET_PINNED_V2;
	/// `rocksdb_pinnable_handle_t* rocksdb_get_pinned_cf_v2(rocksdb_t* db, const rocksdb_readoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t keylen, char** errptr);`
	private static final MethodHandle MH_GET_PINNED_CF_V2;
	/// `unsigned char rocksdb_get_into_buffer(rocksdb_t* db, const rocksdb_readoptions_t* options, const char* key, size_t keylen, char* buffer, size_t buffer_size, size_t* vallen, unsigned char* found, char** errptr);`
	private static final MethodHandle MH_GET_INTO_BUFFER;
	/// `unsigned char rocksdb_get_into_buffer_cf(rocksdb_t* db, const rocksdb_readoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t keylen, char* buffer, size_t buffer_size, size_t* vallen, unsigned char* found, char** errptr);`
	private static final MethodHandle MH_GET_INTO_BUFFER_CF;
	/// `unsigned char rocksdb_key_may_exist(rocksdb_t* db, const rocksdb_readoptions_t* options, const char* key, size_t key_len, char** value, size_t* val_len, const char* timestamp, size_t timestamp_len, unsigned char* value_found);`
	private static final MethodHandle MH_KEY_MAY_EXIST;
	/// `unsigned char rocksdb_key_may_exist_cf(rocksdb_t* db, const rocksdb_readoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t key_len, char** value, size_t* val_len, const char* timestamp, size_t timestamp_len, unsigned char* value_found);`
	private static final MethodHandle MH_KEY_MAY_EXIST_CF;
	/// `rocksdb_iterator_t* rocksdb_create_iterator_cf(rocksdb_t* db, const rocksdb_readoptions_t* options, rocksdb_column_family_handle_t* column_family);`
	private static final MethodHandle MH_CREATE_ITERATOR_CF;
	/// `const rocksdb_snapshot_t* rocksdb_create_snapshot(rocksdb_t* db);`
	private static final MethodHandle MH_CREATE_SNAPSHOT;
	/// `char* rocksdb_property_value(rocksdb_t* db, const char* propname);`
	private static final MethodHandle MH_PROPERTY_VALUE;
	/// `int rocksdb_property_int(rocksdb_t* db, const char* propname, uint64_t* out_val);`
	private static final MethodHandle MH_PROPERTY_INT;
	/// `char* rocksdb_property_value_cf(rocksdb_t* db, rocksdb_column_family_handle_t* column_family, const char* propname);`
	private static final MethodHandle MH_PROPERTY_VALUE_CF;
	/// `int rocksdb_property_int_cf(rocksdb_t* db, rocksdb_column_family_handle_t* column_family, const char* propname, uint64_t* out_val);`
	private static final MethodHandle MH_PROPERTY_INT_CF;

	static {
		MH_GET_PINNED_V2 = NativeLibrary.lookup("rocksdb_get_pinned_v2",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_GET_PINNED_CF_V2 = NativeLibrary.lookup("rocksdb_get_pinned_cf_v2",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_GET_INTO_BUFFER = NativeLibrary.lookup("rocksdb_get_into_buffer",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS));

		MH_GET_INTO_BUFFER_CF = NativeLibrary.lookup("rocksdb_get_into_buffer_cf",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS));

		MH_KEY_MAY_EXIST = NativeLibrary.lookup("rocksdb_key_may_exist",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_KEY_MAY_EXIST_CF = NativeLibrary.lookup("rocksdb_key_may_exist_cf",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_CREATE_ITERATOR_CF = NativeLibrary.lookup("rocksdb_create_iterator_cf",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_CREATE_SNAPSHOT = NativeLibrary.lookup("rocksdb_create_snapshot",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_PROPERTY_VALUE = NativeLibrary.lookup("rocksdb_property_value",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_PROPERTY_INT = NativeLibrary.lookup("rocksdb_property_int",
				FunctionDescriptor.of(ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_PROPERTY_VALUE_CF = NativeLibrary.lookup("rocksdb_property_value_cf",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_PROPERTY_INT_CF = NativeLibrary.lookup("rocksdb_property_int_cf",
				FunctionDescriptor.of(ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private RocksDBReadOperationsBindings() {
	}

	/// Single-copy byte[] get: pins the value via `rocksdb_get_pinned_v2` and copies it out
	/// once. Not zero-copy — the returned array is a copy by definition — but cheaper than
	/// `rocksdb_get`, which per `c.h` returns "a malloc()ed array" the caller must free:
	/// that path copies the value into a fresh native buffer first, so producing a byte[]
	/// from it costs two copies plus a malloc/free round trip. Pinning skips the
	/// intermediate buffer entirely; `destroy` just drops the pin.
	///
	/// Uses the `_v2` handle rather than the older `rocksdb_get_pinned`, per `c.h`'s note
	/// on that family: "These functions avoid unnecessary memory allocations and copies.
	/// Bindings should migrate to these for better performance." [Transaction] and
	/// [TransactionDB] have no `_v2` equivalent in the C API and still go through
	/// [PinnableSlice].
	///
	/// Returns `null` if not found.
	static byte[] getBytes(RocksDBReadOperations db, ReadOptions readOpts, byte[] key) {
		// Single arena: it already needs one to marshal `key`, and withPinned would open
		// its own, paying for two Arena.ofConfined() per get instead of one.
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment k = NativeCalls.toNative(arena, key);
			MemorySegment handle = (MemorySegment) MH_GET_PINNED_V2.invokeExact(
					db.dbPtr(), readOpts.ptr(), k, (long) key.length, err);
			NativeCalls.checkError(err);
			if (MemorySegment.NULL.equals(handle)) {
				return null;
			}
			try (PinnableHandle ph = PinnableHandle.wrap(handle)) {
				return ph.toByteArray(err);
			}
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("get failed", t);
		}
	}

	/// ByteBuffer get via `rocksdb_get_into_buffer` — copies directly into the caller's buffer,
	/// with no intermediate PinnableSlice. Copies nothing when the buffer is too small.
	static CopyResult getIntoBuffer(RocksDBReadOperations db, ReadOptions readOpts, MemorySegment key, ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment valLenSeg = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment foundSeg = arena.allocate(ValueLayout.JAVA_BYTE);
			byte fit = (byte) MH_GET_INTO_BUFFER.invokeExact(db.dbPtr(), readOpts.ptr(), key, key.byteSize(),
					MemorySegment.ofBuffer(value), (long) value.remaining(), valLenSeg, foundSeg, err);
			NativeCalls.checkError(err);
			if (foundSeg.get(ValueLayout.JAVA_BYTE, 0) == 0) {
				return CopyResult.NotFound.INSTANCE;
			}
			long valLen = valLenSeg.get(ValueLayout.JAVA_LONG, 0);
			if (fit == 0) {
				return new CopyResult.NotEnoughCapacity(valLen);
			}
			value.position(value.position() + (int) valLen);
			return CopyResult.Copied.INSTANCE;
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("get failed", t);
		}
	}

	/// MemorySegment get via `rocksdb_get_into_buffer` — copies directly into the caller's
	/// segment, with no intermediate PinnableSlice. Copies nothing when `value` is too small.
	static CopyResult getIntoSegment(RocksDBReadOperations db, ReadOptions readOpts, MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment valLenSeg = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment foundSeg = arena.allocate(ValueLayout.JAVA_BYTE);
			byte fit = (byte) MH_GET_INTO_BUFFER.invokeExact(db.dbPtr(), readOpts.ptr(), key, key.byteSize(),
					value, value.byteSize(), valLenSeg, foundSeg, err);
			NativeCalls.checkError(err);
			if (foundSeg.get(ValueLayout.JAVA_BYTE, 0) == 0) {
				return CopyResult.NotFound.INSTANCE;
			}
			long valLen = valLenSeg.get(ValueLayout.JAVA_LONG, 0);
			if (fit == 0) {
				return new CopyResult.NotEnoughCapacity(valLen);
			}
			return CopyResult.Copied.INSTANCE;
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("get failed", t);
		}
	}

	/// Scoped zero-copy get via `rocksdb_get_pinned_v2`. [PinnableHandle] owns the pinned
	/// value's lifetime and every way it gets consumed.
	static <R> R withPinned(RocksDBReadOperations db, ReadOptions readOpts, MemorySegment key, Mapper<R> fn) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment handle = (MemorySegment) MH_GET_PINNED_V2.invokeExact(db.dbPtr(), readOpts.ptr(), key, key.byteSize(), err);
			NativeCalls.checkError(err);
			if (MemorySegment.NULL.equals(handle)) {
				return null;
			}
			try (PinnableHandle ph = PinnableHandle.wrap(handle)) {
				return ph.map(arena, fn, err);
			}
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("get_pinned failed", t);
		}
	}

	/// Scoped zero-copy get from `cf` via `rocksdb_get_pinned_cf_v2`. See [#withPinned]
	/// for the lifetime contract.
	static <R> R withPinnedCf(RocksDBReadOperations db, ReadOptions readOpts, ColumnFamilyHandle cf,
	                           MemorySegment key, Mapper<R> fn) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment handle = (MemorySegment) MH_GET_PINNED_CF_V2.invokeExact(
					db.dbPtr(), readOpts.ptr(), cf.ptr(), key, key.byteSize(), err);
			NativeCalls.checkError(err);
			if (MemorySegment.NULL.equals(handle)) {
				return null;
			}
			try (PinnableHandle ph = PinnableHandle.wrap(handle)) {
				return ph.map(arena, fn, err);
			}
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("get_pinned failed", t);
		}
	}

	/// Single-copy byte[] get from `cf` via `rocksdb_get_pinned_cf_v2`. See [#getBytes] for
	/// why this pins rather than calling `rocksdb_get`, and why it uses the `_v2` handle.
	/// Returns `null` if not found.
	static byte[] getCfBytes(RocksDBReadOperations db, ReadOptions readOpts, ColumnFamilyHandle cf,
	                         byte[] key) {
		// Single arena, same reasoning as getBytes above.
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment k = NativeCalls.toNative(arena, key);
			MemorySegment handle = (MemorySegment) MH_GET_PINNED_CF_V2.invokeExact(
					db.dbPtr(), readOpts.ptr(), cf.ptr(), k, (long) key.length, err);
			NativeCalls.checkError(err);
			if (MemorySegment.NULL.equals(handle)) {
				return null;
			}
			try (PinnableHandle ph = PinnableHandle.wrap(handle)) {
				return ph.toByteArray(err);
			}
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("get failed", t);
		}
	}

	/// ByteBuffer get with explicit column family via `rocksdb_get_into_buffer_cf`.
	/// Copies nothing when the buffer is too small.
	static CopyResult getCfIntoBuffer(RocksDBReadOperations db, ReadOptions readOpts, ColumnFamilyHandle cf,
	                                  MemorySegment key, ByteBuffer value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment valLenSeg = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment foundSeg = arena.allocate(ValueLayout.JAVA_BYTE);
			byte fit = (byte) MH_GET_INTO_BUFFER_CF.invokeExact(db.dbPtr(), readOpts.ptr(), cf.ptr(), key, key.byteSize(),
					MemorySegment.ofBuffer(value), (long) value.remaining(), valLenSeg, foundSeg, err);
			NativeCalls.checkError(err);
			if (foundSeg.get(ValueLayout.JAVA_BYTE, 0) == 0) {
				return CopyResult.NotFound.INSTANCE;
			}
			long valLen = valLenSeg.get(ValueLayout.JAVA_LONG, 0);
			if (fit == 0) {
				return new CopyResult.NotEnoughCapacity(valLen);
			}
			value.position(value.position() + (int) valLen);
			return CopyResult.Copied.INSTANCE;
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("get failed", t);
		}
	}

	/// MemorySegment get with explicit column family via `rocksdb_get_into_buffer_cf` —
	/// copies directly into the caller's segment. Copies nothing when `value` is too small.
	static CopyResult getCfIntoSegment(RocksDBReadOperations db, ReadOptions readOpts, ColumnFamilyHandle cf,
	                                   MemorySegment key, MemorySegment value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment valLenSeg = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment foundSeg = arena.allocate(ValueLayout.JAVA_BYTE);
			byte fit = (byte) MH_GET_INTO_BUFFER_CF.invokeExact(db.dbPtr(), readOpts.ptr(), cf.ptr(), key, key.byteSize(),
					value, value.byteSize(), valLenSeg, foundSeg, err);
			NativeCalls.checkError(err);
			if (foundSeg.get(ValueLayout.JAVA_BYTE, 0) == 0) {
				return CopyResult.NotFound.INSTANCE;
			}
			long valLen = valLenSeg.get(ValueLayout.JAVA_LONG, 0);
			if (fit == 0) {
				return new CopyResult.NotEnoughCapacity(valLen);
			}
			return CopyResult.Copied.INSTANCE;
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("get failed", t);
		}
	}

	static boolean keyMayExistSegment(RocksDBReadOperations db, ReadOptions roOpts, MemorySegment key) {
		try {
			return NativeCalls.fromByte((byte) MH_KEY_MAY_EXIST.invokeExact(db.dbPtr(), roOpts.ptr(), key, key.byteSize(),
					MemorySegment.NULL, MemorySegment.NULL,
					MemorySegment.NULL, 0L, MemorySegment.NULL));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("keyMayExist failed", t);
		}
	}

	/// [#keyMayExistSegment] for a `byte[]` key: marshals `key` into a scratch [Arena]
	/// before delegating.
	static boolean keyMayExistBytes(RocksDBReadOperations db, ReadOptions roOpts, byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			return keyMayExistSegment(db, roOpts, NativeCalls.toNative(arena, key));
		}
	}

	static boolean keyMayExistCfSegment(RocksDBReadOperations db, ReadOptions roOpts,
	                                    ColumnFamilyHandle cf, MemorySegment key) {
		try {
			return NativeCalls.fromByte((byte) MH_KEY_MAY_EXIST_CF.invokeExact(db.dbPtr(), roOpts.ptr(), cf.ptr(), key, key.byteSize(),
					MemorySegment.NULL, MemorySegment.NULL,
					MemorySegment.NULL, 0L, MemorySegment.NULL));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("keyMayExist failed", t);
		}
	}

	/// [#keyMayExistCfSegment] for a `byte[]` key: marshals `key` into a scratch [Arena]
	/// before delegating.
	static boolean keyMayExistCfBytes(RocksDBReadOperations db, ReadOptions roOpts,
	                                  ColumnFamilyHandle cf, byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			return keyMayExistCfSegment(db, roOpts, cf, NativeCalls.toNative(arena, key));
		}
	}

	static RocksIterator createIteratorCf(RocksDBReadOperations db, ReadOptions readOpts,
	                                      ColumnFamilyHandle cf) {
		try {
			MemorySegment iterPtr = (MemorySegment) MH_CREATE_ITERATOR_CF.invokeExact(
					db.dbPtr(), readOpts.ptr(), cf.ptr());
			// Every implementor extends NativeObjectWithChildren (see RocksDBReadOperations#getSnapshot()).
			return RocksIterator.create((NativeObjectWithChildren) db, iterPtr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("newIterator failed", t);
		}
	}

	static Snapshot createSnapshot(NativeObjectWithChildren owningDb, MemorySegment db) {
		try {
			MemorySegment snapPtr = (MemorySegment) MH_CREATE_SNAPSHOT.invokeExact(db);
			return new Snapshot(owningDb, db, snapPtr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getSnapshot failed", t);
		}
	}

	static Optional<String> getProperty(RocksDBReadOperations db, Property property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment propSeg = arena.allocateFrom(property.propertyName());
			MemorySegment result = (MemorySegment) MH_PROPERTY_VALUE.invokeExact(db.dbPtr(), propSeg);
			return NativeCalls.toOptionalString(result);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getProperty failed", t);
		}
	}

	static OptionalLong getLongProperty(RocksDBReadOperations db, Property property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment propSeg = arena.allocateFrom(property.propertyName());
			MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG);
			int rc = (int) MH_PROPERTY_INT.invokeExact(db.dbPtr(), propSeg, out);
			if (rc != 0) {
				return OptionalLong.empty();
			}
			return OptionalLong.of(out.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getLongProperty failed", t);
		}
	}

	static Optional<String> getPropertyCf(MemorySegment db, ColumnFamilyHandle cf,
	                                       Property property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment propSeg = arena.allocateFrom(property.propertyName());
			MemorySegment result = (MemorySegment) MH_PROPERTY_VALUE_CF.invokeExact(
					db, cf.ptr(), propSeg);
			return NativeCalls.toOptionalString(result);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getProperty failed", t);
		}
	}

	static OptionalLong getLongPropertyCf(MemorySegment db, ColumnFamilyHandle cf,
	                                      Property property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment propSeg = arena.allocateFrom(property.propertyName());
			MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG);
			int rc = (int) MH_PROPERTY_INT_CF.invokeExact(db, cf.ptr(), propSeg, out);
			if (rc != 0) {
				return OptionalLong.empty();
			}
			return OptionalLong.of(out.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getLongProperty failed", t);
		}
	}
}
