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

package org.springframework.cloud.sleuth.otel.propagation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extensions.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.extensions.trace.propagation.B3Propagator;
import io.opentelemetry.extensions.trace.propagation.JaegerPropagator;
import io.opentelemetry.extensions.trace.propagation.OtTracerPropagator;
import io.opentelemetry.extensions.trace.propagation.TraceMultiPropagator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import io.opentelemetry.trace.propagation.HttpTraceContext;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.api.BaggageManager;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable propagation configuration via Spring Cloud Sleuth and
 * OpenTelemetry SDK.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(Tracer.class)
@AutoConfigureBefore(TraceAutoConfiguration.class)
@EnableConfigurationProperties({ SleuthPropagationProperties.class, OtelPropagationProperties.class })
public class TraceOtelPropagationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ContextPropagators otelContextPropagators(ObjectProvider<List<TextMapPropagator>> propagators) {
		List<TextMapPropagator> mapPropagators = propagators.getIfAvailable(ArrayList::new);
		if (mapPropagators.isEmpty()) {
			return OpenTelemetry.getPropagators();
		}
		DefaultContextPropagators.Builder builder = DefaultContextPropagators.builder();
		mapPropagators.forEach(builder::addTextMapPropagator);
		OpenTelemetry.setPropagators(builder.build());
		return OpenTelemetry.getPropagators();
	}

	@Configuration(proxyBeanMethods = false)
	static class PropagatorsConfiguration {

		@Bean
		CompositeTextMapPropagator compositeTextMapPropagator(SleuthPropagationProperties properties) {
			return new CompositeTextMapPropagator(properties);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.sleuth-baggage.enabled", matchIfMissing = true)
	static class BaggagePropagatorConfiguration {

		@Bean
		TextMapPropagator baggageTextMapPropagator(SleuthBaggageProperties properties,
				io.opentelemetry.baggage.BaggageManager otelBaggageManager, BaggageManager baggageManager,
				ApplicationEventPublisher publisher) {
			return new BaggageTextMapPropagator(properties, otelBaggageManager, baggageManager, publisher);
		}

	}

}

class CompositeTextMapPropagator implements TextMapPropagator {

	private final Map<SleuthPropagationProperties.PropagationType, TextMapPropagator> mapping = new HashMap<>();

	private final SleuthPropagationProperties properties;

	CompositeTextMapPropagator(SleuthPropagationProperties properties) {
		this.properties = properties;
		this.mapping.put(SleuthPropagationProperties.PropagationType.AWS, AwsXRayPropagator.getInstance());
		this.mapping.put(SleuthPropagationProperties.PropagationType.B3,
				TraceMultiPropagator.builder().addPropagator(B3Propagator.getSingleHeaderPropagator())
						.addPropagator(B3Propagator.getMultipleHeaderPropagator()).build());
		this.mapping.put(SleuthPropagationProperties.PropagationType.JAEGER, JaegerPropagator.getInstance());
		this.mapping.put(SleuthPropagationProperties.PropagationType.OT_TRACER, OtTracerPropagator.getInstance());
		this.mapping.put(SleuthPropagationProperties.PropagationType.W3C, HttpTraceContext.getInstance());
		this.mapping.put(SleuthPropagationProperties.PropagationType.CUSTOM, NoopTextMapPropagator.INSTANCE);
	}

	@Override
	public List<String> fields() {
		return this.properties.getType().stream().map(this.mapping::get).flatMap(p -> p.fields().stream())
				.collect(Collectors.toList());
	}

	@Override
	public <C> void inject(Context context, C carrier, Setter<C> setter) {
		this.properties.getType().stream().map(this.mapping::get).forEach(p -> p.inject(context, carrier, setter));
	}

	@Override
	public <C> Context extract(Context context, C carrier, Getter<C> getter) {
		for (SleuthPropagationProperties.PropagationType type : this.properties.getType()) {
			TextMapPropagator propagator = this.mapping.get(type);
			if (propagator == null || propagator == NoopTextMapPropagator.INSTANCE) {
				continue;
			}
			Context extractedContext = propagator.extract(context, carrier, getter);
			Span span = TracingContextUtils.getSpanWithoutDefault(extractedContext);
			if (span != null) {
				return extractedContext;
			}
		}
		return context;
	}

	private static final class NoopTextMapPropagator implements TextMapPropagator {

		private static final NoopTextMapPropagator INSTANCE = new NoopTextMapPropagator();

		@Override
		public List<String> fields() {
			return Collections.emptyList();
		}

		@Override
		public <C> void inject(Context context, C carrier, Setter<C> setter) {
		}

		@Override
		public <C> Context extract(Context context, C carrier, Getter<C> getter) {
			return context;
		}

	}

}
