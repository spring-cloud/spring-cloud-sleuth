package org.springframework.cloud.sleuth.sampler;

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.Test;

public class PercentageBasedSamplerTest {

	SamplerConfiguration samplerConfiguration = new SamplerConfiguration();

	@Test
	public void should_pass_all_samples_when_config_has_1_percentage() throws Exception {
		boolean passed = new PercentageBasedSampler(samplerConfiguration).next(null);

		for (int i = 0; i < 10; i++) {
			then(passed).isTrue();
		}
	}

	@Test
	public void should_reject_all_samples_when_config_has_0_percentage() throws Exception {
		samplerConfiguration.setPercentage(0d);

		boolean passed = new PercentageBasedSampler(samplerConfiguration).next(null);

		for (int i = 0; i < 10; i++) {
			then(passed).isFalse();
		}
	}

	@Test
	public void should_pass_given_percent_of_samples() throws Exception {
		samplerConfiguration.setPercentage(0.3d);
		PercentageBasedSampler sampler = new PercentageBasedSampler(samplerConfiguration);

		int numberOfSampledElements = countNumberOfSampledElements(sampler);

		then(numberOfSampledElements).isEqualTo(30);
	}

	private int countNumberOfSampledElements(PercentageBasedSampler sampler) {
		int passedCounter = 0;

		for (int i = 0; i < 100; i++) {
			boolean passed = sampler.next(null);
			passedCounter = passedCounter + (passed ? 1 : 0);
		}
		return passedCounter;
	}

}