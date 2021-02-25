/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.sampler;

import java.util.Random;
import java.util.function.Supplier;

import brave.sampler.Sampler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class ProbabilityBasedSamplerTests {

	private static Random RANDOM = new Random();

	@Test
	public void should_pass_all_samples_when_config_has_1_probability() throws Exception {
		for (int i = 0; i < 10; i++) {
			then(new ProbabilityBasedSampler(() -> 1f).isSampled(RANDOM.nextLong())).isTrue();
		}

	}

	@Test
	public void should_reject_all_samples_when_config_has_0_probability() throws Exception {
		for (int i = 0; i < 10; i++) {
			then(new ProbabilityBasedSampler(() -> 0f).isSampled(RANDOM.nextLong())).isFalse();
		}
	}

	@Test
	public void should_pass_given_percent_of_samples() throws Exception {
		int numberOfIterations = 1000;
		float probability = 1f;

		int numberOfSampledElements = countNumberOfSampledElements(numberOfIterations, () -> probability);

		then(numberOfSampledElements).isEqualTo((int) (numberOfIterations * probability));
	}

	@Test
	public void should_pass_given_percent_of_samples_with_fractional_element() throws Exception {
		int numberOfIterations = 1000;
		float probability = 0.35f;

		int numberOfSampledElements = countNumberOfSampledElements(numberOfIterations, () -> probability);

		int threshold = (int) (numberOfIterations * probability);
		then(numberOfSampledElements).isEqualTo(threshold);
	}

	@Test
	public void should_fail_given_no_probability() {
		assertThatThrownBy(() -> new ProbabilityBasedSampler(null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("probability property is required for ProbabilityBasedSampler");
	}

	private int countNumberOfSampledElements(int numberOfIterations, Supplier<Float> probability) {
		Sampler sampler = new ProbabilityBasedSampler(probability);
		int passedCounter = 0;
		for (int i = 0; i < numberOfIterations; i++) {
			boolean passed = sampler.isSampled(RANDOM.nextLong());
			passedCounter = passedCounter + (passed ? 1 : 0);
		}
		return passedCounter;
	}

}
