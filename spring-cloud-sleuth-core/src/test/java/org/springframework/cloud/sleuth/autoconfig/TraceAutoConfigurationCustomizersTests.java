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

import brave.TracingCustomizer;
import brave.http.HttpTracingCustomizer;
import brave.propagation.CurrentTraceContextCustomizer;
import brave.propagation.ExtraFieldCustomizer;
import brave.rpc.RpcTracingCustomizer;
import brave.sampler.Sampler;
import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.instrument.rpc.TraceRpcAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceHttpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.BDDAssertions.then;

public class TraceAutoConfigurationCustomizersTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class,
					TraceWebAutoConfiguration.class, TraceHttpAutoConfiguration.class,
					TraceRpcAutoConfiguration.class))
			.withUserConfiguration(Customizers.class);

	@Test
	public void should_apply_customizers() {
		this.contextRunner.withPropertyValues("spring.sleuth.baggage-keys=my-baggage")
				.run((context) -> {
					Customizers bean = context.getBean(Customizers.class);

					shouldApplyCustomizations(bean);
					shouldNotOverrideTheDefaults(context);
				});
	}

	@Test
	public void should_apply_extra_field_customizer_when_no_extra_properties_are_defined() {
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
		then(bean.extraFieldCustomizerApplied).isTrue();
		then(bean.httpCustomizerApplied).isTrue();
		then(bean.rpcCustomizerApplied).isTrue();
	}

	@Configuration
	static class Customizers {

		boolean tracingCustomizerApplied;

		boolean contextCustomizerApplied;

		boolean extraFieldCustomizerApplied;

		boolean httpCustomizerApplied;

		boolean rpcCustomizerApplied;

		@Bean
		TracingCustomizer sleuthTracingCustomizer() {
			return builder -> tracingCustomizerApplied = true;
		}

		@Bean
		CurrentTraceContextCustomizer sleuthCurrentTraceContextCustomizer() {
			return builder -> contextCustomizerApplied = true;
		}

		@Bean
		ExtraFieldCustomizer sleuthExtraFieldCustomizer() {
			return builder -> extraFieldCustomizerApplied = true;
		}

		@Bean
		HttpTracingCustomizer sleuthHttpTracingCustomizer() {
			return builder -> httpCustomizerApplied = true;
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
