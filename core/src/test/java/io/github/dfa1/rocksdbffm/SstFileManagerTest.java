package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
				     .setDeleteRateBytesPerSecond(MemorySize.ofMB(64));
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			var result = sut.getDeleteRateBytesPerSecond();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(64));
		}
	}

	@Test
	void setDeleteRateBytesPerSecond_null_roundTripsAsUnlimited(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env).setDeleteRateBytesPerSecond(null);
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			var result = sut.getDeleteRateBytesPerSecond();

			// Then
			assertThat(result).isNull();
		}
	}

	@Test
	void setDeleteRateBytesPerSecond_zero_roundTripsAsSynchronous(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env).setDeleteRateBytesPerSecond(MemorySize.ZERO);
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			var result = sut.getDeleteRateBytesPerSecond();

			// Then
			assertThat(result).isEqualTo(MemorySize.ZERO);
		}
	}

	@Test
	void setMaxTrashDbRatio_roundTrips(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = SstFileManager.create(env).setMaxTrashDbRatio(Ratio.of(0.5));
		     var opts = Options.newOptions().setCreateIfMissing(true).setSstFileManager(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			var result = sut.getMaxTrashDbRatio();

			// Then
			assertThat(result).isEqualTo(Ratio.of(0.5));
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
		// Given — an already-closed instance
		var env = Env.defaultEnv();
		var sut = SstFileManager.create(env);
		sut.close();

		// When
		ThrowingCallable secondClose = sut::close;

		// Then — closing twice must not crash the JVM
		assertThatCode(secondClose).doesNotThrowAnyException();

		env.close();
	}
}
