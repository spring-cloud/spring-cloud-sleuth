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

package org.springframework.cloud.sleuth.brave.bridge;

import brave.Tracing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable the bridge between Sleuth API and Brave.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @author Tim Ysewyn
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
public class TraceBraveBridgeConfiguation {

	@Bean
	Tracer braveTracer(brave.Tracer tracer) {
		return new BraveTracer(tracer);
	}

	@Bean
	CurrentTraceContext braveCurrentTraceContext(brave.propagation.CurrentTraceContext currentTraceContext) {
		return new BraveCurrentTraceContext(currentTraceContext);
	}

	@Bean
	SpanCustomizer braveSpanCustomizer(brave.SpanCustomizer spanCustomizer) {
		return new BraveSpanCustomizer(spanCustomizer);
	}

	@Bean
	Propagator bravePropagator(Tracing tracing) {
		return new BravePropagator(tracing);
	}

}
