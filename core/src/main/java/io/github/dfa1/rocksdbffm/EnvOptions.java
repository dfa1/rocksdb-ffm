package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_envoptions_t`.
///
/// Tunes low-level file-I/O behavior (mmap vs. direct I/O, fsync cadence, readahead, ...) for
/// a single [SstFileWriter] -- distinct from [Env], which selects the pluggable
/// filesystem/threading environment itself. Attach via
/// [SstFileWriter#newSstFileWriter(Options, EnvOptions)]. `EnvOptions` may be closed once
/// passed to that call; RocksDB copies the underlying struct by value.
///
/// ```
/// try (var envOpts = EnvOptions.newEnvOptions().setUseDirectWrites(true);
///      var opts = Options.newOptions();
///      var writer = SstFileWriter.newSstFileWriter(opts, envOpts)) {
///     ...
/// }
/// ```
public final class EnvOptions extends AbstractOptions {

	/// `rocksdb_envoptions_t* rocksdb_envoptions_create(void);`
	private static final MethodHandle MH_CREATE;
	/// `void rocksdb_envoptions_destroy(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_DESTROY;
	/// `void rocksdb_envoptions_set_use_mmap_reads(rocksdb_envoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_USE_MMAP_READS;
	/// `unsigned char rocksdb_envoptions_get_use_mmap_reads(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_USE_MMAP_READS;
	/// `void rocksdb_envoptions_set_use_mmap_writes(rocksdb_envoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_USE_MMAP_WRITES;
	/// `unsigned char rocksdb_envoptions_get_use_mmap_writes(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_USE_MMAP_WRITES;
	/// `void rocksdb_envoptions_set_use_direct_reads(rocksdb_envoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_USE_DIRECT_READS;
	/// `unsigned char rocksdb_envoptions_get_use_direct_reads(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_USE_DIRECT_READS;
	/// `void rocksdb_envoptions_set_use_direct_writes(rocksdb_envoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_USE_DIRECT_WRITES;
	/// `unsigned char rocksdb_envoptions_get_use_direct_writes(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_USE_DIRECT_WRITES;
	/// `void rocksdb_envoptions_set_allow_fallocate(rocksdb_envoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_ALLOW_FALLOCATE;
	/// `unsigned char rocksdb_envoptions_get_allow_fallocate(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_ALLOW_FALLOCATE;
	/// `void rocksdb_envoptions_set_fd_cloexec(rocksdb_envoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_FD_CLOEXEC;
	/// `unsigned char rocksdb_envoptions_get_fd_cloexec(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_FD_CLOEXEC;
	/// `void rocksdb_envoptions_set_bytes_per_sync(rocksdb_envoptions_t* opt, uint64_t v);`
	private static final MethodHandle MH_SET_BYTES_PER_SYNC;
	/// `uint64_t rocksdb_envoptions_get_bytes_per_sync(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_BYTES_PER_SYNC;
	/// `void rocksdb_envoptions_set_strict_bytes_per_sync(rocksdb_envoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_STRICT_BYTES_PER_SYNC;
	/// `unsigned char rocksdb_envoptions_get_strict_bytes_per_sync(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_STRICT_BYTES_PER_SYNC;
	/// `void rocksdb_envoptions_set_fallocate_with_keep_size(rocksdb_envoptions_t* opt, unsigned char v);`
	private static final MethodHandle MH_SET_FALLOCATE_WITH_KEEP_SIZE;
	/// `unsigned char rocksdb_envoptions_get_fallocate_with_keep_size(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_FALLOCATE_WITH_KEEP_SIZE;
	/// `void rocksdb_envoptions_set_compaction_readahead_size(rocksdb_envoptions_t* opt, size_t v);`
	private static final MethodHandle MH_SET_COMPACTION_READAHEAD_SIZE;
	/// `size_t rocksdb_envoptions_get_compaction_readahead_size(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_COMPACTION_READAHEAD_SIZE;
	/// `void rocksdb_envoptions_set_writable_file_max_buffer_size(rocksdb_envoptions_t* opt, size_t v);`
	private static final MethodHandle MH_SET_WRITABLE_FILE_MAX_BUFFER_SIZE;
	/// `size_t rocksdb_envoptions_get_writable_file_max_buffer_size(rocksdb_envoptions_t* opt);`
	private static final MethodHandle MH_GET_WRITABLE_FILE_MAX_BUFFER_SIZE;
	/// `void rocksdb_envoptions_set_rate_limiter(rocksdb_envoptions_t* opt, rocksdb_ratelimiter_t* rate_limiter);`
	private static final MethodHandle MH_SET_RATE_LIMITER;

