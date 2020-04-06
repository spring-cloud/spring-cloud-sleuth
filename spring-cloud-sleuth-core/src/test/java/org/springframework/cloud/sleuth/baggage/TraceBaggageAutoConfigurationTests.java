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

package org.springframework.cloud.sleuth.baggage;

import java.util.List;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.BaggagePropagationCustomizer;
import brave.handler.FinishedSpanHandler;
import brave.propagation.Propagation;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class TraceBaggageAutoConfigurationTests {

	static final String[] EMPTY_ARRAY = {};

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(TraceBaggageAutoConfiguration.class));

	@Test
	public void shouldCreateLocalFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage.local-fields=bp")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(tuple("bp", EMPTY_ARRAY)));
	}

	@Test
	public void shouldCreateLocalFields_oldName() {
		this.contextRunner.withPropertyValues("spring.sleuth.local-keys=bp")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(tuple("bp", EMPTY_ARRAY)));
	}

	static ListAssert<Tuple> assertThatBaggageFieldNameToKeyNames(
			AssertableApplicationContext context) {
		return assertThat(context.getBean(Propagation.Factory.class))
				.extracting("handlersWithKeyNames")
				.asInstanceOf(InstanceOfAssertFactories.ARRAY)
				.extracting("handler.field.name", "keyNames")
				.asInstanceOf(InstanceOfAssertFactories.list(Tuple.class));
	}

	@Test
	public void shouldCreateRemoteFields() {
		this.contextRunner.withPropertyValues(
				"spring.sleuth.baggage.remote-fields=x-vcap-request-id,country-code")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(
								tuple("x-vcap-request-id",
										new String[] { "x-vcap-request-id" }),
								tuple("country-code", new String[] { "country-code" })));
	}

	@Test
	public void shouldCreateRemoteFields_oldName() {
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
	public void shouldCreateDeprecatedBaggageFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage-keys=country-code")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(tuple("country-code", new String[] {
								"baggage-country-code", "baggage_country-code" })));
	}

	@Test
	public void catCreateDeprecatedBaggageFieldsWithJavaConfig() {
		this.contextRunner.withUserConfiguration(CustomBaggageConfiguration.class)
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context)
						.containsOnly(tuple("country-code", new String[] {
								"baggage-country-code", "baggage_country-code" })));
	}

	@Test
	public void shouldCreateTagHandler() {
		this.contextRunner
				.withPropertyValues(
						"spring.sleuth.baggage.tag-fields=x-vcap-request-id,country-code")
				.run((context) -> assertThatFieldNamesToTag(context)
						.containsOnly("x-vcap-request-id", "country-code"));
	}

	@Test
	public void shouldCreateTagHandler_oldProperty() {
		this.contextRunner.withPropertyValues(
				"spring.sleuth.propagation.tag.whitelisted-keys=x-vcap-request-id,country-code")
				.run((context) -> assertThatFieldNamesToTag(context)
						.containsOnly("x-vcap-request-id", "country-code"));
	}

	static AbstractListAssert<?, List<? extends String>, String, ObjectAssert<String>> assertThatFieldNamesToTag(
			AssertableApplicationContext context) {
		return assertThat(context.getBean(FinishedSpanHandler.class))
				.isInstanceOf(BaggageTagFinishedSpanHandler.class)
				.extracting("fieldsToTag")
				.asInstanceOf(InstanceOfAssertFactories.array(BaggageField[].class))
				.extracting(BaggageField::name);
	}

	@Test
	public void noopOnNoTagFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage.tag-fields=")
				.run((context) -> {
					assertThat(context.getBean(FinishedSpanHandler.class))
							.isSameAs(FinishedSpanHandler.NOOP);
				});
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
