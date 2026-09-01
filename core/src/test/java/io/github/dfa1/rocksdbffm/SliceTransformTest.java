package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SliceTransformTest {

	@Test
	void newFixedPrefix_negativePrefixLen_throws() {
		// Given / When / Then
		assertThatThrownBy(() -> SliceTransform.newFixedPrefix(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void newFixedPrefix_closesWithoutOwnershipTransfer() {
		// Given
		var sut = SliceTransform.newFixedPrefix(4);

		// When
		sut.close();

		// Then — tryClose (native destroy) actually ran; ptr() now reports closed
		assertThatThrownBy(sut::ptr).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void close_isIdempotent() {
		// Given
		var sut = SliceTransform.newFixedPrefix(4);
		sut.close();

		// When — NativeObject.close() swaps its pointer reference to NULL atomically before
		// calling tryClose, so a second close() is guaranteed to skip tryClose entirely rather
		// than double-free; this only re-confirms close() itself never throws, since
		// NativeObject.close() always catches and logs rather than rethrowing
		ThrowingCallable action = sut::close;

		// Then
		assertThatCode(action).doesNotThrowAnyException();
	}

	@Test
	void setPrefixExtractor_transferredTransform_closeIsSafeNoOp() {
		// Given
		var transform = SliceTransform.newFixedPrefix(4);
		try (var opts = Options.newOptions().setPrefixExtractor(transform)) {

			// When — ownership already transferred to opts; transform.ptr() is NULL, so
			// transform.close() finds nothing to free (same guarantee as close_isIdempotent
			// above: this confirms close() doesn't throw, not that no native call was made)
			ThrowingCallable action = transform::close;

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	@Test
	void setPrefixExtractor_transferredTransform_ptrThrows() {
		// Given
		var transform = SliceTransform.newFixedPrefix(4);

		// When
		try (var opts = Options.newOptions().setPrefixExtractor(transform)) {

			// Then
			assertThatThrownBy(transform::ptr).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void setPrefixExtractor_closedImmediately_isANoOpAndDbStillWorks(@TempDir Path dir) {
		// Given — setPrefixExtractor transfers ownership to Options, so closing the
		// SliceTransform wrapper right away (before it's even used to open a DB) must be a
		// no-op rather than freeing the pointer RocksDB's own copy now owns
		var transform = SliceTransform.newFixedPrefix(3);
		try (var opts = Options.newOptions().setCreateIfMissing(true).setPrefixExtractor(transform)) {
			transform.close();

			// When
			try (var db = RocksDB.openReadWrite(opts, dir)) {
				db.put("pre-key1".getBytes(), "value1".getBytes());
				var hit = db.get("pre-key1".getBytes());

				// Then
				assertThat(hit).isEqualTo("value1".getBytes());
			}
		}
	}

	@Test
	void prefixBloomSeek_withAutoPrefixMode_findsOnlyMatchingPrefix(@TempDir Path dir) {
		// Given — the prefix-bloom pattern for Seek()-heavy workloads: a prefix extractor +
		// prefix-only filtering (wholeKeyFiltering off) + a bloom filter, so Seek() can skip
		// SST files whose prefix bloom proves the target isn't there; ReadOptions.autoPrefixMode
		// lets RocksDB pick prefix-seek mode on its own instead of the caller reasoning about it
		try (var filter = FilterPolicy.newBloom(10);
		     var transform = SliceTransform.newFixedPrefix(4);
		     var tbl = BlockBasedTableOptions.newBlockBasedConfig()
				     .setWholeKeyFiltering(false)
				     .setFilterPolicy(filter);
		     var opts = Options.newOptions()
				     .setCreateIfMissing(true)
				     .setPrefixExtractor(transform)
				     .setTableFormatConfig(tbl);
		     var db = RocksDB.openReadWrite(opts, dir)) {
			db.put("aaaa-1".getBytes(), "v1".getBytes());
			db.put("aaaa-2".getBytes(), "v2".getBytes());
			db.put("bbbb-1".getBytes(), "v3".getBytes());

			try (var ro = ReadOptions.newReadOptions().setAutoPrefixMode(true).setPrefixSameAsStart(true);
			     var it = db.newIterator(ro)) {
				// When
				it.seek("aaaa-1".getBytes());
				List<String> keysInPrefix = new ArrayList<>();
				for (; it.isValid(); it.next()) {
					keysInPrefix.add(new String(it.key()));
				}

				// Then — only the "aaaa" prefix is returned; "bbbb-1" is a different prefix
				assertThat(keysInPrefix).containsExactly("aaaa-1", "aaaa-2");
			}
		}
	}
}
