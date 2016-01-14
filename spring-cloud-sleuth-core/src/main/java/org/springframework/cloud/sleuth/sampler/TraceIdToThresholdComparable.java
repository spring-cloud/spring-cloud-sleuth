package org.springframework.cloud.sleuth.sampler;

/**
 * Given the absolute value of a random 64 bit trace id, we expect inputs to be balanced across
 * 0-MAX. Threshold is the range of inputs between 0-MAX that we retain.
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 */
final class TraceIdToThresholdComparable implements Comparable<Long> {

	private static final int GREATER_THAN_THRESHOLD = 1;

	private final Long threshold;

	TraceIdToThresholdComparable(float rate) {
		threshold = Math.abs(Long.MAX_VALUE * (int) (rate * 100)); // drops fractional percentage.
	}

	/**
	 * Compares the given Trace Id to the provided threshold
	 * @param traceId - 64bit traceId
	 */
	@Override
	public int compareTo(Long traceId) {
		if (traceId == null) {
			return GREATER_THAN_THRESHOLD;
		}
		Long mod = Math.abs(traceId % 100);
		return mod.compareTo(threshold);
	}

}