package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// FFM plumbing for [RocksDBWriteOperations]'s default methods. Kept out of [RocksDB] itself so
/// that class doesn't also have to hold every feature area's `MH_` fields -- see
/// [#131](https://github.com/dfa1/rocksdbffm/issues/131).
final class RocksDBWriteOperationsBindings {

	/// `void rocksdb_put(rocksdb_t* db, const rocksdb_writeoptions_t* options, const char* key, size_t keylen, const char* val, size_t vallen, char** errptr);`
	private static final MethodHandle MH_PUT;
	/// `void rocksdb_put_cf(rocksdb_t* db, const rocksdb_writeoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t keylen, const char* val, size_t vallen, char** errptr);`
	private static final MethodHandle MH_PUT_CF;
	/// `void rocksdb_merge(rocksdb_t* db, const rocksdb_writeoptions_t* options, const char* key, size_t keylen, const char* val, size_t vallen, char** errptr);`
	private static final MethodHandle MH_MERGE;
	/// `void rocksdb_merge_cf(rocksdb_t* db, const rocksdb_writeoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t keylen, const char* val, size_t vallen, char** errptr);`
	private static final MethodHandle MH_MERGE_CF;
	/// `void rocksdb_delete(rocksdb_t* db, const rocksdb_writeoptions_t* options, const char* key, size_t keylen, char** errptr);`
	private static final MethodHandle MH_DELETE;
	/// `void rocksdb_delete_cf(rocksdb_t* db, const rocksdb_writeoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* key, size_t keylen, char** errptr);`
	private static final MethodHandle MH_DELETE_CF;
	/// `void rocksdb_delete_range_cf(rocksdb_t* db, const rocksdb_writeoptions_t* options, rocksdb_column_family_handle_t* column_family, const char* start_key, size_t start_key_len, const char* end_key, size_t end_key_len, char** errptr);`
	private static final MethodHandle MH_DELETE_RANGE_CF;
	/// `rocksdb_column_family_handle_t* rocksdb_get_default_column_family_handle(rocksdb_t* db);`
	private static final MethodHandle MH_GET_DEFAULT_CF;
	/// `void rocksdb_write(rocksdb_t* db, const rocksdb_writeoptions_t* options, rocksdb_writebatch_t* batch, char** errptr);`
	private static final MethodHandle MH_WRITE;
	/// `void rocksdb_flush(rocksdb_t* db, const rocksdb_flushoptions_t* options, char** errptr);`
	private static final MethodHandle MH_FLUSH;
	/// `void rocksdb_flush_cf(rocksdb_t* db, const rocksdb_flushoptions_t* options, rocksdb_column_family_handle_t* column_family, char** errptr);`
	private static final MethodHandle MH_FLUSH_CF;
	/// `void rocksdb_flush_wal(rocksdb_t* db, unsigned char sync, char** errptr);`
	private static final MethodHandle MH_FLUSH_WAL;
	/// `void rocksdb_cancel_all_background_work(rocksdb_t* db, unsigned char wait);`
	private static final MethodHandle MH_CANCEL_ALL_BACKGROUND_WORK;
	/// `uint64_t rocksdb_get_latest_sequence_number(rocksdb_t* db);`
	private static final MethodHandle MH_GET_LATEST_SEQUENCE_NUMBER;
	/// `rocksdb_wal_iterator_t* rocksdb_get_updates_since(rocksdb_t* db, uint64_t seq_number, const rocksdb_wal_readoptions_t* options, char** errptr);`
	private static final MethodHandle MH_GET_UPDATES_SINCE;
	/// `void rocksdb_ingest_external_file(rocksdb_t* db, const char* const* file_list, const size_t list_len, const rocksdb_ingestexternalfileoptions_t* opt, char** errptr);`
	private static final MethodHandle MH_INGEST_EXTERNAL_FILE;
	/// `rocksdb_column_family_handle_t* rocksdb_create_column_family(rocksdb_t* db, const rocksdb_options_t* column_family_options, const char* column_family_name, char** errptr);`
	private static final MethodHandle MH_CREATE_CF;
	/// `void rocksdb_drop_column_family(rocksdb_t* db, rocksdb_column_family_handle_t* handle, char** errptr);`
	private static final MethodHandle MH_DROP_CF;

