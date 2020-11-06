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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import brave.internal.codec.HexCodec;
import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.brave.autoconfig.TraceBraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceBravePropagationAutoConfigurationTests {

	@Test
	void should_start_a_composite_propagation_factory_supplier_with_b3_as_default() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(
				AutoConfigurations.of(TraceBraveAutoConfiguration.class, TraceBravePropagationAutoConfiguration.class))
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
				.withConfiguration(AutoConfigurations.of(TraceBraveAutoConfiguration.class,
						TraceBravePropagationAutoConfiguration.class))
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
				.withConfiguration(AutoConfigurations.of(TraceBraveAutoConfiguration.class,
						TraceBravePropagationAutoConfiguration.class))
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
			assertThat(propagator.keys()).contains("myCustomTraceId", "myCustomSpanId");
		});
	}

	@Test
	void should_inject_and_extract_from_custom_propagator() {
		CustomPropagator customPropagator = new CustomPropagator();
		Map<String, String> carrier = carrierWithTracingData();

		// Extraction
		TraceContextOrSamplingFlags extract = customPropagator
				.extractor((Propagation.Getter<Map<String, String>, String>) Map::get).extract(carrier);
		assertThat(extract.context().traceIdString()).isEqualTo("ff00000000000041");
		assertThat(extract.context().spanIdString()).isEqualTo("ff00000000000041");

		// Injection
		Map<String, String> emptyMap = new HashMap<>();
		customPropagator.injector((Propagation.Setter<Map<String, String>, String>) Map::put).inject(extract.context(),
				emptyMap);
		assertThat(emptyMap).containsEntry("myCustomTraceId", "ff00000000000041").containsEntry("myCustomSpanId",
				"ff00000000000041");
	}

	private Map<String, String> carrierWithTracingData() {
		Map<String, String> carrier = new HashMap<>();
		carrier.put("myCustomTraceId", "ff00000000000041");
		carrier.put("myCustomSpanId", "ff00000000000041");
		return carrier;
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

// tag::custom_propagator[]
class CustomPropagator extends Propagation.Factory implements Propagation<String> {

	@Override
	public List<String> keys() {
		return Arrays.asList("myCustomTraceId", "myCustomSpanId");
	}

	@Override
	public <R> TraceContext.Injector<R> injector(Setter<R, String> setter) {
		return (traceContext, request) -> {
			setter.put(request, "myCustomTraceId", traceContext.traceIdString());
			setter.put(request, "myCustomSpanId", traceContext.spanIdString());
		};
	}

	@Override
	public <R> TraceContext.Extractor<R> extractor(Getter<R, String> getter) {
		return request -> TraceContextOrSamplingFlags.create(TraceContext.newBuilder()
				.traceId(HexCodec.lowerHexToUnsignedLong(getter.get(request, "myCustomTraceId")))
				.spanId(HexCodec.lowerHexToUnsignedLong(getter.get(request, "myCustomSpanId"))).build());
	}

	@Override
	public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
		return StringPropagationAdapter.create(this, keyFactory);
	}

}
// end::custom_propagator[]
