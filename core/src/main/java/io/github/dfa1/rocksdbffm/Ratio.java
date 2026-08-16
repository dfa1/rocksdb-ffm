package io.github.dfa1.rocksdbffm;

/// Immutable value object representing a fraction in the closed range `[0.0, 1.0]`.
///
/// Several RocksDB options are ratios/thresholds expressed as a raw `double` in the C API
/// (e.g. blob GC age cutoff, trash-to-total-size ratio). Wrapping them in [Ratio] keeps the
/// valid range structural rather than documented-and-hoped-for:
///
/// ```
/// sstFileManager.setMaxTrashDbRatio(Ratio.of(0.25));
/// options.setBlobGcForceThreshold(Ratio.ONE); // 1.0 == disabled
/// ```
///
/// The constructor rejects values outside `[0.0, 1.0]` (including `NaN`) at construction
/// time — an invalid [Ratio] cannot be created and therefore cannot be passed anywhere.
public final class Ratio implements Comparable<Ratio> {

	/// Convenience constant representing `0.0`.
	public static final Ratio ZERO = new Ratio(0.0);

	/// Convenience constant representing `1.0`.
	public static final Ratio ONE = new Ratio(1.0);

	private final double value;

	private Ratio(double value) {
		if (!(value >= 0.0 && value <= 1.0)) {
			throw new IllegalArgumentException("Ratio must be in [0.0, 1.0]: " + value);
		}
		this.value = value;
	}

	/// Creates a [Ratio] from a Java `double`.
	///
	/// @param value the ratio value, must be in `[0.0, 1.0]`
	/// @return a new [Ratio] wrapping `value`
	/// @throws IllegalArgumentException if `value` is outside `[0.0, 1.0]` or is `NaN`
	public static Ratio of(double value) {
		return new Ratio(value);
	}

	/// Returns the raw value, for passing to native calls.
	///
	/// @return the ratio as a `double` in `[0.0, 1.0]`
	public double toDouble() {
		return value;
	}

	@Override
	public int compareTo(Ratio other) {
		return Double.compare(this.value, other.value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Ratio other && Double.compare(this.value, other.value) == 0;
	}

	@Override
	public int hashCode() {
		return Double.hashCode(value);
	}

	@Override
	public String toString() {
		return "Ratio(" + value + ")";
	}
}
