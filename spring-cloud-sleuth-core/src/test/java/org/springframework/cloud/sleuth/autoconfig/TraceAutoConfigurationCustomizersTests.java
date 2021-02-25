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

import brave.TracingCustomizer;
import brave.baggage.BaggagePropagationCustomizer;
import brave.http.HttpTracingCustomizer;
import brave.messaging.MessagingTracingCustomizer;
import brave.propagation.CurrentTraceContextCustomizer;
import brave.propagation.ExtraFieldCustomizer;
import brave.propagation.Propagation;
import brave.rpc.RpcTracingCustomizer;
import brave.sampler.Sampler;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.instrument.messaging.TraceMessagingAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.rpc.TraceRpcAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceHttpAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageHeaderAccessor;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceAutoConfigurationCustomizersTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class,
					TraceHttpAutoConfiguration.class, TraceRpcAutoConfiguration.class,
					TraceMessagingAutoConfiguration.class,
					FakeSpringMessagingAutoConfiguration.class))
			.withUserConfiguration(Customizers.class);

	@Test
	public void should_apply_deprecated_customizers() {
		this.contextRunner.withUserConfiguration(DeprecatedCustomizers.class)
				.withPropertyValues("spring.sleuth.baggage-keys=my-baggage")
				.run((context) -> {
					Customizers bean = context.getBean(Customizers.class);
					DeprecatedCustomizers deprecated = context
							.getBean(DeprecatedCustomizers.class);

					shouldApplyOldCustomizations(bean, deprecated);
					shouldNotOverrideTheDefaults(context);
				});
	}

	@Test
	public void should_apply_deprecated_customizers_with_new_values() {
		this.contextRunner.withUserConfiguration(DeprecatedCustomizers.class)
				.withPropertyValues("spring.sleuth.baggage.remote-fields=country-code")
				.run((context) -> {
					Customizers bean = context.getBean(Customizers.class);
					DeprecatedCustomizers deprecated = context
							.getBean(DeprecatedCustomizers.class);

					shouldApplyOldCustomizations(bean, deprecated);
					shouldNotOverrideTheDefaults(context);
				});
	}

	@Test
	public void should_apply_customizers() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage-keys=my-baggage")
				.run((context) -> {
					Customizers bean = context.getBean(Customizers.class);
					assertThatDeprecatedCustomizersAreNotDefined(context);

					shouldApplyNewCustomizations(bean);
					shouldNotOverrideTheDefaults(context);
				});
	}

	private void assertThatDeprecatedCustomizersAreNotDefined(
			AssertableApplicationContext context) {
		try {
			context.getBean(DeprecatedCustomizers.class);
			BDDAssertions.fail("DeprecatedCustomizers bean should not be defined");
		}
		catch (NoSuchBeanDefinitionException ex) {

		}
	}

	@Test
	public void should_apply_customizers_with_new_values() {
		this.contextRunner
				.withPropertyValues("spring.sleuth.baggage.remote-fields=country-code")
				.run((context) -> {
					Customizers bean = context.getBean(Customizers.class);
					assertThatDeprecatedCustomizersAreNotDefined(context);

					shouldApplyNewCustomizations(bean);
					shouldNotOverrideTheDefaults(context);
				});
	}

	@Test
	public void should_apply_baggage_customizer_when_no_baggage_properties_are_defined() {
		this.contextRunner.run((context) -> {
			Customizers bean = context.getBean(Customizers.class);
			assertThatDeprecatedCustomizersAreNotDefined(context);

			shouldApplyNewCustomizations(bean);
		});
	}

	private void shouldNotOverrideTheDefaults(AssertableApplicationContext context) {
		then(context.getBean(Sampler.class)).isSameAs(Sampler.ALWAYS_SAMPLE);
	}

	private void shouldApplyOldCustomizations(Customizers bean,
			DeprecatedCustomizers deprecated) {
		then(bean.tracingCustomizerApplied).isTrue();
		then(bean.contextCustomizerApplied).isTrue();
		then(deprecated.extraFieldCustomizerApplied).isTrue();
		then(bean.baggagePropagationCustomizerApplied).isFalse();
		then(bean.httpCustomizerApplied).isTrue();
		then(bean.rpcCustomizerApplied).isTrue();
	}

	private void shouldApplyNewCustomizations(Customizers bean) {
		then(bean.tracingCustomizerApplied).isTrue();
		then(bean.contextCustomizerApplied).isTrue();
		then(bean.baggagePropagationCustomizerApplied).isTrue();
		then(bean.httpCustomizerApplied).isTrue();
		then(bean.rpcCustomizerApplied).isTrue();
	}

	// SQS has a dependency on the getter and this is better than exposing things public
	@Configuration
	static class FakeSpringMessagingAutoConfiguration {

		@Bean
		Propagation.Getter<MessageHeaderAccessor, String> traceMessagePropagationGetter() {
			return (headers, key) -> null;
		}

	}

	@Configuration
	static class DeprecatedCustomizers {

		boolean extraFieldCustomizerApplied;

		@Bean
		ExtraFieldCustomizer sleuthExtraFieldCustomizer() {
			return builder -> extraFieldCustomizerApplied = true;
		}

	}

	@Configuration
	static class Customizers {

		boolean tracingCustomizerApplied;

		boolean contextCustomizerApplied;

		boolean baggagePropagationCustomizerApplied;

		boolean httpCustomizerApplied;

		boolean rpcCustomizerApplied;

		boolean messagingCustomizerApplied;

		@Bean
		TracingCustomizer sleuthTracingCustomizer() {
			return builder -> tracingCustomizerApplied = true;
		}

		@Bean
		CurrentTraceContextCustomizer sleuthCurrentTraceContextCustomizer() {
			return builder -> contextCustomizerApplied = true;
		}

		@Bean
		BaggagePropagationCustomizer sleuthBaggagePropagationCustomizer() {
			return builder -> baggagePropagationCustomizerApplied = true;
		}

		@Bean
		HttpTracingCustomizer sleuthHttpTracingCustomizer() {
			return builder -> httpCustomizerApplied = true;
		}

		@Bean
		MessagingTracingCustomizer sleuthMessagingCustomizer() {
			return builder -> messagingCustomizerApplied = true;
		}

		@Bean
		RpcTracingCustomizer sleuthRpcTracingCustomizer() {
			return builder -> rpcCustomizerApplied = true;
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
