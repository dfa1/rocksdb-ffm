package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveFileStorageInfoTest {

	// -----------------------------------------------------------------------
	// LiveFilesStorageInfoOptions round-trip
	// -----------------------------------------------------------------------

	@Test
	void options_defaults() {
		// Given / When
		try (var opts = LiveFilesStorageInfoOptions.create()) {

			// Then
			assertThat(opts.isIncludeChecksumInfo()).isFalse();
			assertThat(opts.getWalSizeForFlush()).isEqualTo(MemorySize.ZERO);
			assertThat(opts.isAtomicFlush()).isFalse();
		}
	}

	@Test
	void options_setters_roundTrip() {
		// Given
		try (var opts = LiveFilesStorageInfoOptions.create()) {

			// When
			opts.setIncludeChecksumInfo(true);
			opts.setWalSizeForFlush(MemorySize.ofMB(4));
			opts.setAtomicFlush(true);

			// Then
			assertThat(opts.isIncludeChecksumInfo()).isTrue();
			assertThat(opts.getWalSizeForFlush()).isEqualTo(MemorySize.ofMB(4));
			assertThat(opts.isAtomicFlush()).isTrue();
		}
	}

	// -----------------------------------------------------------------------
	// getLiveFilesStorageInfo()
	// -----------------------------------------------------------------------

	@Test
	void getLiveFilesStorageInfo_afterFlush_includesCoreFileTypes(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("a".getBytes(), "1".getBytes());
			db.flush(fo);

			// When
			List<FileType> fileTypes;
			try (var info = db.getLiveFilesStorageInfo()) {
				fileTypes = new ArrayList<>();
				for (LiveFileStorageInfo file : info) {
					fileTypes.add(file.fileType());
				}
			}

			// Then — every file needed to reconstruct the DB, not just SST
			assertThat(fileTypes).contains(FileType.TABLE_FILE, FileType.DESCRIPTOR_FILE,
					FileType.CURRENT_FILE, FileType.OPTIONS_FILE, FileType.WAL_FILE);
		}
	}

	@Test
	void getLiveFilesStorageInfo_tableFileEntry_hasExpectedMetadata(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("a".getBytes(), "1".getBytes());
			db.flush(fo);

			// When
			try (var info = db.getLiveFilesStorageInfo()) {
				LiveFileStorageInfo tableFile = findFirst(info, FileType.TABLE_FILE);

				// Then
				assertThat(tableFile.relativeFilename()).endsWith(".sst");
				assertThat(tableFile.directory()).isEqualTo(dir);
				assertThat(tableFile.fileNumber()).isPositive();
				assertThat(tableFile.size().toBytes()).isPositive();
				assertThat(tableFile.temperature()).isEqualTo(Temperature.UNKNOWN);
				assertThat(tableFile.fileChecksum()).isEmpty();
				assertThat(tableFile.replacementContents()).isEmpty();
			}
		}
	}

	@Test
	void getLiveFilesStorageInfo_currentFileEntry_hasNoFileNumberButHasReplacementContents(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("a".getBytes(), "1".getBytes());
			db.flush(fo);

			// When
			try (var info = db.getLiveFilesStorageInfo()) {
				LiveFileStorageInfo currentFile = findFirst(info, FileType.CURRENT_FILE);

				// Then — CURRENT has no file number, and its "true" contents at the moment of
				// this snapshot are captured directly rather than read back off disk later
				assertThat(currentFile.relativeFilename()).isEqualTo("CURRENT");
				assertThat(currentFile.fileNumber()).isZero();
				assertThat(currentFile.replacementContents()).isNotEmpty()
						.hasSize((int) currentFile.size().toBytes());
			}
		}
	}

	@Test
	void getLiveFilesStorageInfo_withChecksumInfo_setsChecksumFuncNameEvenWithoutChecksum(@TempDir Path dir) {
		// Given — no checksum function configured on the DB, so requesting checksum info
		// still leaves the checksum itself empty, but the func name reports "Unknown" rather
		// than being left unset
		try (var db = RocksDB.openReadWrite(dir);
		     var fo = FlushOptions.newFlushOptions()) {
			db.put("a".getBytes(), "1".getBytes());
			db.flush(fo);

			// When
			try (var opts = LiveFilesStorageInfoOptions.create().setIncludeChecksumInfo(true);
			     var info = db.getLiveFilesStorageInfo(opts)) {
				LiveFileStorageInfo tableFile = findFirst(info, FileType.TABLE_FILE);

				// Then
				assertThat(tableFile.fileChecksumFuncName()).isEqualTo("Unknown");
			}
		}
	}

	// -----------------------------------------------------------------------
	// LiveFilesStorageInfo.get() / iterator() / close()
	// -----------------------------------------------------------------------

	@Test
	void get_negativeIndex_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var info = db.getLiveFilesStorageInfo()) {

			// When / Then
			assertThatThrownBy(() -> info.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
		}
	}

	@Test
	void get_indexEqualToSize_throws(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var info = db.getLiveFilesStorageInfo()) {
			int size = info.size();

			// When / Then
			assertThatThrownBy(() -> info.get(size)).isInstanceOf(IndexOutOfBoundsException.class);
		}
	}

	@Test
	void iterator_exhausted_throwsNoSuchElement(@TempDir Path dir) {
		// Given
		try (var db = RocksDB.openReadWrite(dir);
		     var info = db.getLiveFilesStorageInfo()) {
			var it = info.iterator();
			while (it.hasNext()) {
				it.next();
			}

			// When / Then
			assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
		}
	}

	@Test
	void liveFileStorageInfo_afterOwnerClosed_throws(@TempDir Path dir) {
		// Given
		LiveFileStorageInfo file;
		try (var db = RocksDB.openReadWrite(dir)) {
			try (var info = db.getLiveFilesStorageInfo()) {
				file = info.get(0);
			}

			// When / Then
			assertThatThrownBy(file::relativeFilename).isInstanceOf(IllegalStateException.class);
		}
	}

	private static LiveFileStorageInfo findFirst(LiveFilesStorageInfo info, FileType type) {
		for (LiveFileStorageInfo file : info) {
			if (file.fileType() == type) {
				return file;
			}
		}
		throw new AssertionError("no " + type + " entry found");
	}
}
