/*
 * Copyright 2013-2021 the original author or authors.
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

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.BaggagePropagationCustomizer;
import brave.propagation.Propagation;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;
import org.assertj.core.groups.Tuple;
import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class TraceBaggageConfigurationTests {

	static final String[] EMPTY_ARRAY = {};

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceBaggageConfiguration.class));

	@Test
	public void shouldCreateLocalFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.local-keys=bp")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(tuple("bp", EMPTY_ARRAY)));
	}

	static ListAssert<Tuple> assertThatBaggageFieldNameToKeyNames(
			AssertableApplicationContext context) {
		return assertThat(context.getBean(Propagation.Factory.class))
				.extracting("configs").asInstanceOf(InstanceOfAssertFactories.ARRAY)
				.extracting("field.name", "keyNames.toArray")
				.asInstanceOf(InstanceOfAssertFactories.list(Tuple.class));
	}

	@Test
	public void shouldCreateRemoteFields() {
		this.contextRunner
				.withPropertyValues(
						"spring.sleuth.propagation-keys=x-vcap-request-id,country-code")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(
								tuple("x-vcap-request-id",
										new String[] { "x-vcap-request-id" }),
								tuple("country-code", new String[] { "country-code" })));
	}

	@Test
	public void shouldCreateBaggageFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage-keys=country-code")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(tuple("country-code", new String[] {
								"baggage-country-code", "baggage_country-code" })));
	}

	@Test
	public void canCreateBaggageFieldsWithJavaConfig() {
		this.contextRunner.withUserConfiguration(CustomBaggageConfiguration.class)
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(tuple("country-code", new String[] {
								"baggage-country-code", "baggage_country-code" })));
	}

	@Configuration
	static class CustomBaggageConfiguration {

		@Bean
		BaggagePropagationCustomizer countryCodeBaggageConfig() {
			return fb -> fb.add(
					SingleBaggageField.newBuilder(BaggageField.create("country-code"))
							.addKeyName("baggage-country-code")
							.addKeyName("baggage_country-code").build());
		}

	}

}
