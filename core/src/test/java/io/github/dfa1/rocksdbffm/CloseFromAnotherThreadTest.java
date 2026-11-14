package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/// A native wrapper can be released by a thread other than the one that created it: a parent's close sweeps its
/// children from whichever thread closes the parent. [NativeObject#release()] logs, and never throws, when releasing
/// fails, so a confined arena closed from the wrong thread shows up only as an ERROR log line and a leak.
class CloseFromAnotherThreadTest {

	private final List<LogRecord> errors = new CopyOnWriteArrayList<>();
	private final Logger logger = Logger.getLogger(NativeObject.class.getName());
	private final Handler handler = new Handler() {
		@Override
		public void publish(LogRecord record) {
			if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
				errors.add(record);
			}
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	};

	@BeforeEach
	void captureErrors() {
		logger.addHandler(handler);
	}

	@AfterEach
	void stopCapturing() {
		logger.removeHandler(handler);
	}

	private static void onAnotherThread(Runnable action) throws InterruptedException {
		var thread = new Thread(action);
		thread.start();
		thread.join();
	}

	@Test
	void readOptions_closedOnAnotherThread_releasesCleanly() throws InterruptedException {
		// Given
		var readOptions = ReadOptions.newReadOptions().setIterateUpperBound("z".getBytes());

		// When
		onAnotherThread(readOptions::close);

		// Then
		assertThat(errors).isEmpty();
	}

	@Test
	void iterator_releasedByTheDbClosingOnAnotherThread_releasesCleanly(@TempDir Path dir) throws InterruptedException {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openReadWrite(dir, opts);
		var iterator = db.newIterator();
		iterator.seekToFirst();

		// When
		onAnotherThread(db::close);

		// Then
		assertThat(errors).isEmpty();
		opts.close();
	}
}
