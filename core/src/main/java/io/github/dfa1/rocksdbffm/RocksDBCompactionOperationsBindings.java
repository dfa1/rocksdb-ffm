package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

/// FFM plumbing for [RocksDBCompactionOperations]'s default methods. Kept out of [RocksDB] itself so
/// that class doesn't also have to hold every feature area's `MH_` fields -- see
/// [#131](https://github.com/dfa1/rocksdbffm/issues/131).
final class RocksDBCompactionOperationsBindings {

	/// `void rocksdb_compact_range(rocksdb_t* db, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len);`
	private static final MethodHandle MH_COMPACT_RANGE;
	/// `void rocksdb_compact_range_opt(rocksdb_t* db, rocksdb_compactoptions_t* opt, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len);`
	private static final MethodHandle MH_COMPACT_RANGE_OPT;
	/// `void rocksdb_suggest_compact_range(rocksdb_t* db, const char* start_key, size_t start_key_len, const char* limit_key, size_t limit_key_len, char** errptr);`
	private static final MethodHandle MH_SUGGEST_COMPACT_RANGE;
	/// `void rocksdb_disable_file_deletions(rocksdb_t* db, char** errptr);`
	private static final MethodHandle MH_DISABLE_FILE_DELETIONS;
	/// `void rocksdb_enable_file_deletions(rocksdb_t* db, char** errptr);`
	private static final MethodHandle MH_ENABLE_FILE_DELETIONS;
	/// `void rocksdb_disable_manual_compaction(rocksdb_t* db);`
	private static final MethodHandle MH_DISABLE_MANUAL_COMPACTION;
	/// `void rocksdb_enable_manual_compaction(rocksdb_t* db);`
	private static final MethodHandle MH_ENABLE_MANUAL_COMPACTION;
	/// `void rocksdb_wait_for_compact(rocksdb_t* db, rocksdb_wait_for_compact_options_t* options, char** errptr);`
	private static final MethodHandle MH_WAIT_FOR_COMPACT;

	static {
		MH_COMPACT_RANGE = NativeLibrary.lookup("rocksdb_compact_range",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_COMPACT_RANGE_OPT = NativeLibrary.lookup("rocksdb_compact_range_opt",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_SUGGEST_COMPACT_RANGE = NativeLibrary.lookup("rocksdb_suggest_compact_range",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_DISABLE_FILE_DELETIONS = NativeLibrary.lookup("rocksdb_disable_file_deletions",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_ENABLE_FILE_DELETIONS = NativeLibrary.lookup("rocksdb_enable_file_deletions",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_DISABLE_MANUAL_COMPACTION = NativeLibrary.lookup("rocksdb_disable_manual_compaction",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_ENABLE_MANUAL_COMPACTION = NativeLibrary.lookup("rocksdb_enable_manual_compaction",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_WAIT_FOR_COMPACT = NativeLibrary.lookup("rocksdb_wait_for_compact",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private RocksDBCompactionOperationsBindings() {
	}

	static void compactRangeBytes(RocksDBCompactionOperations db, byte[] startKey, byte[] endKey) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment s = startKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, endKey);
			MH_COMPACT_RANGE.invokeExact(db.dbPtr(),
					s, startKey == null ? 0L : (long) startKey.length,
					e, endKey == null ? 0L : (long) endKey.length);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		}
	}

	static void compactRangeBuffer(RocksDBCompactionOperations db, ByteBuffer startKey, ByteBuffer endKey) {
		try {
			MemorySegment s = startKey == null ? MemorySegment.NULL : MemorySegment.ofBuffer(startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : MemorySegment.ofBuffer(endKey);
			MH_COMPACT_RANGE.invokeExact(db.dbPtr(),
					s, startKey == null ? 0L : (long) startKey.remaining(),
					e, endKey == null ? 0L : (long) endKey.remaining());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		}
	}

	static void compactRangeSegment(RocksDBCompactionOperations db, MemorySegment startKey, MemorySegment endKey) {
		try {
			MemorySegment s = startKey == null ? MemorySegment.NULL : startKey;
			MemorySegment e = endKey == null ? MemorySegment.NULL : endKey;
			MH_COMPACT_RANGE.invokeExact(db.dbPtr(),
					s, s == MemorySegment.NULL ? 0L : s.byteSize(),
					e, e == MemorySegment.NULL ? 0L : e.byteSize());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		}
	}

	static void compactRangeOptBytes(RocksDBCompactionOperations db, CompactOptions opts, byte[] startKey, byte[] endKey) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment s = startKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, startKey);
			MemorySegment e = endKey == null ? MemorySegment.NULL : NativeCalls.toNative(arena, endKey);
			MH_COMPACT_RANGE_OPT.invokeExact(db.dbPtr(), opts.ptr(),
					s, startKey == null ? 0L : (long) startKey.length,
					e, endKey == null ? 0L : (long) endKey.length);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("compactRange failed", t);
		}
	}

	static void suggestCompactRangeBytes(RocksDBCompactionOperations db, byte[] startKey, byte[] endKey) {
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
		}
	}

	static void disableFileDeletions(RocksDBCompactionOperations db) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DISABLE_FILE_DELETIONS.invokeExact(db.dbPtr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("disableFileDeletions failed", t);
		}
	}

	static void enableFileDeletions(RocksDBCompactionOperations db) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_ENABLE_FILE_DELETIONS.invokeExact(db.dbPtr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("enableFileDeletions failed", t);
		}
	}

	static void disableManualCompaction(RocksDBCompactionOperations db) {
		try {
			MH_DISABLE_MANUAL_COMPACTION.invokeExact(db.dbPtr());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("disableManualCompaction failed", t);
		}
	}

	static void enableManualCompaction(RocksDBCompactionOperations db) {
		try {
			MH_ENABLE_MANUAL_COMPACTION.invokeExact(db.dbPtr());
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("enableManualCompaction failed", t);
		}
	}

	static void waitForCompact(RocksDBCompactionOperations db, WaitForCompactOptions options) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_WAIT_FOR_COMPACT.invokeExact(db.dbPtr(), options.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("waitForCompact failed", t);
		}
	}
}
