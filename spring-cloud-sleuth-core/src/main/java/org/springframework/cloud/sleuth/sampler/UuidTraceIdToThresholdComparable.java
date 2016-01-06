package org.springframework.cloud.sleuth.sampler;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;

/**
 * Given the absolute value of a random 128 bit trace id, we expect inputs to be balanced across
 * 0-MAX. Threshold is the range of inputs between 0-MAX that we retain.
 *
 * This decodes a trace id in UUID format into a 128 bit number, then compares it against a threshold.
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 */
final class UuidTraceIdToThresholdComparable implements Comparable<String> {

	private static final int SIXTY_FOUR_BITS = 64;
	private static final int GREATER_THAN_THRESHOLD = 1;

	/**
	 * 0111....1111 ('0' - for the sign and then 127 times '1')
	 */
	static final BigInteger MAX_128 = max_128signed();

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
						.shiftLeft(SIXTY_FOUR_BITS)
						.add(BigInteger.valueOf(uuid.getLeastSignificantBits()))
						.abs();
		return asInteger.compareTo(threshold);
	}

	/**
	 * The Long.MAX_VALUE in binary 0 followed by 63 1s.
	 *
	 * We simulate a 128bit long, by doing the same, except following by 127 1s
	 */
	static BigInteger max_128signed() {
		byte[] max_128signed = new byte[16];
		Arrays.fill(max_128signed, (byte) -1); // initialize to 11111111
		max_128signed[0] = (byte) 127; // reset MSBs to 01111111
		return new BigInteger(max_128signed);
	}
}