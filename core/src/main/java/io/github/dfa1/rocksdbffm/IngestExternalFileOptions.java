package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_ingestexternalfileoptions_t`.
///
/// Controls the behavior of [RocksDB#ingestExternalFile].
///
/// ```
/// try (var opts = IngestExternalFileOptions.newIngestExternalFileOptions().setMoveFiles(true)) {
///     db.ingestExternalFile(List.of(sstPath), opts);
/// }
/// ```
public final class IngestExternalFileOptions extends NativeObject {

	/// `rocksdb_ingestexternalfileoptions_t* rocksdb_ingestexternalfileoptions_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_ingestexternalfileoptions_destroy(rocksdb_ingestexternalfileoptions_t* opt);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_ingestexternalfileoptions_set_move_files(rocksdb_ingestexternalfileoptions_t* opt, unsigned char move_files);`
	private static final MethodHandle MH_SET_MOVE_FILES;
	/// `void rocksdb_ingestexternalfileoptions_set_snapshot_consistency(rocksdb_ingestexternalfileoptions_t* opt, unsigned char snapshot_consistency);`
	private static final MethodHandle MH_SET_SNAPSHOT_CONSISTENCY;
	/// `void rocksdb_ingestexternalfileoptions_set_allow_global_seqno(rocksdb_ingestexternalfileoptions_t* opt, unsigned char allow_global_seqno);`
	private static final MethodHandle MH_SET_ALLOW_GLOBAL_SEQNO;
	/// `void rocksdb_ingestexternalfileoptions_set_allow_blocking_flush(rocksdb_ingestexternalfileoptions_t* opt, unsigned char allow_blocking_flush);`
	private static final MethodHandle MH_SET_ALLOW_BLOCKING_FLUSH;
	/// `void rocksdb_ingestexternalfileoptions_set_ingest_behind(rocksdb_ingestexternalfileoptions_t* opt, unsigned char ingest_behind);`
	private static final MethodHandle MH_SET_INGEST_BEHIND;
	/// `void rocksdb_ingestexternalfileoptions_set_fail_if_not_bottommost_level(rocksdb_ingestexternalfileoptions_t* opt, unsigned char fail_if_not_bottommost_level);`
	private static final MethodHandle MH_SET_FAIL_IF_NOT_BOTTOMMOST_LEVEL;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_ingestexternalfileoptions_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_ingestexternalfileoptions_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_MOVE_FILES = NativeLibrary.lookup("rocksdb_ingestexternalfileoptions_set_move_files",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_SNAPSHOT_CONSISTENCY = NativeLibrary.lookup("rocksdb_ingestexternalfileoptions_set_snapshot_consistency",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_ALLOW_GLOBAL_SEQNO = NativeLibrary.lookup("rocksdb_ingestexternalfileoptions_set_allow_global_seqno",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_ALLOW_BLOCKING_FLUSH = NativeLibrary.lookup("rocksdb_ingestexternalfileoptions_set_allow_blocking_flush",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_INGEST_BEHIND = NativeLibrary.lookup("rocksdb_ingestexternalfileoptions_set_ingest_behind",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_SET_FAIL_IF_NOT_BOTTOMMOST_LEVEL = NativeLibrary.lookup(
				"rocksdb_ingestexternalfileoptions_set_fail_if_not_bottommost_level",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));
	}

	private IngestExternalFileOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates [IngestExternalFileOptions] with RocksDB defaults.
	///
	/// @return a new instance; caller must close it
	public static IngestExternalFileOptions newIngestExternalFileOptions() {
		try {
			return new IngestExternalFileOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("ingestexternalfileoptions create failed", t);
		}
	}

	/// If `true`, the SST files are moved rather than copied into the DB directory.
	///
	/// @param moveFiles `true` to move files instead of copying
	/// @return `this` for chaining
	public IngestExternalFileOptions setMoveFiles(boolean moveFiles) {
		NativeFields.setBoolean(MH_SET_MOVE_FILES, ptr(), moveFiles);
		return this;
	}

	/// If `true` (default), snapshot consistency is enforced during ingest.
	///
	/// @param snapshotConsistency `true` to enforce snapshot consistency
	/// @return `this` for chaining
	public IngestExternalFileOptions setSnapshotConsistency(boolean snapshotConsistency) {
		NativeFields.setBoolean(MH_SET_SNAPSHOT_CONSISTENCY, ptr(), snapshotConsistency);
		return this;
	}

	/// If `true` (default), allows assigning a global sequence number to ingested files.
	///
	/// @param allowGlobalSeqno `true` to allow global sequence number assignment
	/// @return `this` for chaining
	public IngestExternalFileOptions setAllowGlobalSeqno(boolean allowGlobalSeqno) {
		NativeFields.setBoolean(MH_SET_ALLOW_GLOBAL_SEQNO, ptr(), allowGlobalSeqno);
		return this;
	}

	/// If `true` (default), allows a blocking flush before ingest if needed.
	///
	/// @param allowBlockingFlush `true` to allow blocking flush before ingest
	/// @return `this` for chaining
	public IngestExternalFileOptions setAllowBlockingFlush(boolean allowBlockingFlush) {
		NativeFields.setBoolean(MH_SET_ALLOW_BLOCKING_FLUSH, ptr(), allowBlockingFlush);
		return this;
	}

	/// If `true`, ingest files behind existing data (at the bottommost level).
	/// Requires `allow_ingest_behind` to be set on the DB options.
	///
	/// @param ingestBehind `true` to ingest behind existing data
	/// @return `this` for chaining
	public IngestExternalFileOptions setIngestBehind(boolean ingestBehind) {
		NativeFields.setBoolean(MH_SET_INGEST_BEHIND, ptr(), ingestBehind);
		return this;
	}

	/// If `true`, fails if the file cannot be placed at the bottommost level.
	///
	/// @param failIfNotBottommostLevel `true` to fail when placement at bottommost level is not possible
	/// @return `this` for chaining
	public IngestExternalFileOptions setFailIfNotBottommostLevel(boolean failIfNotBottommostLevel) {
		NativeFields.setBoolean(MH_SET_FAIL_IF_NOT_BOTTOMMOST_LEVEL, ptr(), failIfNotBottommostLevel);
		return this;
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
