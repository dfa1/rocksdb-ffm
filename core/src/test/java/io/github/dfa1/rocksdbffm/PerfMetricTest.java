package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class PerfMetricTest {

	@Test
	void values_haveUniqueNonNegativeNativeValues() {
		// Given
		var metrics = PerfMetric.values();

		// When
		var distinctValues = new HashSet<Integer>();
		for (PerfMetric metric : metrics) {
			distinctValues.add(metric.value);
		}

		// Then
		assertThat(distinctValues).hasSize(metrics.length);
		assertThat(distinctValues).allSatisfy(v -> assertThat(v).isNotNegative());
	}

	@Test
	void valueOf_roundTripsThroughName() {
		// Given / When / Then
		assertThat(PerfMetric.valueOf("BLOCK_CACHE_HIT_COUNT")).isEqualTo(PerfMetric.BLOCK_CACHE_HIT_COUNT);
	}

	@Test
	void metric_isReadableForEveryDeclaredMetric(@TempDir Path dir) {
		// Given
		PerfContext.setPerfLevel(PerfLevel.ENABLE_TIME);
		try (Options options = Options.newOptions().setCreateIfMissing(true);
		     ReadWriteDB db = RocksDB.open(options, dir);
		     PerfContext ctx = PerfContext.newPerfContext()) {

			db.put("key".getBytes(), "value".getBytes());
			db.get("key".getBytes());

			// When / Then — must not throw for any declared constant
			for (PerfMetric metric : PerfMetric.values()) {
				assertThat(ctx.metric(metric)).isGreaterThanOrEqualTo(0);
			}
		} finally {
			PerfContext.setPerfLevel(PerfLevel.DISABLE);
		}
	}
}
