package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsHistogramDataTest {

	@TempDir
	Path tempDir;

	@Test
	void freshInstance_allFieldsAreZero() {
		// Given / When
		try (var sut = StatisticsHistogramData.newStatisticsHistogramData()) {

			// Then
			assertThat(sut.getCount()).isZero();
			assertThat(sut.getSum()).isZero();
			assertThat(sut.getMin()).isZero();
			assertThat(sut.getMax()).isZero();
			assertThat(sut.getAverage()).isZero();
			assertThat(sut.getMedian()).isZero();
			assertThat(sut.getP95()).isZero();
			assertThat(sut.getP99()).isZero();
			assertThat(sut.getStdDev()).isZero();
		}
	}

	@Test
	void getHistogramData_populatesFieldsFromRealSamples() {
		// Given
		try (Options options = Options.newOptions()
				.setCreateIfMissing(true)
				.enableStatistics()
				.setStatisticsLevel(StatsLevel.ALL);
		     ReadWriteDB db = RocksDB.openReadWrite(options, tempDir)) {

			for (int i = 0; i < 200; i++) {
				db.put(("key-" + i).getBytes(), ("value-" + i).getBytes());
			}
			for (int i = 0; i < 200; i++) {
				db.get(("key-" + i).getBytes());
			}

			// When
			try (var sut = StatisticsHistogramData.newStatisticsHistogramData()) {
				options.getHistogramData(HistogramType.DB_GET, sut);

				// Then
				assertThat(sut.getCount()).isEqualTo(200);
				assertThat(sut.getSum()).isGreaterThan(0);
				assertThat(sut.getMin()).isGreaterThanOrEqualTo(0.0);
				assertThat(sut.getMax()).isGreaterThanOrEqualTo(sut.getMin());
				assertThat(sut.getAverage()).isBetween(sut.getMin(), sut.getMax());
				assertThat(sut.getMedian()).isBetween(sut.getMin(), sut.getMax());
				assertThat(sut.getP95()).isBetween(sut.getMin(), sut.getMax());
				assertThat(sut.getP99()).isBetween(sut.getMin(), sut.getMax());
				assertThat(sut.getStdDev()).isGreaterThanOrEqualTo(0.0);
			}
		}
	}

	@Test
	void toString_includesAllFields() {
		// Given
		try (var sut = StatisticsHistogramData.newStatisticsHistogramData()) {

			// When
			var result = sut.toString();

			// Then
			assertThat(result)
					.startsWith("Histogram[")
					.contains("count=", "sum=", "min=", "max=", "avg=", "median=", "p95=", "p99=", "stddev=");
		}
	}
}
