package io.github.dfa1.rocksdbffm;

import java.util.Arrays;

/// A half-open key range `[startKey, endKey)`, used by
/// [MonitoringOperations#getApproximateSizes(java.util.List)] and its overloads to describe
/// which portions of the keyspace to estimate the size of.
///
/// No native resource is associated with this record.
///
/// @param startKey inclusive lower bound
/// @param endKey   exclusive upper bound
public record Range(byte[] startKey, byte[] endKey) {

	/// Creates a range.
	///
	/// @param startKey inclusive lower bound
	/// @param endKey   exclusive upper bound
	/// @return a new [Range]
	public static Range of(byte[] startKey, byte[] endKey) {
		return new Range(startKey, endKey);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Range other)) {
			return false;
		}
		return Arrays.equals(startKey, other.startKey) && Arrays.equals(endKey, other.endKey);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(startKey) + Arrays.hashCode(endKey);
	}

	@Override
	public String toString() {
		return "Range[startKey=" + Arrays.toString(startKey) + ", endKey=" + Arrays.toString(endKey) + "]";
	}
}
