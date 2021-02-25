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

import brave.sampler.Sampler;

/**
 * The rate-limited sampler allows you to choose an amount of traces to accept on a
 * per-second interval. The minimum number is 0 and the max is 2,147,483,647 (max int).
 *
 * You can read more about it in {@link brave.sampler.RateLimitingSampler}
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
class RateLimitingSampler extends Sampler {

	private final Sampler sampler;

	RateLimitingSampler(SamplerProperties configuration) {
		this.sampler = brave.sampler.RateLimitingSampler.create(rateLimit(configuration));
	}

	private Integer rateLimit(SamplerProperties configuration) {
		return configuration.getRate() != null ? configuration.getRate() : 0;
	}

	@Override
	public boolean isSampled(long traceId) {
		return this.sampler.isSampled(traceId);
	}

}
