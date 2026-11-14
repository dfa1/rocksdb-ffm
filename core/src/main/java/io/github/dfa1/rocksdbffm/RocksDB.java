package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/// Entry point for opening RocksDB databases.
///
/// All factory methods return a strongly-typed instance:
///
/// | Method | Returns |
/// |---|---|
/// | [#openReadWrite] | [ReadWriteDB] |
/// | [#openReadOnly] | [ReadOnlyDB] |
/// | [#openTtl] | [TtlDB] |
/// | [#openSecondary] | [SecondaryDB] |
/// | [#openTransaction] | [TransactionDB] |
/// | [#openOptimistic] | [OptimisticTransactionDB] |
/// | [#listColumnFamilies] | `List<ColumnFamilyName>` |
/// | [#loadLatestOptions] | [LoadedOptions] |
///
/// `RocksDB` is non-instantiable and holds only the `rocksdb_t*` open/list/load-options
/// method handles used by its own static factories. Every other capability (read, write,
/// compaction, monitoring, tracing) has its own `MethodHandle`-holding companion class instead
/// -- `RocksDBReadOperationsBindings`, `RocksDBWriteOperationsBindings`,
/// `RocksDBCompactionOperationsBindings`, `RocksDBMonitoringOperationsBindings`,
/// `RocksDBTracingOperationsBindings` -- and low-level shared plumbing (`errptr` handling,
/// native string/byte marshaling, `close`/`free`) lives on [NativeCalls]. See
/// [#131](https://github.com/dfa1/rocksdb-ffm/issues/131).
public final class RocksDB {

	// -----------------------------------------------------------------------
	// Open handles — used only inside factory methods
	// -----------------------------------------------------------------------

	/// `rocksdb_t* rocksdb_open(const rocksdb_options_t* options, const char* name, char** errptr);`
	private static final MethodHandle MH_OPEN;
	/// `rocksdb_t* rocksdb_open_with_ttl(const rocksdb_options_t* options, const char* name, int ttl, char** errptr);`
	private static final MethodHandle MH_OPEN_WITH_TTL;
	/// `rocksdb_t* rocksdb_open_for_read_only(const rocksdb_options_t* options, const char* name, unsigned char error_if_wal_file_exists, char** errptr);`
	private static final MethodHandle MH_OPEN_FOR_READ_ONLY;
	/// `rocksdb_t* rocksdb_open_as_secondary(const rocksdb_options_t* options, const char* name, const char* secondary_path, char** errptr);`
	private static final MethodHandle MH_OPEN_SECONDARY;
	/// `rocksdb_transactiondb_t* rocksdb_transactiondb_open(const rocksdb_options_t* options, const rocksdb_transactiondb_options_t* txn_db_options, const char* name, char** errptr);`
	private static final MethodHandle MH_OPEN_TRANSACTION;
	/// `rocksdb_optimistictransactiondb_t* rocksdb_optimistictransactiondb_open(const rocksdb_options_t* options, const char* name, char** errptr);`
	private static final MethodHandle MH_OPEN_OPTIMISTIC;
	/// `rocksdb_t* rocksdb_optimistictransactiondb_get_base_db(rocksdb_optimistictransactiondb_t* otxn_db);`
	private static final MethodHandle MH_OPTIMISTIC_GET_BASE_DB;

	// -----------------------------------------------------------------------
	// Column-family open/list method handles — used only inside factory methods
	// -----------------------------------------------------------------------

