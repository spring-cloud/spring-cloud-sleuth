package org.springframework.cloud.sleuth.sampler;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.Percentage.withPercentage;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.Span;

public class PercentageBasedSamplerTests {

	SamplerConfiguration samplerConfiguration = new SamplerConfiguration();
	private Span span;
	private static Random RANDOM = new Random();

	@Test
	public void should_pass_all_samples_when_config_has_1_percentage() throws Exception {
		this.samplerConfiguration.setPercentage(1f);

		for (int i = 0; i < 10; i++) {
			then(new PercentageBasedSampler(this.samplerConfiguration).isSampled(this.span))
					.isTrue();
		}

	}

	@Test
	public void should_reject_all_samples_when_config_has_0_percentage()
			throws Exception {
		this.samplerConfiguration.setPercentage(0f);

		for (int i = 0; i < 10; i++) {
			then(new PercentageBasedSampler(this.samplerConfiguration).isSampled(this.span))
					.isFalse();
		}
	}

	@Test
	public void should_pass_given_percent_of_samples() throws Exception {
		int numberOfIterations = 10000;
		float percentage = 1f;
		this.samplerConfiguration.setPercentage(percentage);

		int numberOfSampledElements = countNumberOfSampledElements(numberOfIterations);

		then(numberOfSampledElements).isCloseTo((int) (numberOfIterations * percentage),
				withPercentage(3));
	}

	private int countNumberOfSampledElements(int numberOfIterations) {
		int passedCounter = 0;
		for (int i = 0; i < numberOfIterations; i++) {
			boolean passed = new PercentageBasedSampler(this.samplerConfiguration)
					.isSampled(this.span);
			passedCounter = passedCounter + (passed ? 1 : 0);
		}
		return passedCounter;
	}

	@Before
	public void setupSpan() {
		this.span = Span.builder().traceId(RANDOM.nextLong()).build();
	}

}