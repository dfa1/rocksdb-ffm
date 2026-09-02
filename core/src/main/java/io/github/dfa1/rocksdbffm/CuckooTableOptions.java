package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_cuckoo_table_options_t`.
///
/// Configures Cuckoo Table, a hash-based SST format optimized for fixed-size keys and
/// point lookups (no range scans). Attach via
/// [Options#setTableFormatConfig(CuckooTableOptions)]; `CuckooTableOptions` may be closed once
/// attached, RocksDB copies the underlying struct by value.
///
/// ```
/// try (var cuckoo = CuckooTableOptions.newCuckooTableOptions().setHashTableRatio(0.75);
///      var opts = Options.newOptions()
///          .setCreateIfMissing(true)
///          .setTableFormatConfig(cuckoo)) {
///     ...
/// }
/// ```
public final class CuckooTableOptions extends AbstractOptions {

	/// `rocksdb_cuckoo_table_options_t* rocksdb_cuckoo_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_cuckoo_options_destroy(rocksdb_cuckoo_table_options_t* options);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_cuckoo_options_set_hash_table_ratio(rocksdb_cuckoo_table_options_t* opt, double v);`
	private static final MethodHandle MH_SET_HASH_TABLE_RATIO;
	/// `double rocksdb_cuckoo_options_get_hash_table_ratio(rocksdb_cuckoo_table_options_t* opt);`
	private static final MethodHandle MH_GET_HASH_TABLE_RATIO;
	/// `void rocksdb_cuckoo_options_set_max_search_depth(rocksdb_cuckoo_table_options_t* opt, uint32_t v);`
	private static final MethodHandle MH_SET_MAX_SEARCH_DEPTH;
	/// `uint32_t rocksdb_cuckoo_options_get_max_search_depth(rocksdb_cuckoo_table_options_t* opt);`
	private static final MethodHandle MH_GET_MAX_SEARCH_DEPTH;
	/// `void rocksdb_cuckoo_options_set_cuckoo_block_size(rocksdb_cuckoo_table_options_t* opt, uint32_t v);`
	private static final MethodHandle MH_SET_CUCKOO_BLOCK_SIZE;
	/// `uint32_t rocksdb_cuckoo_options_get_cuckoo_block_size(rocksdb_cuckoo_table_options_t* opt);`
	private static final MethodHandle MH_GET_CUCKOO_BLOCK_SIZE;
	/// `void rocksdb_cuckoo_options_set_identity_as_first_hash(rocksdb_cuckoo_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_IDENTITY_AS_FIRST_HASH;
	/// `unsigned char rocksdb_cuckoo_options_get_identity_as_first_hash(rocksdb_cuckoo_table_options_t* opt);`
	private static final MethodHandle MH_GET_IDENTITY_AS_FIRST_HASH;
	/// `void rocksdb_cuckoo_options_set_use_module_hash(rocksdb_cuckoo_table_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_USE_MODULE_HASH;
	/// `unsigned char rocksdb_cuckoo_options_get_use_module_hash(rocksdb_cuckoo_table_options_t* opt);`
	private static final MethodHandle MH_GET_USE_MODULE_HASH;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_cuckoo_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_cuckoo_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_HASH_TABLE_RATIO = NativeLibrary.lookup("rocksdb_cuckoo_options_set_hash_table_ratio",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_HASH_TABLE_RATIO = NativeLibrary.lookup("rocksdb_cuckoo_options_get_hash_table_ratio",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));

