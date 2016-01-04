package org.springframework.cloud.sleuth.sampler;

import java.math.BigInteger;
import java.util.UUID;

class UuidToBigIntegerConverter {

	/**
	 * Takes two sets of bits (most and least significant ones), puts them together
	 * and represents as a BigInteger
	 *
	 * @param uuid - UUID to convert
	 * @return - BigInteger representation of the UUID
	 */
	static BigInteger uuidToBigInt(UUID uuid) {
		long leastSignificantBits = uuid.getLeastSignificantBits();
		long mostSignificantBits = uuid.getMostSignificantBits();
		BigInteger leastBigInt = BigInteger.valueOf(leastSignificantBits);
		BigInteger mostBigInt = BigInteger.valueOf(mostSignificantBits).shiftLeft(63);
		return mostBigInt.add(leastBigInt);
	}

}
