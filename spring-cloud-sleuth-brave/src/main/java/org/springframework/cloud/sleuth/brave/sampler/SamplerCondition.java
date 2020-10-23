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

package org.springframework.cloud.sleuth.brave.sampler;

import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/**
 * Sleuth 1.x optimized for log-correlation only. Unless "spring-cloud-sleuth-zipkin" was
 * present, the sampler defaulted to {@link Sampler#NEVER_SAMPLE}. This was to ensure the
 * log only nodes never set the sampled flag.
 *
 * <p>
 * "spring-cloud-sleuth-zipkin" obviated the {@link Sampler#NEVER_SAMPLE} default by
 * importing {@link SamplerAutoConfiguration}. Nothing else did, so sampling properties
 * were effectively ignored unless "spring-cloud-sleuth-zipkin" was in use, or something
 * else similarly imported {@link SamplerAutoConfiguration}.
 *
 * <p>
 * During a review of Wavefront integration, it was considered not correct to have other
 * code import {@link SamplerAutoConfiguration}. To avoid that, retain the old behaviour
 * about log only nodes, and also not pin configuration to Zipkin involves a more complex
 * condition.
 *
 * <p>
 * This condition looks for signs of non-default setup which likely requires sampling
 * configuration. It passes on one of the following beans exist:
 *
 * <p>
 * <ul>
 * <li>{@code zipkin2.reporter.Reporter} - what's used by Zipkin or others like
 * Stackdriver</li>
 * <li>{@link SpanHandler} - only accepts sampled data</li>
 * <li>{@link TracingCustomizer} - can configure one of the above</li>
 * </ul>
 *
 * <p>
 * An integrated test that shows {@link Sampler#NEVER_SAMPLE} is default on fail exists in
 * {@code TraceAutoConfigurationTests} intentionally, as users now needn't import
 * {@link SamplerAutoConfiguration} directly.
 */
final class SamplerCondition extends AnyNestedCondition {

	SamplerCondition() {
		super(ConfigurationPhase.REGISTER_BEAN);
	}

	// zipkin2.reporter.Reporter is in the classpath here, but technically it is optional
	@ConditionalOnBean(type = "zipkin2.reporter.Reporter")
	static final class ReporterAvailable {

	}

	@ConditionalOnBean(SpanHandler.class)
	static final class SpanHandlerAvailable {

	}

	@ConditionalOnBean(TracingCustomizer.class)
	static final class TracingCustomizerAvailable {

	}

}
