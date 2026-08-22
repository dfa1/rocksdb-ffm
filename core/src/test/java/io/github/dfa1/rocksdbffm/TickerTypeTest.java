package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TickerTypeTest {

	@Test
	void values_haveUniqueNonNegativeNativeValues() {
		// Given
		var tickers = TickerType.values();

		// When
		var distinctValues = new java.util.HashSet<Integer>();
		for (TickerType ticker : tickers) {
			distinctValues.add(ticker.getValue());
		}

		// Then
		assertThat(distinctValues).hasSize(tickers.length)
				.allSatisfy(v -> assertThat(v).isNotNegative());
	}

	@Test
	void valueOf_roundTripsThroughName() {
		// Given / When / Then
		assertThat(TickerType.valueOf("BLOCK_CACHE_HIT")).isEqualTo(TickerType.BLOCK_CACHE_HIT);
	}

	@Test
	void getTickerCount_reflectsRealDbActivity(@TempDir Path dir) {
		// Given
		try (Options options = Options.newOptions()
				.setCreateIfMissing(true)
				.enableStatistics()
				.setStatisticsLevel(StatsLevel.ALL);
		     ReadWriteDB db = RocksDB.openReadWrite(options, dir)) {

			db.put("key".getBytes(), "value".getBytes());
			db.get("key".getBytes());
			db.get("missing".getBytes());

			// When
			long keysWritten = options.getTickerCount(TickerType.NUMBER_KEYS_WRITTEN);
			long bloomOrGetMisses = options.getTickerCount(TickerType.NUMBER_KEYS_READ);

			// Then
			assertThat(keysWritten).isGreaterThan(0);
			assertThat(bloomOrGetMisses).isGreaterThanOrEqualTo(0);
		}
	}

	@Test
	void getTickerCount_isCallableForEveryDeclaredTicker(@TempDir Path dir) {
		// Given — every ticker must be a valid index into the native rocksdb::Tickers enum
		try (Options options = Options.newOptions()
				.setCreateIfMissing(true)
				.enableStatistics();
		     ReadWriteDB db = RocksDB.openReadWrite(options, dir)) {

			db.put("key".getBytes(), "value".getBytes());

			// When / Then — must not throw for any declared constant
			for (TickerType ticker : TickerType.values()) {
				assertThat(options.getTickerCount(ticker)).isGreaterThanOrEqualTo(0);
			}
		}
	}
}
