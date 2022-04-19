/*
 * Copyright 2022-2022 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.instrument.prometheus;

import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.prometheus.SleuthSpanContextSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PrometheusExemplarsAutoConfiguration}.
 *
 * @author Jonatan Ivanov
 */
class PrometheusExemplarsAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(AutoConfigurations
					.of(TraceNoOpAutoConfiguration.class, PrometheusExemplarsAutoConfiguration.class));

	@Test
	void should_register_span_context_supplier() {
		contextRunner.run(context -> assertThat(context).hasSingleBean(SpanContextSupplier.class));
	}

	@Test
	void should_not_register_span_context_supplier_if_sleuth_disabled() {
		contextRunner.withPropertyValues("spring.sleuth.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(SpanContextSupplier.class));
	}

	@Test
	void should_not_register_span_context_supplier_if_exemplars_disabled() {
		contextRunner.withPropertyValues("spring.sleuth.prometheus.exemplars.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(SpanContextSupplier.class));
	}

	@Test
	void should_not_register_span_context_supplier_if_class_is_missing() {
		contextRunner.withClassLoader(new FilteredClassLoader(SpanContextSupplier.class))
				.run(context -> assertThat(context).doesNotHaveBean(SpanContextSupplier.class));
	}

	@Test
	void should_not_register_span_context_supplier_if_bean_exists() {
		contextRunner.withUserConfiguration(TestPrometheusExemplarsAutoConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(SpanContextSupplier.class);
			assertThat(context.getBean(SpanContextSupplier.class))
					.isSameAs(TestPrometheusExemplarsAutoConfiguration.SUPPLIER);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class TestPrometheusExemplarsAutoConfiguration {

		static final SpanContextSupplier SUPPLIER = new SleuthSpanContextSupplier(null);

		@Bean
		SpanContextSupplier testSpanContextSupplier() {
			return SUPPLIER;
		}

	}

}
