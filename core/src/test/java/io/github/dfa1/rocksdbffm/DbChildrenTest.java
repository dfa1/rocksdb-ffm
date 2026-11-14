package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// [WalIterator], [Checkpoint] and [Replayer] are children of their database: closing the database releases
/// them first, so using them afterwards throws instead of touching freed native memory.
class DbChildrenTest {

	@Test
	void walIterator_usedAfterDbClosed_throws(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openReadWrite(dir, opts);
		db.put("k".getBytes(), "v".getBytes());
		var it = db.getUpdatesSince(SequenceNumber.of(0));

		// When
		db.close();

		// Then
		assertThatThrownBy(it::isValid).isInstanceOf(IllegalStateException.class);
		assertThatCode(it::close).doesNotThrowAnyException();
		opts.close();
	}

	@Test
	void walIterator_closedBeforeDb_dbCloseIsClean(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)) {
			var db = RocksDB.openReadWrite(dir, opts);
			var it = db.getUpdatesSince(SequenceNumber.of(0));
			it.close();
			it.close();

			// When
			ThrowingCallable close = db::close;

			// Then
			assertThatCode(close).doesNotThrowAnyException();
		}
	}

	@Test
	void checkpoint_usedAfterDbClosed_throws(@TempDir Path dir) {
		// Given
		var opts = Options.newOptions().setCreateIfMissing(true);
		var db = RocksDB.openReadWrite(dir.resolve("db"), opts);
		var checkpoint = Checkpoint.newCheckpoint(db);

		// When
		db.close();

		// Then
		assertThatThrownBy(() -> checkpoint.exportTo(dir.resolve("cp"))).isInstanceOf(IllegalStateException.class);
		assertThatCode(checkpoint::close).doesNotThrowAnyException();
		opts.close();
	}

	@Test
	void checkpoint_closedBeforeDb_dbCloseIsClean(@TempDir Path dir) {
		// Given
		try (var opts = Options.newOptions().setCreateIfMissing(true)) {
			var db = RocksDB.openReadWrite(dir, opts);
			var checkpoint = Checkpoint.newCheckpoint(db);
			checkpoint.close();
			checkpoint.close();

			// When
			ThrowingCallable close = db::close;

			// Then
			assertThatCode(close).doesNotThrowAnyException();
		}
	}

	@Test
	void replayer_usedAfterDbClosed_throws(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var source = RocksDB.openReadWrite(dir.resolve("source"), opts);
		     var traceOptions = TraceOptions.newTraceOptions()) {
			source.startTrace(tracePath, traceOptions);
			source.put("k".getBytes(), "v".getBytes());
			source.endTrace();
		}
		var opts = Options.newOptions().setCreateIfMissing(true);
		var target = RocksDB.openReadWrite(dir.resolve("target"), opts);
		var replayer = Replayer.create(target, tracePath);

		// When
		target.close();

		// Then
		assertThatThrownBy(replayer::prepare).isInstanceOf(IllegalStateException.class);
		assertThatCode(replayer::close).doesNotThrowAnyException();
		opts.close();
	}

	@Test
	void replayer_closedBeforeDb_dbCloseIsClean(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var source = RocksDB.openReadWrite(dir.resolve("source"), opts);
		     var traceOptions = TraceOptions.newTraceOptions()) {
			source.startTrace(tracePath, traceOptions);
			source.endTrace();
		}
		try (var opts = Options.newOptions().setCreateIfMissing(true)) {
			var target = RocksDB.openReadWrite(dir.resolve("target"), opts);
			var replayer = Replayer.create(target, tracePath);
			replayer.close();
			replayer.close();

			// When
			ThrowingCallable close = target::close;

			// Then
			assertThatCode(close).doesNotThrowAnyException();
		}
	}
}
