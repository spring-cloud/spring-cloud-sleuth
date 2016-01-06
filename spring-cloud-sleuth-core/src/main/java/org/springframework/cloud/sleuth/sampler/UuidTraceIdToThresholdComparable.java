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

	private static final int EIGHT_BYTES = 64;
	private static final int GREATER_THAN_THRESHOLD = 1;
	/**
	 * We're shifting the first 64 bits by 8 bytes (64 bits) to the left cause these are
	 * the most significant bits. Then we're adding -1 (1111...1111). That's how we're getting
	 * 1111....1111 (128 times '1')
	 */
	private static final BigInteger MAX_128 =
			BigInteger.valueOf(Long.MAX_VALUE).shiftLeft(EIGHT_BYTES).add(BigInteger.valueOf(-1L));

	private final BigInteger threshold;
	private final StringToUuidConverter stringToUuidConverter;

	UuidTraceIdToThresholdComparable(float rate, StringToUuidConverter converter) {
		threshold = MAX_128
				.multiply(BigInteger.valueOf((int) (rate * 100))) // drops fractional percentage.
				.divide(BigInteger.valueOf(100));
		stringToUuidConverter = converter;
	}

	UuidTraceIdToThresholdComparable(float rate) {
		this(rate, new DefaultStringToUuidConverter());
	}

	/**
	 * Compares the given Trace Id to the provided threshold
	 * @param traceId - the UUID version of Trace Id
	 */
	@Override
	public int compareTo(String traceId) {
		UUID uuid = stringToUuidConverter.convert(traceId);
		if (uuid == null) {
			return GREATER_THAN_THRESHOLD;
		}
		BigInteger asInteger = BigInteger.valueOf(uuid.getMostSignificantBits())
						.shiftLeft(EIGHT_BYTES)
						.add(BigInteger.valueOf(uuid.getLeastSignificantBits()))
						.abs();
		return asInteger.compareTo(threshold);
	}
}