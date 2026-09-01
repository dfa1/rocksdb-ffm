package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TracingTest {

	@Test
	void startTrace_and_replay_reproducesWritesOnTargetDb(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		try (var source = RocksDB.openReadWrite(dir.resolve("source"));
		     var traceOptions = TraceOptions.newTraceOptions()) {
			source.startTrace(traceOptions, tracePath);
			source.put("k1".getBytes(), "v1".getBytes());
			source.put("k2".getBytes(), "v2".getBytes());
			source.endTrace();
		}

		// When
		try (var target = RocksDB.openReadWrite(dir.resolve("target"));
		     var replayer = Replayer.create(target, tracePath);
		     var replayOptions = ReplayOptions.newReplayOptions()) {
			replayer.prepare();
			replayer.replay(replayOptions);
		}

		// Then
		try (var target = RocksDB.openReadWrite(dir.resolve("target"))) {
			assertThat(target.get("k1".getBytes())).isEqualTo("v1".getBytes());
			assertThat(target.get("k2".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	@Test
	void getHeaderTimestamp_returnsNonNullInstant(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		try (var source = RocksDB.openReadWrite(dir.resolve("source"));
		     var traceOptions = TraceOptions.newTraceOptions()) {
			source.startTrace(traceOptions, tracePath);
			source.put("k".getBytes(), "v".getBytes());
			source.endTrace();
		}

		try (var target = RocksDB.openReadWrite(dir.resolve("target"));
		     var replayer = Replayer.create(target, tracePath)) {
			replayer.prepare();

			// When
			var result = replayer.getHeaderTimestamp();

			// Then
			assertThat(result).isNotNull();
		}
	}

	@Test
	void startTrace_withExplicitEnvAndEnvOptions_capturesTrace(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		try (var source = RocksDB.openReadWrite(dir.resolve("source"));
		     var env = Env.defaultEnv();
		     var envOptions = EnvOptions.newEnvOptions();
		     var traceOptions = TraceOptions.newTraceOptions()) {
			// When
			source.startTrace(env, envOptions, traceOptions, tracePath);
			source.put("k".getBytes(), "v".getBytes());
			source.endTrace();
		}

		// Then
		try (var target = RocksDB.openReadWrite(dir.resolve("target"));
		     var replayer = Replayer.create(target, tracePath);
		     var replayOptions = ReplayOptions.newReplayOptions()) {
			replayer.prepare();
			replayer.replay(replayOptions);
		}
		try (var target = RocksDB.openReadWrite(dir.resolve("target"))) {
			assertThat(target.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}
}
