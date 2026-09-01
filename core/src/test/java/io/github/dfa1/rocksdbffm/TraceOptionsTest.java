package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TraceOptionsTest {

	@Test
	void newTraceOptions_hasRocksDBDefaults() {
		// Given

		// When
		try (var opts = TraceOptions.newTraceOptions()) {

			// Then
			assertThat(opts.getMaxTraceFileSize()).isEqualTo(MemorySize.ofBytes(64L * 1024 * 1024 * 1024));
			assertThat(opts.getSamplingFrequency()).isEqualTo(1);
			assertThat(opts.getFilter()).isEmpty();
			assertThat(opts.getPreserveWriteOrder()).isFalse();
		}
	}

	@Test
	void setMaxTraceFileSize_roundTrips() {
		// Given
		try (var opts = TraceOptions.newTraceOptions().setMaxTraceFileSize(MemorySize.ofMB(10))) {

			// When
			var result = opts.getMaxTraceFileSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(10));
		}
	}

	@Test
	void setSamplingFrequency_roundTrips() {
		// Given
		try (var opts = TraceOptions.newTraceOptions().setSamplingFrequency(10)) {

			// When
			var result = opts.getSamplingFrequency();

			// Then
			assertThat(result).isEqualTo(10);
		}
	}

	@Test
	void setFilter_roundTrips() {
		// Given
		try (var opts = TraceOptions.newTraceOptions()
				     .setFilter(Set.of(TraceFilter.GET, TraceFilter.MULTI_GET))) {

			// When
			var result = opts.getFilter();

			// Then
			assertThat(result).containsExactlyInAnyOrder(TraceFilter.GET, TraceFilter.MULTI_GET);
		}
	}

	@Test
	void setFilter_empty_meansEveryOperationTypeIsTraced() {
		// Given
		try (var opts = TraceOptions.newTraceOptions().setFilter(Set.of(TraceFilter.WRITE))) {

			// When
			opts.setFilter(Set.of());

			// Then
			assertThat(opts.getFilter()).isEmpty();
		}
	}

	@Test
	void setPreserveWriteOrder_roundTrips() {
		// Given
		try (var opts = TraceOptions.newTraceOptions().setPreserveWriteOrder(true)) {

			// When
			var result = opts.getPreserveWriteOrder();

			// Then
			assertThat(result).isTrue();
		}
	}
}
