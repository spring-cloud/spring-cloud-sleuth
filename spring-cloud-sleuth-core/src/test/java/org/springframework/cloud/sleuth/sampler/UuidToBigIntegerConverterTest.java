package org.springframework.cloud.sleuth.sampler;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.Data;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.util.UUID;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;
import static junitparams.JUnitParamsRunner.$;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.cloud.sleuth.sampler.UuidToBigIntegerConverterTest.TestData.sample;

@RunWith(JUnitParamsRunner.class)
public class UuidToBigIntegerConverterTest {

	private static final BigInteger TWO = new BigInteger("2");

	@Test
	@Parameters
	public void should_convert_uuid_to_big_integer(TestData testData) throws Exception {
		UUID uuid = new UUID(testData.mostSignificantBits, testData.leastSignificantBits);

		BigInteger bigInteger = UuidToBigIntegerConverter.uuidToBigInt(uuid);

		then(bigInteger).isEqualByComparingTo(testData.expectedInteger);
	}

	public Object[] parametersForShould_convert_uuid_to_big_integer() {
		return $(
				sample(Long.MAX_VALUE, Long.MAX_VALUE, TWO.pow(126).subtract(ONE)),
				sample(0L, 0L, ZERO),
				sample(0L, 1L, ONE)
		);
	}

	@Data
	static class TestData {
		final long mostSignificantBits;
		final long leastSignificantBits;
		final BigInteger expectedInteger;

		static TestData sample(long mostSignificantBits, long leastSignificantBits, BigInteger expectedInteger) {
			return new TestData(mostSignificantBits, leastSignificantBits, expectedInteger);
		}
	}

}