package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SstFileManagerTest {

	@Test
	void getTotalSize_reflectsActualSstUsage(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env);
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());
			try (var flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
				db.flush(flushOptions);
			}

			// When
			var result = sut.getTotalSize();

			// Then
			assertThat(result.toBytes()).isGreaterThan(0);
		}
	}

	@Test
	void setMaxAllowedSpaceUsage_notReachedUnderGenerousLimit(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env)
				     .setMaxAllowedSpaceUsage(MemorySize.ofGB(10))
				     .setCompactionBufferSize(MemorySize.ofMB(512));
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then
			assertThat(sut.isMaxAllowedSpaceReached()).isFalse();
			assertThat(sut.isMaxAllowedSpaceReachedIncludingCompactions()).isFalse();
		}
	}

	@Test
	void setMaxAllowedSpaceUsage_zeroMeansNoLimit(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env).setMaxAllowedSpaceUsage(MemorySize.ZERO);
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			db.put("k".getBytes(), "v".getBytes());
			try (var flushOptions = FlushOptions.newFlushOptions().setWait(true)) {
				db.flush(flushOptions);
			}

			// Then — a zero limit means "unlimited", never reached
			assertThat(sut.isMaxAllowedSpaceReached()).isFalse();
		}
	}

	@Test
	void setDeleteRateBytesPerSecond_roundTrips(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env)
				     .setDeleteRateBytesPerSecond(MemorySize.ofMB(64).toBytes());
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			var result = sut.getDeleteRateBytesPerSecond();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(64).toBytes());
		}
	}

	@Test
	void setMaxTrashDbRatio_roundTrips(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env).setMaxTrashDbRatio(0.5);
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			var result = sut.getMaxTrashDbRatio();

			// Then
			assertThat(result).isEqualTo(0.5);
		}
	}

	@Test
	void getTotalTrashSize_isNonNegative(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env);
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = sut.getTotalTrashSize();

			// Then
			assertThat(result.toBytes()).isGreaterThanOrEqualTo(0);
		}
	}

	@Test
	void sharedOwnership_survivesIndependentCloseBeforeOptions(@TempDir Path dir) {
		// Given — sfm uses shared ownership, safe to close before Options
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env);
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut)) {

			// When
			sut.close();

			// Then — Options (and the underlying shared_ptr) is still valid
			try (var db = RocksDB.openReadWrite(opts, dir)) {
				db.put("k".getBytes(), "v".getBytes());
				assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			}
		}
	}

	@Test
	void close_isIdempotent() {
		// Given
		var env = Env.defaultEnv();
		var sut = SstFileManager.create(env);

		// When
		sut.close();

		// Then — closing twice must not crash the JVM
		sut.close();

		env.close();
	}
}
