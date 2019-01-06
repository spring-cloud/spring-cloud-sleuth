package org.springframework.cloud.sleuth.sampler;

import brave.sampler.Sampler;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Marcin Grzejszczak
 * @since
 */
public class SamplerAutoConfigurationTests {

	@Test
	public void should_use_rate_limit_sampler_when_property_set() {
		SamplerProperties properties = new SamplerProperties();
		properties.setRatelimit(10);

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(RateLimitingSampler.class);
	}

	@Test
	public void should_use_probability_sampler_when_rate_limiting_not_set() {
		SamplerProperties properties = new SamplerProperties();

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(ProbabilityBasedSampler.class);
	}

}