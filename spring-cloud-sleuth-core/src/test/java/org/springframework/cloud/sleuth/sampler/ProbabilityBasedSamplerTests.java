package org.springframework.cloud.sleuth.sampler;

import java.util.Random;

import brave.sampler.Sampler;
import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class ProbabilityBasedSamplerTests {
	
	SamplerProperties samplerConfiguration = new SamplerProperties();
	private static Random RANDOM = new Random();

	@Test
	public void should_pass_all_samples_when_config_has_1_percentage() throws Exception {
		this.samplerConfiguration.setProbability(1f);

		for (int i = 0; i < 10; i++) {
			then(new ProbabilityBasedSampler(this.samplerConfiguration).isSampled(RANDOM.nextLong()))
					.isTrue();
		}

	}

	@Test
	public void should_reject_all_samples_when_config_has_0_percentage()
			throws Exception {
		this.samplerConfiguration.setProbability(0f);

		for (int i = 0; i < 10; i++) {
			then(new ProbabilityBasedSampler(this.samplerConfiguration).isSampled(RANDOM.nextLong()))
					.isFalse();
		}
	}

	@Test
	public void should_pass_given_percent_of_samples() throws Exception {
		int numberOfIterations = 1000;
		float percentage = 1f;
		this.samplerConfiguration.setProbability(percentage);

		int numberOfSampledElements = countNumberOfSampledElements(numberOfIterations);

		then(numberOfSampledElements).isEqualTo((int) (numberOfIterations * percentage));
	}

	@Test
	public void should_pass_given_percent_of_samples_with_fractional_element() throws Exception {
		int numberOfIterations = 1000;
		float percentage = 0.35f;
		this.samplerConfiguration.setProbability(percentage);

		int numberOfSampledElements = countNumberOfSampledElements(numberOfIterations);

		int threshold = (int) (numberOfIterations * percentage);
		then(numberOfSampledElements).isEqualTo(threshold);
	}

	private int countNumberOfSampledElements(int numberOfIterations) {
		Sampler sampler = new ProbabilityBasedSampler(this.samplerConfiguration);
		int passedCounter = 0;
		for (int i = 0; i < numberOfIterations; i++) {
			boolean passed = sampler.isSampled(RANDOM.nextLong());
			passedCounter = passedCounter + (passed ? 1 : 0);
		}
		return passedCounter;
	}
}