	static {
		MH_CREATE = NativeLibrary.lookup("rocksdb_envoptions_create",
				FunctionDescriptor.of(ValueLayout.ADDRESS));

		MH_DESTROY = NativeLibrary.lookup("rocksdb_envoptions_destroy",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

		MH_SET_USE_MMAP_READS = NativeLibrary.lookup("rocksdb_envoptions_set_use_mmap_reads",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_MMAP_READS = NativeLibrary.lookup("rocksdb_envoptions_get_use_mmap_reads",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_USE_MMAP_WRITES = NativeLibrary.lookup("rocksdb_envoptions_set_use_mmap_writes",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_MMAP_WRITES = NativeLibrary.lookup("rocksdb_envoptions_get_use_mmap_writes",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_USE_DIRECT_READS = NativeLibrary.lookup("rocksdb_envoptions_set_use_direct_reads",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_DIRECT_READS = NativeLibrary.lookup("rocksdb_envoptions_get_use_direct_reads",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_USE_DIRECT_WRITES = NativeLibrary.lookup("rocksdb_envoptions_set_use_direct_writes",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_USE_DIRECT_WRITES = NativeLibrary.lookup("rocksdb_envoptions_get_use_direct_writes",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_ALLOW_FALLOCATE = NativeLibrary.lookup("rocksdb_envoptions_set_allow_fallocate",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_ALLOW_FALLOCATE = NativeLibrary.lookup("rocksdb_envoptions_get_allow_fallocate",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_FD_CLOEXEC = NativeLibrary.lookup("rocksdb_envoptions_set_fd_cloexec",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_FD_CLOEXEC = NativeLibrary.lookup("rocksdb_envoptions_get_fd_cloexec",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_BYTES_PER_SYNC = NativeLibrary.lookup("rocksdb_envoptions_set_bytes_per_sync",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_BYTES_PER_SYNC = NativeLibrary.lookup("rocksdb_envoptions_get_bytes_per_sync",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_STRICT_BYTES_PER_SYNC = NativeLibrary.lookup("rocksdb_envoptions_set_strict_bytes_per_sync",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_STRICT_BYTES_PER_SYNC = NativeLibrary.lookup("rocksdb_envoptions_get_strict_bytes_per_sync",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_FALLOCATE_WITH_KEEP_SIZE = NativeLibrary.lookup("rocksdb_envoptions_set_fallocate_with_keep_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

		MH_GET_FALLOCATE_WITH_KEEP_SIZE = NativeLibrary.lookup("rocksdb_envoptions_get_fallocate_with_keep_size",
				FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS));

		MH_SET_COMPACTION_READAHEAD_SIZE = NativeLibrary.lookup(
				"rocksdb_envoptions_set_compaction_readahead_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_COMPACTION_READAHEAD_SIZE = NativeLibrary.lookup(
				"rocksdb_envoptions_get_compaction_readahead_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_WRITABLE_FILE_MAX_BUFFER_SIZE = NativeLibrary.lookup(
				"rocksdb_envoptions_set_writable_file_max_buffer_size",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		MH_GET_WRITABLE_FILE_MAX_BUFFER_SIZE = NativeLibrary.lookup(
				"rocksdb_envoptions_get_writable_file_max_buffer_size",
				FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

		MH_SET_RATE_LIMITER = NativeLibrary.lookup("rocksdb_envoptions_set_rate_limiter",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	}

	private EnvOptions(MemorySegment ptr) {
		super(ptr);
	}

	/// Creates env options with RocksDB defaults.
	///
	/// @return a new instance; caller must close it
	public static EnvOptions newEnvOptions() {
		try {
			return new EnvOptions((MemorySegment) MH_CREATE.invokeExact());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("envoptions create failed", t);
		}
	}

	/// If true, reads go through `mmap` instead of `pread`. Default: platform-dependent
	/// (`true` on most POSIX platforms). Unlike a regular read, a page fault on an `mmap`-ed
	/// region past the end of a file that has since been truncated or deleted crashes the
	/// process instead of returning an I/O error -- prefer direct/buffered reads unless you
	/// specifically need `mmap`'s zero-copy behavior and control every reader's lifetime.
	///
	/// @param value `true` to use `mmap` for reads
	/// @return `this` for chaining
	public EnvOptions setUseMmapReads(boolean value) {
		setBoolean(MH_SET_USE_MMAP_READS, value);
		return this;
	}

	/// Returns whether reads go through `mmap`.
	///
	/// @return `true` if reads go through `mmap`
	public boolean getUseMmapReads() {
		return getBoolean(MH_GET_USE_MMAP_READS);
	}

	/// If true, writes go through `mmap` instead of regular `write` calls. Default: `false`.
	///
	/// @param value `true` to use `mmap` for writes
	/// @return `this` for chaining
	public EnvOptions setUseMmapWrites(boolean value) {
		setBoolean(MH_SET_USE_MMAP_WRITES, value);
		return this;
	}

	/// Returns whether writes go through `mmap`.
	///
	/// @return `true` if writes go through `mmap`
	public boolean getUseMmapWrites() {
		return getBoolean(MH_GET_USE_MMAP_WRITES);
	}

	/// If true, uses direct I/O (`O_DIRECT`) for reads, bypassing the OS page cache. Avoids
	/// double-caching the same data in both RocksDB's block cache and the OS page cache, at
	/// the cost of RocksDB itself now being responsible for readahead the OS would otherwise
	/// provide. Default: `false`.
	///
	/// @param value `true` to use direct I/O for reads
	/// @return `this` for chaining
	public EnvOptions setUseDirectReads(boolean value) {
		setBoolean(MH_SET_USE_DIRECT_READS, value);
		return this;
	}

	/// Returns whether direct I/O is used for reads.
	///
	/// @return `true` if direct I/O is used for reads
	public boolean getUseDirectReads() {
		return getBoolean(MH_GET_USE_DIRECT_READS);
	}

	/// If true, uses direct I/O (`O_DIRECT`) for writes, bypassing the OS page cache. Default:
	/// `false`.
	///
	/// @param value `true` to use direct I/O for writes
	/// @return `this` for chaining
	public EnvOptions setUseDirectWrites(boolean value) {
		setBoolean(MH_SET_USE_DIRECT_WRITES, value);
		return this;
	}

	/// Returns whether direct I/O is used for writes.
	///
	/// @return `true` if direct I/O is used for writes
	public boolean getUseDirectWrites() {
		return getBoolean(MH_GET_USE_DIRECT_WRITES);
	}

	/// If true, allows `fallocate` to preallocate disk space for a file before writing to it,
	/// reducing fragmentation on some filesystems. Default: `true`.
	///
	/// @param value `true` to allow `fallocate`
	/// @return `this` for chaining
	public EnvOptions setAllowFallocate(boolean value) {
		setBoolean(MH_SET_ALLOW_FALLOCATE, value);
		return this;
	}

	/// Returns whether `fallocate` is allowed.
	///
	/// @return `true` if `fallocate` is allowed
	public boolean getAllowFallocate() {
		return getBoolean(MH_GET_ALLOW_FALLOCATE);
	}

	/// If true, sets the close-on-exec flag on file descriptors RocksDB opens, so they don't
	/// leak into subprocesses spawned via `fork`/`exec`. Default: `true`.
	///
	/// @param value `true` to set close-on-exec on opened file descriptors
	/// @return `this` for chaining
	public EnvOptions setFdCloexec(boolean value) {
		setBoolean(MH_SET_FD_CLOEXEC, value);
		return this;
	}

	/// Returns whether close-on-exec is set on opened file descriptors.
	///
	/// @return `true` if close-on-exec is set on opened file descriptors
	public boolean getFdCloexec() {
		return getBoolean(MH_GET_FD_CLOEXEC);
	}

	/// Issues a periodic `sync` (via `sync_file_range` on Linux) after every this-many bytes
	/// written to a single file, smoothing out write latency by avoiding a large buildup of
	/// dirty pages that would otherwise all get flushed at once. Default: `0` (disabled).
	///
	/// @param size number of bytes between periodic syncs
	/// @return `this` for chaining
	public EnvOptions setBytesPerSync(MemorySize size) {
		setMemorySize(MH_SET_BYTES_PER_SYNC, size);
		return this;
	}

	/// Returns the configured bytes-per-sync interval.
	///
	/// @return current bytes-per-sync interval
	public MemorySize getBytesPerSync() {
		return getMemorySize(MH_GET_BYTES_PER_SYNC);
	}

	/// If true, the periodic sync triggered by [#setBytesPerSync] blocks until the sync
	/// actually completes instead of merely being requested. Default: `false`.
	///
	/// @param value `true` to block until each periodic sync completes
	/// @return `this` for chaining
	public EnvOptions setStrictBytesPerSync(boolean value) {
		setBoolean(MH_SET_STRICT_BYTES_PER_SYNC, value);
		return this;
	}

	/// Returns whether periodic syncs block until completion.
	///
	/// @return `true` if periodic syncs block until completion
	public boolean getStrictBytesPerSync() {
		return getBoolean(MH_GET_STRICT_BYTES_PER_SYNC);
	}

	/// If true, `fallocate` preallocates space without changing the file's reported size
	/// (`FALLOC_FL_KEEP_SIZE` on Linux). Default: `true`.
	///
	/// @param value `true` to keep the reported file size unchanged when preallocating
	/// @return `this` for chaining
	public EnvOptions setFallocateWithKeepSize(boolean value) {
		setBoolean(MH_SET_FALLOCATE_WITH_KEEP_SIZE, value);
		return this;
	}

	/// Returns whether `fallocate` keeps the reported file size unchanged.
	///
	/// @return `true` if `fallocate` keeps the reported file size unchanged
	public boolean getFallocateWithKeepSize() {
		return getBoolean(MH_GET_FALLOCATE_WITH_KEEP_SIZE);
	}

	/// Readahead size used specifically for compaction reads (as opposed to regular reads).
	/// Default: `0` (use the OS's own readahead heuristics).
	///
	/// @param size compaction readahead size
	/// @return `this` for chaining
	public EnvOptions setCompactionReadaheadSize(MemorySize size) {
		setMemorySize(MH_SET_COMPACTION_READAHEAD_SIZE, size);
		return this;
	}

	/// Returns the configured compaction readahead size.
	///
	/// @return current compaction readahead size
	public MemorySize getCompactionReadaheadSize() {
		return getMemorySize(MH_GET_COMPACTION_READAHEAD_SIZE);
	}

	/// Maximum buffer size used for writing to a single file before flushing to disk. Default:
	/// `1 MB`.
	///
	/// @param size maximum write buffer size
	/// @return `this` for chaining
	public EnvOptions setWritableFileMaxBufferSize(MemorySize size) {
		setMemorySize(MH_SET_WRITABLE_FILE_MAX_BUFFER_SIZE, size);
		return this;
	}

	/// Returns the configured maximum write buffer size.
	///
	/// @return current maximum write buffer size
	public MemorySize getWritableFileMaxBufferSize() {
		return getMemorySize(MH_GET_WRITABLE_FILE_MAX_BUFFER_SIZE);
	}

	/// Attaches a [RateLimiter] to throttle this file's I/O. `rateLimiter` remains owned by
	/// the caller and can be shared across multiple `EnvOptions`/`Options`.
	///
	/// @param rateLimiter rate limiter to use; caller retains ownership
	/// @return `this` for chaining
	public EnvOptions setRateLimiter(RateLimiter rateLimiter) {
		try {
			MH_SET_RATE_LIMITER.invokeExact(ptr(), rateLimiter.ptr());
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("setRateLimiter failed", t);
		}
		return this;
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_DESTROY.invokeExact(ptr);
	}
}
