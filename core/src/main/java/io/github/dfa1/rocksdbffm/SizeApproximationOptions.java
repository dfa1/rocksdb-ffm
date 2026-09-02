package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// Options controlling [MonitoringOperations#getApproximateSizes(SizeApproximationOptions, java.util.List)]
/// and its column-family-scoped overload -- what data to count toward the estimate, and how
/// precise the estimate needs to be.
///
/// ```
/// try (var opts = SizeApproximationOptions.create().setIncludeMemtables(true);
///      var sizes = db.getApproximateSizes(opts, List.of(Range.of(start, end)))) {
///     // ...
/// }
/// ```
public final class SizeApproximationOptions extends NativeObject {

	/// `rocksdb_size_approximation_options_t* rocksdb_size_approximation_options_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_size_approximation_options_destroy(rocksdb_size_approximation_options_t* options);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_size_approximation_options_set_include_memtables(rocksdb_size_approximation_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_INCLUDE_MEMTABLES;
	/// `unsigned char rocksdb_size_approximation_options_get_include_memtables(rocksdb_size_approximation_options_t* opt);`
	private static final MethodHandle MH_GET_INCLUDE_MEMTABLES;
	/// `void rocksdb_size_approximation_options_set_include_files(rocksdb_size_approximation_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_INCLUDE_FILES;
	/// `unsigned char rocksdb_size_approximation_options_get_include_files(rocksdb_size_approximation_options_t* opt);`
	private static final MethodHandle MH_GET_INCLUDE_FILES;
	/// `void rocksdb_size_approximation_options_set_include_blob_files(rocksdb_size_approximation_options_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_INCLUDE_BLOB_FILES;
	/// `unsigned char rocksdb_size_approximation_options_get_include_blob_files(rocksdb_size_approximation_options_t* opt);`
	private static final MethodHandle MH_GET_INCLUDE_BLOB_FILES;
	/// `void rocksdb_size_approximation_options_set_files_size_error_margin(rocksdb_size_approximation_options_t* opt, double v);`
	private static final MethodHandle MH_SET_FILES_SIZE_ERROR_MARGIN;
	/// `double rocksdb_size_approximation_options_get_files_size_error_margin(rocksdb_size_approximation_options_t* opt);`
	private static final MethodHandle MH_GET_FILES_SIZE_ERROR_MARGIN;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_size_approximation_options_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_size_approximation_options_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_INCLUDE_MEMTABLES = NativeLibrary.lookup("rocksdb_size_approximation_options_set_include_memtables",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_INCLUDE_MEMTABLES = NativeLibrary.lookup("rocksdb_size_approximation_options_get_include_memtables",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_INCLUDE_FILES = NativeLibrary.lookup("rocksdb_size_approximation_options_set_include_files",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_INCLUDE_FILES = NativeLibrary.lookup("rocksdb_size_approximation_options_get_include_files",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_INCLUDE_BLOB_FILES = NativeLibrary.lookup("rocksdb_size_approximation_options_set_include_blob_files",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_INCLUDE_BLOB_FILES = NativeLibrary.lookup("rocksdb_size_approximation_options_get_include_blob_files",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_FILES_SIZE_ERROR_MARGIN = NativeLibrary.lookup(
				"rocksdb_size_approximation_options_set_files_size_error_margin",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE));

		MH_GET_FILES_SIZE_ERROR_MARGIN = NativeLibrary.lookup(
				"rocksdb_size_approximation_options_get_files_size_error_margin",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
	}

	private SizeApproximationOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates size approximation options with RocksDB defaults: memtables excluded, on-disk
	/// files included, blob files excluded, exact (not error-margined) file size computation.
	///
	/// @return a new instance; caller must close it
	public static SizeApproximationOptions create() {
		try {
			return new SizeApproximationOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("SizeApproximationOptions create failed", t);
		}
	}

	/// If `true`, the estimate includes data still sitting in memtables that hasn't been
	/// flushed to disk yet. At least one of [#setIncludeMemtables]/[#setIncludeFiles] must be
	/// `true`. Default: `false`.
	///
	/// @param value `true` to include memtable data in the estimate
	/// @return `this` for chaining
	public SizeApproximationOptions setIncludeMemtables(boolean value) {
		try {
			MH_SET_INCLUDE_MEMTABLES.invokeExact(ptr(), RocksDB.toByte(value));
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIncludeMemtables failed", t);
		}
	}

	/// Returns whether memtable data is included in the estimate.
	///
	/// @return `true` if memtable data is included
	public boolean isIncludeMemtables() {
		try {
			return RocksDB.fromByte((byte) MH_GET_INCLUDE_MEMTABLES.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("isIncludeMemtables failed", t);
		}
	}

	/// If `true`, the estimate includes data already serialized to SST files on disk. At least
	/// one of [#setIncludeMemtables]/[#setIncludeFiles] must be `true`. Default: `true`.
	///
	/// @param value `true` to include on-disk SST data in the estimate
	/// @return `this` for chaining
	public SizeApproximationOptions setIncludeFiles(boolean value) {
		try {
			MH_SET_INCLUDE_FILES.invokeExact(ptr(), RocksDB.toByte(value));
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIncludeFiles failed", t);
		}
	}

	/// Returns whether on-disk SST data is included in the estimate.
	///
	/// @return `true` if on-disk SST data is included
	public boolean isIncludeFiles() {
		try {
			return RocksDB.fromByte((byte) MH_GET_INCLUDE_FILES.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("isIncludeFiles failed", t);
		}
	}

	/// If `true`, the estimate includes an approximation of blob file data in the range,
	/// prorated by the ratio of SST data in the range to total SST data. Assumes blob data is
	/// distributed proportionally to SST data, and contributes `0` if there are no SST files
	/// (all data still in memtables) even if blob files exist on disk. Default: `false`.
	///
	/// @param value `true` to include a prorated blob file estimate
	/// @return `this` for chaining
	public SizeApproximationOptions setIncludeBlobFiles(boolean value) {
		try {
			MH_SET_INCLUDE_BLOB_FILES.invokeExact(ptr(), RocksDB.toByte(value));
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIncludeBlobFiles failed", t);
		}
	}

	/// Returns whether a prorated blob file estimate is included.
	///
	/// @return `true` if a prorated blob file estimate is included
	public boolean isIncludeBlobFiles() {
		try {
			return RocksDB.fromByte((byte) MH_GET_INCLUDE_BLOB_FILES.invokeExact(ptr()));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("isIncludeBlobFiles failed", t);
		}
	}

	/// Allows the on-disk file size portion of the estimate to be off by up to
	/// `totalFilesSize * margin`, trading precision for a cheaper computation -- e.g. `0.1`
	/// permits up to 10% error. A non-positive value (the default, `-1.0`) requests an exact,
	/// more CPU-intensive computation instead.
	///
	/// @param margin allowed error margin as a fraction, or a non-positive value for exact
	/// @return `this` for chaining
	public SizeApproximationOptions setFilesSizeErrorMargin(double margin) {
		try {
			MH_SET_FILES_SIZE_ERROR_MARGIN.invokeExact(ptr(), margin);
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setFilesSizeErrorMargin failed", t);
		}
	}

	/// Returns the configured file size error margin.
	///
	/// @return current file size error margin; non-positive means exact computation
	public double getFilesSizeErrorMargin() {
		try {
			return (double) MH_GET_FILES_SIZE_ERROR_MARGIN.invokeExact(ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getFilesSizeErrorMargin failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
