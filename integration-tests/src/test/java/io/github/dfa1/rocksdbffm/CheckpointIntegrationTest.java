package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckpointIntegrationTest {

	/// Tune this to exercise the workload with more or fewer keys.
	private static final int KEY_COUNT = 1_000;

	// DELETE_COUNT is capped below KEY_COUNT and UPDATED_INDEX is pinned to the last
	// key so the deleted range and the updated key can never collide, even at the
	// smallest workloads (e.g. KEY_COUNT = 1, where nothing is deleted).
	private static final int DELETE_COUNT = Math.min(Math.max(1, KEY_COUNT / 10), KEY_COUNT - 1);
	private static final int ADD_COUNT = Math.max(1, KEY_COUNT / 10);
	private static final int UPDATED_INDEX = KEY_COUNT - 1;

	@TempDir
	static Path sharedDir;

	private static Path checkpoint1Dir;
	private static Path checkpoint2Dir;

	private static byte[] keyFor(int i) {
		return String.format("key-%08d", i).getBytes();
	}

	private static byte[] valueFor(int i) {
		return String.format("value-%08d", i).getBytes();
	}

	private static byte[] updatedValueFor(int i) {
		return String.format("value-%08d-updated", i).getBytes();
	}

	@BeforeAll
	static void setup() {
		var dbDir = sharedDir.resolve("db");
		checkpoint1Dir = sharedDir.resolve("checkpoint-1");
		checkpoint2Dir = sharedDir.resolve("checkpoint-2");

		try (var opts = Options.newOptions().setCreateIfMissing(true);
		     var db = RocksDB.openReadWrite(opts, dbDir)) {

			// Write the initial set of keys
			try (var batch = WriteBatch.create()) {
				for (int i = 0; i < KEY_COUNT; i++) {
					batch.put(keyFor(i), valueFor(i));
				}
				db.write(batch);
			}

			// Checkpoint 1 — captures the initial state
			try (var cp = Checkpoint.newCheckpoint(db)) {
				cp.exportTo(checkpoint1Dir);
			}

			// Mutate: delete the first DELETE_COUNT keys, update the last key's value,
			// and add ADD_COUNT brand-new keys beyond the initial range
			try (var batch = WriteBatch.create()) {
				for (int i = 0; i < DELETE_COUNT; i++) {
					batch.delete(keyFor(i));
				}
				batch.put(keyFor(UPDATED_INDEX), updatedValueFor(UPDATED_INDEX));
				for (int i = KEY_COUNT; i < KEY_COUNT + ADD_COUNT; i++) {
					batch.put(keyFor(i), valueFor(i));
				}
				db.write(batch);
			}

			// Checkpoint 2 — captures the state after mutation
			try (var cp = Checkpoint.newCheckpoint(db)) {
				cp.exportTo(checkpoint2Dir);
			}
		}
	}

	@Test
	void checkpoint1_isIsolatedFromLaterMutations() {
		// Given — checkpoint 1 exported in @BeforeAll, before any mutation

		try (var db = RocksDB.openReadOnly(checkpoint1Dir)) {
			// When
			var deletedKeyValue = db.get(keyFor(0));
			var updatedKeyValue = db.get(keyFor(UPDATED_INDEX));
			var addedKeyValue = db.get(keyFor(KEY_COUNT));

			// Then — checkpoint 1 is isolated from mutations made after it was taken:
			// the key later deleted is still present, the key later updated still has
			// its original value, and the key later added does not exist yet
			assertThat(deletedKeyValue).isEqualTo(valueFor(0));
			assertThat(updatedKeyValue).isEqualTo(valueFor(UPDATED_INDEX));
			assertThat(addedKeyValue).isNull();
		}
	}

	@Test
	void checkpoint1_containsAllInitialKeys() {
		// Given — checkpoint 1 exported in @BeforeAll

		try (var db = RocksDB.openReadOnly(checkpoint1Dir)) {
			// When
			List<byte[]> values = new ArrayList<>(KEY_COUNT);
			for (int i = 0; i < KEY_COUNT; i++) {
				values.add(db.get(keyFor(i)));
			}

			// Then — every key written before checkpoint 1 is present with its original value
			for (int i = 0; i < KEY_COUNT; i++) {
				assertThat(values.get(i)).isEqualTo(valueFor(i));
			}
		}
	}

	@Test
	void checkpoint2_reflectsDeletedKeys() {
		// Given — checkpoint 2 exported in @BeforeAll, after deleting the first DELETE_COUNT keys

		try (var db = RocksDB.openReadOnly(checkpoint2Dir)) {
			// When
			List<byte[]> values = new ArrayList<>(DELETE_COUNT);
			for (int i = 0; i < DELETE_COUNT; i++) {
				values.add(db.get(keyFor(i)));
			}

			// Then
			for (byte[] value : values) {
				assertThat(value).isNull();
			}
		}
	}

	@Test
	void checkpoint2_reflectsUpdatedValue() {
		// Given — checkpoint 2 exported in @BeforeAll, after updating the last key's value

		try (var db = RocksDB.openReadOnly(checkpoint2Dir)) {
			// When
			var value = db.get(keyFor(UPDATED_INDEX));

			// Then
			assertThat(value).isEqualTo(updatedValueFor(UPDATED_INDEX));
		}
	}

	@Test
	void checkpoint2_reflectsAddedKeys() {
		// Given — checkpoint 2 exported in @BeforeAll, after adding ADD_COUNT new keys

		try (var db = RocksDB.openReadOnly(checkpoint2Dir)) {
			// When
			List<byte[]> values = new ArrayList<>(ADD_COUNT);
			for (int i = KEY_COUNT; i < KEY_COUNT + ADD_COUNT; i++) {
				values.add(db.get(keyFor(i)));
			}

			// Then
			for (int i = 0; i < ADD_COUNT; i++) {
				assertThat(values.get(i)).isEqualTo(valueFor(KEY_COUNT + i));
			}
		}
	}

	@Test
	void checkpoint2_leavesUntouchedKeysUnchanged() {
		// Given — checkpoint 2 exported in @BeforeAll; every key between DELETE_COUNT
		// and UPDATED_INDEX (exclusive) was never touched by the mutation

		try (var db = RocksDB.openReadOnly(checkpoint2Dir)) {
			// When
			List<byte[]> values = new ArrayList<>();
			for (int i = DELETE_COUNT; i < UPDATED_INDEX; i++) {
				values.add(db.get(keyFor(i)));
			}

			// Then
			for (int i = 0; i < values.size(); i++) {
				assertThat(values.get(i)).isEqualTo(valueFor(DELETE_COUNT + i));
			}
		}
	}
}
