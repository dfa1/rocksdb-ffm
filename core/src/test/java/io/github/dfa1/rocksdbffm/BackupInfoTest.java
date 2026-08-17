package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BackupInfoTest {

	@Test
	void components_areExposedExactly() {
		// Given
		var backupId = BackupId.of(3);
		var size = MemorySize.ofMB(5);
		var timestamp = Instant.ofEpochSecond(1_700_000_000L);

		// When
		var sut = new BackupInfo(backupId, timestamp, size, 12L);

		// Then
		assertThat(sut.backupId()).isEqualTo(backupId);
		assertThat(sut.timestamp()).isEqualTo(timestamp);
		assertThat(sut.size()).isEqualTo(size);
		assertThat(sut.numberOfFiles()).isEqualTo(12L);
	}

	@Test
	void equals_isByValue() {
		// Given
		var timestamp = Instant.ofEpochSecond(100L);
		var a = new BackupInfo(BackupId.of(1), timestamp, MemorySize.ofBytes(50), 2L);
		var b = new BackupInfo(BackupId.of(1), timestamp, MemorySize.ofBytes(50), 2L);
		var c = new BackupInfo(BackupId.of(2), timestamp, MemorySize.ofBytes(50), 2L);

		// When / Then
		assertThat(a).isEqualTo(b);
		assertThat(a).isNotEqualTo(c);
	}
}
