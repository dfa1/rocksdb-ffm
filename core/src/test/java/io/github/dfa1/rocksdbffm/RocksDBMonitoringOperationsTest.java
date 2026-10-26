package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RocksDBMonitoringOperationsTest {

    @Test
    void verifyChecksum_onValidDatabase_succeeds(@TempDir Path dir) {
        // Given
        try (var db = RocksDB.openReadWrite(dir)) {
            db.put("key".getBytes(), "value".getBytes());

            // When
            db.verifyChecksum();

            // Then
            assertThat(db.get("key".getBytes()))
                    .isEqualTo("value".getBytes());
        }
    }

    @Test
    void verifyChecksum_withReadOptions_onValidDatabase_succeeds(
            @TempDir Path dir) {
        // Given
        try (var db = RocksDB.openReadWrite(dir);
             var readOptions = ReadOptions.newReadOptions()) {
            db.put("key".getBytes(), "value".getBytes());

            // When
            db.verifyChecksum(readOptions);

            // Then
            assertThat(db.get("key".getBytes()))
                    .isEqualTo("value".getBytes());
        }
    }

    @Test
    void verifyFileChecksums_onValidDatabase_succeeds(@TempDir Path dir) {
        // Given
        try (var factory = FileChecksumGenFactory.newCrc32cFactory();
             var options = Options.newOptions()
                     .setCreateIfMissing(true)
                     .setFileChecksumGenFactory(factory);
             var db = RocksDB.openReadWrite(options, dir);
             var flushOptions = FlushOptions.newFlushOptions()) {
            db.put("key".getBytes(), "value".getBytes());
            db.flush(flushOptions);

            // When
            db.verifyFileChecksums();

            // Then
            assertThat(db.get("key".getBytes()))
                    .isEqualTo("value".getBytes());
        }
    }

    @Test
    void verifyFileChecksums_withReadOptions_onValidDatabase_succeeds(
            @TempDir Path dir) {
        // Given
        try (var factory = FileChecksumGenFactory.newCrc32cFactory();
             var options = Options.newOptions()
                     .setCreateIfMissing(true)
                     .setFileChecksumGenFactory(factory);
             var db = RocksDB.openReadWrite(options, dir);
             var readOptions = ReadOptions.newReadOptions();
             var flushOptions = FlushOptions.newFlushOptions()) {
            db.put("key".getBytes(), "value".getBytes());
            db.flush(flushOptions);

            // When
            db.verifyFileChecksums(readOptions);

            // Then
            assertThat(db.get("key".getBytes()))
                    .isEqualTo("value".getBytes());
        }
    }
}
