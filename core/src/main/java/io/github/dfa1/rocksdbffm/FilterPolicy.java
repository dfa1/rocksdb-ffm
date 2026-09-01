package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_filterpolicy_t`.
///
/// Use inside a try-with-resources block. If the policy is passed to
/// [BlockBasedTableOptions#setFilterPolicy(FilterPolicy)], ownership
/// transfers to RocksDB's internal reference counting and [#close()]
/// becomes a no-op — it is safe (and recommended) to still call it via
/// try-with-resources.
///
/// ```
/// try (var filter = FilterPolicy.newBloom(10);
///      var tbl = BlockBasedTableOptions.newBlockBasedConfig().setFilterPolicy(filter);
///      var opts = Options.newOptions().setCreateIfMissing(true).setTableFormatConfig(tbl)) {
///     // filter.close() called automatically — no-op because ownership transferred
/// }
/// ```
public final class FilterPolicy extends NativeObject {

	/// `rocksdb_filterpolicy_t* rocksdb_filterpolicy_create_bloom(double bits_per_key);`
	private static final MethodHandle MH_CREATE_BLOOM;
	/// `rocksdb_filterpolicy_t* rocksdb_filterpolicy_create_ribbon(double bloom_equivalent_bits_per_key);`
	private static final MethodHandle MH_CREATE_RIBBON;
	/// `rocksdb_filterpolicy_t* rocksdb_filterpolicy_create_ribbon_hybrid(double bloom_equivalent_bits_per_key, int bloom_before_level);`
	private static final MethodHandle MH_CREATE_RIBBON_HYBRID;
	/// `void rocksdb_filterpolicy_destroy(rocksdb_filterpolicy_t*);`
	private static final MethodHandle MH_DESTROY;

	static {
		MH_CREATE_BLOOM = NativeLibrary.lookup("rocksdb_filterpolicy_create_bloom",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_CREATE_RIBBON = NativeLibrary.lookup("rocksdb_filterpolicy_create_ribbon",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_CREATE_RIBBON_HYBRID = NativeLibrary.lookup("rocksdb_filterpolicy_create_ribbon_hybrid",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_filterpolicy_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	}

	private FilterPolicy(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates a Bloom filter with the given number of bits per key.
	/// Typical value: `10` (≈1% false-positive rate).
	///
	/// @param bitsPerKey number of bits per key (higher = lower false-positive rate)
	/// @return a new [FilterPolicy]; caller must close it (or transfer ownership via [BlockBasedTableOptions#setFilterPolicy])
	public static FilterPolicy newBloom(double bitsPerKey) {
		try {
			return new FilterPolicy((MemorySegment) MH_CREATE_BLOOM.invokeExact(bitsPerKey));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("FilterPolicy.newBloom failed", t);
		}
	}

	/// Creates a Ribbon filter (successor to Bloom, better space efficiency at
	/// similar query cost). Uses `bloomEquivalentBitsPerKey` to set the
	/// target false-positive rate equivalent to a Bloom filter at that setting.
	///
	/// @param bloomEquivalentBitsPerKey bits-per-key equivalent to a Bloom filter at that false-positive rate
	/// @return a new [FilterPolicy]; caller must close it (or transfer ownership via [BlockBasedTableOptions#setFilterPolicy])
	public static FilterPolicy newRibbon(double bloomEquivalentBitsPerKey) {
		try {
			return new FilterPolicy((MemorySegment) MH_CREATE_RIBBON.invokeExact(bloomEquivalentBitsPerKey));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("FilterPolicy.newRibbon failed", t);
		}
	}

	/// Creates a hybrid filter: Ribbon for lower (larger, longer-lived) levels, Bloom for the
	/// highest levels -- Ribbon's ~30% space saving matters most where most of the data lives,
	/// while Bloom's cheaper construction suits levels rebuilt often by flushes/compaction.
	///
	/// @param bloomEquivalentBitsPerKey bits-per-key equivalent to a Bloom filter at the target
	///                                  false-positive rate, same meaning as [#newRibbon(double)]
	/// @param bloomBeforeLevel          use Bloom filters for levels below this number (memtable
	///                                  flushes count as level `-1`, distinct from intra-L0
	///                                  compaction); Ribbon everywhere else. `1` means Bloom for
	///                                  flushes and L0, Ribbon from L1 down -- the configuration
	///                                  RocksDB's own docs use as the standard example. `0` means
	///                                  Bloom for flushes only. `-1` is equivalent to
	///                                  [#newRibbon(double)] (always Ribbon). `Integer.MAX_VALUE`
	///                                  means always Bloom
	/// @return a new [FilterPolicy]; caller must close it (or transfer ownership via [BlockBasedTableOptions#setFilterPolicy])
	public static FilterPolicy newRibbonHybrid(double bloomEquivalentBitsPerKey, int bloomBeforeLevel) {
		try {
			return new FilterPolicy(
					(MemorySegment) MH_CREATE_RIBBON_HYBRID.invokeExact(bloomEquivalentBitsPerKey, bloomBeforeLevel));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("FilterPolicy.newRibbonHybrid failed", t);
		}
	}

	@Override
	public void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
