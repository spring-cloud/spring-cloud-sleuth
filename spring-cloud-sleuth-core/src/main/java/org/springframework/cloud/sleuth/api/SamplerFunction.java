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
 * Taken from Brave.
 *
 * @param <T>
 */
// TODO: Not yet used in Brave version
public interface SamplerFunction<T> {

	@Nullable
	Boolean trySample(@Nullable T arg);

	static <T> SamplerFunction<T> deferDecision() {
		return (SamplerFunction<T>) Constants.DEFER_DECISION;
	}

	static <T> SamplerFunction<T> neverSample() {
		return (SamplerFunction<T>) Constants.NEVER_SAMPLE;
	}

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
