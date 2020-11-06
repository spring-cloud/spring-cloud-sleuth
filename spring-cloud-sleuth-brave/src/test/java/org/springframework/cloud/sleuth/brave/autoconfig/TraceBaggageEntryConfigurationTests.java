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

package org.springframework.cloud.sleuth.brave.autoconfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import brave.baggage.BaggageField;
import brave.baggage.BaggageFields;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.BaggagePropagationCustomizer;
import brave.baggage.CorrelationScopeConfig;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.baggage.CorrelationScopeCustomizer;
import brave.baggage.CorrelationScopeDecorator;
import brave.handler.SpanHandler;
import brave.propagation.Propagation;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.brave.autoconfig.TraceBaggageConfiguration.BaggageTagSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.array;

public class TraceBaggageEntryConfigurationTests {

	static final Set EMPTY_ARRAY = new HashSet();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceBaggageConfiguration.class));

	@Test
	public void shouldCreateLocalFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage.local-fields=bp")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context).containsOnly(tuple("bp", EMPTY_ARRAY)));
	}

	@Test
	public void shouldCreateLocalFields_oldName() {
		this.contextRunner.withPropertyValues("spring.sleuth.local-keys=bp")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context).containsOnly(tuple("bp", EMPTY_ARRAY)));
	}

	static ListAssert<Tuple> assertThatBaggageFieldNameToKeyNames(AssertableApplicationContext context) {
		return assertThat(context.getBean(Propagation.Factory.class)).extracting("configs")
				.asInstanceOf(InstanceOfAssertFactories.ARRAY).extracting("field.name", "keyNames")
				.asInstanceOf(InstanceOfAssertFactories.list(Tuple.class));
	}

	@Disabled("Brave broke its internal implementation and tests are bound to it - will skip for now")
	@Test
	public void shouldCreateRemoteFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage.remote-fields=x-vcap-request-id,country-code")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context).containsOnly(
						tuple("x-vcap-request-id", new HashSet<>(Collections.singletonList("x-vcap-request-id")),
								tuple("country-code", new HashSet<>(Collections.singletonList("country-code"))))));
	}

	@Disabled("Brave broke its internal implementation and tests are bound to it - will skip for now")
	@Test
	public void shouldCreateRemoteFields_oldName() {
		this.contextRunner.withPropertyValues("spring.sleuth.propagation-keys=x-vcap-request-id,country-code")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context).containsOnly(
						tuple("x-vcap-request-id", new HashSet<>(Collections.singletonList("x-vcap-request-id")),
								tuple("country-code", new HashSet<>(Collections.singletonList("country-code"))))));
	}

	@Test
	public void shouldCreateDeprecatedBaggageFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage-keys=country-code")
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context).containsOnly(tuple("country-code",
						new HashSet<>(Arrays.asList("baggage-country-code", "baggage_country-code")))));
	}

	@Test
	public void canCreateDeprecatedBaggageFieldsWithJavaConfig() {
		this.contextRunner.withUserConfiguration(CustomBaggageConfiguration.class)
				.run((context) -> assertThatBaggageFieldNameToKeyNames(context).containsOnly(tuple("country-code",
						new HashSet<>(Arrays.asList("baggage-country-code", "baggage_country-code")))));
	}

	@Test
	public void shouldCreateTagHandler() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage.tag-fields=x-vcap-request-id,country-code")
				.run((context) -> assertThatFieldNamesToTag(context).containsOnly("x-vcap-request-id", "country-code"));
	}

	@Test
	public void shouldCreateTagHandler_yaml() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.baggage.tag-fields[0]=x-vcap-request-id",
						"spring.sleuth.baggage.tag-fields[1]=country-code")
				.run((context) -> assertThatFieldNamesToTag(context).containsOnly("x-vcap-request-id", "country-code"));
	}

	@Test
	public void shouldCreateTagHandler_oldProperty() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.propagation.tag.whitelisted-keys=x-vcap-request-id,country-code")
				.run((context) -> assertThatFieldNamesToTag(context).containsOnly("x-vcap-request-id", "country-code"));
	}

	@Test
	public void shouldCreateTagHandler_oldProperty_yaml() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.propagation.tag.whitelisted-keys[0]=x-vcap-request-id",
						"spring.sleuth.propagation.tag.whitelisted-keys[1]=country-code")
				.run((context) -> assertThatFieldNamesToTag(context).containsOnly("x-vcap-request-id", "country-code"));
	}

	static AbstractListAssert<?, List<? extends String>, String, ObjectAssert<String>> assertThatFieldNamesToTag(
			AssertableApplicationContext context) {
		return assertThat(context.getBean(SpanHandler.class)).isInstanceOf(BaggageTagSpanHandler.class)
				.extracting("fieldsToTag").asInstanceOf(array(BaggageField[].class)).extracting(BaggageField::name);
	}

	@Test
	public void noopOnNoTagFields() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage.tag-fields=").run((context) -> {
			assertThat(context.getBean(SpanHandler.class)).isSameAs(SpanHandler.NOOP);
		});
	}

	@Test
	public void canAddOldCorrelationFieldsForLogScraping() {
		this.contextRunner.withUserConfiguration(OldCorrelationFieldsForLogScrapingConfiguration.class)
				.run((context) -> assertThat(context.getBean(CorrelationScopeDecorator.class)).extracting("fields")
						.asInstanceOf(array(SingleCorrelationField[].class)).extracting(SingleCorrelationField::name)
						.containsExactly("traceId", "spanId", "parentId", "spanExportable"));
	}

	@Test
	public void canMakeAllCorrelationFieldsDirty() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage.correlation-fields=country-code")
				.withUserConfiguration(DirtyCorrelationFieldConfiguration.class)
				.run((context) -> assertThat(context.getBean(CorrelationScopeDecorator.class)).extracting("fields")
						.asInstanceOf(array(SingleCorrelationField[].class)).filteredOn(c -> !c.readOnly())
						.extracting(SingleCorrelationField::dirty).containsExactly(true));
	}

	@Configuration(proxyBeanMethods = false)
	static class DirtyCorrelationFieldConfiguration {

		@Bean
		CorrelationScopeCustomizer makeCorrelationFieldsDirty() {
			return b -> {
				Set<CorrelationScopeConfig> configs = b.configs();
				b.clear();

				for (CorrelationScopeConfig config : configs) {
					if (config instanceof SingleCorrelationField) {
						SingleCorrelationField field = (SingleCorrelationField) config;
						if (!field.readOnly()) {
							config = field.toBuilder().dirty().build();
						}
					}
					b.add(config);
				}
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class OldCorrelationFieldsForLogScrapingConfiguration {

		@Bean
		CorrelationScopeCustomizer addParentAndSpanExportable() {
			return b -> b.add(SingleCorrelationField.create(BaggageFields.PARENT_ID))
					.add(SingleCorrelationField.newBuilder(BaggageFields.SAMPLED).name("spanExportable").build());
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomBaggageConfiguration {

		@Bean
		BaggagePropagationCustomizer countryCodeBaggageConfig() {
			return fb -> fb.add(SingleBaggageField.newBuilder(BaggageField.create("country-code"))
					.addKeyName("baggage-country-code").addKeyName("baggage_country-code").build());
		}

	}

}
