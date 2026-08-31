package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FifoCompactionOptionsTest {

	@Test
	void fifoCompaction_allowsReadWrite(@TempDir Path dir) {
		// Given
		try (var fifo = FifoCompactionOptions.newFifoCompactionOptions()
				     .setMaxTableFilesSize(MemorySize.ofMB(64))
				     .setAllowCompaction(true);
		     var opts = Options.newOptions()
				     .setCreateIfMissing(true)
				     .setCompactionStyle(Options.CompactionStyle.FIFO)
				     .setFifoCompactionOptions(fifo);
		     var db = RocksDB.openReadWrite(opts, dir)) {

			db.put("k".getBytes(), "v".getBytes());

			// When
			var result = db.get("k".getBytes());

			// Then
			assertThat(result).isEqualTo("v".getBytes());
		}
	}

	@Test
	void setAllowCompaction_roundTrips() {
		// Given
		try (var fifo = FifoCompactionOptions.newFifoCompactionOptions().setAllowCompaction(true)) {

			// When
			var result = fifo.getAllowCompaction();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setMaxTableFilesSize_roundTrips() {
		// Given
		try (var fifo = FifoCompactionOptions.newFifoCompactionOptions()
				     .setMaxTableFilesSize(MemorySize.ofMB(64))) {

			// When
			var result = fifo.getMaxTableFilesSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(64));
		}
	}

	@Test
	void setMaxDataFilesSize_roundTrips() {
		// Given
		try (var fifo = FifoCompactionOptions.newFifoCompactionOptions()
				     .setMaxDataFilesSize(MemorySize.ofMB(32))) {

			// When
			var result = fifo.getMaxDataFilesSize();

			// Then
			assertThat(result).isEqualTo(MemorySize.ofMB(32));
		}
	}

	@Test
	void setUseKvRatioCompaction_roundTrips() {
		// Given
		try (var fifo = FifoCompactionOptions.newFifoCompactionOptions().setUseKvRatioCompaction(true)) {

			// When
			var result = fifo.getUseKvRatioCompaction();

			// Then
			assertThat(result).isTrue();
		}
	}

	@Test
	void setCompactionStyle_roundTrips() {
		// Given
		try (var opts = Options.newOptions().setCompactionStyle(Options.CompactionStyle.FIFO)) {

			// When
			var result = opts.getCompactionStyle();

			// Then
			assertThat(result).isEqualTo(Options.CompactionStyle.FIFO);
		}
	}
}
