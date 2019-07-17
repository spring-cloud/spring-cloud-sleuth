/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.sampler;

import brave.sampler.Sampler;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;

/**
 * @author Marcin Grzejszczak
 * @since
 */
public class SamplerAutoConfigurationTests {

	@Test
	public void should_use_probability_sampler_when_property_set() {
		SamplerProperties properties = new SamplerProperties();
		properties.setProbability(10f);

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(ProbabilityBasedSampler.class);
	}

	@Test
	public void should_use_rate_limiting_sampler_when_probability_not_set() {
		SamplerProperties properties = new SamplerProperties();

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(RateLimitingSampler.class);
	}

	@Test
	public void should_use_probability_sampler_when_both_rate_and_probability_is_set() {
		SamplerProperties properties = new SamplerProperties();
		properties.setProbability(10f);
		properties.setRate(20);

		Sampler sampler = SamplerAutoConfiguration.samplerFromProps(properties);

		BDDAssertions.then(sampler).isInstanceOf(ProbabilityBasedSampler.class);
	}

}
