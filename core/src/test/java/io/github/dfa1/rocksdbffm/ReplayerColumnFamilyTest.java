package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/// A [Replayer] created with column families keeps raw pointers to their handles. Dropping one of them while the
/// replayer is alive must make the replayer throw instead of replaying into a freed handle.
class ReplayerColumnFamilyTest {

	private static final ColumnFamilyName CF = ColumnFamilyName.of("cf1");

	private static void traceWritesToCf(Path dir, Path tracePath) {
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var source = RocksDB.openReadWrite(dir.resolve("source"), opts);
		     var cf = source.createColumnFamily(ColumnFamilyDescriptor.of(CF));
		     var traceOptions = TraceOptions.newTraceOptions()) {
			source.startTrace(tracePath, traceOptions);
			source.put(cf, "k".getBytes(), "v".getBytes());
			source.endTrace();
		}
	}

	@Test
	void replay_afterAReplayedColumnFamilyWasDropped_throws(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		traceWritesToCf(dir, tracePath);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var target = RocksDB.openReadWrite(dir.resolve("target"), opts);
		     var replayOptions = ReplayOptions.newReplayOptions()) {
			var cf = target.createColumnFamily(ColumnFamilyDescriptor.of(CF));
			var replayer = Replayer.create(target, tracePath, List.of(cf), Env.DEFAULTS, EnvOptions.DEFAULTS);
			replayer.prepare();
			target.dropColumnFamily(cf);

			// When
			var thrown = catchThrowable(() -> replayer.replay(replayOptions));

			// Then
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			replayer.close();
		}
	}

	@Test
	void prepare_afterAReplayedColumnFamilyWasDropped_throws(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		traceWritesToCf(dir, tracePath);
		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var target = RocksDB.openReadWrite(dir.resolve("target"), opts)) {
			var cf = target.createColumnFamily(ColumnFamilyDescriptor.of(CF));
			var replayer = Replayer.create(target, tracePath, List.of(cf), Env.DEFAULTS, EnvOptions.DEFAULTS);
			target.dropColumnFamily(cf);

			// When
			var thrown = catchThrowable(replayer::prepare);

			// Then
			assertThat(thrown).isInstanceOf(IllegalStateException.class);
			replayer.close();
		}
	}
}
