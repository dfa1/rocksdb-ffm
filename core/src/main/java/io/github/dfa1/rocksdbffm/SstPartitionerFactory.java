package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_sst_partitioner_factory_t`.
///
/// Only the built-in fixed-prefix partitioner is exposed: `rocksdb/c.h` has no callback-based
/// factory constructor for custom partitioning logic (unlike, say, `CompactionFilterFactory`),
/// so a caller-supplied `ShouldPartition`/`CanDoTrivialMove` implementation is not reachable
/// through this C API — tracked as a follow-up gap in `docs/c-api-gaps.md`.
///
/// ```
/// try (var factory = SstPartitionerFactory.newFixedPrefix(4);
///      var opts = Options.newOptions()
///          .setCreateIfMissing(true)
///          .setSstPartitionerFactory(factory)) {
///     // compaction now starts a new SST file at every 4-byte key-prefix boundary
/// }
/// ```
///
/// The factory uses shared ownership: passing it to [Options#setSstPartitionerFactory] does not
/// transfer ownership — both objects may be closed independently.
public final class SstPartitionerFactory extends NativeObject {

	/// `rocksdb_sst_partitioner_factory_t* rocksdb_sst_partitioner_fixed_prefix_factory_create(size_t prefix_len);`
	private static final MethodHandle MH_CREATE_FIXED_PREFIX;
	/// `void rocksdb_sst_partitioner_factory_destroy(rocksdb_sst_partitioner_factory_t*);`
	private static final MethodHandle MH_DESTROY;

	static {
		MH_CREATE_FIXED_PREFIX = NativeLibrary.lookup("rocksdb_sst_partitioner_fixed_prefix_factory_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_sst_partitioner_factory_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	private SstPartitionerFactory(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a factory for the built-in fixed-prefix partitioner: compaction starts a new SST
	/// file whenever the leading `prefixLen` bytes of the key change.
	///
	/// @param prefixLen number of leading key bytes that define a partition boundary
	/// @return a new [SstPartitionerFactory]; caller must close it
	public static SstPartitionerFactory newFixedPrefix(long prefixLen) {
		try {
			return new SstPartitionerFactory((MemorySegment) MH_CREATE_FIXED_PREFIX.invokeExact(prefixLen));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("SstPartitionerFactory.newFixedPrefix failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
