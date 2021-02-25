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

package org.springframework.cloud.sleuth.sampler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties related to sampling.
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 * @since 1.0.0
 */
@ConfigurationProperties("spring.sleuth.sampler")
// TODO: Hide in 3.x, if it isn't already deleted
public class SamplerProperties {

	/**
	 * Probability of requests that should be sampled. E.g. 1.0 - 100% requests should be
	 * sampled. The precision is whole-numbers only (i.e. there's no support for 0.1% of
	 * the traces).
	 */
	private Float probability;

	/**
	 * A rate per second can be a nice choice for low-traffic endpoints as it allows you
	 * surge protection. For example, you may never expect the endpoint to get more than
	 * 50 requests per second. If there was a sudden surge of traffic, to 5000 requests
	 * per second, you would still end up with 50 traces per second. Conversely, if you
	 * had a percentage, like 10%, the same surge would end up with 500 traces per second,
	 * possibly overloading your storage. Amazon X-Ray includes a rate-limited sampler
	 * (named Reservoir) for this purpose. Brave has taken the same approach via the
	 * {@link brave.sampler.RateLimitingSampler}.
	 */
	private Integer rate = 10;

	public Float getProbability() {
		return this.probability;
	}

	public void setProbability(Float probability) {
		this.probability = probability;
	}

	public Integer getRate() {
		return this.rate;
	}

	public void setRate(Integer rate) {
		this.rate = rate;
	}

}
