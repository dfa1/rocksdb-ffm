package io.github.dfa1.rocksdbffm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterPolicyTest {

	@Test
	void newBloom_closesWithoutOwnershipTransfer() {
		// Given
		var sut = FilterPolicy.newBloom(10);

		// When
		sut.close();

		// Then — tryClose (native destroy) actually ran; ptr() now reports closed
		assertThatThrownBy(sut::ptr).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void newRibbon_closesWithoutOwnershipTransfer() {
		// Given
		var sut = FilterPolicy.newRibbon(10);

		// When
		sut.close();

		// Then — tryClose (native destroy) actually ran; ptr() now reports closed
		assertThatThrownBy(sut::ptr).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void close_isIdempotent() {
		// Given
		var sut = FilterPolicy.newBloom(10);
		sut.close();

		// When / Then — closing an already-closed policy must not double-free
		assertThatCode(sut::close).doesNotThrowAnyException();
	}

	@Test
	void setFilterPolicy_transferredPolicy_closeIsSafeNoOp() {
		// Given
		var filter = FilterPolicy.newBloom(10);
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()) {
			tbl.setFilterPolicy(filter);

			// When / Then — ownership already transferred to tbl; closing filter
			// here must not double-free the native filter policy
			assertThatCode(filter::close).doesNotThrowAnyException();
		}
	}

	@Test
	void setFilterPolicy_transferredPolicy_ptrThrows() {
		// Given
		var filter = FilterPolicy.newBloom(10);

		// When
		try (var tbl = BlockBasedTableOptions.newBlockBasedConfig()) {
			tbl.setFilterPolicy(filter);

			// Then
			assertThatThrownBy(filter::ptr).isInstanceOf(IllegalStateException.class);
		}
	}
}
