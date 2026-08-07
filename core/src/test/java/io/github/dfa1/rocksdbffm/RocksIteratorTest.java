package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
				int keyLen = it.key(keyBuf);
				keyBuf.flip();
				byte[] keyBytes = new byte[keyBuf.remaining()];
				keyBuf.get(keyBytes);

				// When — ByteBuffer value
				ByteBuffer valBuf = ByteBuffer.allocateDirect(64);
				int valLen = it.value(valBuf);
				valBuf.flip();
				byte[] valBytes = new byte[valBuf.remaining()];
				valBuf.get(valBytes);

				// Then
				assertThat(keyLen).isEqualTo(5);
				assertThat(keyBytes).isEqualTo("hello".getBytes());
				assertThat(valLen).isEqualTo(5);
				assertThat(valBytes).isEqualTo("world".getBytes());
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
	// Segment lifetime
	//
	// rocksdb_iter_key/_value return a pointer into a buffer the iterator
	// reuses in place. The address is stable across moves, so a segment that
	// outlives its position would silently report the *new* position's bytes
	// rather than fail. These tests pin the invalidation that prevents that.
	// -----------------------------------------------------------------------

	@Test
	void keySegment_isInvalidated_byNext(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("aaaa".getBytes(), "1111".getBytes());
			db.put("bbbb".getBytes(), "2222".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();
				var firstKey = it.keySegment();
				assertThat(firstKey.toArray(ValueLayout.JAVA_BYTE)).isEqualTo("aaaa".getBytes());

				// When
				it.next();

				// Then
				assertThatThrownBy(() -> firstKey.toArray(ValueLayout.JAVA_BYTE))
						.as("must not silently report the new position's bytes")
						.isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	void valueSegment_isInvalidated_byNext(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("aaaa".getBytes(), "1111".getBytes());
			db.put("bbbb".getBytes(), "2222".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();
				var firstValue = it.valueSegment();

				// When
				it.next();

				// Then
				assertThatThrownBy(() -> firstValue.toArray(ValueLayout.JAVA_BYTE))
						.isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	void keySegment_isInvalidated_bySeek(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("aaaa".getBytes(), "1111".getBytes());
			db.put("bbbb".getBytes(), "2222".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();
				var firstKey = it.keySegment();

				// When
				it.seek("bbbb".getBytes());

				// Then
				assertThatThrownBy(() -> firstKey.toArray(ValueLayout.JAVA_BYTE))
						.isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	void keySegment_isInvalidated_byIteratorClose(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("aaaa".getBytes(), "1111".getBytes());

			MemorySegment escaped;
			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();
				escaped = it.keySegment();
			}

			// When / Then — the iterator's buffer is freed by rocksdb_iter_destroy,
			// so this must throw rather than read freed memory
			assertThatThrownBy(() -> escaped.toArray(ValueLayout.JAVA_BYTE))
					.isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void keySegment_collectedAcrossIteration_failsLoudly(@TempDir Path dir) {
		// Given — the natural-looking idiom that used to silently produce
		// N references all showing the last key
		try (var db = RocksDB.open(dir)) {
			db.put("aaaa".getBytes(), "1111".getBytes());
			db.put("bbbb".getBytes(), "2222".getBytes());
			db.put("cccc".getBytes(), "3333".getBytes());

			List<MemorySegment> collected = new ArrayList<>();
			try (RocksIterator it = db.newIterator()) {
				for (it.seekToFirst(); it.isValid(); it.next()) {
					collected.add(it.keySegment());
				}
			}

			// When / Then
			assertThat(collected).hasSize(3);
			assertThatThrownBy(() -> collected.getFirst().toArray(ValueLayout.JAVA_BYTE))
					.isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void keySegment_staysValid_whileIteratorDoesNotMove(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.open(dir)) {
			db.put("aaaa".getBytes(), "1111".getBytes());

			try (RocksIterator it = db.newIterator()) {
				it.seekToFirst();

				// When — non-positioning calls must not invalidate
				var key = it.keySegment();
				var value = it.valueSegment();
				boolean valid = it.isValid();

				// Then
				assertThat(valid).isTrue();
				assertThat(key.toArray(ValueLayout.JAVA_BYTE)).isEqualTo("aaaa".getBytes());
				assertThat(value.toArray(ValueLayout.JAVA_BYTE)).isEqualTo("1111".getBytes());
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
}
