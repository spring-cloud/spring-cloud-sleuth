package org.springframework.cloud.sleuth.sampler;

import org.junit.Test;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.Percentage.withPercentage;

public class UuidTraceIdToThresholdComparableTest {

	final UUID[] traceIds = createRandomUuids();

	@Test
	public void should_retain_10_percent() {
		float sampleRate = 0.1f;
		UuidTraceIdToThresholdComparable sampler = new UuidTraceIdToThresholdComparable(sampleRate);

		long passCount = Stream.of(traceIds).filter(uuid -> smallerThanThreshold(sampler, uuid)).count();

		then(passCount)
				.isCloseTo((long) (traceIds.length * sampleRate), withPercentage(3));
	}

	@Test
	public void should_be_idempotent() {
		UuidTraceIdToThresholdComparable sampler1 = new UuidTraceIdToThresholdComparable(0.1f);
		UuidTraceIdToThresholdComparable sampler2 = new UuidTraceIdToThresholdComparable(0.1f);

		then(Stream.of(traceIds).filter(uuid -> smallerThanThreshold(sampler1, uuid)).toArray())
				.containsExactly(Stream.of(traceIds).filter(uuid -> smallerThanThreshold(sampler2, uuid)).toArray());
	}

	@Test
	public void should_have_127_bit_number_as_max_long() {
		then(UuidTraceIdToThresholdComparable.MAX_128.toString(2)).matches("^1+$").hasSize(127);
	}

	private boolean smallerThanThreshold(UuidTraceIdToThresholdComparable comparable, UUID traceId) {
		return comparable.compareTo(traceId.toString()) == -1;
	}

	private UUID[] createRandomUuids() {
		UUID[] traceIds;
		traceIds = new UUID[100000];
		for (int i = 0; i < traceIds.length; i++) {
			traceIds[i] = UUID.randomUUID();
		}
		return traceIds;
	}

}