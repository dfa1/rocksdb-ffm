package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

/// FFM plumbing for [RocksDBCompactionOperations]'s default methods. Kept out of [RocksDB] itself so
/// that class doesn't also have to hold every feature area's `MH_` fields -- see
/// [#131](https://github.com/dfa1/rocksdb-ffm/issues/131).
final class RocksDBCompactionOperationsBindings {

	/// `void rocksdb_compact_range(rocksdb_t* db, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len);`
	private static final MethodHandle MH_COMPACT_RANGE = NativeLibrary.downcall(
		"rocksdb_compact_range", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS, NativeCalls.SIZE_T)
	);
	/// `void rocksdb_compact_range_cf(rocksdb_t* db, rocksdb_column_family_handle_t* column_family, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len);`
	private static final MethodHandle MH_COMPACT_RANGE_CF = NativeLibrary.downcall(
		"rocksdb_compact_range_cf", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS, NativeCalls.SIZE_T)
	);
	/// `void rocksdb_compact_range_opt(rocksdb_t* db, rocksdb_compactoptions_t* opt, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len);`
	private static final MethodHandle MH_COMPACT_RANGE_OPT = NativeLibrary.downcall(
		"rocksdb_compact_range_opt", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS, NativeCalls.SIZE_T)
	);
	/// `void rocksdb_suggest_compact_range(rocksdb_t* db, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len, char** errptr);`
	private static final MethodHandle MH_SUGGEST_COMPACT_RANGE = NativeLibrary.downcall(
		"rocksdb_suggest_compact_range", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS)
	);
	/// `void rocksdb_disable_file_deletions(rocksdb_t* db, char** errptr);`
	private static final MethodHandle MH_DISABLE_FILE_DELETIONS = NativeLibrary.downcall(
		"rocksdb_disable_file_deletions", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
	);
	/// `void rocksdb_enable_file_deletions(rocksdb_t* db, char** errptr);`
	private static final MethodHandle MH_ENABLE_FILE_DELETIONS = NativeLibrary.downcall(
		"rocksdb_enable_file_deletions", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
	);
	/// `void rocksdb_disable_manual_compaction(rocksdb_t* db);`
	private static final MethodHandle MH_DISABLE_MANUAL_COMPACTION = NativeLibrary.downcall(
		"rocksdb_disable_manual_compaction", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);
	/// `void rocksdb_enable_manual_compaction(rocksdb_t* db);`
	private static final MethodHandle MH_ENABLE_MANUAL_COMPACTION = NativeLibrary.downcall(
		"rocksdb_enable_manual_compaction", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);
	/// `void rocksdb_wait_for_compact(rocksdb_t* db, rocksdb_wait_for_compact_options_t* options, char** errptr);`
	private static final MethodHandle MH_WAIT_FOR_COMPACT = NativeLibrary.downcall(
		"rocksdb_wait_for_compact", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
	);
	/// `void rocksdb_delete_file_in_range(rocksdb_t* db, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len, char** errptr);`
	private static final MethodHandle MH_DELETE_FILE_IN_RANGE = NativeLibrary.downcall(
		"rocksdb_delete_file_in_range", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS)
	);
	/// `void rocksdb_delete_file_in_range_cf(rocksdb_t* db, rocksdb_column_family_handle_t* column_family, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len, char** errptr);`
	private static final MethodHandle MH_DELETE_FILE_IN_RANGE_CF = NativeLibrary.downcall(
		"rocksdb_delete_file_in_range_cf", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS, NativeCalls.SIZE_T, ValueLayout.ADDRESS)
	);

	private RocksDBCompactionOperationsBindings() {
	}

	static void compactRangeBytes(RocksDBCompactionOperations db, byte[] startKey, byte[] endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment s = startKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, endKey);
			MH_COMPACT_RANGE.invokeExact(db.dbPtr(),
					s, startKey == null ? 0L : (long) startKey.length,
					e, endKey == null ? 0L : (long) endKey.length);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void compactRangeCfBytes(RocksDBCompactionOperations db, ColumnFamilyHandle cf, byte[] startKey, byte[] endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment s = startKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, endKey);
			MH_COMPACT_RANGE_CF.invokeExact(db.dbPtr(), cf.ptr(),
					s, startKey == null ? 0L : (long) startKey.length,
					e, endKey == null ? 0L : (long) endKey.length);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void compactRangeBuffer(RocksDBCompactionOperations db, ByteBuffer startKey, ByteBuffer endKey) {
		NativeDB guard = NativeDB.enter(db);
		try {
			MemorySegment s = startKey == null ? MemorySegment.NULL : MemorySegment.ofBuffer(startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : MemorySegment.ofBuffer(endKey);
			MH_COMPACT_RANGE.invokeExact(db.dbPtr(),
					s, startKey == null ? 0L : (long) startKey.remaining(),
					e, endKey == null ? 0L : (long) endKey.remaining());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void compactRangeSegment(RocksDBCompactionOperations db, MemorySegment startKey, MemorySegment endKey) {
		NativeDB guard = NativeDB.enter(db);
		try {
			MemorySegment s = startKey == null ? MemorySegment.NULL : startKey;
			MemorySegment e = endKey == null ? MemorySegment.NULL : endKey;
			MH_COMPACT_RANGE.invokeExact(db.dbPtr(),
					s, s == MemorySegment.NULL ? 0L : s.byteSize(),
					e, e == MemorySegment.NULL ? 0L : e.byteSize());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void compactRangeOptBytes(RocksDBCompactionOperations db, CompactOptions opts, byte[] startKey, byte[] endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment s = startKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, endKey);
			MH_COMPACT_RANGE_OPT.invokeExact(db.dbPtr(), opts.ptr(),
					s, startKey == null ? 0L : (long) startKey.length,
					e, endKey == null ? 0L : (long) endKey.length);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void suggestCompactRangeBytes(RocksDBCompactionOperations db, byte[] startKey, byte[] endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment s = startKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, endKey);
			MH_SUGGEST_COMPACT_RANGE.invokeExact(db.dbPtr(),
					s, startKey == null ? 0L : (long) startKey.length,
					e, endKey == null ? 0L : (long) endKey.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("suggestCompactRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void disableFileDeletions(RocksDBCompactionOperations db) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DISABLE_FILE_DELETIONS.invokeExact(db.dbPtr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("disableFileDeletions failed", t);
		} finally {
			guard.exit();
		}
	}

	static void enableFileDeletions(RocksDBCompactionOperations db) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_ENABLE_FILE_DELETIONS.invokeExact(db.dbPtr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("enableFileDeletions failed", t);
		} finally {
			guard.exit();
		}
	}

	static void disableManualCompaction(RocksDBCompactionOperations db) {
		NativeDB guard = NativeDB.enter(db);
		try {
			MH_DISABLE_MANUAL_COMPACTION.invokeExact(db.dbPtr());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("disableManualCompaction failed", t);
		} finally {
			guard.exit();
		}
	}

	static void enableManualCompaction(RocksDBCompactionOperations db) {
		NativeDB guard = NativeDB.enter(db);
		try {
			MH_ENABLE_MANUAL_COMPACTION.invokeExact(db.dbPtr());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("enableManualCompaction failed", t);
		} finally {
			guard.exit();
		}
	}

	static void waitForCompact(RocksDBCompactionOperations db, WaitForCompactOptions options) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_WAIT_FOR_COMPACT.invokeExact(db.dbPtr(), options.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("waitForCompact failed", t);
		} finally {
			guard.exit();
		}
	}

	static void deleteFilesInRangeBytes(RocksDBCompactionOperations db, byte[] startKey, byte[] endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment s = startKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, endKey);
			MH_DELETE_FILE_IN_RANGE.invokeExact(db.dbPtr(),
					s, startKey == null ? 0L : (long) startKey.length,
					e, endKey == null ? 0L : (long) endKey.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteFilesInRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void deleteFilesInRangeBuffer(RocksDBCompactionOperations db, ByteBuffer startKey, ByteBuffer endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment s = startKey == null ? MemorySegment.NULL : MemorySegment.ofBuffer(startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : MemorySegment.ofBuffer(endKey);
			MH_DELETE_FILE_IN_RANGE.invokeExact(db.dbPtr(),
					s, startKey == null ? 0L : (long) startKey.remaining(),
					e, endKey == null ? 0L : (long) endKey.remaining(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteFilesInRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void deleteFilesInRangeSegment(RocksDBCompactionOperations db, MemorySegment startKey, MemorySegment endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment s = startKey == null ? MemorySegment.NULL : startKey;
			MemorySegment e = endKey == null ? MemorySegment.NULL : endKey;
			MH_DELETE_FILE_IN_RANGE.invokeExact(db.dbPtr(),
					s, s == MemorySegment.NULL ? 0L : s.byteSize(),
					e, e == MemorySegment.NULL ? 0L : e.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteFilesInRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void deleteFilesInRangeCfBytes(RocksDBCompactionOperations db, ColumnFamilyHandle cf, byte[] startKey, byte[] endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment s = startKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, endKey);
			MH_DELETE_FILE_IN_RANGE_CF.invokeExact(db.dbPtr(), cf.ptr(),
					s, startKey == null ? 0L : (long) startKey.length,
					e, endKey == null ? 0L : (long) endKey.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteFilesInRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void deleteFilesInRangeCfBuffer(RocksDBCompactionOperations db, ColumnFamilyHandle cf, ByteBuffer startKey, ByteBuffer endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment s = startKey == null ? MemorySegment.NULL : MemorySegment.ofBuffer(startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : MemorySegment.ofBuffer(endKey);
			MH_DELETE_FILE_IN_RANGE_CF.invokeExact(db.dbPtr(), cf.ptr(),
					s, startKey == null ? 0L : (long) startKey.remaining(),
					e, endKey == null ? 0L : (long) endKey.remaining(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteFilesInRange failed", t);
		} finally {
			guard.exit();
		}
	}

	static void deleteFilesInRangeCfSegment(RocksDBCompactionOperations db, ColumnFamilyHandle cf, MemorySegment startKey, MemorySegment endKey) {
		NativeDB guard = NativeDB.enter(db);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment s = startKey == null ? MemorySegment.NULL : startKey;
			MemorySegment e = endKey == null ? MemorySegment.NULL : endKey;
			MH_DELETE_FILE_IN_RANGE_CF.invokeExact(db.dbPtr(), cf.ptr(),
					s, s == MemorySegment.NULL ? 0L : s.byteSize(),
					e, e == MemorySegment.NULL ? 0L : e.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteFilesInRange failed", t);
		} finally {
			guard.exit();
		}
	}
}
