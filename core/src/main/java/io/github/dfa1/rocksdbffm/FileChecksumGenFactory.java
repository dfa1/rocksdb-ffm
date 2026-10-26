
package io.github.dfa1.rocksdbffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// FFM wrapper for `rocksdb_file_checksum_gen_factory_t`.
///
/// This factory generates CRC32C checksums for SST files. It uses shared ownership:
/// passing it to [Options#setFileChecksumGenFactory] does not transfer ownership,
/// so both objects may be closed independently.
public final class FileChecksumGenFactory extends NativeObject {

    /// `rocksdb_file_checksum_gen_factory_t* rocksdb_file_checksum_gen_crc32c_factory_create(void);`
    private static final MethodHandle MH_CREATE = NativeLibrary.lookup(
            "rocksdb_file_checksum_gen_crc32c_factory_create",
            FunctionDescriptor.of(ValueLayout.ADDRESS));

    /// `void rocksdb_file_checksum_gen_factory_destroy(rocksdb_file_checksum_gen_factory_t* factory);`
    private static final MethodHandle MH_DESTROY = NativeLibrary.lookup(
            "rocksdb_file_checksum_gen_factory_destroy",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private FileChecksumGenFactory(MemorySegment ptr) {
        super(ptr);
    }

    /// Creates a factory that generates CRC32C file checksums.
    ///
    /// @return a new factory; the caller must close it when finished
    public static FileChecksumGenFactory newCrc32cFactory() {
        try {
            return new FileChecksumGenFactory((MemorySegment) MH_CREATE.invokeExact());
        } catch (Throwable t) {
            throw NativeCalls.wrapInvokeFailure("FileChecksumGenFactory.newCrc32cFactory failed", t);
        }
    }

    @Override
    protected void tryClose(MemorySegment ptr) throws Throwable {
        MH_DESTROY.invokeExact(ptr);
    }
}
