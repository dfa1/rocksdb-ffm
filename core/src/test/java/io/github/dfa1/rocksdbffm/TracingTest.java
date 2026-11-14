package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TracingTest {

	@Test
	void startTrace_and_replay_reproducesWritesOnTargetDb(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		try (Options sourceOpts = Options.newOptions().setCreateIfMissing(true); var source = RocksDB.openReadWrite(dir.resolve("source"), sourceOpts);
		     var traceOptions = TraceOptions.newTraceOptions()) {
			source.startTrace(tracePath, traceOptions);
			source.put("k1".getBytes(), "v1".getBytes());
			source.put("k2".getBytes(), "v2".getBytes());
			source.endTrace();
		}

		// When
		try (Options targetOpts = Options.newOptions().setCreateIfMissing(true); var target = RocksDB.openReadWrite(dir.resolve("target"), targetOpts);
		     var replayer = Replayer.create(target, tracePath);
		     var replayOptions = ReplayOptions.newReplayOptions()) {
			replayer.prepare();
			replayer.replay(replayOptions);
		}

		// Then
		try (Options targetOpts = Options.newOptions().setCreateIfMissing(true); var target = RocksDB.openReadWrite(dir.resolve("target"), targetOpts)) {
			assertThat(target.get("k1".getBytes())).isEqualTo("v1".getBytes());
			assertThat(target.get("k2".getBytes())).isEqualTo("v2".getBytes());
		}
	}

	@Test
	void getHeaderTimestamp_returnsNonNullInstant(@TempDir Path dir) {
		// Given
		var tracePath = dir.resolve("trace.log");
		try (Options sourceOpts = Options.newOptions().setCreateIfMissing(true); var source = RocksDB.openReadWrite(dir.resolve("source"), sourceOpts);
		     var traceOptions = TraceOptions.newTraceOptions()) {
			source.startTrace(tracePath, traceOptions);
			source.put("k".getBytes(), "v".getBytes());
			source.endTrace();
		}

		try (Options targetOpts = Options.newOptions().setCreateIfMissing(true); var target = RocksDB.openReadWrite(dir.resolve("target"), targetOpts);
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
		try (Options sourceOpts = Options.newOptions().setCreateIfMissing(true); var source = RocksDB.openReadWrite(dir.resolve("source"), sourceOpts);
		     var env = Env.defaultEnv();
		     var envOptions = EnvOptions.newEnvOptions();
		     var traceOptions = TraceOptions.newTraceOptions()) {
			// When
			source.startTrace(tracePath, env, envOptions, traceOptions);
			source.put("k".getBytes(), "v".getBytes());
			source.endTrace();
		}

		// Then
		try (Options targetOpts = Options.newOptions().setCreateIfMissing(true); var target = RocksDB.openReadWrite(dir.resolve("target"), targetOpts);
		     var replayer = Replayer.create(target, tracePath);
		     var replayOptions = ReplayOptions.newReplayOptions()) {
			replayer.prepare();
			replayer.replay(replayOptions);
		}
		try (Options targetOpts = Options.newOptions().setCreateIfMissing(true); var target = RocksDB.openReadWrite(dir.resolve("target"), targetOpts)) {
			assertThat(target.get("k".getBytes())).isEqualTo("v".getBytes());
		}
	}

	@Test
	void replayerCreate_withExplicitColumnFamilies_capturesEachHandle(@TempDir Path dir) {
		// Given -- the full create() overload marshals the cfs list into a native array; a
		// single-element list is enough to exercise that marshaling instead of the empty-list
		// default column family path every other test in this class uses.
		var tracePath = dir.resolve("trace.log");
		try (Options sourceOpts = Options.newOptions().setCreateIfMissing(true); var source = RocksDB.openReadWrite(dir.resolve("source"), sourceOpts);
		     var traceOptions = TraceOptions.newTraceOptions()) {
			source.startTrace(tracePath, traceOptions);
			source.put("k".getBytes(), "v".getBytes());
			source.endTrace();
		}

		try (Options targetOpts = Options.newOptions().setCreateIfMissing(true); var target = RocksDB.openReadWrite(dir.resolve("target"), targetOpts);
		     var cf = target.createColumnFamily(ColumnFamilyDescriptor.of(ColumnFamilyName.of("cf1")));
		     var env = Env.defaultEnv();
		     var envOptions = EnvOptions.newEnvOptions();
		     var replayer = Replayer.create(target, tracePath, List.of(cf), env, envOptions)) {

			// When
			replayer.prepare();

			// Then -- reaching prepare() without throwing confirms the column-family array was
			// built and passed correctly
			assertThat(replayer.getHeaderTimestamp()).isNotNull();
		}
	}

	@Test
	void startIoTrace_and_endIoTrace_writeANonEmptyTraceFile(@TempDir Path dir) throws IOException {
		// Given
		var tracePath = dir.resolve("io-trace.log");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var traceOptions = TraceOptions.newTraceOptions()) {

			// When
			db.startIoTrace(tracePath, traceOptions);
			db.put("k".getBytes(), "v".getBytes());
			db.endIoTrace();
		}

		// Then
		assertThat(Files.size(tracePath)).isPositive();
	}

	@Test
	void startIoTrace_withExplicitEnvAndEnvOptions_writesANonEmptyTraceFile(@TempDir Path dir) throws IOException {
		// Given
		var tracePath = dir.resolve("io-trace.log");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var env = Env.defaultEnv();
		     var envOptions = EnvOptions.newEnvOptions();
		     var traceOptions = TraceOptions.newTraceOptions()) {

			// When
			db.startIoTrace(tracePath, env, envOptions, traceOptions);
			db.put("k".getBytes(), "v".getBytes());
			db.endIoTrace();
		}

		// Then
		assertThat(Files.size(tracePath)).isPositive();
	}

	@Test
	void startBlockCacheTrace_withTraceOptions_writesANonEmptyTraceFile(@TempDir Path dir) throws IOException {
		// Given
		var tracePath = dir.resolve("block-cache-trace.log");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var traceOptions = TraceOptions.newTraceOptions()) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.startBlockCacheTrace(tracePath, traceOptions);
			db.get("k".getBytes());
			db.endBlockCacheTrace();
		}

		// Then
		assertThat(Files.size(tracePath)).isPositive();
	}

	@Test
	void startBlockCacheTrace_withExplicitEnvAndEnvOptions_writesANonEmptyTraceFile(@TempDir Path dir)
			throws IOException {
		// Given
		var tracePath = dir.resolve("block-cache-trace.log");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var env = Env.defaultEnv();
		     var envOptions = EnvOptions.newEnvOptions();
		     var traceOptions = TraceOptions.newTraceOptions()) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.startBlockCacheTrace(tracePath, env, envOptions, traceOptions);
			db.get("k".getBytes());
			db.endBlockCacheTrace();
		}

		// Then
		assertThat(Files.size(tracePath)).isPositive();
	}

	@Test
	void startBlockCacheTrace_withBlockCacheTraceOptions_writesANonEmptyTraceFile(@TempDir Path dir)
			throws IOException {
		// Given
		var tracePath = dir.resolve("block-cache-trace.log");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var traceOptions = BlockCacheTraceOptions.newBlockCacheTraceOptions();
		     var writerOptions = BlockCacheTraceWriterOptions.newBlockCacheTraceWriterOptions()) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.startBlockCacheTrace(tracePath, traceOptions, writerOptions);
			db.get("k".getBytes());
			db.endBlockCacheTrace();
		}

		// Then
		assertThat(Files.size(tracePath)).isPositive();
	}

	@Test
	void startBlockCacheTrace_withBlockCacheTraceOptionsAndExplicitEnv_writesANonEmptyTraceFile(@TempDir Path dir)
			throws IOException {
		// Given
		var tracePath = dir.resolve("block-cache-trace.log");
		try (Options dbOpts = Options.newOptions().setCreateIfMissing(true); var db = RocksDB.openReadWrite(dir.resolve("db"), dbOpts);
		     var env = Env.defaultEnv();
		     var envOptions = EnvOptions.newEnvOptions();
		     var traceOptions = BlockCacheTraceOptions.newBlockCacheTraceOptions();
		     var writerOptions = BlockCacheTraceWriterOptions.newBlockCacheTraceWriterOptions()) {
			db.put("k".getBytes(), "v".getBytes());

			// When
			db.startBlockCacheTrace(tracePath, env, envOptions, traceOptions, writerOptions);
			db.get("k".getBytes());
			db.endBlockCacheTrace();
		}

		// Then
		assertThat(Files.size(tracePath)).isPositive();
	}
}
