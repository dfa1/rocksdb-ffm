package io.github.dfa1.rocksdbffm.benchmark;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/// A genuinely reused [Arena] for the "arena reuse" benchmark rows: a fixed native block
/// handed out by bumping an offset, rewound by [#reset()] before every call.
///
/// This is the pattern the [Arena] javadoc itself shows for custom arenas, and what a caller
/// passing their own arena to `put(Arena, ...)` on a hot path would actually do. The rows
/// previously used a `static final Arena.ofAuto()`, which never frees anything: every put
/// malloc'd 2-3 fresh blocks that lived until the JVM exited, so they measured "malloc
/// without free" (and an ever-growing native heap), not reuse.
///
/// Memory is not zeroed between uses, same as `SegmentAllocator.slicingAllocator`; every
/// allocation the put paths make is either fully overwritten (key/value copies) or explicitly
/// initialized (`NativeCalls.errHolder` writes `NULL` itself).
final class ResettableArena implements Arena {

	private final Arena backing;
	private final MemorySegment block;
	private long offset;

	ResettableArena(long capacity) {
		backing = Arena.ofConfined();
		block = backing.allocate(capacity, 16);
	}

	/// Rewinds to the start of the block, logically freeing everything handed out so far.
	void reset() {
		offset = 0;
	}

	@Override
	public MemorySegment allocate(long byteSize, long byteAlignment) {
		long start = (offset + byteAlignment - 1) & -byteAlignment;
		long end = start + byteSize;
		if (end > block.byteSize()) {
			throw new IllegalStateException("ResettableArena exhausted: missing reset()?");
		}
		offset = end;
		return block.asSlice(start, byteSize, byteAlignment);
	}

	@Override
	public MemorySegment.Scope scope() {
		return backing.scope();
	}

	@Override
	public void close() {
		backing.close();
	}
}