	/// `rocksdb_t* rocksdb_open_column_families(const rocksdb_options_t* options, const char* name, int num_column_families, const char* const* column_family_names, const rocksdb_options_t* const* column_family_options, rocksdb_column_family_handle_t** column_family_handles, char** errptr);`
	private static final MethodHandle MH_OPEN_CF;
	/// `char** rocksdb_list_column_families(const rocksdb_options_t* options, const char* name, size_t* lencf, char** errptr);`
	private static final MethodHandle MH_LIST_CF;
	/// `void rocksdb_list_column_families_destroy(char** list, size_t len);`
	private static final MethodHandle MH_LIST_CF_DESTROY;
	/// `void rocksdb_load_latest_options(const char* db_path, rocksdb_env_t* env, bool ignore_unknown_options, rocksdb_cache_t* cache, rocksdb_options_t** db_options, size_t* num_column_families, char*** column_family_names, rocksdb_options_t*** column_family_options, char** errptr);`
	private static final MethodHandle MH_LOAD_LATEST_OPTIONS;
	/// `void rocksdb_load_latest_options_destroy(rocksdb_options_t* db_options, char** list_column_family_names, rocksdb_options_t** list_column_family_options, size_t len);`
	private static final MethodHandle MH_LOAD_LATEST_OPTIONS_DESTROY;
	/// `rocksdb_t* rocksdb_open_for_read_only_column_families(const rocksdb_options_t* options, const char* name, int num_column_families, const char* const* column_family_names, const rocksdb_options_t* const* column_family_options, rocksdb_column_family_handle_t** column_family_handles, unsigned char error_if_wal_file_exists, char** errptr);`
	private static final MethodHandle MH_OPEN_FOR_READ_ONLY_CF;
	/// `rocksdb_t* rocksdb_open_as_secondary_column_families(const rocksdb_options_t* options, const char* name, const char* secondary_path, int num_column_families, const char* const* column_family_names, const rocksdb_options_t* const* column_family_options, rocksdb_column_family_handle_t** column_family_handles, char** errptr);`
	private static final MethodHandle MH_OPEN_SECONDARY_CF;
	/// `rocksdb_t* rocksdb_open_column_families_with_ttl(const rocksdb_options_t* options, const char* name, int num_column_families, const char* const* column_family_names, const rocksdb_options_t* const* column_family_options, rocksdb_column_family_handle_t** column_family_handles, const int* ttls, char** errptr);`
	private static final MethodHandle MH_OPEN_CF_WITH_TTL;
	/// `rocksdb_transactiondb_t* rocksdb_transactiondb_open_column_families(const rocksdb_options_t* options, const rocksdb_transactiondb_options_t* txn_db_options, const char* name, int num_column_families, const char* const* column_family_names, const rocksdb_options_t* const* column_family_options, rocksdb_column_family_handle_t** column_family_handles, char** errptr);`
	private static final MethodHandle MH_OPEN_TRANSACTION_CF;
	/// `rocksdb_optimistictransactiondb_t* rocksdb_optimistictransactiondb_open_column_families(const rocksdb_options_t* options, const char* name, int num_column_families, const char* const* column_family_names, const rocksdb_options_t* const* column_family_options, rocksdb_column_family_handle_t** column_family_handles, char** errptr);`
	private static final MethodHandle MH_OPEN_OPTIMISTIC_CF;
	/// `rocksdb_t* rocksdb_transactiondb_get_base_db(rocksdb_transactiondb_t* txn_db);`
	private static final MethodHandle MH_TRANSACTION_GET_BASE_DB;