		MH_SET_MAX_SEARCH_DEPTH = NativeLibrary.lookup("rocksdb_cuckoo_options_set_max_search_depth",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_MAX_SEARCH_DEPTH = NativeLibrary.lookup("rocksdb_cuckoo_options_get_max_search_depth",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_CUCKOO_BLOCK_SIZE = NativeLibrary.lookup("rocksdb_cuckoo_options_set_cuckoo_block_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

		MH_GET_CUCKOO_BLOCK_SIZE = NativeLibrary.lookup("rocksdb_cuckoo_options_get_cuckoo_block_size",
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_SET_IDENTITY_AS_FIRST_HASH = NativeLibrary.lookup("rocksdb_cuckoo_options_set_identity_as_first_hash",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_IDENTITY_AS_FIRST_HASH = NativeLibrary.lookup("rocksdb_cuckoo_options_get_identity_as_first_hash",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_USE_MODULE_HASH = NativeLibrary.lookup("rocksdb_cuckoo_options_set_use_module_hash",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_MODULE_HASH = NativeLibrary.lookup("rocksdb_cuckoo_options_get_use_module_hash",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));
	}

	private CuckooTableOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates Cuckoo table options with RocksDB defaults: hash table ratio `0.9`, max search
	/// depth `100`, cuckoo block size `5`, `identityAsFirstHash` disabled, `useModuleHash`
	/// enabled.
	///
	/// @return a new instance; caller must close it
	public static CuckooTableOptions newCuckooTableOptions() {
		try {
			return new CuckooTableOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("CuckooTableOptions create failed", t);
		}
	}

	/// Determines hash table utilization. Smaller values result in larger hash tables with
	/// fewer collisions. Default: `0.9`.
	///
	/// @param ratio hash table utilization ratio
	/// @return `this` for chaining
	public CuckooTableOptions setHashTableRatio(double ratio) {
		setDouble(MH_SET_HASH_TABLE_RATIO, ratio);
		return this;
	}

	/// Returns the configured hash table utilization ratio.
	///
	/// @return current hash table utilization ratio
	public double getHashTableRatio() {
		return getDouble(MH_GET_HASH_TABLE_RATIO);
	}

	/// Depth to search for a path to displace elements on collision while building the table.
	/// Higher values produce more efficient hash tables with fewer lookups, at the cost of a
	/// slower build. Default: `100`.
	///
	/// @param depth maximum search depth
	/// @return `this` for chaining
	public CuckooTableOptions setMaxSearchDepth(int depth) {
		setInt(MH_SET_MAX_SEARCH_DEPTH, depth);
		return this;
	}

	/// Returns the configured maximum search depth.
	///
	/// @return current maximum search depth
	public int getMaxSearchDepth() {
		return getInt(MH_GET_MAX_SEARCH_DEPTH);
	}

	/// On collision, the builder tries inserting in the next `blockSize` locations before
	/// moving on to the next Cuckoo hash function, improving lookup cache-friendliness on
	/// collision. Default: `5`.
	///
	/// @param blockSize cuckoo block size
	/// @return `this` for chaining
	public CuckooTableOptions setCuckooBlockSize(int blockSize) {
		setInt(MH_SET_CUCKOO_BLOCK_SIZE, blockSize);
		return this;
	}

	/// Returns the configured cuckoo block size.
	///
	/// @return current cuckoo block size
	public int getCuckooBlockSize() {
		return getInt(MH_GET_CUCKOO_BLOCK_SIZE);
	}

	/// If `true`, the user key is treated as a `uint64_t` and its value used directly as the
	/// hash, changing the builder's behavior (the reader ignores this flag and instead follows
	/// what is recorded in the table property). Default: `false`.
	///
	/// @param value `true` to use the user key directly as the hash value
	/// @return `this` for chaining
	public CuckooTableOptions setIdentityAsFirstHash(boolean value) {
		setBoolean(MH_SET_IDENTITY_AS_FIRST_HASH, value);
		return this;
	}

	/// Returns whether the user key is used directly as the hash value.
	///
	/// @return `true` if the user key is used directly as the hash value
	public boolean getIdentityAsFirstHash() {
		return getBoolean(MH_GET_IDENTITY_AS_FIRST_HASH);
	}

	/// If `true`, the modulo operator is used during hash calculation, which is more
	/// space-efficient at the cost of some performance. If `false`, the table size is
	/// constrained to a power of two and a bitwise AND is used instead, which is faster.
	/// Default: `true`.
	///
	/// @param value `true` to use modulo-based hash calculation
	/// @return `this` for chaining
	public CuckooTableOptions setUseModuleHash(boolean value) {
		setBoolean(MH_SET_USE_MODULE_HASH, value);
		return this;
	}

	/// Returns whether modulo-based hash calculation is used.
	///
	/// @return `true` if modulo-based hash calculation is used
	public boolean getUseModuleHash() {
		return getBoolean(MH_GET_USE_MODULE_HASH);
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
