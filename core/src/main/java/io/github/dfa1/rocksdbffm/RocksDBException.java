package io.github.dfa1.rocksdbffm;

/// Unchecked exception thrown when RocksDB itself reports a native operation failure via its
/// `errptr` out-parameter — corruption, an IO error, an invalid argument at the DB level.
///
/// No public constructor: only [RocksDB#checkError(java.lang.foreign.MemorySegment)] constructs
/// one, from the C string `errptr` reports. This type is never used to wrap an `invokeExact`
/// failure — see [RocksDB#wrapInvokeFailure(String, Throwable)] and
/// [ADR 0004](https://github.com/dfa1/rocksdbffm/blob/main/docs/adr/0004-error-handling.md) for
/// why: an `invokeExact` failure is a bug in this library's own FFM plumbing, never a genuine
/// RocksDB error, so catching `RocksDBException` means exactly one thing.
public class RocksDBException extends RuntimeException {

	RocksDBException(String message) {
		super(message);
	}
}
