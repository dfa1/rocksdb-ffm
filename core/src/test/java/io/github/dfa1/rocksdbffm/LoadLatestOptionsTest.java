package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadLatestOptionsTest {

	@TempDir
	Path dir;

	private void createDb(MemorySize defaultBuffer, MemorySize cf1Buffer) {
		try (var opts = Options.newOptions().setCreateIfMissing(true).setWriteBufferSize(defaultBuffer);
		     var db = RocksDB.openReadWrite(opts, dir);
		     var cfOpts = Options.newOptions().setWriteBufferSize(cf1Buffer);
		     var cf = db.createColumnFamily(ColumnFamilyDescriptor.of("cf1", cfOpts))) {
			db.put("k".getBytes(), "v".getBytes());
		}
	}

	@Test
	void loadLatestOptions_restoresPerColumnFamilyOptions() {
		// Given
		createDb(MemorySize.ofMB(16), MemorySize.ofMB(8));

		// When
		try (var loaded = RocksDB.loadLatestOptions(dir, false)) {

			// Then
			var names = loaded.columnFamilies().stream().map(d -> new String(d.name(), StandardCharsets.UTF_8)).toList();
			assertThat(names).containsExactlyInAnyOrder("default", "cf1");
			var buffers = loaded.columnFamilies().stream()
					.collect(java.util.stream.Collectors.toMap(
							d -> new String(d.name(), StandardCharsets.UTF_8),
							d -> d.options().getWriteBufferSize()));
			assertThat(buffers).containsEntry("default", MemorySize.ofMB(16)).containsEntry("cf1", MemorySize.ofMB(8));
		}
	}

	@Test
	void loadLatestOptions_resultReopensTheDatabase() {
		// Given
		createDb(MemorySize.ofMB(16), MemorySize.ofMB(8));
		var handles = new java.util.ArrayList<ColumnFamilyHandle>();

		// When
		try (var loaded = RocksDB.loadLatestOptions(dir, false);
		     var db = RocksDB.openReadWrite(loaded.dbOptions(), dir, loaded.columnFamilies(), handles)) {
			var value = db.get("k".getBytes());

			// Then
			assertThat(handles).hasSize(2);
			assertThat(value).isEqualTo("v".getBytes());
			handles.forEach(ColumnFamilyHandle::close);
		}
	}

	@Test
	void loadLatestOptions_missingDatabase_throws() {
		// Given
		var missing = dir.resolve("missing");

		// When
		var thrown = assertThatThrownBy(() -> RocksDB.loadLatestOptions(missing, false));

		// Then
		thrown.isInstanceOf(RocksDBException.class);
	}

	@Test
	void loadLatestOptions_unknownOption_failsUnlessIgnored() throws IOException {
		// Given
		createDb(MemorySize.ofMB(16), MemorySize.ofMB(8));
		Path optionsFile;
		try (Stream<Path> files = Files.list(dir)) {
			optionsFile = files.filter(p -> p.getFileName().toString().startsWith("OPTIONS-")).max(Path::compareTo).orElseThrow();
		}
		var text = Files.readString(optionsFile).replace("[DBOptions]\n", "[DBOptions]\n  bogus_option_from_the_future=1\n").replaceAll("rocksdb_version=\\S+", "rocksdb_version=99.0.0");
		Files.writeString(optionsFile, text);

		// When
		var strict = assertThatThrownBy(() -> RocksDB.loadLatestOptions(dir, false));
		try (var lenient = RocksDB.loadLatestOptions(dir, true)) {

			// Then
			strict.isInstanceOf(RocksDBException.class);
			assertThat(lenient.columnFamilies()).hasSize(2);
		}
	}

	@Test
	void withOverrides_appliesStringWithoutTouchingBase() {
		// Given
		try (var base = Options.newOptions().setWriteBufferSize(MemorySize.ofMB(16))) {

			// When
			try (var result = base.withOverrides("write_buffer_size=4194304;num_levels=5")) {

				// Then
				assertThat(result.getWriteBufferSize()).isEqualTo(MemorySize.ofMB(4));
				assertThat(result.getNumLevels()).isEqualTo(5);
				assertThat(base.getWriteBufferSize()).isEqualTo(MemorySize.ofMB(16));
				assertThat(base.getNumLevels()).isEqualTo(7);
			}
		}
	}

	@Test
	void withOverrides_malformedString_throws() {
		// Given
		try (var base = Options.newOptions()) {

			// When
			var thrown = assertThatThrownBy(() -> base.withOverrides("no_such_option=1"));

			// Then
			thrown.isInstanceOf(RocksDBException.class);
		}
	}

	@Test
	void copy_isIndependentOfOriginal() {
		// Given
		try (var original = Options.newOptions().setWriteBufferSize(MemorySize.ofMB(16))) {
			var copy = original.copy();

			// When
			original.close();

			// Then
			assertThat(copy.getWriteBufferSize()).isEqualTo(MemorySize.ofMB(16));
			copy.close();
		}
	}
}
