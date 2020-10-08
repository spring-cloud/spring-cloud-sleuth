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
import java.util.List;

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
import io.opentelemetry.trace.Tracer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
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
@EnableConfigurationProperties(OtelPropagationProperties.class)
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
	@ConditionalOnMissingBean(TextMapPropagator.class)
	@ConditionalOnClass(AwsXRayPropagator.class)
	static class PropagatorsConfiguration {

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.type", havingValue = "AWS")
		static class AwsPropagatorConfiguration {

			@Bean
			TextMapPropagator otelTextMapPropagator() {
				return AwsXRayPropagator.getInstance();
			}

		}

		// TODO: [OTEL] Making B3 a default
		@Configuration(proxyBeanMethods = false)
		@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.type", havingValue = "B3", matchIfMissing = true)
		@ConditionalOnClass(B3Propagator.class)
		static class B3PropagatorConfiguration {

			@Bean
			TextMapPropagator otelTextMapPropagator() {
				return TraceMultiPropagator.builder().addPropagator(B3Propagator.getSingleHeaderPropagator())
						.addPropagator(B3Propagator.getMultipleHeaderPropagator()).build();
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.type", havingValue = "JAEGER")
		@ConditionalOnClass(JaegerPropagator.class)
		static class JaegerPropagatorConfiguration {

			@Bean
			TextMapPropagator otelTextMapPropagator() {
				return JaegerPropagator.getInstance();
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.type", havingValue = "OT_TRACER")
		@ConditionalOnClass(OtTracerPropagator.class)
		static class OtTracerPropagatorConfiguration {

			@Bean
			TextMapPropagator otelTextMapPropagator() {
				return OtTracerPropagator.getInstance();
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnMissingClass(value = "io.opentelemetry.extensions.trace.propagation.TraceMultiPropagator")
		static class NoExtraPropagatorsConfiguration {

			@Bean
			@ConditionalOnMissingBean
			TextMapPropagator noOpTextMapPropagator() {
				return NoopTextMapPropagator.INSTANCE;
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnProperty(name = "spring.sleuth.otel.propagation.type", havingValue = "custom")
		static class CustomPropagatorsConfiguration {

			@Bean
			@ConditionalOnMissingBean
			TextMapPropagator noOpTextMapPropagator() {
				return NoopTextMapPropagator.INSTANCE;
			}

		}

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