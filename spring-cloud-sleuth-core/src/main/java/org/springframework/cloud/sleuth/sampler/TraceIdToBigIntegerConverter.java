package org.springframework.cloud.sleuth.sampler;

import java.math.BigInteger;
import java.util.UUID;

class TraceIdToBigIntegerConverter {

	/**
	 * Takes two sets of bits (most and least significant ones), puts them together
	 * and represents as a BigInteger
	 *
	 * @param traceIdToConvert
	 * @return - BigInteger representation of the TraceId
	 */
	static BigInteger traceIdToBigInt(String traceIdToConvert) {
		UUID uuid = UUID.fromString(traceIdToConvert);
		long leastSignificantBits = uuid.getLeastSignificantBits();
		long mostSignificantBits = uuid.getMostSignificantBits();
		BigInteger leastBigInt = BigInteger.valueOf(leastSignificantBits);
		BigInteger mostBigInt = BigInteger.valueOf(mostSignificantBits).shiftLeft(63);
		return mostBigInt.add(leastBigInt);
	}

}
