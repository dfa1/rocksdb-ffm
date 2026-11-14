package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TtlDBIntegrationTest {

	@Test
	void putGet_withinTtl(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(dir, opts, Duration.ofSeconds(60))) {

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then — key is readable within TTL
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void getTtl_returnsConfiguredDuration(@TempDir Path dir) {
		// Given
		var ttl = Duration.ofSeconds(30);

		// When
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(dir, opts, ttl)) {

			// Then
			assertThat(db.getTtl()).contains(ttl);
		}
	}

	@Test
	void zeroTtl_disablesExpiry(@TempDir Path dir) {
		// Given — Duration.ZERO disables expiry entirely
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openTtl(dir, opts, Duration.ZERO)) {

			// When
			db.put("k".getBytes(), "v".getBytes());

			// Then — key survives without any TTL pressure
			assertThat(db.get("k".getBytes())).isEqualTo("v".getBytes());
			assertThat(db.getTtl()).contains(Duration.ZERO);
		}
	}
}
