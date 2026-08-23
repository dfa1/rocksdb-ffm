package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SstPartitionerFactoryTest {

	@Test
	void newFixedPrefix_closesCleanly() {
		// Given
		var sut = SstPartitionerFactory.newFixedPrefix(4);

		// When
		sut.close();

		// Then
		assertThatThrownBy(sut::ptr).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void close_isIdempotent() {
		// Given
		var sut = SstPartitionerFactory.newFixedPrefix(4);
		sut.close();

		// When / Then
		assertThatCode(sut::close).doesNotThrowAnyException();
	}

	@Test
	void setSstPartitionerFactory_sharedOwnership_bothCloseIndependently() {
		// Given
		var factory = SstPartitionerFactory.newFixedPrefix(4);

		// When
		try (var _ = Options.newOptions().setSstPartitionerFactory(factory)) {

			// Then — no ownership transfer: factory.ptr() stays valid while opts is still open
			assertThatCode(factory::ptr).doesNotThrowAnyException();
		}

		// Then — and closing factory afterward is safe too
		assertThatCode(factory::close).doesNotThrowAnyException();
	}
}
