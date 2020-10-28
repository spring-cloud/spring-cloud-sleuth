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

import io.opentelemetry.baggage.BaggageManager;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable the bridge between Sleuth API and OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = true)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
public class TraceOtelBridgeConfiguation {

	@Bean
	Tracer otelTracerBridge(io.opentelemetry.trace.Tracer tracer, BaggageManager baggageManager,
			SleuthBaggageProperties sleuthBaggageProperties, ApplicationEventPublisher publisher) {
		return new OtelTracer(tracer,
				otelBaggageManagerBridge(tracer, baggageManager, sleuthBaggageProperties, publisher));
	}

	@Bean(autowireCandidate = false)
	OtelBaggageManager otelBaggageManagerBridge(io.opentelemetry.trace.Tracer tracer, BaggageManager baggageManager,
			SleuthBaggageProperties sleuthBaggageProperties, ApplicationEventPublisher publisher) {
		return new OtelBaggageManager(tracer, baggageManager, sleuthBaggageProperties, publisher);
	}

	@Bean
	CurrentTraceContext otelCurrentTraceContext(io.opentelemetry.trace.Tracer tracer,
			ApplicationEventPublisher publisher) {
		return new OtelCurrentTraceContext(tracer, publisher);
	}

	@Bean
	SpanCustomizer otelSpanCustomizer(io.opentelemetry.trace.Tracer tracer) {
		return new OtelSpanCustomizer(tracer);
	}

}
