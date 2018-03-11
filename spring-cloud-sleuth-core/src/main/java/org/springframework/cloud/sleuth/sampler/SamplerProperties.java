/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.sampler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties related to sampling
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 * @since 1.0.0
 */
@ConfigurationProperties("spring.sleuth.sampler")
public class SamplerProperties {

	/**
	 * Probability of requests that should be sampled. E.g. 1.0 - 100% requests should be
	 * sampled. The precision is whole-numbers only (i.e. there's no support for 0.1% of
	 * the traces).
	 */
	private float probability = 0.1f;

	public float getProbability() {
		return this.probability;
	}

	public void setProbability(float probability) {
		this.probability = probability;
	}
}
