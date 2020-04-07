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

import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.propagation.B3Propagation;
import brave.propagation.B3SinglePropagation;
import brave.propagation.Propagation;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.baggage.TraceBaggageAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceHttpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

public class TraceAutoConfigurationPropagationCustomizationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class,
					TraceBaggageAutoConfiguration.class));

	@Test
	public void stillCreatesDefault() {
		this.contextRunner.run((context) -> {
			BDDAssertions.then(context.getBean(Tracing.class).propagation())
					.isInstanceOf(B3Propagation.class);
		});
	}

	@Test
	public void allowsCustomization() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.baggage.remote-fields=country-code")
				.run((context) -> {
					BDDAssertions.then(context.getBean(Propagation.Factory.class))
							.extracting("delegate").isNotNull();
				});
	}

	@Test
	public void defaultValueUsedWhenApplicationNameNotSet() {
		this.contextRunner.withPropertyValues("spring.application.name=")
				.run((context) -> {
					BDDAssertions.then(context.getBean(Tracing.class).propagation())
							.isInstanceOf(B3Propagation.class);
				});
	}

	@Test
	public void hasNoCycles() {
		this.contextRunner
				.withConfiguration(AutoConfigurations.of(TraceWebAutoConfiguration.class,
						TraceHttpAutoConfiguration.class))
				.withInitializer(c -> ((GenericApplicationContext) c)
						.setAllowCircularReferences(false))
				.run((context) -> {
					BDDAssertions.then(context.isRunning()).isEqualTo(true);
				});
	}

	@Test
	public void allowsCustomizationOfBuilder() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.baggage.remote-fields=country-code")
				.withUserConfiguration(CustomPropagationFactoryBuilderConfig.class)
				.run((context) -> BDDAssertions
						.then(context.getBean(Propagation.Factory.class))
						.extracting("delegate").isSameAs(B3SinglePropagation.FACTORY));
	}

	@Configuration
	static class CustomPropagationFactoryBuilderConfig {

		@Bean
		public BaggagePropagation.FactoryBuilder b3Single() {
			return BaggagePropagation.newFactoryBuilder(B3SinglePropagation.FACTORY);
		}

	}

}
