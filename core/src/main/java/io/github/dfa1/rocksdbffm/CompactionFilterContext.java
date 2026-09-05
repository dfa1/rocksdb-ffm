package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// Read-only view of why a compaction is running, passed to
/// [CompactionFilterFactory.CreateFilterFn#createCompactionFilter(CompactionFilterContext)].
///
/// Wraps a `rocksdb_compactionfiltercontext_t*` owned by RocksDB for the duration of the creation
/// callback only: every accessor reads through that pointer on demand, so an instance must never
/// be retained or used after the callback method returns.
public final class CompactionFilterContext {

	/// `unsigned char rocksdb_compactionfiltercontext_is_full_compaction(rocksdb_compactionfiltercontext_t* context);`
	private static final MethodHandle MH_IS_FULL_COMPACTION = NativeLibrary.lookup(
			"rocksdb_compactionfiltercontext_is_full_compaction",
			FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

	/// `unsigned char rocksdb_compactionfiltercontext_is_manual_compaction(rocksdb_compactionfiltercontext_t* context);`
	private static final MethodHandle MH_IS_MANUAL_COMPACTION = NativeLibrary.lookup(
			"rocksdb_compactionfiltercontext_is_manual_compaction",
			FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

	private final MemorySegment ptr;

	CompactionFilterContext(MemorySegment ptr) {
		this.ptr = ptr;
	}

	/// Returns whether this table file is being created as part of a compaction covering every
	/// file in the column family.
	///
	/// @return `true` if this is a full compaction
	public boolean isFullCompaction() {
		return NativeFields.getBoolean(MH_IS_FULL_COMPACTION, ptr);
	}

	/// Returns whether this table file is being created as part of a compaction explicitly
	/// requested by the client (e.g. via `compactRange()`), rather than one RocksDB scheduled on
	/// its own.
	///
	/// @return `true` if this is a manual compaction
	public boolean isManualCompaction() {
		return NativeFields.getBoolean(MH_IS_MANUAL_COMPACTION, ptr);
	}
}
