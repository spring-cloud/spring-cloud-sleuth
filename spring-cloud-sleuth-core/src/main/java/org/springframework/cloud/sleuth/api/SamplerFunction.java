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

package org.springframework.cloud.sleuth.api;

import org.springframework.lang.Nullable;

/**
 * Decides whether to start a new trace based on request properties such as an HTTP path.
 *
 * <p>
 * Ex. Here's a sampler that only traces api requests <pre>{@code
 * serverSampler = new SamplerFunction<HttpRequest>() {
 *   &#64;Override public Boolean trySample(HttpRequest request) {
 *     return request.path().startsWith("/api");
 *   }
 * });
 * }</pre>
 *
 * @param <T> type of the input, for example a request or method
 * @since 5.8
 */
// TODO: Not yet used in Brave
public interface SamplerFunction<T> {

	/**
	 * Returns an overriding sampling decision for a new trace. Returning null is
	 * typically used to defer to the sampler.
	 * @param arg parameter to evaluate for a sampling decision. null input results in a
	 * null result
	 * @return true to sample a new trace or false to deny. Null defers the decision.
	 * @since 5.8
	 */
	@Nullable
	Boolean trySample(@Nullable T arg);

	/**
	 * Ignores the argument and returns null. This is typically used to defer to the
	 * tracer.
	 *
	 * @since 5.8
	 */
	static <T> SamplerFunction<T> deferDecision() {
		return (SamplerFunction<T>) Constants.DEFER_DECISION;
	}

	/**
	 * Ignores the argument and returns false. This means it will never start new traces.
	 *
	 * <p>
	 * For example, you may wish to only capture traces if they originated from an inbound
	 * server request. Such a policy would filter out client requests made during
	 * bootstrap.
	 *
	 * @since 5.8
	 */
	static <T> SamplerFunction<T> neverSample() {
		return (SamplerFunction<T>) Constants.NEVER_SAMPLE;
	}

	/**
	 * Ignores the argument and returns false. This means it will never start new traces.
	 *
	 * <p>
	 * For example, you may wish to only capture traces if they originated from an inbound
	 * server request. Such a policy would filter out client requests made during
	 * bootstrap.
	 *
	 * @since 5.8
	 */
	static <T> SamplerFunction<T> alwaysSample() {
		return (SamplerFunction<T>) Constants.ALWAYS_SAMPLE;
	}

	enum Constants implements SamplerFunction<Object> {

		DEFER_DECISION {
			@Override
			public Boolean trySample(Object request) {
				return null;
			}

			@Override
			public String toString() {
				return "DeferDecision";
			}
		},
		NEVER_SAMPLE {
			@Override
			public Boolean trySample(Object request) {
				return false;
			}

			@Override
			public String toString() {
				return "NeverSample";
			}
		},
		ALWAYS_SAMPLE {
			@Override
			public Boolean trySample(Object request) {
				return true;
			}

			@Override
			public String toString() {
				return "AlwaysSample";
			}
		}

	}

}