	static {
		MH_PUT = NativeLibrary.lookup("rocksdb_put",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_PUT_CF = NativeLibrary.lookup("rocksdb_put_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_MERGE = NativeLibrary.lookup("rocksdb_merge",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_MERGE_CF = NativeLibrary.lookup("rocksdb_merge_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_DELETE = NativeLibrary.lookup("rocksdb_delete",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_DELETE_CF = NativeLibrary.lookup("rocksdb_delete_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_DELETE_RANGE_CF = NativeLibrary.lookup("rocksdb_delete_range_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS));

		MH_GET_DEFAULT_CF = NativeLibrary.lookup("rocksdb_get_default_column_family_handle",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_WRITE = NativeLibrary.lookup("rocksdb_write",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_FLUSH = NativeLibrary.lookup("rocksdb_flush",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_FLUSH_CF = NativeLibrary.lookup("rocksdb_flush_cf",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_FLUSH_WAL = NativeLibrary.lookup("rocksdb_flush_wal",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_CANCEL_ALL_BACKGROUND_WORK = NativeLibrary.lookup("rocksdb_cancel_all_background_work",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_LATEST_SEQUENCE_NUMBER = NativeLibrary.lookup("rocksdb_get_latest_sequence_number",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_GET_UPDATES_SINCE = NativeLibrary.lookup("rocksdb_get_updates_since",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_INGEST_EXTERNAL_FILE = NativeLibrary.lookup("rocksdb_ingest_external_file",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_CREATE_CF = NativeLibrary.lookup("rocksdb_create_column_family",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_DROP_CF = NativeLibrary.lookup("rocksdb_drop_column_family",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private RocksDBWriteOperationsBindings() {
	}

	/// byte[] put — slow path, allocates native memory.
	static void putBytes(RocksDBWriteOperations db, WriteOptions writeOpts, byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment k = NativeCalls.toNative(arena, key);
			MemorySegment v = NativeCalls.toNative(arena, value);
			MH_PUT.invokeExact(db.dbPtr(), writeOpts.ptr(), k, (long) key.length, v, (long) value.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("put failed", t);
		}
	}

	/// byte[] put using the caller's arena.
	static void putBytes(Arena arena, RocksDBWriteOperations db, WriteOptions writeOpts, byte[] key, byte[] value) {
		try {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment k = NativeCalls.toNative(arena, key);
			MemorySegment v = NativeCalls.toNative(arena, value);
			MH_PUT.invokeExact(db.dbPtr(), writeOpts.ptr(), k, (long) key.length, v, (long) value.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("put failed", t);
		}
	}

	/// MemorySegment put — zero-copy, caller supplies pre-allocated native segments.
	static void putSegment(RocksDBWriteOperations db, WriteOptions writeOpts,
	                       MemorySegment key, MemorySegment val) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_PUT.invokeExact(db.dbPtr(), writeOpts.ptr(), key, key.byteSize(), val, val.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("put failed", t);
		}
	}

	/// MemorySegment put using the caller's arena.
	static void putSegment(Arena arena, RocksDBWriteOperations db, WriteOptions writeOpts,
	                       MemorySegment key, MemorySegment val) {
		try {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_PUT.invokeExact(db.dbPtr(), writeOpts.ptr(), key, key.byteSize(), val, val.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("put failed", t);
		}
	}

	/// byte[] merge — slow path, allocates native memory.
	static void mergeBytes(RocksDBWriteOperations db, WriteOptions writeOpts, byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			mergeBytes(arena, db, writeOpts, key, value);
		}
	}

	/// byte[] merge using the caller's arena.
	static void mergeBytes(Arena arena, RocksDBWriteOperations db, WriteOptions writeOpts, byte[] key, byte[] value) {
		MemorySegment k = NativeCalls.toNative(arena, key);
		MemorySegment v = NativeCalls.toNative(arena, value);
		mergeSegment(arena, db, writeOpts, k, v);
	}

	/// MemorySegment merge — zero-copy, caller supplies pre-allocated native segments.
	static void mergeSegment(RocksDBWriteOperations db, WriteOptions writeOpts,
	                         MemorySegment key, MemorySegment val) {
		try (Arena arena = Arena.ofConfined()) {
			mergeSegment(arena, db, writeOpts, key, val);
		}
	}

	/// MemorySegment merge using the caller's arena.
	static void mergeSegment(Arena arena, RocksDBWriteOperations db, WriteOptions writeOpts,
	                         MemorySegment key, MemorySegment val) {
		try {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_MERGE.invokeExact(db.dbPtr(), writeOpts.ptr(), key, key.byteSize(), val, val.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("merge failed", t);
		}
	}

	/// byte[] delete — slow path.
	static void deleteBytes(RocksDBWriteOperations db, WriteOptions writeOpts, byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment k = NativeCalls.toNative(arena, key);
			MH_DELETE.invokeExact(db.dbPtr(), writeOpts.ptr(), k, (long) key.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("delete failed", t);
		}
	}

	/// MemorySegment delete — zero-copy.
	static void deleteSegment(RocksDBWriteOperations db, WriteOptions writeOpts, MemorySegment key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DELETE.invokeExact(db.dbPtr(), writeOpts.ptr(), key, key.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("delete failed", t);
		}
	}

	static void flush(RocksDBWriteOperations db, FlushOptions flushOptions) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_FLUSH.invokeExact(db.dbPtr(), flushOptions.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("flush failed", t);
		}
	}

	static void cancelAllBackgroundWork(RocksDBWriteOperations db, boolean wait) {
		try {
			MH_CANCEL_ALL_BACKGROUND_WORK.invokeExact(db.dbPtr(), NativeCalls.toByte(wait));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("cancelAllBackgroundWork failed", t);
		}
	}

	static SequenceNumber getLatestSequenceNumber(RocksDBWriteOperations db) {
		try {
			long seq = (long) MH_GET_LATEST_SEQUENCE_NUMBER.invokeExact(db.dbPtr());
			return SequenceNumber.of(seq);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getLatestSequenceNumber failed", t);
		}
	}

	static WalIterator getUpdatesSince(RocksDBWriteOperations db, SequenceNumber sequenceNumber) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment iterPtr = (MemorySegment) MH_GET_UPDATES_SINCE.invokeExact(
					db.dbPtr(), sequenceNumber.toLong(), MemorySegment.NULL, err);
			NativeCalls.checkError(err);
			return WalIterator.wrap(iterPtr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("getUpdatesSince failed", t);
		}
	}

	static void flushWal(RocksDBWriteOperations db, boolean sync) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_FLUSH_WAL.invokeExact(db.dbPtr(), NativeCalls.toByte(sync), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("flushWal failed", t);
		}
	}

	static void deleteRangeCfBytes(RocksDBWriteOperations db, WriteOptions writeOpts, byte[] startKey, byte[] endKey) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment cf = (MemorySegment) MH_GET_DEFAULT_CF.invokeExact(db.dbPtr());
			MH_DELETE_RANGE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf,
					NativeCalls.toNative(arena, startKey), (long) startKey.length,
					NativeCalls.toNative(arena, endKey), (long) endKey.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteRange failed", t);
		}
	}

	static void deleteRangeCfBuffer(RocksDBWriteOperations db, WriteOptions writeOpts,
	                                ByteBuffer startKey, ByteBuffer endKey) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment cf = (MemorySegment) MH_GET_DEFAULT_CF.invokeExact(db.dbPtr());
			MH_DELETE_RANGE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf,
					MemorySegment.ofBuffer(startKey), (long) startKey.remaining(),
					MemorySegment.ofBuffer(endKey), (long) endKey.remaining(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteRange failed", t);
		}
	}

	static void deleteRangeCfSegment(RocksDBWriteOperations db, WriteOptions writeOpts,
	                                 MemorySegment startKey, MemorySegment endKey) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment cf = (MemorySegment) MH_GET_DEFAULT_CF.invokeExact(db.dbPtr());
			MH_DELETE_RANGE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf,
					startKey, startKey.byteSize(), endKey, endKey.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteRange failed", t);
		}
	}

	static void writeBatch(RocksDBWriteOperations db, WriteOptions writeOpts, WriteBatch batch) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_WRITE.invokeExact(db.dbPtr(), writeOpts.ptr(), batch.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("write failed", t);
		}
	}

	static void writeBatch(Arena arena, RocksDBWriteOperations db, WriteOptions writeOpts, WriteBatch batch) {
		try {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_WRITE.invokeExact(db.dbPtr(), writeOpts.ptr(), batch.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("write failed", t);
		}
	}

	static void ingestExternalFile(RocksDBWriteOperations db, List<Path> files, IngestExternalFileOptions options) {
		if (files.isEmpty()) {
			return;
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment fileArray = arena.allocate(ValueLayout.ADDRESS, files.size());
			for (int i = 0; i < files.size(); i++) {
				fileArray.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(files.get(i).toString()));
			}
			RocksDB.requireNoNullEntries(fileArray, files.size(), "ingest file list array");
			MH_INGEST_EXTERNAL_FILE.invokeExact(db.dbPtr(), fileArray, (long) files.size(), options.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("ingestExternalFile failed", t);
		}
	}

	static void ingestExternalFileWithDefaults(RocksDBWriteOperations db, List<Path> files) {
		try (IngestExternalFileOptions opts = IngestExternalFileOptions.newIngestExternalFileOptions()) {
			ingestExternalFile(db, files, opts);
		}
	}

	static ColumnFamilyHandle createCf(RocksDBWriteOperations db, ColumnFamilyDescriptor descriptor) {
		List<Options> tempOptions = new ArrayList<>(1);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			Options cfOpts = descriptor.options();
			if (cfOpts == null) {
				cfOpts = Options.newOptions();
				tempOptions.add(cfOpts);
			}
			MemorySegment nameSeg = arena.allocateFrom(
					new String(descriptor.name(), StandardCharsets.UTF_8));
			MemorySegment handle = (MemorySegment) MH_CREATE_CF.invokeExact(
					db.dbPtr(), cfOpts.ptr(), nameSeg, err);
			NativeCalls.checkError(err);
			return ColumnFamilyHandle.wrap(handle);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("createColumnFamily failed", t);
		} finally {
			for (Options o : tempOptions) {
				o.close();
			}
		}
	}

	static void dropCf(MemorySegment db, ColumnFamilyHandle handle) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DROP_CF.invokeExact(db, handle.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("dropColumnFamily failed", t);
		}
	}

	/// byte[] put with explicit column family — slow path.
	static void putCfBytes(RocksDBWriteOperations db, WriteOptions writeOpts, ColumnFamilyHandle cf,
	                       byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_PUT_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf.ptr(),
					NativeCalls.toNative(arena, key), (long) key.length,
					NativeCalls.toNative(arena, value), (long) value.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("put failed", t);
		}
	}

	/// MemorySegment put with explicit column family — zero-copy.
	static void putCfSegment(RocksDBWriteOperations db, WriteOptions writeOpts, ColumnFamilyHandle cf,
	                         MemorySegment key, MemorySegment val) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_PUT_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf.ptr(), key, key.byteSize(), val, val.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("put failed", t);
		}
	}

	/// byte[] merge with explicit column family — slow path.
	static void mergeCfBytes(RocksDBWriteOperations db, WriteOptions writeOpts, ColumnFamilyHandle cf,
	                         byte[] key, byte[] value) {
		try (Arena arena = Arena.ofConfined()) {
			mergeCfBytes(arena, db, writeOpts, cf, key, value);
		}
	}

	/// byte[] merge with explicit column family using the caller's arena.
	static void mergeCfBytes(Arena arena, RocksDBWriteOperations db, WriteOptions writeOpts, ColumnFamilyHandle cf,
	                         byte[] key, byte[] value) {
		MemorySegment k = NativeCalls.toNative(arena, key);
		MemorySegment v = NativeCalls.toNative(arena, value);
		mergeCfSegment(arena, db, writeOpts, cf, k, v);
	}

	/// MemorySegment merge with explicit column family — zero-copy.
	static void mergeCfSegment(RocksDBWriteOperations db, WriteOptions writeOpts, ColumnFamilyHandle cf,
	                           MemorySegment key, MemorySegment val) {
		try (Arena arena = Arena.ofConfined()) {
			mergeCfSegment(arena, db, writeOpts, cf, key, val);
		}
	}

	/// MemorySegment merge with explicit column family using the caller's arena.
	static void mergeCfSegment(Arena arena, RocksDBWriteOperations db, WriteOptions writeOpts, ColumnFamilyHandle cf,
	                           MemorySegment key, MemorySegment val) {
		try {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_MERGE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf.ptr(), key, key.byteSize(), val, val.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("merge failed", t);
		}
	}

	/// byte[] delete with explicit column family — slow path.
	static void deleteCfBytes(RocksDBWriteOperations db, WriteOptions writeOpts, ColumnFamilyHandle cf,
	                          byte[] key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DELETE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf.ptr(),
					NativeCalls.toNative(arena, key), (long) key.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("delete failed", t);
		}
	}

	/// MemorySegment delete with explicit column family — zero-copy.
	static void deleteCfSegment(RocksDBWriteOperations db, WriteOptions writeOpts, ColumnFamilyHandle cf,
	                            MemorySegment key) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DELETE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf.ptr(), key, key.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("delete failed", t);
		}
	}

	/// deleteRange with explicit column family — slow path.
	static void deleteRangeCfBytesExplicit(RocksDBWriteOperations db, WriteOptions writeOpts,
	                                       ColumnFamilyHandle cf,
	                                       byte[] startKey, byte[] endKey) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DELETE_RANGE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf.ptr(),
					NativeCalls.toNative(arena, startKey), (long) startKey.length,
					NativeCalls.toNative(arena, endKey), (long) endKey.length, err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteRange failed", t);
		}
	}

	/// deleteRange with explicit column family — zero-copy for direct ByteBuffers.
	static void deleteRangeCfBufferExplicit(RocksDBWriteOperations db, WriteOptions writeOpts,
	                                        ColumnFamilyHandle cf,
	                                        ByteBuffer startKey, ByteBuffer endKey) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DELETE_RANGE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf.ptr(),
					MemorySegment.ofBuffer(startKey), (long) startKey.remaining(),
					MemorySegment.ofBuffer(endKey), (long) endKey.remaining(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteRange failed", t);
		}
	}

	/// deleteRange with explicit column family — zero-copy for MemorySegments.
	static void deleteRangeCfSegmentExplicit(RocksDBWriteOperations db, WriteOptions writeOpts,
	                                         ColumnFamilyHandle cf,
	                                         MemorySegment startKey, MemorySegment endKey) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_DELETE_RANGE_CF.invokeExact(db.dbPtr(), writeOpts.ptr(), cf.ptr(),
					startKey, startKey.byteSize(), endKey, endKey.byteSize(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("deleteRange failed", t);
		}
	}

	static void flushCf(RocksDBWriteOperations db, FlushOptions flushOptions, ColumnFamilyHandle cf) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MH_FLUSH_CF.invokeExact(db.dbPtr(), flushOptions.ptr(), cf.ptr(), err);
			NativeCalls.checkError(err);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("flush failed", t);
		}
	}
}
