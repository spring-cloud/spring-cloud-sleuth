package org.springframework.cloud.sleuth.sampler;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Given the absolute value of a random 128 bit trace id, we expect inputs to be balanced across
 * 0-MAX. Threshold is the range of inputs between 0-MAX that we retain.
 *
 * Can be compared with a UUID TraceId to see its relation to the provided threshold.
 */
final class UuidTraceIdToThresholdComparable implements Comparable<String> {

	/**
	 * We're shifting the first 64 bits by 8 bytes (64 bits) to the left cause these are
	 * the most significant bits. Then we're adding -1 (1111...1111). That's how we're getting
	 * 1111....1111 (128 times '1')
	 */
	static final BigInteger MAX_128 =
			BigInteger.valueOf(Long.MAX_VALUE).shiftLeft(8).add(BigInteger.valueOf(-1L));
	static final int EIGHT_BYTES = 8;

	private final BigInteger threshold;

	UuidTraceIdToThresholdComparable(float rate) {
		threshold = MAX_128
				.multiply(BigInteger.valueOf((int) (rate * 100))) // drops fractional percentage.
				.divide(BigInteger.valueOf(100));
	}

	/**
	 * Compares the given Trace Id to the provided threshold
	 * @param traceId - the UUID version of Trace Id
	 */
	@Override
	public int compareTo(String traceId) {
		UUID uuid = UUID.fromString(traceId);
		BigInteger asInteger = BigInteger.valueOf(uuid.getMostSignificantBits())
						.shiftLeft(EIGHT_BYTES)
						.add(BigInteger.valueOf(uuid.getLeastSignificantBits()))
						.abs();
		return asInteger.compareTo(threshold);
	}
}