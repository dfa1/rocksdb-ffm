package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackgroundErrorReasonTest {

	@Test
	void fromValue_decodesEveryKnownValue() {
		// Given / When / Then
		assertThat(BackgroundErrorReason.fromValue(0)).isEqualTo(BackgroundErrorReason.FLUSH);
		assertThat(BackgroundErrorReason.fromValue(1)).isEqualTo(BackgroundErrorReason.COMPACTION);
		assertThat(BackgroundErrorReason.fromValue(2)).isEqualTo(BackgroundErrorReason.WRITE_CALLBACK);
		assertThat(BackgroundErrorReason.fromValue(3)).isEqualTo(BackgroundErrorReason.MEMTABLE);
		assertThat(BackgroundErrorReason.fromValue(4)).isEqualTo(BackgroundErrorReason.MANIFEST_WRITE);
		assertThat(BackgroundErrorReason.fromValue(5)).isEqualTo(BackgroundErrorReason.FLUSH_NO_WAL);
		assertThat(BackgroundErrorReason.fromValue(6)).isEqualTo(BackgroundErrorReason.MANIFEST_WRITE_NO_WAL);
		assertThat(BackgroundErrorReason.fromValue(7)).isEqualTo(BackgroundErrorReason.ASYNC_FILE_OPEN);
	}

	@Test
	void fromValue_unknownValue_throws() {
		// Given / When / Then
		assertThatThrownBy(() -> BackgroundErrorReason.fromValue(8)).isInstanceOf(IllegalArgumentException.class);
	}
}
