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

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * @author Marcin Grzejszczak
 * @author Tim Ysewyn
 * @since
 */
public class SamplerAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SamplerAutoConfiguration.class))
			.withPropertyValues("spring.sleuth.enabled=true",
					"spring.sleuth.sampler.enabled=false");

	@Test
	public void notEnabledByDefault() {
		this.contextRunner.run((context) -> BDDAssertions
				.then(context.getBeanNamesForType(Sampler.class)).isEmpty());
	}

	@Test
	public void notEnabledWhenSleuthIsDisabled() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.enabled=false",
						"spring.sleuth.sampler.enabled=true")
				.run((context) -> BDDAssertions
						.then(context.getBeanNamesForType(Sampler.class)).isEmpty());
	}

	@Test
	public void enabledWhenPropertySet() {
		this.contextRunner.withPropertyValues("spring.sleuth.sampler.enabled=true")
				.run((context) -> BDDAssertions
						.then(context.getBean(ProbabilityBasedSampler.class))
						.isNotNull());
	}

	@Test
	public void shouldUseRateLimitingSampler() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.sampler.enabled=true",
						"spring.sleuth.sampler.rate=10")
				.run((context) -> BDDAssertions
						.then(context.getBean(RateLimitingSampler.class)).isNotNull());
	}

}
