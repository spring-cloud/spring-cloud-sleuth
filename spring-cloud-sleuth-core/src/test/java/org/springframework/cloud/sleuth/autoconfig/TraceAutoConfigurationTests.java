/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig;

import brave.propagation.B3Propagation;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.BDDAssertions;
import org.junit.Test;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.ReporterMetrics;
import zipkin2.reporter.metrics.micrometer.MicrometerReporterMetrics;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class TraceAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class));

	@Test
	public void should_apply_micrometer_reporter_metrics_when_meter_registry_bean_present() {
		this.contextRunner.withUserConfiguration(WithMeterRegistry.class)
				.run((context) -> {
					ReporterMetrics bean = context.getBean(ReporterMetrics.class);

					BDDAssertions.then(bean)
							.isInstanceOf(MicrometerReporterMetrics.class);
				});
	}

	@Test
	public void should_apply_in_memory_metrics_when_meter_registry_bean_missing() {
		this.contextRunner.run((context) -> {
			ReporterMetrics bean = context.getBean(ReporterMetrics.class);

			BDDAssertions.then(bean).isInstanceOf(InMemoryReporterMetrics.class);
		});
	}

	@Test
	public void should_apply_in_memory_metrics_when_meter_registry_class_missing() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(MeterRegistry.class))
				.run((context) -> {
					ReporterMetrics bean = context.getBean(ReporterMetrics.class);

					BDDAssertions.then(bean).isInstanceOf(InMemoryReporterMetrics.class);
				});
	}

	@Test
	public void should_use_B3Propagation_factory_if_no_have_any_config() {
		this.contextRunner.run((context -> {
			final Propagation.Factory bean = context.getBean(Propagation.Factory.class);
			BDDAssertions.then(bean).isInstanceOf(Propagation.Factory.class);
		}));
	}

	@Test
	public void should_use_local_keys_from_properties() {
		this.contextRunner.withUserConfiguration(WithLocalKeys.class).run((context -> {
			final Propagation.Factory bean = context.getBean(Propagation.Factory.class);
			BDDAssertions.then(bean).isInstanceOf(ExtraFieldPropagation.Factory.class);
		}));
	}

	@Test
	public void should_use_extraFieldPropagationFactoryBuilder_bean() {
		this.contextRunner
				.withUserConfiguration(WithExtraFieldPropagationFactoryBuilderBean.class)
				.run((context -> {
					final Propagation.Factory bean = context
							.getBean(Propagation.Factory.class);
					BDDAssertions.then(bean)
							.isInstanceOf(ExtraFieldPropagation.Factory.class);
				}));
	}

	@Configuration
	static class WithMeterRegistry {

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

	}

	@Configuration
	static class WithLocalKeys {

		@Bean
		SleuthProperties sleuthProperties() {
			final SleuthProperties sleuthProperties = new SleuthProperties();
			sleuthProperties.getLocalKeys().add("test-key");
			return sleuthProperties;
		}

	}

	@Configuration
	static class WithExtraFieldPropagationFactoryBuilderBean {

		@Bean
		ExtraFieldPropagation.FactoryBuilder extraFieldPropagationFactoryBuilderBean() {
			return ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY);
		}

	}

}
