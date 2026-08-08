package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RocksIteratorTest {

	// -----------------------------------------------------------------------
	// Forward iteration
	// -----------------------------------------------------------------------

	@Test
	void seekToFirst_iteratesAllKeysInOrder(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("b".getBytes(), "2".getBytes());
			db.put("a".getBytes(), "1".getBytes());
			db.put("c".getBytes(), "3".getBytes());

			// When
			List<String> keys = new ArrayList<>();
			try (RocksIterator it = db.newIterator()) {
				for (it.seekToFirst(); it.isValid(); it.next()) {
					keys.add(new String(it.key()));
				}
				it.checkError();
			}

			// Then
			assertThat(keys).containsExactly("a", "b", "c");
		}
	}

	@Test
	void seekToLast_iteratesAllKeysInReverseOrder(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());
			db.put("c".getBytes(), "3".getBytes());

			// When
			List<String> keys = new ArrayList<>();
			try (RocksIterator it = db.newIterator()) {
				for (it.seekToLast(); it.isValid(); it.prev()) {
					keys.add(new String(it.key()));
				}
				it.checkError();
			}

			// Then
			assertThat(keys).containsExactly("c", "b", "a");
		}
	}

	// -----------------------------------------------------------------------
	// Seek
	// -----------------------------------------------------------------------

	@Test
	void seek_positionsAtFirstKeyGreaterOrEqual(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("c".getBytes(), "3".getBytes());
			db.put("e".getBytes(), "5".getBytes());

			// When
			try (RocksIterator it = db.newIterator()) {
				it.seek("b".getBytes());

				// Then — "b" doesn't exist, should land on "c"
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("c".getBytes());
				assertThat(it.value()).isEqualTo("3".getBytes());
			}
		}
	}

	@Test
	void seek_exactMatch(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			// When
			try (RocksIterator it = db.newIterator()) {
				it.seek("b".getBytes());

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("b".getBytes());
			}
		}
	}

	@Test
	void seek_pastLastKey_isInvalid(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());

			// When
			try (RocksIterator it = db.newIterator()) {
				it.seek("z".getBytes());

				// Then
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void seekForPrev_positionsAtLastKeyLessOrEqual(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("c".getBytes(), "3".getBytes());
			db.put("e".getBytes(), "5".getBytes());

			// When
			try (RocksIterator it = db.newIterator()) {
				it.seekForPrev("d".getBytes());

				// Then — "d" doesn't exist, should land on "c"
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("c".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// ByteBuffer and MemorySegment seek variants
	// -----------------------------------------------------------------------

	@Test
	void seek_withDirectByteBuffer(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			// When
			ByteBuffer target = ByteBuffer.allocateDirect(1);
			target.put((byte) 'b').flip();
			try (RocksIterator it = db.newIterator()) {
				it.seek(target);

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("b".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// key/value access tiers
	// -----------------------------------------------------------------------

	@Test
	void key_value_byteBuffer_variant(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("hello".getBytes(), "world".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();
				assertThat(it.isValid()).isTrue();

				// When — ByteBuffer key
				ByteBuffer keyBuf = ByteBuffer.allocateDirect(64);
				CopyResult keyResult = it.key(keyBuf);
				keyBuf.flip();
				byte[] keyBytes = new byte[keyBuf.remaining()];
				keyBuf.get(keyBytes);

				// When — ByteBuffer value
				ByteBuffer valBuf = ByteBuffer.allocateDirect(64);
				CopyResult valResult = it.value(valBuf);
				valBuf.flip();
				byte[] valBytes = new byte[valBuf.remaining()];
				valBuf.get(valBytes);

				// Then
				assertThat(keyResult).isEqualTo(new CopyResult.Copied());
				assertThat(keyBytes).isEqualTo("hello".getBytes());
				assertThat(valResult).isEqualTo(new CopyResult.Copied());
				assertThat(valBytes).isEqualTo("world".getBytes());
			}
		}
	}

	@Test
	void key_value_byteBuffer_returnsNotEnoughCapacity_whenTooSmall(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("hello".getBytes(), "world".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();
				assertThat(it.isValid()).isTrue();

				ByteBuffer keyBuf = ByteBuffer.allocateDirect(2);
				keyBuf.put((byte) 'X').put((byte) 'X').clear();
				ByteBuffer valBuf = ByteBuffer.allocateDirect(2);
				valBuf.put((byte) 'X').put((byte) 'X').clear();

				// When
				CopyResult keyResult = it.key(keyBuf);
				CopyResult valResult = it.value(valBuf);

				// Then
				assertThat(keyResult).isEqualTo(new CopyResult.NotEnoughCapacity(5));
				assertThat(keyBuf.position()).isZero();
				assertThat(keyBuf.get(0)).isEqualTo((byte) 'X');
				assertThat(keyBuf.get(1)).isEqualTo((byte) 'X');
				assertThat(valResult).isEqualTo(new CopyResult.NotEnoughCapacity(5));
				assertThat(valBuf.position()).isZero();
				assertThat(valBuf.get(0)).isEqualTo((byte) 'X');
				assertThat(valBuf.get(1)).isEqualTo((byte) 'X');
			}
		}
	}

	@Test
	void keySegment_valueSegment_zeroCopy(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();
				assertThat(it.isValid()).isTrue();

				// When
				var keySeg = it.keySegment();
				var valSeg = it.valueSegment();

				// Then
				assertThat(keySeg.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE))
						.isEqualTo("k".getBytes());
				assertThat(valSeg.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE))
						.isEqualTo("v".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// Empty database
	// -----------------------------------------------------------------------

	@Test
	void seekToFirst_onEmptyDb_isInvalid(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {

			// When
			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isFalse();
				it.checkError();
			}
		}
	}

	// -----------------------------------------------------------------------
	// Custom ReadOptions
	// -----------------------------------------------------------------------

	@Test
	void newIterator_withReadOptions(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir);
		     var readOptions = ReadOptions.newReadOptions()) {
			db.put("x".getBytes(), "y".getBytes());

			// When
			try (RocksIterator it = db.newIterator(readOptions)) {
				it.seekToFirst();

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("x".getBytes());
				assertThat(it.value()).isEqualTo("y".getBytes());
			}
		}
	}

	// -----------------------------------------------------------------------
	// status()
	// -----------------------------------------------------------------------

	@Test
	void error_returnsEmptyWhenNoError(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();

				// When
				var result = it.error();

				// Then
				assertThat(result).isEmpty();
			}
		}
	}

	@Test
	void error_returnsEmptyAfterFullIteration(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			try (RocksIterator it = db.newIterator()) {
				for (it.seekToFirst(); it.isValid(); it.next()) {
					// consume all entries
				}

				// When
				var result = it.error();

				// Then
				assertThat(result).isEmpty();
			}
		}
	}

	// -----------------------------------------------------------------------
	// refresh()
	// -----------------------------------------------------------------------

	@Test
	void refresh_notCalled_doesNotSeeWritesMadeAfterIteratorCreation(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());

			try (RocksIterator it = db.newIterator()) {
				db.put("b".getBytes(), "2".getBytes());

				// When
				it.seek("b".getBytes());

				// Then — iterator still reflects the state at creation time
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void refresh_seesWritesMadeAfterIteratorCreation(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());

			try (RocksIterator it = db.newIterator()) {
				db.put("b".getBytes(), "2".getBytes());

				// When
				it.refresh();
				it.seek("b".getBytes());

				// Then
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("b".getBytes());
				assertThat(it.value()).isEqualTo("2".getBytes());
			}
		}
	}

	@Test
	void refresh_seesDeletesMadeAfterIteratorCreation(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("a".getBytes(), "1".getBytes());
			db.put("b".getBytes(), "2".getBytes());

			try (RocksIterator it = db.newIterator()) {
				db.delete("b".getBytes());

				// When
				it.refresh();
				it.seek("b".getBytes());

				// Then — "b" is gone, seek lands past it (nothing left)
				assertThat(it.isValid()).isFalse();
			}
		}
	}

	@Test
	void refresh_alwaysInvalidatesIterator_requiresReseek(@TempDir Path dir) {
		// Given — matches Iterator::Refresh()'s documented contract (iterator_base.h):
		// the iterator is invalidated by the call itself, independent of whether the DB
		// changed or whether the previously-current key still exists.
		try (var db = RocksDB.open(dir)) {
			db.put("k".getBytes(), "v".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();
				assertThat(it.isValid()).isTrue();

				// When
				it.refresh();

				// Then
				assertThat(it.isValid()).isFalse();
				it.checkError();

				it.seekToFirst();
				assertThat(it.isValid()).isTrue();
				assertThat(it.key()).isEqualTo("k".getBytes());
			}
		}
	}
}
