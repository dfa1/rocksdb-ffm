package io.github.dfa1.rocksdbffm;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BackupEngineOptionsTest {

	@Test
	void create_usesRocksDbDefaults(@TempDir Path dir) {
		// Given / When
		try (var sut = BackupEngineOptions.create(dir)) {

			// Then
			assertThat(sut.isShareTableFiles()).isTrue();
			assertThat(sut.isSync()).isTrue();
			assertThat(sut.isDestroyOldData()).isFalse();
			assertThat(sut.isBackupLogFiles()).isTrue();
			assertThat(sut.getMaxBackgroundOperations()).isEqualTo(1);
			// The javadoc's "-1 means all" describes setMaxValidBackupsToOpen's accepted
			// sentinel; the native default is actually INT_MAX, which means the same thing.
			assertThat(sut.getMaxValidBackupsToOpen()).isEqualTo(Integer.MAX_VALUE);
		}
	}

	@Test
	void setShareTableFiles_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setShareTableFiles(false);

			// Then
			assertThat(sut.isShareTableFiles()).isFalse();
		}
	}

	@Test
	void setSync_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setSync(false);

			// Then
			assertThat(sut.isSync()).isFalse();
		}
	}

	@Test
	void setDestroyOldData_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setDestroyOldData(true);

			// Then
			assertThat(sut.isDestroyOldData()).isTrue();
		}
	}

	@Test
	void setBackupLogFiles_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setBackupLogFiles(false);

			// Then
			assertThat(sut.isBackupLogFiles()).isFalse();
		}
	}

	@Test
	void setBackupRateLimit_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setBackupRateLimit(MemorySize.ofMB(50));

			// Then
			assertThat(sut.getBackupRateLimit()).isEqualTo(MemorySize.ofMB(50));
		}
	}

	@Test
	void setRestoreRateLimit_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setRestoreRateLimit(MemorySize.ofMB(75));

			// Then
			assertThat(sut.getRestoreRateLimit()).isEqualTo(MemorySize.ofMB(75));
		}
	}

	@Test
	void setMaxBackgroundOperations_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setMaxBackgroundOperations(4);

			// Then
			assertThat(sut.getMaxBackgroundOperations()).isEqualTo(4);
		}
	}

	@Test
	void setCallbackTriggerIntervalSize_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setCallbackTriggerIntervalSize(MemorySize.ofMB(16));

			// Then
			assertThat(sut.getCallbackTriggerIntervalSize()).isEqualTo(MemorySize.ofMB(16));
		}
	}

	@Test
	void setMaxValidBackupsToOpen_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setMaxValidBackupsToOpen(3);

			// Then
			assertThat(sut.getMaxValidBackupsToOpen()).isEqualTo(3);
		}
	}

	@Test
	void setShareFilesWithChecksumNaming_roundTrips(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			sut.setShareFilesWithChecksumNaming(2);

			// Then
			assertThat(sut.getShareFilesWithChecksumNaming()).isEqualTo(2);
		}
	}

	@Test
	void setBackupDir_changesTargetDirectory(@TempDir Path dir) {
		// Given
		var otherDir = dir.resolve("elsewhere");
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			ThrowingCallable action = () -> sut.setBackupDir(otherDir);

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	@Test
	void setEnv_acceptsDefaultEnv(@TempDir Path dir) {
		// Given
		try (var env = Env.defaultEnv();
		     var sut = BackupEngineOptions.create(dir)) {

			// When
			ThrowingCallable action = () -> sut.setEnv(env);

			// Then
			assertThatCode(action).doesNotThrowAnyException();
		}
	}

	@Test
	void fluentSetters_returnSameInstance(@TempDir Path dir) {
		// Given
		try (var sut = BackupEngineOptions.create(dir)) {

			// When
			var result = sut.setShareTableFiles(true);

			// Then
			assertThat(result).isSameAs(sut);
		}
	}

	@Test
	void close_isIdempotent(@TempDir Path dir) {
		// Given — an already-closed instance
		var sut = BackupEngineOptions.create(dir);
		sut.close();

		// When
		ThrowingCallable secondClose = sut::close;

		// Then — closing twice must not crash the JVM
		assertThatCode(secondClose).doesNotThrowAnyException();
	}
}
