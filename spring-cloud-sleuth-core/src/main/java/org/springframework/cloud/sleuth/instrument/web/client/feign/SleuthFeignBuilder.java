/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;

import feign.Feign;
import feign.hystrix.HystrixFeign;

/**
 * Contains {@link feign.Feign.Builder} implementation that delegates execution
 * {@link feign.hystrix.HystrixFeign} with tracing components
 * that close spans on exceptions / success and continues them on retries.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class SleuthFeignBuilder {

	private SleuthFeignBuilder() {}

	static Feign.Builder builder(Tracer tracer, HttpTraceKeysInjector keysInjector) {
		return HystrixFeign.builder()
				.client(new TraceFeignClient(tracer, keysInjector))
				.retryer(new TraceFeignRetryer(tracer))
				.decoder(new TraceFeignDecoder(tracer))
				.errorDecoder(new TraceFeignErrorDecoder(tracer));
	}
}