	static {
		MH_OPEN = NativeLibrary.lookup("rocksdb_open",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_OPEN_WITH_TTL = NativeLibrary.lookup("rocksdb_open_with_ttl",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

		MH_OPEN_FOR_READ_ONLY = NativeLibrary.lookup("rocksdb_open_for_read_only",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_OPEN_SECONDARY = NativeLibrary.lookup("rocksdb_open_as_secondary",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_OPEN_TRANSACTION = NativeLibrary.lookup("rocksdb_transactiondb_open",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_OPEN_OPTIMISTIC = NativeLibrary.lookup("rocksdb_optimistictransactiondb_open",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_OPTIMISTIC_GET_BASE_DB = NativeLibrary.lookup("rocksdb_optimistictransactiondb_get_base_db",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_OPEN_CF = NativeLibrary.lookup("rocksdb_open_column_families",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_LIST_CF = NativeLibrary.lookup("rocksdb_list_column_families",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_LIST_CF_DESTROY = NativeLibrary.lookup("rocksdb_list_column_families_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_LOAD_LATEST_OPTIONS = NativeLibrary.lookup("rocksdb_load_latest_options",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_LOAD_LATEST_OPTIONS_DESTROY = NativeLibrary.lookup("rocksdb_load_latest_options_destroy",
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_OPEN_FOR_READ_ONLY_CF = NativeLibrary.lookup("rocksdb_open_for_read_only_column_families",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_OPEN_SECONDARY_CF = NativeLibrary.lookup("rocksdb_open_as_secondary_column_families",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_OPEN_CF_WITH_TTL = NativeLibrary.lookup("rocksdb_open_column_families_with_ttl",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_OPEN_TRANSACTION_CF = NativeLibrary.lookup("rocksdb_transactiondb_open_column_families",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_OPEN_OPTIMISTIC_CF = NativeLibrary.lookup("rocksdb_optimistictransactiondb_open_column_families",
				FunctionDescriptor.of(ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.JAVA_INT,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS,
						ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_TRANSACTION_GET_BASE_DB = NativeLibrary.lookup("rocksdb_transactiondb_get_base_db",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private RocksDB() {
		// no instances
	}

	// -----------------------------------------------------------------------
	// Factory — read-write
	// -----------------------------------------------------------------------

	/// Opens a read-write database at `path`.
	/// Use [Options#setCreateIfMissing(boolean)] to control behavior when
	/// the path does not exist.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @return a new [ReadWriteDB] instance
	public static ReadWriteDB openReadWrite(Path path, Options options) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment ptr = (MemorySegment) MH_OPEN.invokeExact(options.ptr(), pathSeg, err);
			NativeCalls.checkError(err);
			return withDefaultColumnFamily(new ReadWriteDB(ptr));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openReadWrite failed", t);
		}
	}

	/// Equivalent to `openReadWrite(path, options)` with default options. Fails if the database does not
	/// exist; to create it, pass options with [Options#setCreateIfMissing(boolean)] set.
	///
	/// @param path directory where the database files are stored
	/// @return a new [ReadWriteDB] instance
	public static ReadWriteDB openReadWrite(Path path) {
		return openReadWrite(path, Options.DEFAULTS);
	}

	/// Opens (or creates) a TTL-aware read-write database at `path`.
	///
	/// Keys are lazily expired during the next compaction that covers their
	/// range. A `ttl` of [Duration#ZERO] disables expiry entirely.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @param ttl time-to-live for keys; [Duration#ZERO] disables expiry
	/// @return a new [TtlDB] instance
	/// @throws IllegalArgumentException if `ttl` is negative or longer than [Integer#MAX_VALUE] seconds
	public static TtlDB openTtl(Path path, Options options, Duration ttl) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment ptr = (MemorySegment) MH_OPEN_WITH_TTL.invokeExact(
					options.ptr(), pathSeg, TtlDB.toTtlSeconds(ttl), err);
			NativeCalls.checkError(err);
			return withDefaultColumnFamily(new TtlDB(ptr, ttl));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openTtl failed", t);
		}
	}

	/// Equivalent to `openTtl(path, options, ttl)` with default options. Fails if the database does not
	/// exist; to create it, pass options with [Options#setCreateIfMissing(boolean)] set.
	///
	/// @param path directory where the database files are stored
	/// @param ttl time-to-live for keys; [Duration#ZERO] disables expiry
	/// @return a new [TtlDB] instance
	/// @throws IllegalArgumentException if `ttl` is negative or longer than [Integer#MAX_VALUE] seconds
	public static TtlDB openTtl(Path path, Duration ttl) {
		return openTtl(path, Options.DEFAULTS, ttl);
	}

	// -----------------------------------------------------------------------
	// Factory — read-only
	// -----------------------------------------------------------------------

	/// Opens the database at `path` in read-only mode.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @param errorIfWalFileExists if `true`, fails when unrecovered WAL files are present
	/// @return a new [ReadOnlyDB] instance
	public static ReadOnlyDB openReadOnly(Path path, Options options, boolean errorIfWalFileExists) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment ptr = (MemorySegment) MH_OPEN_FOR_READ_ONLY.invokeExact(
					options.ptr(), pathSeg, NativeCalls.toByte(errorIfWalFileExists), err);
			NativeCalls.checkError(err);
			return withDefaultColumnFamily(new ReadOnlyDB(ptr));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openReadOnly failed", t);
		}
	}

	/// Equivalent to `openReadOnly(path, options, false)`.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @return a new [ReadOnlyDB] instance
	public static ReadOnlyDB openReadOnly(Path path, Options options) {
		return openReadOnly(path, options, false);
	}

	/// Opens the database at `path` in read-only mode with default options.
	///
	/// @param path directory where the database files are stored
	/// @return a new [ReadOnlyDB] instance
	public static ReadOnlyDB openReadOnly(Path path) {
		return openReadOnly(path, Options.DEFAULTS, false);
	}

	// -----------------------------------------------------------------------
	// Factory — secondary
	// -----------------------------------------------------------------------

	/// Opens a secondary (read-only replica) instance of the database at `primaryPath`.
	///
	/// @param primaryPath directory of the primary database
	/// @param secondaryPath a dedicated directory for this secondary's own MANIFEST/WAL tails
	/// @param options the database options
	/// @return a new [SecondaryDB] instance
	public static SecondaryDB openSecondary(Path primaryPath, Path secondaryPath, Options options) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment primary = arena.allocateFrom(primaryPath.toString());
			MemorySegment secondary = arena.allocateFrom(secondaryPath.toString());

			MemorySegment ptr = (MemorySegment) MH_OPEN_SECONDARY.invokeExact(
					options.ptr(), primary, secondary, err);
			NativeCalls.checkError(err);

			return withDefaultColumnFamily(new SecondaryDB(ptr));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openSecondary failed", t);
		}
	}

	/// Opens a secondary (read-only replica) instance at `primaryPath` with multiple column
	/// families. The handles are owned by the returned database and looked up with
	/// `getColumnFamily(ColumnFamilyName)` on the returned database; each is legitimately scoped to this secondary
	/// instance — unlike a handle obtained from the primary or any other `rocksdb_t*`, safe to pass
	/// to this instance's column-family-scoped reads.
	///
	/// @param primaryPath   directory of the primary database
	/// @param secondaryPath a dedicated directory for this secondary's own MANIFEST/WAL tails
	/// @param options      the database options
	/// @param descriptors   one descriptor per column family (must include [ColumnFamilyName#DEFAULT])
	/// @return a new [SecondaryDB] instance
	/// @throws IllegalArgumentException if two descriptors share a name, or one carries a TTL (only
	///                                  [#openTtl(Path, Options, List)] accepts that)
	public static SecondaryDB openSecondary(Path primaryPath,
	                                        Path secondaryPath,
	                                        Options options,
	                                        List<ColumnFamilyDescriptor> descriptors) {
		int n = descriptors.size();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment primary = arena.allocateFrom(primaryPath.toString());
			MemorySegment secondary = arena.allocateFrom(secondaryPath.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, false);

			MemorySegment ptr = (MemorySegment) MH_OPEN_SECONDARY_CF.invokeExact(
					options.ptr(), primary, secondary, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);

			return adoptHandles(new SecondaryDB(ptr), handlesArr, descriptors);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openSecondary failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Factory — transactional
	// -----------------------------------------------------------------------

	/// Opens a [TransactionDB] (pessimistic / locking transactions) at `path`.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @param txnDbOptions the transaction DB options
	/// @return a new [TransactionDB] instance
	public static TransactionDB openTransaction(Path path, Options options, TransactionDBOptions txnDbOptions) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());

			MemorySegment ptr = (MemorySegment) MH_OPEN_TRANSACTION.invokeExact(
					options.ptr(), txnDbOptions.ptr(), pathSeg, err);
			NativeCalls.checkError(err);

			MemorySegment baseDb = (MemorySegment) MH_TRANSACTION_GET_BASE_DB.invokeExact(ptr);
			return withDefaultColumnFamily(new TransactionDB(ptr, baseDb));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openTransaction failed", t);
		}
	}

	/// Opens an [OptimisticTransactionDB] (conflict-detection-at-commit) at `path`.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @return a new [OptimisticTransactionDB] instance
	public static OptimisticTransactionDB openOptimistic(Path path, Options options) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());

			MemorySegment ptr = (MemorySegment) MH_OPEN_OPTIMISTIC.invokeExact(
					options.ptr(), pathSeg, err);
			NativeCalls.checkError(err);

			MemorySegment baseDb = (MemorySegment) MH_OPTIMISTIC_GET_BASE_DB.invokeExact(ptr);
			return withDefaultColumnFamily(new OptimisticTransactionDB(ptr, baseDb));
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openOptimistic failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Factory — column families
	// -----------------------------------------------------------------------

	/// Opens a read-write database at `path` with multiple column families.
	///
	/// The `descriptors` list must include a descriptor for every existing column family in the
	/// database, including the default column family ([ColumnFamilyName#DEFAULT]). The returned
	/// database owns one [ColumnFamilyHandle] per descriptor and releases them when it is closed;
	/// look them up with `getColumnFamily(ColumnFamilyName)` on the returned database.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @param descriptors one descriptor per column family (must include [ColumnFamilyName#DEFAULT])
	/// @return a new [ReadWriteDB] instance
	/// @throws IllegalArgumentException if two descriptors share a name, or one carries a TTL (only
	///                                  [#openTtl(Path, Options, List)] accepts that)
	public static ReadWriteDB openReadWrite(Path path,
	                                        Options options,
	                                        List<ColumnFamilyDescriptor> descriptors) {
		int n = descriptors.size();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, false);

			MemorySegment ptr = (MemorySegment) MH_OPEN_CF.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);

			return adoptHandles(new ReadWriteDB(ptr), handlesArr, descriptors);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openReadWrite failed", t);
		}
	}

	/// Opens a read-only database at `path` with multiple column families.
	///
	/// The handles are owned by the returned database; look them up with
	/// `getColumnFamily(ColumnFamilyName)` on the returned database.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @param descriptors one descriptor per column family (must include [ColumnFamilyName#DEFAULT])
	/// @return a new [ReadOnlyDB] instance
	/// @throws IllegalArgumentException if two descriptors share a name, or one carries a TTL (only
	///                                  [#openTtl(Path, Options, List)] accepts that)
	public static ReadOnlyDB openReadOnly(Path path,
	                                      Options options,
	                                      List<ColumnFamilyDescriptor> descriptors) {
		return openReadOnly(path, options, descriptors, false);
	}

	/// Opens a read-only database at `path` with multiple column families.
	///
	/// @param path directory where the database files are stored
	/// @param options the database options
	/// @param descriptors one descriptor per column family (must include [ColumnFamilyName#DEFAULT])
	/// @param errorIfWalFileExists if `true`, fails when unrecovered WAL files are present
	/// @return a new [ReadOnlyDB] instance
	/// @throws IllegalArgumentException if two descriptors share a name, or one carries a TTL (only
	///                                  [#openTtl(Path, Options, List)] accepts that)
	public static ReadOnlyDB openReadOnly(Path path,
	                                      Options options,
	                                      List<ColumnFamilyDescriptor> descriptors,
	                                      boolean errorIfWalFileExists) {
		int n = descriptors.size();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, false);
			MemorySegment ptr = (MemorySegment) MH_OPEN_FOR_READ_ONLY_CF.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr,
					NativeCalls.toByte(errorIfWalFileExists), err);
			NativeCalls.checkError(err);
			return adoptHandles(new ReadOnlyDB(ptr), handlesArr, descriptors);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openReadOnly failed", t);
		}
	}

	/// Opens a TTL-aware read-write database at `path` with multiple column families.
	///
	/// Each family's TTL is the one given with [ColumnFamilyDescriptor#withTtl(Duration)]; a descriptor
	/// without one never expires its keys, the same as [Duration#ZERO]. The handles are owned by the
	/// returned database; look them up with `getColumnFamily(ColumnFamilyName)` on it.
	///
	/// @param path        path to the database directory
	/// @param options     database-level options
	/// @param descriptors one descriptor per column family (must include [ColumnFamilyName#DEFAULT]), each
	///                    optionally carrying its TTL
	/// @return an open [TtlDB] instance; caller must close it
	/// @throws IllegalArgumentException if two descriptors share a name
	public static TtlDB openTtl(Path path, Options options, List<ColumnFamilyDescriptor> descriptors) {
		int n = descriptors.size();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			MemorySegment ttlsArr = arena.allocate(ValueLayout.JAVA_INT, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, true);
			for (int i = 0; i < n; i++) {
				ttlsArr.setAtIndex(ValueLayout.JAVA_INT, i, descriptors.get(i).ttlSeconds());
			}
			MemorySegment ptr = (MemorySegment) MH_OPEN_CF_WITH_TTL.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, ttlsArr, err);
			NativeCalls.checkError(err);
			return adoptHandles(new TtlDB(ptr, null), handlesArr, descriptors);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openTtl failed", t);
		}
	}

	/// Opens a [TransactionDB] at `path` with multiple column families.
	///
	/// The handles are owned by the returned database; look them up with
	/// `getColumnFamily(ColumnFamilyName)` on the returned database.
	///
	/// @param path          path to the database directory
	/// @param options       database-level options
	/// @param txnDbOptions  transaction database options
	/// @param descriptors   one descriptor per column family (must include [ColumnFamilyName#DEFAULT])
	/// @return an open [TransactionDB] instance; caller must close it
	/// @throws IllegalArgumentException if two descriptors share a name, or one carries a TTL (only
	///                                  [#openTtl(Path, Options, List)] accepts that)
	public static TransactionDB openTransaction(Path path,
	                                            Options options,
	                                            TransactionDBOptions txnDbOptions,
	                                            List<ColumnFamilyDescriptor> descriptors) {
		int n = descriptors.size();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, false);
			MemorySegment ptr = (MemorySegment) MH_OPEN_TRANSACTION_CF.invokeExact(
					options.ptr(), txnDbOptions.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);
			MemorySegment baseDb = (MemorySegment) MH_TRANSACTION_GET_BASE_DB.invokeExact(ptr);
			return adoptHandles(new TransactionDB(ptr, baseDb), handlesArr, descriptors);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openTransaction failed", t);
		}
	}

	/// Opens an [OptimisticTransactionDB] at `path` with multiple column families.
	///
	/// The handles are owned by the returned database; look them up with
	/// `getColumnFamily(ColumnFamilyName)` on the returned database.
	///
	/// @param path        path to the database directory
	/// @param options     database-level options
	/// @param descriptors one descriptor per column family (must include [ColumnFamilyName#DEFAULT])
	/// @return an open [OptimisticTransactionDB] instance; caller must close it
	/// @throws IllegalArgumentException if two descriptors share a name, or one carries a TTL (only
	///                                  [#openTtl(Path, Options, List)] accepts that)
	public static OptimisticTransactionDB openOptimistic(Path path,
	                                                     Options options,
	                                                     List<ColumnFamilyDescriptor> descriptors) {
		int n = descriptors.size();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, false);
			MemorySegment ptr = (MemorySegment) MH_OPEN_OPTIMISTIC_CF.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);
			MemorySegment baseDb = (MemorySegment) MH_OPTIMISTIC_GET_BASE_DB.invokeExact(ptr);
			return adoptHandles(new OptimisticTransactionDB(ptr, baseDb), handlesArr, descriptors);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openOptimistic failed", t);
		}
	}

	/// Lists the names of all column families in the database at `path`.
	///
	/// Names only: the families' options are not restored. Building descriptors from these names with
	/// [ColumnFamilyDescriptor#of(ColumnFamilyName)] reopens every family with default options, not the
	/// ones it was created with. To reopen a database as it was configured, use
	/// [#loadLatestOptions(Path, boolean)], whose descriptors carry each family's saved options.
	///
	/// @param path    path to the database directory
	/// @param options database-level options used to open the database metadata
	/// @return the column family names, including [ColumnFamilyName#DEFAULT]
	public static List<ColumnFamilyName> listColumnFamilies(Path path, Options options) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);

			MemorySegment namesPtr = (MemorySegment) MH_LIST_CF.invokeExact(
					options.ptr(), pathSeg, lenSeg, err);
			NativeCalls.checkError(err);

			long count = lenSeg.get(ValueLayout.JAVA_LONG, 0);
			try {
				List<ColumnFamilyName> result = new ArrayList<>((int) count);
				MemorySegment namesArr = namesPtr.reinterpret(ValueLayout.ADDRESS.byteSize() * count);
				for (int i = 0; i < count; i++) {
					MemorySegment namePtr = namesArr.getAtIndex(ValueLayout.ADDRESS, i);
					result.add(ColumnFamilyName.ofBytes(NativeCalls.toBorrowedBytes(namePtr)));
				}
				return result;
			} finally {
				MH_LIST_CF_DESTROY.invokeExact(namesPtr, count);
			}
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("listColumnFamilies failed", t);
		}
	}

	/// Loads the options the database at `path` was last opened with, from its latest
	/// `OPTIONS-*` file, using the default [Env] and a default 32 MiB block cache.
	///
	/// See [LoadedOptions] for what is and is not restored.
	///
	/// @param path                 path to the database directory
	/// @param ignoreUnknownOptions if `true`, options this RocksDB version does not know (e.g.
	///                             written by a newer one) are skipped instead of failing the load
	/// @return the loaded options; caller must close it
	/// @throws RocksDBException if there is no readable `OPTIONS` file or it fails to parse
	public static LoadedOptions loadLatestOptions(Path path, boolean ignoreUnknownOptions) {
		try (Env env = Env.defaultEnv(); Cache cache = LRUCache.newLRUCache(MemorySize.ofMB(32))) {
			return loadLatestOptions(path, ignoreUnknownOptions, env, cache);
		}
	}

	/// Loads the options the database at `path` was last opened with, from its latest
	/// `OPTIONS-*` file.
	///
	/// `cache` becomes the block cache of every block-based table factory in the loaded options,
	/// so the loaded options keep it alive after the caller closes its own [Cache] wrapper. See
	/// [LoadedOptions] for what is and is not restored.
	///
	/// @param path                 path to the database directory
	/// @param ignoreUnknownOptions if `true`, options this RocksDB version does not know (e.g.
	///                             written by a newer one) are skipped instead of failing the load
	/// @param env                  environment used to read the `OPTIONS` file
	/// @param cache                block cache shared into the loaded table options
	/// @return the loaded options; caller must close it
	/// @throws RocksDBException if there is no readable `OPTIONS` file or it fails to parse
	public static LoadedOptions loadLatestOptions(Path path, boolean ignoreUnknownOptions, Env env, Cache cache) {
		Objects.requireNonNull(env, "env");
		Objects.requireNonNull(cache, "cache");
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment dbOptionsOut = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment countOut = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment namesOut = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment optionsOut = arena.allocate(ValueLayout.ADDRESS);

			MH_LOAD_LATEST_OPTIONS.invokeExact(arena.allocateFrom(path.toString()), env.ptr(),
					ignoreUnknownOptions, cache.ptr(), dbOptionsOut, countOut, namesOut, optionsOut, err);
			NativeCalls.checkError(err);

			MemorySegment nativeDbOptions = dbOptionsOut.get(ValueLayout.ADDRESS, 0);
			long count = countOut.get(ValueLayout.JAVA_LONG, 0);
			MemorySegment names = namesOut.get(ValueLayout.ADDRESS, 0);
			MemorySegment options = optionsOut.get(ValueLayout.ADDRESS, 0);
			try {
				MemorySegment namesArr = names.reinterpret(ValueLayout.ADDRESS.byteSize() * count);
				MemorySegment optionsArr = options.reinterpret(ValueLayout.ADDRESS.byteSize() * count);
				List<ColumnFamilyDescriptor> descriptors = new ArrayList<>((int) count);
				Options dbOptions = Options.copyOf(nativeDbOptions);
				try {
					for (int i = 0; i < count; i++) {
						ColumnFamilyName name = ColumnFamilyName.ofBytes(
								NativeCalls.toBorrowedBytes(namesArr.getAtIndex(ValueLayout.ADDRESS, i)));
						descriptors.add(ColumnFamilyDescriptor.of(name,
								Options.copyOf(optionsArr.getAtIndex(ValueLayout.ADDRESS, i))));
					}
				} catch (Throwable t) {
					dbOptions.close();
					descriptors.forEach(d -> d.options().close());
					throw t;
				}
				return new LoadedOptions(dbOptions, descriptors);
			} finally {
				MH_LOAD_LATEST_OPTIONS_DESTROY.invokeExact(nativeDbOptions, names, options, count);
			}
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("loadLatestOptions failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Column family open helpers
	// -----------------------------------------------------------------------

	/// The parallel `char* names[]` / `rocksdb_options_t* options[]` arrays a
	/// `*_column_families` C API call expects, one entry per descriptor.
	private record CfNamesAndOptions(MemorySegment names, MemorySegment options) {
	}

	/// Allocates and fills the parallel names/options arrays every `*_column_families` open
	/// call marshals, one entry per `descriptors[i]`.
	///
	/// @param arena       arena backing the returned native arrays (and any allocated names)
	/// @param descriptors one descriptor per column family
	/// @param allowTtl    whether a descriptor may carry a TTL (only for [TtlDB])
	/// @return the names and options arrays, both length `descriptors.size()`
	private static CfNamesAndOptions buildCfArrays(Arena arena, List<ColumnFamilyDescriptor> descriptors,
	                                               boolean allowTtl) {
		int n = descriptors.size();
		var seen = new HashSet<ColumnFamilyName>();
		for (ColumnFamilyDescriptor desc : descriptors) {
			if (!seen.add(desc.name())) {
				throw new IllegalArgumentException("duplicate column family name: " + desc.name());
			}
			if (!allowTtl) {
				desc.requireNoTtl();
			}
		}
		MemorySegment namesArr = arena.allocate(ValueLayout.ADDRESS, n);
		MemorySegment optsArr = arena.allocate(ValueLayout.ADDRESS, n);
		for (int i = 0; i < n; i++) {
			ColumnFamilyDescriptor desc = descriptors.get(i);
			namesArr.setAtIndex(ValueLayout.ADDRESS, i,
					desc.name().toNative(arena));
			optsArr.setAtIndex(ValueLayout.ADDRESS, i, desc.options().ptr());
		}
		NativeCalls.requireNoNullEntries(namesArr, n, "column family names array");
		NativeCalls.requireNoNullEntries(optsArr, n, "column family options array");
		return new CfNamesAndOptions(namesArr, optsArr);
	}

	/// Hands the `rocksdb_column_family_handle_t*[]` array populated by a `*_column_families` open
	/// call to `db`, which owns and later releases each handle. On failure `db` is closed.
	///
	/// @param db          the freshly opened database
	/// @param handlesArr  native array of one handle pointer per descriptor
	/// @param descriptors the descriptors passed to the open call, in the same order
	/// @param <T>         the database type
	/// @return `db`
	private static <T extends NativeDB> T adoptHandles(T db, MemorySegment handlesArr, List<ColumnFamilyDescriptor> descriptors) {
		try {
			for (int i = 0; i < descriptors.size(); i++) {
				db.adopt(descriptors.get(i).name(), handlesArr.getAtIndex(ValueLayout.ADDRESS, i));
			}
			return db;
		} catch (Throwable t) {
			db.close();
			throw t;
		}
	}

	/// Registers the default column family handle of a database opened without column family
	/// descriptors, so `getColumnFamily(ColumnFamilyName)` on the returned database works on every database.
	///
	/// @param db the freshly opened database
	/// @param <T> the database type
	/// @return `db` aa
	private static <T extends NativeDB> T withDefaultColumnFamily(T db) {
		try {
			db.adopt(ColumnFamilyName.DEFAULT, ColumnFamilyHandle.defaultHandle(db.dbPtr()));
			return db;
		} catch (Throwable t) {
			db.close();
			throw t;
		}
	}
}
