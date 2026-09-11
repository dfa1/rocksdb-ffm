package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
/// | [#openBlob] | [BlobDB] |
/// | [#openTransaction] | [TransactionDB] |
/// | [#openOptimistic] | [OptimisticTransactionDB] |
///
/// `RocksDB` is non-instantiable and holds only the `rocksdb_t*` open/list-column-families
/// method handles used by its own static factories. Every other capability (read, write,
/// compaction, monitoring, tracing) has its own `MethodHandle`-holding companion class instead
/// -- `RocksDBReadOperationsBindings`, `RocksDBWriteOperationsBindings`,
/// `CompactionOperationsBindings`, `RocksDBMonitoringOperationsBindings`,
/// `RocksDBTracingOperationsBindings` -- and low-level shared plumbing (`errptr` handling,
/// native string/byte marshaling, `close`/`free`) lives on [NativeCalls]. See
/// [#131](https://github.com/dfa1/rocksdbffm/issues/131).
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
	private static final MethodHandle MH_GET_BASE_DB;

	// -----------------------------------------------------------------------
	// Column-family open/list method handles — used only inside factory methods
	// -----------------------------------------------------------------------

	/// `rocksdb_t* rocksdb_open_column_families(const rocksdb_options_t* options, const char* name, int num_column_families, const char* const* column_family_names, const rocksdb_options_t* const* column_family_options, rocksdb_column_family_handle_t** column_family_handles, char** errptr);`
	private static final MethodHandle MH_OPEN_CF;
	/// `char** rocksdb_list_column_families(const rocksdb_options_t* options, const char* name, size_t* lencf, char** errptr);`
	private static final MethodHandle MH_LIST_CF;
	/// `void rocksdb_list_column_families_destroy(char** list, size_t len);`
	private static final MethodHandle MH_LIST_CF_DESTROY;
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

		MH_GET_BASE_DB = NativeLibrary.lookup("rocksdb_optimistictransactiondb_get_base_db",
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
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @return a new [ReadWriteDB] instance
	public static ReadWriteDB openReadWrite(Options options, Path path) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment ptr = (MemorySegment) MH_OPEN.invokeExact(options.ptr(), pathSeg, err);
			NativeCalls.checkError(err);
			return new ReadWriteDB(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openReadWrite failed", t);
		}
	}

	/// Equivalent to `openReadWrite(options, path)` with `createIfMissing = true`.
	///
	/// @param path directory where the database files are stored
	/// @return a new [ReadWriteDB] instance
	public static ReadWriteDB openReadWrite(Path path) {
		try (Options opts = Options.newOptions().setCreateIfMissing(true)) {
			return openReadWrite(opts, path);
		}
	}

	/// Opens (or creates) a TTL-aware read-write database at `path`.
	///
	/// Keys are lazily expired during the next compaction that covers their
	/// range. A `ttl` of [Duration#ZERO] disables expiry entirely.
	///
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @param ttl time-to-live for keys; [Duration#ZERO] disables expiry
	/// @return a new [TtlDB] instance
	public static TtlDB openTtl(Options options, Path path, Duration ttl) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment ptr = (MemorySegment) MH_OPEN_WITH_TTL.invokeExact(
					options.ptr(), pathSeg, (int) ttl.toSeconds(), err);
			NativeCalls.checkError(err);
			return new TtlDB(ptr, ttl);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openTtl failed", t);
		}
	}

	/// Equivalent to `openTtl(options, path, ttl)` with `createIfMissing = true`.
	///
	/// @param path directory where the database files are stored
	/// @param ttl time-to-live for keys; [Duration#ZERO] disables expiry
	/// @return a new [TtlDB] instance
	public static TtlDB openTtl(Path path, Duration ttl) {
		try (Options opts = Options.newOptions().setCreateIfMissing(true)) {
			return openTtl(opts, path, ttl);
		}
	}

	/// Opens (or creates) a blob-enabled read-write database at `path`.
	///
	/// BlobDB stores large values (≥ [Options#setMinBlobSize]) in separate blob files,
	/// reducing write amplification for value-heavy workloads.
	/// The caller is responsible for setting [Options#setEnableBlobFiles(boolean)] to `true`
	/// and any other blob options before calling this method.
	///
	/// @param options the database options (must have blob files enabled)
	/// @param path directory where the database files are stored
	/// @return a new [BlobDB] instance
	public static BlobDB openBlob(Options options, Path path) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment ptr = (MemorySegment) MH_OPEN.invokeExact(options.ptr(), pathSeg, err);
			NativeCalls.checkError(err);
			return new BlobDB(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openBlob failed", t);
		}
	}

	/// Equivalent to `openBlob(options, path)` with `createIfMissing = true`
	/// and `enableBlobFiles = true`.
	///
	/// @param path directory where the database files are stored
	/// @return a new [BlobDB] instance
	public static BlobDB openBlob(Path path) {
		try (Options opts = Options.newOptions().setCreateIfMissing(true).setEnableBlobFiles(true)) {
			return openBlob(opts, path);
		}
	}

	// -----------------------------------------------------------------------
	// Factory — read-only
	// -----------------------------------------------------------------------

	/// Opens the database at `path` in read-only mode.
	///
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @param errorIfWalFileExists if `true`, fails when unrecovered WAL files are present
	/// @return a new [ReadOnlyDB] instance
	public static ReadOnlyDB openReadOnly(Options options, Path path, boolean errorIfWalFileExists) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment ptr = (MemorySegment) MH_OPEN_FOR_READ_ONLY.invokeExact(
					options.ptr(), pathSeg, NativeCalls.toByte(errorIfWalFileExists), err);
			NativeCalls.checkError(err);
			return new ReadOnlyDB(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openReadOnly failed", t);
		}
	}

	/// Equivalent to `openReadOnly(options, path, false)`.
	///
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @return a new [ReadOnlyDB] instance
	public static ReadOnlyDB openReadOnly(Options options, Path path) {
		return openReadOnly(options, path, false);
	}

	/// Opens the database at `path` in read-only mode with default options.
	///
	/// @param path directory where the database files are stored
	/// @return a new [ReadOnlyDB] instance
	public static ReadOnlyDB openReadOnly(Path path) {
		try (Options opts = Options.newOptions()) {
			return openReadOnly(opts, path, false);
		}
	}

	// -----------------------------------------------------------------------
	// Factory — secondary
	// -----------------------------------------------------------------------

	/// Opens a secondary (read-only replica) instance of the database at `primaryPath`.
	///
	/// @param options the database options
	/// @param primaryPath directory of the primary database
	/// @param secondaryPath a dedicated directory for this secondary's own MANIFEST/WAL tails
	/// @return a new [SecondaryDB] instance
	public static SecondaryDB openSecondary(Options options, Path primaryPath, Path secondaryPath) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment primary = arena.allocateFrom(primaryPath.toString());
			MemorySegment secondary = arena.allocateFrom(secondaryPath.toString());

			MemorySegment ptr = (MemorySegment) MH_OPEN_SECONDARY.invokeExact(
					options.ptr(), primary, secondary, err);
			NativeCalls.checkError(err);

			return new SecondaryDB(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openSecondary failed", t);
		}
	}

	/// Opens a secondary (read-only replica) instance at `primaryPath` with multiple column
	/// families. The `handles` list is cleared and populated with one [ColumnFamilyHandle]
	/// per descriptor, each legitimately scoped to this secondary instance — unlike a handle
	/// obtained from the primary or any other `rocksdb_t*`, safe to pass to this instance's
	/// column-family-scoped reads.
	///
	/// @param options      the database options
	/// @param primaryPath   directory of the primary database
	/// @param secondaryPath a dedicated directory for this secondary's own MANIFEST/WAL tails
	/// @param descriptors   one descriptor per column family (must include `"default"`)
	/// @param handles       output list populated with one handle per descriptor
	/// @return a new [SecondaryDB] instance
	public static SecondaryDB openSecondary(Options options, Path primaryPath, Path secondaryPath,
	                                        List<ColumnFamilyDescriptor> descriptors,
	                                        List<ColumnFamilyHandle> handles) {
		int n = descriptors.size();
		List<Options> tempOptions = new ArrayList<>();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment primary = arena.allocateFrom(primaryPath.toString());
			MemorySegment secondary = arena.allocateFrom(secondaryPath.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, tempOptions);

			MemorySegment ptr = (MemorySegment) MH_OPEN_SECONDARY_CF.invokeExact(
					options.ptr(), primary, secondary, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);

			collectCfHandles(handlesArr, n, handles);
			return new SecondaryDB(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openSecondary failed", t);
		} finally {
			closeTempOptions(tempOptions);
		}
	}

	// -----------------------------------------------------------------------
	// Factory — transactional
	// -----------------------------------------------------------------------

	/// Opens a [TransactionDB] (pessimistic / locking transactions) at `path`.
	///
	/// @param options the database options
	/// @param txnDbOptions the transaction DB options
	/// @param path directory where the database files are stored
	/// @return a new [TransactionDB] instance
	public static TransactionDB openTransaction(Options options, TransactionDBOptions txnDbOptions, Path path) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());

			MemorySegment ptr = (MemorySegment) MH_OPEN_TRANSACTION.invokeExact(
					options.ptr(), txnDbOptions.ptr(), pathSeg, err);
			NativeCalls.checkError(err);

			MemorySegment baseDb = (MemorySegment) MH_TRANSACTION_GET_BASE_DB.invokeExact(ptr);
			return new TransactionDB(ptr, baseDb);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openTransaction failed", t);
		}
	}

	/// Opens an [OptimisticTransactionDB] (conflict-detection-at-commit) at `path`.
	///
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @return a new [OptimisticTransactionDB] instance
	public static OptimisticTransactionDB openOptimistic(Options options, Path path) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());

			MemorySegment ptr = (MemorySegment) MH_OPEN_OPTIMISTIC.invokeExact(
					options.ptr(), pathSeg, err);
			NativeCalls.checkError(err);

			MemorySegment baseDb = (MemorySegment) MH_GET_BASE_DB.invokeExact(ptr);
			return new OptimisticTransactionDB(ptr, baseDb);
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
	/// database, including the default column family (`"default"`). The `handles` list is cleared
	/// and populated with one [ColumnFamilyHandle] per descriptor, in the same order. The caller
	/// is responsible for closing each handle.
	///
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @param descriptors one descriptor per column family (must include `"default"`)
	/// @param handles output list populated with one handle per descriptor
	/// @return a new [ReadWriteDB] instance
	public static ReadWriteDB openReadWrite(Options options, Path path,
	                                        List<ColumnFamilyDescriptor> descriptors,
	                                        List<ColumnFamilyHandle> handles) {
		int n = descriptors.size();
		List<Options> tempOptions = new ArrayList<>();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, tempOptions);

			MemorySegment ptr = (MemorySegment) MH_OPEN_CF.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);

			collectCfHandles(handlesArr, n, handles);
			return new ReadWriteDB(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openReadWrite failed", t);
		} finally {
			closeTempOptions(tempOptions);
		}
	}

	/// Opens a blob-enabled read-write database at `path` with multiple column families.
	///
	/// The `handles` list is cleared and populated with one [ColumnFamilyHandle] per descriptor.
	/// Blob file options (min blob size, blob compression, ...) are set per column family via
	/// each descriptor's [Options], same as for [#openReadWrite(Options, Path, List, List)].
	///
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @param descriptors one descriptor per column family (must include `"default"`)
	/// @param handles output list populated with one handle per descriptor
	/// @return a new [BlobDB] instance
	public static BlobDB openBlob(Options options, Path path,
	                              List<ColumnFamilyDescriptor> descriptors,
	                              List<ColumnFamilyHandle> handles) {
		int n = descriptors.size();
		List<Options> tempOptions = new ArrayList<>();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, tempOptions);

			MemorySegment ptr = (MemorySegment) MH_OPEN_CF.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);

			collectCfHandles(handlesArr, n, handles);
			return new BlobDB(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openBlob failed", t);
		} finally {
			closeTempOptions(tempOptions);
		}
	}

	/// Opens a read-only database at `path` with multiple column families.
	///
	/// The `handles` list is cleared and populated with one [ColumnFamilyHandle] per descriptor.
	///
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @param descriptors one descriptor per column family (must include `"default"`)
	/// @param handles output list populated with one handle per descriptor
	/// @return a new [ReadOnlyDB] instance
	public static ReadOnlyDB openReadOnly(Options options, Path path,
	                                      List<ColumnFamilyDescriptor> descriptors,
	                                      List<ColumnFamilyHandle> handles) {
		return openReadOnly(options, path, descriptors, handles, false);
	}

	/// Opens a read-only database at `path` with multiple column families.
	///
	/// @param options the database options
	/// @param path directory where the database files are stored
	/// @param descriptors one descriptor per column family (must include `"default"`)
	/// @param handles output list populated with one handle per descriptor
	/// @param errorIfWalFileExists if `true`, fails when unrecovered WAL files are present
	/// @return a new [ReadOnlyDB] instance
	public static ReadOnlyDB openReadOnly(Options options, Path path,
	                                      List<ColumnFamilyDescriptor> descriptors,
	                                      List<ColumnFamilyHandle> handles,
	                                      boolean errorIfWalFileExists) {
		int n = descriptors.size();
		List<Options> tempOptions = new ArrayList<>();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, tempOptions);
			MemorySegment ptr = (MemorySegment) MH_OPEN_FOR_READ_ONLY_CF.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr,
					NativeCalls.toByte(errorIfWalFileExists), err);
			NativeCalls.checkError(err);
			collectCfHandles(handlesArr, n, handles);
			return new ReadOnlyDB(ptr);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openReadOnly failed", t);
		} finally {
			closeTempOptions(tempOptions);
		}
	}

	/// Opens a TTL-aware read-write database at `path` with multiple column families.
	///
	/// Each column family is paired with its own TTL from `ttls` (index-aligned with `descriptors`).
	/// A TTL of [Duration#ZERO] disables expiry for that column family.
	/// The `handles` list is cleared and populated with one [ColumnFamilyHandle] per descriptor.
	///
	/// @param options     database-level options
	/// @param path        path to the database directory
	/// @param descriptors column family descriptors (name + optional per-CF options)
	/// @param ttls        per-column-family TTLs, index-aligned with `descriptors`
	/// @param handles     output list; cleared and filled with one handle per descriptor
	/// @return an open [TtlDB] instance; caller must close it
	public static TtlDB openTtl(Options options, Path path,
	                            List<ColumnFamilyDescriptor> descriptors,
	                            List<Duration> ttls,
	                            List<ColumnFamilyHandle> handles) {
		int n = descriptors.size();
		List<Options> tempOptions = new ArrayList<>();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			MemorySegment ttlsArr = arena.allocate(ValueLayout.JAVA_INT, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, tempOptions);
			for (int i = 0; i < n; i++) {
				ttlsArr.setAtIndex(ValueLayout.JAVA_INT, i, (int) ttls.get(i).toSeconds());
			}
			MemorySegment ptr = (MemorySegment) MH_OPEN_CF_WITH_TTL.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, ttlsArr, err);
			NativeCalls.checkError(err);
			collectCfHandles(handlesArr, n, handles);
			Duration globalTtl = ttls.isEmpty() ? Duration.ZERO : ttls.getFirst();
			return new TtlDB(ptr, globalTtl);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openTtl failed", t);
		} finally {
			closeTempOptions(tempOptions);
		}
	}

	/// Opens a [TransactionDB] at `path` with multiple column families.
	///
	/// The `handles` list is cleared and populated with one [ColumnFamilyHandle] per descriptor.
	///
	/// @param options       database-level options
	/// @param txnDbOptions  transaction database options
	/// @param path          path to the database directory
	/// @param descriptors   column family descriptors (name + optional per-CF options)
	/// @param handles       output list; cleared and filled with one handle per descriptor
	/// @return an open [TransactionDB] instance; caller must close it
	public static TransactionDB openTransaction(Options options,
	                                            TransactionDBOptions txnDbOptions,
	                                            Path path,
	                                            List<ColumnFamilyDescriptor> descriptors,
	                                            List<ColumnFamilyHandle> handles) {
		int n = descriptors.size();
		List<Options> tempOptions = new ArrayList<>();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, tempOptions);
			MemorySegment ptr = (MemorySegment) MH_OPEN_TRANSACTION_CF.invokeExact(
					options.ptr(), txnDbOptions.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);
			collectCfHandles(handlesArr, n, handles);
			MemorySegment baseDb = (MemorySegment) MH_TRANSACTION_GET_BASE_DB.invokeExact(ptr);
			return new TransactionDB(ptr, baseDb);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openTransaction failed", t);
		} finally {
			closeTempOptions(tempOptions);
		}
	}

	/// Opens an [OptimisticTransactionDB] at `path` with multiple column families.
	///
	/// The `handles` list is cleared and populated with one [ColumnFamilyHandle] per descriptor.
	///
	/// @param options     database-level options
	/// @param path        path to the database directory
	/// @param descriptors column family descriptors (name + optional per-CF options)
	/// @param handles     output list; cleared and filled with one handle per descriptor
	/// @return an open [OptimisticTransactionDB] instance; caller must close it
	public static OptimisticTransactionDB openOptimistic(Options options, Path path,
	                                                     List<ColumnFamilyDescriptor> descriptors,
	                                                     List<ColumnFamilyHandle> handles) {
		int n = descriptors.size();
		List<Options> tempOptions = new ArrayList<>();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment handlesArr = arena.allocate(ValueLayout.ADDRESS, n);
			CfNamesAndOptions cfArrays = buildCfArrays(arena, descriptors, tempOptions);
			MemorySegment ptr = (MemorySegment) MH_OPEN_OPTIMISTIC_CF.invokeExact(
					options.ptr(), pathSeg, n, cfArrays.names(), cfArrays.options(), handlesArr, err);
			NativeCalls.checkError(err);
			collectCfHandles(handlesArr, n, handles);
			MemorySegment baseDb = (MemorySegment) MH_GET_BASE_DB.invokeExact(ptr);
			return new OptimisticTransactionDB(ptr, baseDb);
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("openOptimistic failed", t);
		} finally {
			closeTempOptions(tempOptions);
		}
	}

	/// Lists the names of all column families in the database at `path`.
	///
	/// @param options database-level options used to open the database metadata
	/// @param path    path to the database directory
	/// @return list of column family names as raw byte arrays
	public static List<byte[]> listColumnFamilies(Options options, Path path) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment err = NativeCalls.errHolder(arena);
			MemorySegment pathSeg = arena.allocateFrom(path.toString());
			MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);

			MemorySegment namesPtr = (MemorySegment) MH_LIST_CF.invokeExact(
					options.ptr(), pathSeg, lenSeg, err);
			NativeCalls.checkError(err);

			long count = lenSeg.get(ValueLayout.JAVA_LONG, 0);
			List<byte[]> result = new ArrayList<>((int) count);
			MemorySegment namesArr = namesPtr.reinterpret(ValueLayout.ADDRESS.byteSize() * count);
			for (int i = 0; i < count; i++) {
				MemorySegment namePtr = namesArr.getAtIndex(ValueLayout.ADDRESS, i);
				result.add(NativeCalls.toBorrowedJavaString(namePtr).getBytes(StandardCharsets.UTF_8));
			}
			MH_LIST_CF_DESTROY.invokeExact(namesPtr, count);
			return result;
		} catch (Throwable t) {
			throw NativeCalls.wrapInvokeFailure("listColumnFamilies failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Package-private CF helpers
	// -----------------------------------------------------------------------

	/// The parallel `char* names[]` / `rocksdb_options_t* options[]` arrays a
	/// `*_column_families` C API call expects, one entry per descriptor.
	private record CfNamesAndOptions(MemorySegment names, MemorySegment options) {
	}

	/// Guards against handing a native call an array with an unfilled (NULL) slot — every C
	/// API this backs (`rocksdb_open_column_families`, `rocksdb_ingest_external_file`, ...)
	/// dereferences each entry as a pointer with no null check of its own, so a slot a
	/// marshalling loop failed to populate would otherwise crash the JVM instead of failing
	/// in Java. Cheap (a handful of pointer comparisons at DB-open/ingest time, not a hot
	/// per-key path) insurance against that class of bug, whatever causes it.
	///
	/// @param arr  native `ADDRESS[n]` array to check
	/// @param n    number of entries to check
	/// @param what description of the array's contents, for the error message
	/// @throws AssertionError if any of the first `n` entries is `MemorySegment.NULL`
	static void requireNoNullEntries(MemorySegment arr, int n, String what) {
		for (int i = 0; i < n; i++) {
			if (MemorySegment.NULL.equals(arr.getAtIndex(ValueLayout.ADDRESS, i))) {
				throw new AssertionError(what + " has an unpopulated (NULL) entry at index " + i);
			}
		}
	}

	/// Allocates and fills the parallel names/options arrays every `*_column_families` open
	/// call marshals, one entry per `descriptors[i]`. A descriptor with no explicit
	/// [ColumnFamilyDescriptor#options()] gets a fresh, disposable [Options] instance,
	/// appended to `tempOptions` so the caller can close it once the native call returns.
	///
	/// @param arena       arena backing the returned native arrays (and any allocated names)
	/// @param descriptors one descriptor per column family
	/// @param tempOptions appended with any default [Options] created here; caller must close them
	/// @return the names and options arrays, both length `descriptors.size()`
	private static CfNamesAndOptions buildCfArrays(Arena arena, List<ColumnFamilyDescriptor> descriptors,
	                                               List<Options> tempOptions) {
		int n = descriptors.size();
		MemorySegment namesArr = arena.allocate(ValueLayout.ADDRESS, n);
		MemorySegment optsArr = arena.allocate(ValueLayout.ADDRESS, n);
		for (int i = 0; i < n; i++) {
			ColumnFamilyDescriptor desc = descriptors.get(i);
			namesArr.setAtIndex(ValueLayout.ADDRESS, i,
					arena.allocateFrom(new String(desc.name(), StandardCharsets.UTF_8)));
			Options cfOpts = desc.options();
			if (cfOpts == null) {
				cfOpts = Options.newOptions();
				tempOptions.add(cfOpts);
			}
			optsArr.setAtIndex(ValueLayout.ADDRESS, i, cfOpts.ptr());
		}
		requireNoNullEntries(namesArr, n, "column family names array");
		requireNoNullEntries(optsArr, n, "column family options array");
		return new CfNamesAndOptions(namesArr, optsArr);
	}

	/// Reads a native `rocksdb_column_family_handle_t*[]` array populated by a
	/// `*_column_families` open call back into `handles`, wrapping each entry.
	///
	/// @param handlesArr native array of `n` column family handle pointers
	/// @param n          number of handles
	/// @param handles    output list; cleared then populated with one handle per entry
	private static void collectCfHandles(MemorySegment handlesArr, int n, List<ColumnFamilyHandle> handles) {
		handles.clear();
		for (int i = 0; i < n; i++) {
			handles.add(ColumnFamilyHandle.wrap(handlesArr.getAtIndex(ValueLayout.ADDRESS, i)));
		}
	}

	/// Closes every [Options] a [#buildCfArrays] call appended to `tempOptions`.
	///
	/// @param tempOptions options to close, as populated by [#buildCfArrays]
	private static void closeTempOptions(List<Options> tempOptions) {
		for (Options o : tempOptions) {
			o.close();
		}
	}
}
