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

package org.springframework.cloud.sleuth.brave.propagation;

import java.util.Arrays;
import java.util.List;

import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceBravePropagationAutoConfigurationTests {

	@Test
	void should_start_a_composite_propagation_factory_supplier_with_b3_as_default() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(TraceBravePropagationAutoConfiguration.class))
				.withUserConfiguration(Config.class);

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			Propagation<String> propagator = context.getBean(CompositePropagationFactorySupplier.class).get().get();
			assertThat(propagator.keys()).contains("X-B3-TraceId");
		});
	}

	@Test
	void should_start_a_composite_propagation_factory_supplier_with_a_single_propagation_type() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(TraceBravePropagationAutoConfiguration.class))
				.withUserConfiguration(Config.class).withPropertyValues("spring.sleuth.propagation.type=w3c");

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			Propagation<String> propagator = context.getBean(CompositePropagationFactorySupplier.class).get().get();
			assertThat(propagator.keys()).doesNotContain("X-B3-TraceId").contains("traceparent");
		});
	}

	@Test
	void should_start_a_composite_propagation_factory_supplier_with_multiple_propagation_types() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(TraceBravePropagationAutoConfiguration.class))
				.withUserConfiguration(Config.class).withPropertyValues("spring.sleuth.propagation.type=b3,w3c");

		runner.run(context -> {
			assertThat(context).hasNotFailed();
			Propagation<String> propagator = context.getBean(CompositePropagationFactorySupplier.class).get().get();
			assertThat(propagator.keys()).contains("X-B3-TraceId", "traceparent");
		});
	}

	@Test
	void should_start_a_composite_propagation_factory_supplier_with_custom_propagation_types() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(TraceBravePropagationAutoConfiguration.class))
				.withUserConfiguration(CustomPropagatorConfig.class)
				.withPropertyValues("spring.sleuth.propagation.type=custom");

		runner.run(context -> {
			assertThat(context).hasNotFailed().doesNotHaveBean(CompositePropagationFactorySupplier.class);
			Propagation<String> propagator = context.getBean(PropagationFactorySupplier.class).get().get();
			assertThat(propagator.keys()).contains("foo", "bar");
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomPropagatorConfig {

		@Bean
		PropagationFactorySupplier myCustomPropagator() {
			return CustomPropagator::new;
		}

	}

}

class CustomPropagator extends Propagation.Factory implements Propagation<String> {

	@Override
	public List<String> keys() {
		return Arrays.asList("foo", "bar");
	}

	@Override
	public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
		return (traceContext, request) -> {

		};
	}

	@Override
	public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
		return request -> TraceContextOrSamplingFlags.EMPTY;
	}

	@Override
	public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
		return StringPropagationAdapter.create(this, keyFactory);
	}

}
