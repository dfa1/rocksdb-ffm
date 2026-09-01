package io.github.dfa1.rocksdbffm;

import java.util.EnumSet;
import java.util.Set;

/// Operation-type flags for [TraceOptions#setFilter(Set)].
///
/// Each flag means "exclude this operation type from the trace" -- RocksDB's own C++ naming
/// (`kTraceFilterGet`, ...) is easy to misread as the opposite: setting [#GET] does not mean
/// "trace gets," it means "do not trace gets." An empty set (the default) traces every
/// operation type. Filtering happens before [TraceOptions#setSamplingFrequency(long)] sampling.
public enum TraceFilter {

	/// Excludes `Get()` operations from the trace.
	GET(0x1L),
	/// Excludes write operations (`Put`/`Delete`/`Merge`/`Write`) from the trace.
	WRITE(0x1L << 1),
	/// Excludes `Iterator::Seek()` operations from the trace.
	ITERATOR_SEEK(0x1L << 2),
	/// Excludes `Iterator::SeekForPrev()` operations from the trace.
	ITERATOR_SEEK_FOR_PREV(0x1L << 3),
	/// Excludes `MultiGet()` operations from the trace.
	MULTI_GET(0x1L << 4);

	private final long bit;

	TraceFilter(long bit) {
		this.bit = bit;
	}

	/// Combines a set of filters into the raw bitmask `rocksdb_trace_options_set_filter` expects.
	///
	/// @param filters filters to combine; an empty set means "trace every operation type"
	/// @return the OR'd bitmask
	static long toMask(Set<TraceFilter> filters) {
		long mask = 0;
		for (TraceFilter f : filters) {
			mask |= f.bit;
		}
		return mask;
	}

	/// Decodes a raw bitmask back into the set of filters it enables.
	///
	/// @param mask raw bitmask as returned by `rocksdb_trace_options_get_filter`
	/// @return the set of filters `mask` encodes
	static Set<TraceFilter> fromMask(long mask) {
		EnumSet<TraceFilter> result = EnumSet.noneOf(TraceFilter.class);
		for (TraceFilter f : values()) {
			if ((mask & f.bit) != 0) {
				result.add(f);
			}
		}
		return result;
	}
}
