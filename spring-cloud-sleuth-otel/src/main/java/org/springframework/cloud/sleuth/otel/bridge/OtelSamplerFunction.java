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

package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.sdk.trace.Sampler;

import org.springframework.cloud.sleuth.api.SamplerFunction;

// TODO: [OTEL] Sampler is in the SDK. Also sampling takes place upon span creation. Currently let's defer the decision for OTel.
public class OtelSamplerFunction<T> implements SamplerFunction<T> {

	final Sampler sampler;

	public OtelSamplerFunction(Sampler sampler) {
		this.sampler = sampler;
	}

	@Override
	public Boolean trySample(T arg) {
		return null;
	}

}
