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

import brave.TracingCustomizer;
import brave.baggage.BaggagePropagationCustomizer;
import brave.http.HttpTracingCustomizer;
import brave.messaging.MessagingTracingCustomizer;
import brave.propagation.CurrentTraceContextCustomizer;
import brave.propagation.Propagation;
import brave.rpc.RpcTracingCustomizer;
import brave.sampler.Sampler;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.brave.instrument.messaging.BraveMessagingAutoConfiguration;
import org.springframework.cloud.sleuth.brave.instrument.rpc.BraveRpcAutoConfiguration;
import org.springframework.cloud.sleuth.brave.instrument.web.BraveHttpConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageHeaderAccessor;

import static org.assertj.core.api.BDDAssertions.then;

public class BraveAutoConfigurationCustomizersTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(BraveAutoConfiguration.class, BraveHttpConfiguration.class,
					BraveRpcAutoConfiguration.class, BraveMessagingAutoConfiguration.class,
					FakeSpringMessagingAutoConfiguration.class))
			.withUserConfiguration(Customizers.class);

	@Test
	public void should_apply_customizers() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage.remote-fields=country-code").run((context) -> {
			Customizers bean = context.getBean(Customizers.class);

			shouldApplyCustomizations(bean);
			shouldNotOverrideTheDefaults(context);
		});
	}

	@Test
	public void should_apply_baggage_customizer_when_no_baggage_properties_are_defined() {
		this.contextRunner.run((context) -> {
			Customizers bean = context.getBean(Customizers.class);

			shouldApplyCustomizations(bean);
		});
	}

	private void shouldNotOverrideTheDefaults(AssertableApplicationContext context) {
		then(context.getBean(Sampler.class)).isSameAs(Sampler.ALWAYS_SAMPLE);
	}

	private void shouldApplyCustomizations(Customizers bean) {
		then(bean.tracingCustomizerApplied).isTrue();
		then(bean.contextCustomizerApplied).isTrue();
		then(bean.baggagePropagationCustomizerApplied).isTrue();
		then(bean.httpCustomizerApplied).isTrue();
		then(bean.rpcCustomizerApplied).isTrue();
	}

	// SQS has a dependency on the getter and this is better than exposing things public
	@Configuration(proxyBeanMethods = false)
	static class FakeSpringMessagingAutoConfiguration {

		@Bean
		Propagation.Getter<MessageHeaderAccessor, String> traceMessagePropagationGetter() {
			return (headers, key) -> null;
		}

	}

	@Configuration(proxyBeanMethods = false)
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
