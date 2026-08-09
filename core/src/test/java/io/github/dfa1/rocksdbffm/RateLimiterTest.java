package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

	@Test
	void create_withDefaultRefillAndFairness(@TempDir Path dir) {
		// Given
		try (var sut = RateLimiter.create(MemorySize.ofMB(100));
		     var opts = Options.newOptions().setCreateIfMissing(true).setRateLimiter(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then — DB opens and accepts writes with the limiter attached
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void create_withExplicitRefillAndFairness(@TempDir Path dir) {
		// Given
		try (var sut = RateLimiter.create(MemorySize.ofMB(100), 50_000L, 5);
		     var opts = Options.newOptions().setCreateIfMissing(true).setRateLimiter(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When / Then — DB opens successfully with the limiter attached
			assertThat(db).isNotNull();
		}
	}

	@Test
	void createAutoTuned_withDefaultRefillAndFairness(@TempDir Path dir) {
		// Given
		try (var sut = RateLimiter.createAutoTuned(MemorySize.ofMB(100));
		     var opts = Options.newOptions().setCreateIfMissing(true).setRateLimiter(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When / Then
			assertThat(db).isNotNull();
		}
	}

	@Test
	void createAutoTuned_withExplicitRefillAndFairness(@TempDir Path dir) {
		// Given
		try (var sut = RateLimiter.createAutoTuned(MemorySize.ofMB(100), 100_000L, 10);
		     var opts = Options.newOptions().setCreateIfMissing(true).setRateLimiter(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When / Then
			assertThat(db).isNotNull();
		}
	}

	@Test
	void createWithMode_readsOnly(@TempDir Path dir) {
		// Given
		try (var sut = RateLimiter.createWithMode(
				MemorySize.ofMB(100), 100_000L, 10, RateLimiter.Mode.READS_ONLY, false);
		     var opts = Options.newOptions().setCreateIfMissing(true).setRateLimiter(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When / Then
			assertThat(db).isNotNull();
		}
	}

	@Test
	void createWithMode_allIoAutoTuned(@TempDir Path dir) {
		// Given
		try (var sut = RateLimiter.createWithMode(
				MemorySize.ofMB(100), 100_000L, 10, RateLimiter.Mode.ALL_IO, true);
		     var opts = Options.newOptions().setCreateIfMissing(true).setRateLimiter(sut);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			// When / Then
			assertThat(db).isNotNull();
		}
	}

	@Test
	void mode_valuesMatchExpectedOrdinals() {
		// Given / When / Then
		assertThat(RateLimiter.Mode.READS_ONLY.value).isZero();
		assertThat(RateLimiter.Mode.WRITES_ONLY.value).isEqualTo(1);
		assertThat(RateLimiter.Mode.ALL_IO.value).isEqualTo(2);
	}

	@Test
	void sharedOwnership_survivesIndependentClose() {
		// Given — RateLimiter is not owned by Options; both may be closed independently
		var sut = RateLimiter.create(MemorySize.ofMB(10));
		var opts = Options.newOptions().setCreateIfMissing(true).setRateLimiter(sut);

		// When
		opts.close();

		// Then — the limiter itself is still usable/closable after Options is gone
		sut.close();
	}

	@Test
	void close_isIdempotent() {
		// Given
		var sut = RateLimiter.create(MemorySize.ofMB(10));

		// When
		sut.close();

		// Then — closing twice must not crash the JVM
		sut.close();
	}
}
