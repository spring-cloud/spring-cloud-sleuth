/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.web;

import brave.http.HttpRequest;
import brave.sampler.SamplerFunction;

/**
 * Composite Http Sampler.
 *
 * @author Adrian Cole
 */
final class CompositeHttpSampler implements SamplerFunction<HttpRequest> {

	final SamplerFunction<HttpRequest> left;

	final SamplerFunction<HttpRequest> right;

	CompositeHttpSampler(SamplerFunction<HttpRequest> left, SamplerFunction<HttpRequest> right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public Boolean trySample(HttpRequest request) {
		// If either decision is false, return false
		Boolean leftDecision = this.left.trySample(request);
		if (Boolean.FALSE.equals(leftDecision)) {
			return false;
		}
		Boolean rightDecision = this.right.trySample(request);
		if (Boolean.FALSE.equals(rightDecision)) {
			return false;
		}
		// If either decision is null, return the other
		if (leftDecision == null) {
			return rightDecision;
		}
		if (rightDecision == null) {
			return leftDecision;
		}
		// Neither are null and at least one is true
		return rightDecision;
	}

}
