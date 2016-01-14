package org.springframework.cloud.sleuth.sampler;

import org.junit.Test;

import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.Percentage.withPercentage;

public class TraceIdToThresholdComparableTests {

	final Long[] traceIds = createRandomUuids();

	@Test
	public void should_retain_10_percent() {
		float sampleRate = 0.1f;
		TraceIdToThresholdComparable sampler = new TraceIdToThresholdComparable(sampleRate);

		long passCount = Stream.of(traceIds).filter(uuid -> smallerThanThreshold(sampler, uuid)).count();

		then(passCount)
				.isCloseTo((long) (traceIds.length * sampleRate), withPercentage(3));
	}

	@Test
	public void should_be_idempotent() {
		TraceIdToThresholdComparable sampler1 = new TraceIdToThresholdComparable(0.1f);
		TraceIdToThresholdComparable sampler2 = new TraceIdToThresholdComparable(0.1f);

		then(Stream.of(traceIds).filter(uuid -> smallerThanThreshold(sampler1, uuid)).toArray())
				.containsExactly(Stream.of(traceIds).filter(uuid -> smallerThanThreshold(sampler2, uuid)).toArray());
	}

	private boolean smallerThanThreshold(TraceIdToThresholdComparable comparable, Long traceId) {
		return comparable.compareTo(traceId) == -1;
	}

	private Long[] createRandomUuids() {
		Long[] traceIds;
		traceIds = new Long[100000];
		Random random = new Random();
		for (int i = 0; i < traceIds.length; i++) {
			traceIds[i] = random.nextLong();
		}
		return traceIds;
	}

}