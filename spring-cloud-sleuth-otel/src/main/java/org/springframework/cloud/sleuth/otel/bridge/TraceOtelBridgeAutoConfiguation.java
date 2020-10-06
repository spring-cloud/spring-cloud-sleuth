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

import io.opentelemetry.context.propagation.ContextPropagators;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.otel.autoconfig.TraceOtelAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.enabled", matchIfMissing = true)
@AutoConfigureAfter(TraceOtelAutoConfiguration.class)
@AutoConfigureBefore(TraceAutoConfiguration.class)
public class TraceOtelBridgeAutoConfiguation {

	@Bean
	Tracer otelTracerBridge(io.opentelemetry.trace.Tracer tracer) {
		return new OtelTracer(tracer);
	}

	@Bean
	CurrentTraceContext otelCurrentTraceContext(io.opentelemetry.trace.Tracer tracer) {
		return new OtelCurrentTraceContext(tracer);
	}

	@Bean
	SpanCustomizer otelSpanCustomizer(io.opentelemetry.trace.Tracer tracer) {
		return new OtelSpanCustomizer(tracer);
	}

	@Bean
	Propagator otelPropagator(ContextPropagators contextPropagators) {
		return new OtelPropagator(contextPropagators);
	}

}
