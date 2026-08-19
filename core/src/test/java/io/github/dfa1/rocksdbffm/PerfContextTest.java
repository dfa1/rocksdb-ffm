package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PerfContextTest {

	@AfterEach
	void resetPerfLevel() {
		PerfContext.setPerfLevel(PerfLevel.DISABLE);
	}

	@Test
	void newPerfContext_resetsCountersToZero(@TempDir Path dir) {
		// Given — accumulate some activity, then start a fresh measurement window
		PerfContext.setPerfLevel(PerfLevel.ENABLE_COUNT);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());
			try (var warmup = PerfContext.newPerfContext()) {
				db.get("k".getBytes());
			}

			// When
			try (var sut = PerfContext.newPerfContext()) {

				// Then
				assertThat(sut.metric(PerfMetric.USER_KEY_COMPARISON_COUNT)).isZero();
			}
		}
	}

	@Test
	void currentPerfContext_doesNotResetCounters(@TempDir Path dir) {
		// Given
		PerfContext.setPerfLevel(PerfLevel.ENABLE_COUNT);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			try (var warmup = PerfContext.newPerfContext()) {
				db.put("k".getBytes(), "v".getBytes());
				db.get("k".getBytes());

				// When
				try (var sut = PerfContext.currentPerfContext()) {

					// Then — sees the accumulation from before it was created
					assertThat(sut.metric(PerfMetric.USER_KEY_COMPARISON_COUNT)).isGreaterThan(0);
				}
			}
		}
	}

	@Test
	void reset_zeroesAccumulatedCounters(@TempDir Path dir) {
		// Given
		PerfContext.setPerfLevel(PerfLevel.ENABLE_COUNT);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var sut = PerfContext.newPerfContext()) {

			db.put("k".getBytes(), "v".getBytes());
			db.get("k".getBytes());
			assertThat(sut.metric(PerfMetric.USER_KEY_COMPARISON_COUNT)).isGreaterThan(0);

			// When
			sut.reset();

			// Then
			assertThat(sut.metric(PerfMetric.USER_KEY_COMPARISON_COUNT)).isZero();
		}
	}

	@Test
	void report_excludingZeroCounters_omitsUnusedMetrics(@TempDir Path dir) {
		// Given
		PerfContext.setPerfLevel(PerfLevel.ENABLE_COUNT);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var sut = PerfContext.newPerfContext()) {

			db.put("k".getBytes(), "v".getBytes());
			db.get("k".getBytes());

			// When
			var result = sut.report(true);

			// Then
			assertThat(result).isNotBlank();
		}
	}

	@Test
	void report_includingZeroCounters_isLongerThanExcluding(@TempDir Path dir) {
		// Given
		PerfContext.setPerfLevel(PerfLevel.ENABLE_COUNT);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var sut = PerfContext.newPerfContext()) {

			db.put("k".getBytes(), "v".getBytes());
			db.get("k".getBytes());

			// When
			var withZeros = sut.report(false);
			var withoutZeros = sut.report(true);

			// Then
			assertThat(withZeros.length()).isGreaterThan(withoutZeros.length());
		}
	}

	@Test
	void close_isIdempotent() {
		// Given — an already-closed instance
		var sut = PerfContext.newPerfContext();
		sut.close();

		// When
		ThrowingCallable secondClose = sut::close;

		// Then — closing twice must not crash the JVM
		assertThatCode(secondClose).doesNotThrowAnyException();
	}
}
