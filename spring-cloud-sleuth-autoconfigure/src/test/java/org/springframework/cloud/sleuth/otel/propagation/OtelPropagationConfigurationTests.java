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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OtelPropagationConfigurationTests {

	@Test
	void should_start_a_composite_text_map_propagator_with_b3_as_default() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.tracer.mode=OTEL").withUserConfiguration(Config.class);

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			CompositeTextMapPropagator propagator = context.getBean(CompositeTextMapPropagator.class);
			assertThat(propagator.fields()).contains("X-B3-TraceId");
		});
	}

	@Test
	void should_start_a_composite_text_map_propagator_with_a_single_propagation_type() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class)
				.withPropertyValues("spring.sleuth.tracer.mode=OTEL")
				.withPropertyValues("spring.sleuth.propagation.type=w3c");

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			CompositeTextMapPropagator propagator = context.getBean(CompositeTextMapPropagator.class);
			assertThat(propagator.fields()).doesNotContain("X-B3-TraceId").contains("traceparent");
		});
	}

	@Test
	void should_start_a_composite_text_map_propagator_with_multiple_propagation_types() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class)
				.withPropertyValues("spring.sleuth.tracer.mode=OTEL")
				.withPropertyValues("spring.sleuth.propagation.type=b3,w3c");

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			CompositeTextMapPropagator propagator = context.getBean(CompositeTextMapPropagator.class);
			assertThat(propagator.fields()).contains("X-B3-TraceId", "traceparent");
		});
	}

	@Test
	void should_start_a_composite_text_map_propagator_with_custom_propagation_types() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withUserConfiguration(CustomPropagatorConfig.class)
				.withPropertyValues("spring.sleuth.tracer.mode=OTEL")
				.withPropertyValues("spring.sleuth.propagation.type=custom");

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			CompositeTextMapPropagator propagator = context.getBean(CompositeTextMapPropagator.class);
			assertThat(propagator.fields()).doesNotContain("myCustomTraceId", "myCustomSpanId", "X-B3-TraceId",
					"traceparent");
		});
	}

	@Test
	void should_inject_and_extract_from_custom_propagator() {
		CustomPropagator customPropagator = new CustomPropagator();
		Map<String, String> carrier = carrierWithTracingData();

		// Extraction
		Context extract = customPropagator.extract(Context.current(), carrier,
				new TextMapPropagator.Getter<Map<String, String>>() {
					@Override
					public Iterable<String> keys(Map<String, String> carrier) {
						return carrier.keySet();
					}

					@Nullable
					@Override
					public String get(@Nullable Map<String, String> carrier, String key) {
						return carrier.get(key);
					}
				});
		Span spanFromContext = Span.fromContext(extract);
		assertThat(spanFromContext.getSpanContext().getTraceIdAsHexString())
				.isEqualTo("ff000000000000000000000000000041");
		assertThat(spanFromContext.getSpanContext().getSpanIdAsHexString()).isEqualTo("ff00000000000041");

		// Injection
		Map<String, String> emptyMap = new HashMap<>();
		customPropagator.inject(extract, emptyMap, Map::put);
		assertThat(emptyMap).containsEntry("myCustomTraceId", "ff000000000000000000000000000041")
				.containsEntry("myCustomSpanId", "ff00000000000041");
	}

	private Map<String, String> carrierWithTracingData() {
		Map<String, String> carrier = new HashMap<>();
		carrier.put("myCustomTraceId", "ff000000000000000000000000000041");
		carrier.put("myCustomSpanId", "ff00000000000041");
		return carrier;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	static class Config {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class,
			GatewayMetricsAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class,
			MongoAutoConfiguration.class, QuartzAutoConfiguration.class })
	static class CustomPropagatorConfig {

		@Bean
		TextMapPropagator myCustomPropagator() {
			return new CustomPropagator();
		}

	}

}

// tag::custom_propagator[]
class CustomPropagator implements TextMapPropagator {

	@Override
	public List<String> fields() {
		return Arrays.asList("myCustomTraceId", "myCustomSpanId");
	}

	@Override
	public <C> void inject(Context context, C carrier, Setter<C> setter) {
		SpanContext spanContext = Span.fromContext(context).getSpanContext();
		if (!spanContext.isValid()) {
			return;
		}
		setter.set(carrier, "myCustomTraceId", spanContext.getTraceIdAsHexString());
		setter.set(carrier, "myCustomSpanId", spanContext.getSpanIdAsHexString());
	}

	@Override
	public <C> Context extract(Context context, C carrier, Getter<C> getter) {
		String traceParent = getter.get(carrier, "myCustomTraceId");
		if (traceParent == null) {
			return Span.getInvalid().storeInContext(context);
		}
		String spanId = getter.get(carrier, "myCustomSpanId");
		return Span.wrap(SpanContext.createFromRemoteParent(traceParent, spanId, TraceFlags.getSampled(),
				TraceState.builder().build())).storeInContext(context);
	}

}
// end::custom_propagator[]
