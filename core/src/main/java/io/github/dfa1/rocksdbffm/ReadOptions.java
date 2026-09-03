package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/// FFM wrapper for `rocksdb_readoptions_t`.
public final class ReadOptions extends NativeObject {

	/// `rocksdb_readoptions_t* rocksdb_readoptions_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_readoptions_destroy(rocksdb_readoptions_t*);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_readoptions_set_snapshot(rocksdb_readoptions_t*, const rocksdb_snapshot_t*);`
	private static final MethodHandle MH_SET_SNAPSHOT;
	/// `void rocksdb_readoptions_set_verify_checksums(rocksdb_readoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_VERIFY_CHECKSUMS;
	/// `unsigned char rocksdb_readoptions_get_verify_checksums(rocksdb_readoptions_t* opt);`
	private static final MethodHandle MH_GET_VERIFY_CHECKSUMS;
	/// `void rocksdb_readoptions_set_fill_cache(rocksdb_readoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_FILL_CACHE;
	/// `unsigned char rocksdb_readoptions_get_fill_cache(rocksdb_readoptions_t* opt);`
	private static final MethodHandle MH_GET_FILL_CACHE;
	/// `void rocksdb_readoptions_set_pin_data(rocksdb_readoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_PIN_DATA;
	/// `unsigned char rocksdb_readoptions_get_pin_data(rocksdb_readoptions_t* opt);`
	private static final MethodHandle MH_GET_PIN_DATA;
	/// `void rocksdb_readoptions_set_tailing(rocksdb_readoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_TAILING;
	/// `unsigned char rocksdb_readoptions_get_tailing(rocksdb_readoptions_t* opt);`
	private static final MethodHandle MH_GET_TAILING;
	/// `void rocksdb_readoptions_set_total_order_seek(rocksdb_readoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_TOTAL_ORDER_SEEK;
	/// `unsigned char rocksdb_readoptions_get_total_order_seek(rocksdb_readoptions_t* opt);`
	private static final MethodHandle MH_GET_TOTAL_ORDER_SEEK;
	/// `void rocksdb_readoptions_set_auto_prefix_mode(rocksdb_readoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_AUTO_PREFIX_MODE;
	/// `unsigned char rocksdb_readoptions_get_auto_prefix_mode(rocksdb_readoptions_t* opt);`
	private static final MethodHandle MH_GET_AUTO_PREFIX_MODE;
	/// `void rocksdb_readoptions_set_prefix_same_as_start(rocksdb_readoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_PREFIX_SAME_AS_START;
	/// `unsigned char rocksdb_readoptions_get_prefix_same_as_start(rocksdb_readoptions_t* opt);`
	private static final MethodHandle MH_GET_PREFIX_SAME_AS_START;
	/// `void rocksdb_readoptions_set_readahead_size(rocksdb_readoptions_t* opt, size_t v);`
	private static final MethodHandle MH_SET_READAHEAD_SIZE;
	/// `size_t rocksdb_readoptions_get_readahead_size(rocksdb_readoptions_t* opt);`
	private static final MethodHandle MH_GET_READAHEAD_SIZE;
	/// `void rocksdb_readoptions_set_iterate_lower_bound(rocksdb_readoptions_t*, const char* key, size_t keylen);`
	private static final MethodHandle MH_SET_ITERATE_LOWER_BOUND;
	/// `void rocksdb_readoptions_set_iterate_upper_bound(rocksdb_readoptions_t*, const char* key, size_t keylen);`
	private static final MethodHandle MH_SET_ITERATE_UPPER_BOUND;
	/// `void rocksdb_readoptions_set_request_id(rocksdb_readoptions_t*, const char* request_id, size_t request_id_len);`
	private static final MethodHandle MH_SET_REQUEST_ID;
	/// `void rocksdb_readoptions_clear_request_id(rocksdb_readoptions_t*);`
	private static final MethodHandle MH_CLEAR_REQUEST_ID;
	/// `const char* rocksdb_readoptions_get_request_id(rocksdb_readoptions_t*, size_t* request_id_len);`
	private static final MethodHandle MH_GET_REQUEST_ID;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_readoptions_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_readoptions_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_SNAPSHOT = NativeLibrary.lookup("rocksdb_readoptions_set_snapshot",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

		MH_SET_VERIFY_CHECKSUMS = NativeLibrary.lookup("rocksdb_readoptions_set_verify_checksums",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_VERIFY_CHECKSUMS = NativeLibrary.lookup("rocksdb_readoptions_get_verify_checksums",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_FILL_CACHE = NativeLibrary.lookup("rocksdb_readoptions_set_fill_cache",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_FILL_CACHE = NativeLibrary.lookup("rocksdb_readoptions_get_fill_cache",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_PIN_DATA = NativeLibrary.lookup("rocksdb_readoptions_set_pin_data",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_PIN_DATA = NativeLibrary.lookup("rocksdb_readoptions_get_pin_data",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_TAILING = NativeLibrary.lookup("rocksdb_readoptions_set_tailing",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_TAILING = NativeLibrary.lookup("rocksdb_readoptions_get_tailing",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_TOTAL_ORDER_SEEK = NativeLibrary.lookup("rocksdb_readoptions_set_total_order_seek",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_TOTAL_ORDER_SEEK = NativeLibrary.lookup("rocksdb_readoptions_get_total_order_seek",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_AUTO_PREFIX_MODE = NativeLibrary.lookup("rocksdb_readoptions_set_auto_prefix_mode",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_AUTO_PREFIX_MODE = NativeLibrary.lookup("rocksdb_readoptions_get_auto_prefix_mode",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_PREFIX_SAME_AS_START = NativeLibrary.lookup("rocksdb_readoptions_set_prefix_same_as_start",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_PREFIX_SAME_AS_START = NativeLibrary.lookup("rocksdb_readoptions_get_prefix_same_as_start",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_READAHEAD_SIZE = NativeLibrary.lookup("rocksdb_readoptions_set_readahead_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_READAHEAD_SIZE = NativeLibrary.lookup("rocksdb_readoptions_get_readahead_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_ITERATE_LOWER_BOUND = NativeLibrary.lookup("rocksdb_readoptions_set_iterate_lower_bound",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_SET_ITERATE_UPPER_BOUND = NativeLibrary.lookup("rocksdb_readoptions_set_iterate_upper_bound",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_SET_REQUEST_ID = NativeLibrary.lookup("rocksdb_readoptions_set_request_id",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_CLEAR_REQUEST_ID = NativeLibrary.lookup("rocksdb_readoptions_clear_request_id",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_GET_REQUEST_ID = NativeLibrary.lookup("rocksdb_readoptions_get_request_id",
				FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	// Backs iterate_lower_bound/iterate_upper_bound/request_id for the byte[] tier: the C API
	// stores a raw Slice view over whatever pointer is passed to set_iterate_*_bound rather than
	// copying it, so a copy made from a bound must outlive this ReadOptions, not just the setter
	// call. Closed alongside the native pointer in tryClose.
	private final Arena boundsArena;

	private ReadOptions(MemorySegment ptr) {
		super(ptr);
		this.boundsArena = Arena.ofConfined();
	}

	/// Creates [ReadOptions] with RocksDB defaults.
	///
	/// @return a new instance; caller must close it
	public static ReadOptions newReadOptions() {
		try {
			return new ReadOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("readoptions create failed", t);
		}
	}

	/// Pins reads to the given snapshot, providing a consistent point-in-time view.
	/// The snapshot must remain open for the lifetime of these read options.
	/// Pass `null` to clear a previously set snapshot.
	///
	/// @param snapshot the snapshot to use, or `null` to clear
	/// @return `this` for chaining
	public ReadOptions setSnapshot(Snapshot snapshot) {
		try {
			MemorySegment snapPtr = (snapshot == null) ? MemorySegment.NULL : snapshot.ptr();
			MH_SET_SNAPSHOT.invokeExact(ptr(), snapPtr);
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("readoptions setSnapshot failed", t);
		}
	}

	/// If `true`, checksums are verified for all data read from disk. Slower but catches
	/// corruption; block-cache hits are never re-verified regardless of this setting. Default: `true`.
	///
	/// @param verifyChecksums `true` to verify checksums on disk reads
	/// @return `this` for chaining
	public ReadOptions setVerifyChecksums(boolean verifyChecksums) {
		NativeFields.setBoolean(MH_SET_VERIFY_CHECKSUMS, ptr(), verifyChecksums);
		return this;
	}

	/// Returns whether checksums are verified for data read from disk.
	///
	/// @return `true` if checksum verification is enabled
	public boolean isVerifyChecksums() {
		return NativeFields.getBoolean(MH_GET_VERIFY_CHECKSUMS, ptr());
	}

	/// If `true`, blocks read from disk are inserted into the block cache. Set `false` for scans
	/// that should not evict hotter data from the cache. Default: `true`.
	///
	/// @param fillCache `true` to populate the block cache with blocks read for this operation
	/// @return `this` for chaining
	public ReadOptions setFillCache(boolean fillCache) {
		NativeFields.setBoolean(MH_SET_FILL_CACHE, ptr(), fillCache);
		return this;
	}

	/// Returns whether blocks read for this operation are inserted into the block cache.
	///
	/// @return `true` if the block cache is populated on read
	public boolean isFillCache() {
		return NativeFields.getBoolean(MH_GET_FILL_CACHE, ptr());
	}

	/// If `true`, an iterator's returned keys/values stay valid for the iterator's lifetime
	/// instead of only until the next `Next()`/`Prev()` call, at the cost of pinning the
	/// underlying blocks in memory. Default: `false`.
	///
	/// @param pinData `true` to pin blocks backing iterator results for the iterator's lifetime
	/// @return `this` for chaining
	public ReadOptions setPinData(boolean pinData) {
		NativeFields.setBoolean(MH_SET_PIN_DATA, ptr(), pinData);
		return this;
	}

	/// Returns whether iterator results pin their backing blocks in memory.
	///
	/// @return `true` if iterator results stay valid for the iterator's lifetime
	public boolean isPinData() {
		return NativeFields.getBoolean(MH_GET_PIN_DATA, ptr());
	}

	/// If `true`, creates a tailing iterator that reflects writes committed after it was created,
	/// without needing to be recreated. Not compatible with snapshots. Default: `false`.
	///
	/// @param tailing `true` to create a tailing iterator
	/// @return `this` for chaining
	public ReadOptions setTailing(boolean tailing) {
		NativeFields.setBoolean(MH_SET_TAILING, ptr(), tailing);
		return this;
	}

	/// Returns whether this creates a tailing iterator.
	///
	/// @return `true` if a tailing iterator is created
	public boolean isTailing() {
		return NativeFields.getBoolean(MH_GET_TAILING, ptr());
	}

	/// If `true`, forces a total-order seek even when the column family uses a prefix extractor,
	/// bypassing the prefix bloom filter. Needed to seek to a key that does not share the
	/// iteration prefix. Default: `false`.
	///
	/// @param totalOrderSeek `true` to bypass prefix-based seek optimizations
	/// @return `this` for chaining
	public ReadOptions setTotalOrderSeek(boolean totalOrderSeek) {
		NativeFields.setBoolean(MH_SET_TOTAL_ORDER_SEEK, ptr(), totalOrderSeek);
		return this;
	}

	/// Returns whether total-order seek is forced, bypassing prefix-based optimizations.
	///
	/// @return `true` if total-order seek is forced
	public boolean isTotalOrderSeek() {
		return NativeFields.getBoolean(MH_GET_TOTAL_ORDER_SEEK, ptr());
	}

	/// If `true`, an iterator behaves like [#setTotalOrderSeek] by default, but RocksDB switches
	/// it to prefix-seek mode on its own whenever it can prove that won't change the result --
	/// based on the seek key and [#setIterateUpperBound(byte[])]. Lets a caller get prefix-bloom
	/// pruning on `Seek()` without manually reasoning about when it's safe to set
	/// [#setPrefixSameAsStart] instead. Requires [Options#setPrefixExtractor] to be configured;
	/// a no-op otherwise. Default: `false`.
	///
	/// Known upstream caveat: keys shorter than the prefix extractor's full length can be
	/// omitted from results in this mode when they would appear under a plain total-order seek,
	/// if such short keys exist in the database. Not an issue if every key is at least as long
	/// as the configured prefix.
	///
	/// @param autoPrefixMode `true` to let RocksDB auto-select prefix-seek mode when it's safe
	/// @return `this` for chaining
	public ReadOptions setAutoPrefixMode(boolean autoPrefixMode) {
		NativeFields.setBoolean(MH_SET_AUTO_PREFIX_MODE, ptr(), autoPrefixMode);
		return this;
	}

	/// Returns whether RocksDB may automatically switch to prefix-seek mode when safe.
	///
	/// @return `true` if automatic prefix-seek mode is enabled
	public boolean isAutoPrefixMode() {
		return NativeFields.getBoolean(MH_GET_AUTO_PREFIX_MODE, ptr());
	}

	/// If `true`, an iterator only returns entries that share the same prefix (per the column
	/// family's prefix extractor) as the seek key, stopping instead of continuing past the prefix
	/// boundary. Default: `false`.
	///
	/// @param prefixSameAsStart `true` to stop iteration at the seek key's prefix boundary
	/// @return `this` for chaining
	public ReadOptions setPrefixSameAsStart(boolean prefixSameAsStart) {
		NativeFields.setBoolean(MH_SET_PREFIX_SAME_AS_START, ptr(), prefixSameAsStart);
		return this;
	}

	/// Returns whether iteration is confined to the seek key's prefix.
	///
	/// @return `true` if iteration stops at the seek key's prefix boundary
	public boolean isPrefixSameAsStart() {
		return NativeFields.getBoolean(MH_GET_PREFIX_SAME_AS_START, ptr());
	}

	/// Read-ahead bytes requested per disk read during a scan. A non-zero value overrides the
	/// per-file setting and applies even when [BlockBasedTableOptions] read-ahead tuning would
	/// otherwise not kick in. [MemorySize#ZERO] leaves read-ahead unset. Default: [MemorySize#ZERO].
	///
	/// @param readaheadSize read-ahead size, or [MemorySize#ZERO] to leave it unset
	/// @return `this` for chaining
	public ReadOptions setReadaheadSize(MemorySize readaheadSize) {
		NativeFields.setMemorySize(MH_SET_READAHEAD_SIZE, ptr(), readaheadSize);
		return this;
	}

	/// Returns the configured read-ahead size.
	///
	/// @return read-ahead size; [MemorySize#ZERO] means unset
	public MemorySize getReadaheadSize() {
		return NativeFields.getMemorySize(MH_GET_READAHEAD_SIZE, ptr());
	}

	// -----------------------------------------------------------------------
	// Iterate bounds
	// -----------------------------------------------------------------------
	//
	// The C API stores a Slice view over whatever pointer is passed in, not a copy, so the bytes
	// backing a bound must stay valid for as long as this ReadOptions is used for reads or
	// iteration -- not just for the duration of the setter call. The byte[] tier copies into
	// this instance's own boundsArena to satisfy that automatically. The ByteBuffer/MemorySegment
	// tiers are zero-copy instead: the caller owns that lifetime guarantee.

	/// Restricts iteration to keys `>= lowerBound`. Slow path: copies `lowerBound` into a
	/// segment owned by this [ReadOptions] instance, valid until it is closed. Calling this
	/// repeatedly on the same instance (e.g. advancing a scan window across a pagination loop)
	/// does not reclaim earlier copies -- each one stays allocated until the whole [ReadOptions]
	/// is closed, so memory grows with the number of calls. Pass `null` to clear a previously
	/// set lower bound.
	///
	/// @param lowerBound inclusive lower bound, or `null` to clear
	/// @return `this` for chaining
	public ReadOptions setIterateLowerBound(byte[] lowerBound) {
		try {
			if (lowerBound == null) {
				MH_SET_ITERATE_LOWER_BOUND.invokeExact(ptr(), MemorySegment.NULL, 0L);
			} else {
				MemorySegment seg = RocksDB.toNative(boundsArena, lowerBound);
				MH_SET_ITERATE_LOWER_BOUND.invokeExact(ptr(), seg, (long) lowerBound.length);
			}
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIterateLowerBound failed", t);
		}
	}

	/// Zero-copy for a direct [ByteBuffer]: `lowerBound` must remain valid for as long as this
	/// [ReadOptions] is used for reads or iteration, not just for this call -- the C API retains
	/// a view over it rather than copying it.
	///
	/// @param lowerBound direct [ByteBuffer] containing the inclusive lower bound
	/// @return `this` for chaining
	public ReadOptions setIterateLowerBound(ByteBuffer lowerBound) {
		try {
			MH_SET_ITERATE_LOWER_BOUND.invokeExact(ptr(),
					MemorySegment.ofBuffer(lowerBound), (long) lowerBound.remaining());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIterateLowerBound failed", t);
		}
	}

	/// Zero-copy for a native segment: `lowerBound` must remain valid for as long as this
	/// [ReadOptions] is used for reads or iteration, not just for this call -- the C API retains
	/// a view over it rather than copying it.
	///
	/// @param lowerBound native segment containing the inclusive lower bound
	/// @return `this` for chaining
	public ReadOptions setIterateLowerBound(MemorySegment lowerBound) {
		try {
			MH_SET_ITERATE_LOWER_BOUND.invokeExact(ptr(), lowerBound, lowerBound.byteSize());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIterateLowerBound failed", t);
		}
	}

	/// Restricts iteration to keys `< upperBound`. Slow path: copies `upperBound` into a
	/// segment owned by this [ReadOptions] instance, valid until it is closed. Calling this
	/// repeatedly on the same instance (e.g. advancing a scan window across a pagination loop)
	/// does not reclaim earlier copies -- each one stays allocated until the whole [ReadOptions]
	/// is closed, so memory grows with the number of calls. Pass `null` to clear a previously
	/// set upper bound.
	///
	/// @param upperBound exclusive upper bound, or `null` to clear
	/// @return `this` for chaining
	public ReadOptions setIterateUpperBound(byte[] upperBound) {
		try {
			if (upperBound == null) {
				MH_SET_ITERATE_UPPER_BOUND.invokeExact(ptr(), MemorySegment.NULL, 0L);
			} else {
				MemorySegment seg = RocksDB.toNative(boundsArena, upperBound);
				MH_SET_ITERATE_UPPER_BOUND.invokeExact(ptr(), seg, (long) upperBound.length);
			}
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIterateUpperBound failed", t);
		}
	}

	/// Zero-copy for a direct [ByteBuffer]: `upperBound` must remain valid for as long as this
	/// [ReadOptions] is used for reads or iteration, not just for this call -- the C API retains
	/// a view over it rather than copying it.
	///
	/// @param upperBound direct [ByteBuffer] containing the exclusive upper bound
	/// @return `this` for chaining
	public ReadOptions setIterateUpperBound(ByteBuffer upperBound) {
		try {
			MH_SET_ITERATE_UPPER_BOUND.invokeExact(ptr(),
					MemorySegment.ofBuffer(upperBound), (long) upperBound.remaining());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIterateUpperBound failed", t);
		}
	}

	/// Zero-copy for a native segment: `upperBound` must remain valid for as long as this
	/// [ReadOptions] is used for reads or iteration, not just for this call -- the C API retains
	/// a view over it rather than copying it.
	///
	/// @param upperBound native segment containing the exclusive upper bound
	/// @return `this` for chaining
	public ReadOptions setIterateUpperBound(MemorySegment upperBound) {
		try {
			MH_SET_ITERATE_UPPER_BOUND.invokeExact(ptr(), upperBound, upperBound.byteSize());
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setIterateUpperBound failed", t);
		}
	}

	// -----------------------------------------------------------------------
	// Request ID
	// -----------------------------------------------------------------------

	/// Sets an opaque identifier attached to this read for tracing/telemetry. Unlike the iterate
	/// bounds above, the C API copies `requestId` into its own buffer immediately, so no
	/// reference to the passed-in bytes is retained. Pass `null` to clear.
	///
	/// @param requestId the identifier to attach, or `null` to clear
	/// @return `this` for chaining
	public ReadOptions setRequestId(String requestId) {
		try (Arena arena = Arena.ofConfined()) {
			if (requestId == null) {
				MH_CLEAR_REQUEST_ID.invokeExact(ptr());
			} else {
				byte[] bytes = requestId.getBytes(StandardCharsets.UTF_8);
				MemorySegment seg = RocksDB.toNative(arena, bytes);
				MH_SET_REQUEST_ID.invokeExact(ptr(), seg, (long) bytes.length);
			}
			return this;
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setRequestId failed", t);
		}
	}

	/// Returns the configured request identifier.
	///
	/// @return the request identifier, or [Optional#empty()] if unset
	public Optional<String> getRequestId() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lenSeg = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment result = (MemorySegment) MH_GET_REQUEST_ID.invokeExact(ptr(), lenSeg);
			if (MemorySegment.NULL.equals(result)) {
				return Optional.empty();
			}
			long len = lenSeg.get(ValueLayout.JAVA_LONG, 0);
			byte[] bytes = RocksDB.toByteArray(result, len);
			return Optional.of(new String(bytes, StandardCharsets.UTF_8));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("getRequestId failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
		boundsArena.close();
	}
}
