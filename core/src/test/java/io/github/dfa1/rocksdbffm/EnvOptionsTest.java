package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnvOptionsTest {

	@Test
	void envOptions_allowsSstFileWriterReadWrite(@TempDir Path dir) {
		// Given
		Path sstPath = dir.resolve("data.sst");
		try (var envOptions = EnvOptions.newEnvOptions()
				     .setUseDirectWrites(false)
				     .setAllowFallocate(true)
				     .setBytesPerSync(MemorySize.ofMB(1))
				     .setCompactionReadaheadSize(MemorySize.ofKB(64))
				     .setWritableFileMaxBufferSize(MemorySize.ofMB(1));
		     var opts = Options.newOptions();
		     var writer = SstFileWriter.newSstFileWriter(opts, envOptions)) {
			writer.open(sstPath);
			writer.put("aaa".getBytes(), "val1".getBytes());
			writer.finish();
		}

		// When
		try (var db = RocksDB.openReadWrite(dir.resolve("db"))) {
			db.ingestExternalFile(List.of(sstPath));
			var result = db.get("aaa".getBytes());

			// Then
			assertThat(result).isEqualTo("val1".getBytes());
		}
	}

	@Test
	void setUseMmapReads_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setUseMmapReads(true)) {

			// When
			var result = envOptions.getUseMmapReads();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setUseMmapWrites_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setUseMmapWrites(true)) {

			// When
			var result = envOptions.getUseMmapWrites();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setUseDirectReads_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setUseDirectReads(true)) {

			// When
			var result = envOptions.getUseDirectReads();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setUseDirectWrites_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setUseDirectWrites(true)) {

			// When
			var result = envOptions.getUseDirectWrites();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setAllowFallocate_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setAllowFallocate(false)) {

			// When
			var result = envOptions.getAllowFallocate();

			// Then
			assertThat(result).isFalse();
		}
	}

	@Test
	void setFdCloexec_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setFdCloexec(false)) {

			// When
			var result = envOptions.getFdCloexec();

			// Then
			assertThat(result).isFalse();
		}
	}

	@Test
	void setBytesPerSync_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setBytesPerSync(MemorySize.ofMB(1))) {

			// When
			var result = envOptions.getBytesPerSync();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(1));
		}
	}

	@Test
	void setStrictBytesPerSync_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setStrictBytesPerSync(true)) {

			// When
			var result = envOptions.getStrictBytesPerSync();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setFallocateWithKeepSize_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setFallocateWithKeepSize(false)) {

			// When
			var result = envOptions.getFallocateWithKeepSize();

			// Then
			assertThat(result).isFalse();
		}
	}

	@Test
	void setCompactionReadaheadSize_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setCompactionReadaheadSize(MemorySize.ofKB(64))) {

			// When
			var result = envOptions.getCompactionReadaheadSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofKB(64));
		}
	}

	@Test
	void setWritableFileMaxBufferSize_roundTrips() {
		// Given
		try (var envOptions = EnvOptions.newEnvOptions().setWritableFileMaxBufferSize(MemorySize.ofMB(2))) {

			// When
			var result = envOptions.getWritableFileMaxBufferSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(2));
		}
	}

	@Test
	void setRateLimiter_doesNotThrowAndCallerRetainsOwnership(@TempDir Path dir) {
		// Given
		try (var rateLimiter = RateLimiter.create(MemorySize.ofMB(10));
		     var envOptions = EnvOptions.newEnvOptions().setRateLimiter(rateLimiter);
		     var opts = Options.newOptions();
		     var writer = SstFileWriter.newSstFileWriter(opts, envOptions)) {
			writer.open(dir.resolve("rl.sst"));
			writer.put("k".getBytes(), "v".getBytes());
			writer.finish();

			// When / Then -- rateLimiter is still open and usable after being shared here
			assertThat(rateLimiter.ptr()).isNotNull();
		}
	}
}
