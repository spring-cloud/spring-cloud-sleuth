package org.springframework.cloud.sleuth.sampler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;

import static org.assertj.core.api.BDDAssertions.then;

public class PercentageBasedSamplerTests {

	SamplerProperties samplerConfiguration = new SamplerProperties();
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
		int numberOfIterations = 1000;
		float percentage = 1f;
		this.samplerConfiguration.setPercentage(percentage);

		int numberOfSampledElements = countNumberOfSampledElements(numberOfIterations);

		then(numberOfSampledElements).isEqualTo((int) (numberOfIterations * percentage));
	}

	@Test
	public void should_pass_given_percent_of_samples_with_fractional_element() throws Exception {
		int numberOfIterations = 1000;
		float percentage = 0.35f;
		this.samplerConfiguration.setPercentage(percentage);

		int numberOfSampledElements = countNumberOfSampledElements(numberOfIterations);

		int threshold = (int) (numberOfIterations * percentage);
		then(numberOfSampledElements).isEqualTo(threshold);
	}

	@Test
	public void should_pass_given_percent_of_samples_with_fractional_element_after_percentage_got_updated() throws Exception {
		float percentage = 0.35f;
		this.samplerConfiguration.setPercentage(percentage);
		PercentageBasedSampler sampler = new PercentageBasedSampler(this.samplerConfiguration);
		int numberOfIterations = 1000;
		int numberOfSampledElements = countNumberOfSampledElements(sampler, numberOfIterations);
		int threshold = (int) (numberOfIterations * percentage);
		then(numberOfSampledElements).isEqualTo(threshold);

		numberOfIterations = 1000;
		percentage = 0.65f;
		this.samplerConfiguration.setPercentage(percentage);
		sampler.onApplicationEvent(new EnvironmentChangeEvent(new HashSet<>(Collections.singletonList("spring.sleuth.sampler.percentage"))));
		numberOfSampledElements = countNumberOfSampledElements(sampler, numberOfIterations);
		threshold = (int) (numberOfIterations * percentage);
		then(numberOfSampledElements).isEqualTo(threshold);
	}

	@Test
	public void should_pass_given_percent_of_samples_with_fractional_element_after_context_got_refreshed_with_not_matching_keys() throws Exception {
		float percentage = 0.35f;
		this.samplerConfiguration.setPercentage(percentage);
		PercentageBasedSampler sampler = new PercentageBasedSampler(this.samplerConfiguration);
		int numberOfIterations = 1000;
		int numberOfSampledElements = countNumberOfSampledElements(sampler, numberOfIterations);
		int originalThreshold = (int) (numberOfIterations * percentage);
		then(numberOfSampledElements).isEqualTo(originalThreshold);

		numberOfIterations = 1000;
		percentage = 0.65f;
		this.samplerConfiguration.setPercentage(percentage);
		sampler.onApplicationEvent(new EnvironmentChangeEvent(new HashSet<>(Collections.emptyList())));
		numberOfSampledElements = countNumberOfSampledElements(sampler, numberOfIterations);
		// cause there was no EnvironmentChangeEvent with percentage key
		then(numberOfSampledElements).isEqualTo(originalThreshold);
	}

	private int countNumberOfSampledElements(int numberOfIterations) {
		Sampler sampler = new PercentageBasedSampler(this.samplerConfiguration);
		return countNumberOfSampledElements(sampler, numberOfIterations);
	}

	private int countNumberOfSampledElements(Sampler sampler, int numberOfIterations) {
		int passedCounter = 0;
		for (int i = 0; i < numberOfIterations; i++) {
			boolean passed = sampler.isSampled(newSpan());
			passedCounter = passedCounter + (passed ? 1 : 0);
		}
		return passedCounter;
	}

	@Before
	public void setupSpan() {
		this.span = newSpan();
	}

	Span newSpan() {
		return Span.builder().traceId(RANDOM.nextLong()).build();
	}

}