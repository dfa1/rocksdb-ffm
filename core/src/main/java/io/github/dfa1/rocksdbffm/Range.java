package io.github.dfa1.rocksdbffm;

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
}
