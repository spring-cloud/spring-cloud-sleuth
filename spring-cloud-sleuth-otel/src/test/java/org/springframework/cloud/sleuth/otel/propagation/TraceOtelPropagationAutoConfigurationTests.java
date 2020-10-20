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
import java.util.List;

import javax.annotation.Nullable;

import io.grpc.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceOtelPropagationAutoConfigurationTests {

	@Test
	void should_start_a_composite_text_map_propagator_with_b3_as_default() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			CompositeTextMapPropagator propagator = context.getBean(CompositeTextMapPropagator.class);
			assertThat(propagator.fields()).contains("X-B3-TraceId");
		});
	}

	@Test
	void should_start_a_composite_text_map_propagator_with_a_single_propagation_type() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class)
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
				.withPropertyValues("spring.sleuth.propagation.type=custom");

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			CompositeTextMapPropagator propagator = context.getBean(CompositeTextMapPropagator.class);
			assertThat(propagator.fields()).doesNotContain("foo", "bar", "X-B3-TraceId", "traceparent");
			assertThat(context.getBean(CustomPropagator.class).fields()).contains("foo", "bar");
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class Config {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class CustomPropagatorConfig {

		@Bean
		TextMapPropagator myCustomPropagator() {
			return new CustomPropagator();
		}

	}

}

class CustomPropagator implements TextMapPropagator {

	@Override
	public List<String> fields() {
		return Arrays.asList("foo", "bar");
	}

	@Override
	public <C> void inject(Context context, @Nullable C carrier, Setter<C> setter) {

	}

	@Override
	public <C> Context extract(Context context, C carrier, Getter<C> getter) {
		return context;
	}

}
