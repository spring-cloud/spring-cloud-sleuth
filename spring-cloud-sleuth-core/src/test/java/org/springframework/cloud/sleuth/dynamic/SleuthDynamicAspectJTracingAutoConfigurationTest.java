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

package org.springframework.cloud.sleuth.dynamic;

import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Taras Danylchuk
 */
public class SleuthDynamicAspectJTracingAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations
					.of(SleuthDynamicAspectJTracingAutoConfiguration.class));

	@Test
	public void shouldNotCreateAdvisorByDefault() {
		this.contextRunner.run((context) -> assertThat(context)
				.doesNotHaveBean("sleuthDynamicAspectJAdvisor"));
	}

	@Test
	public void shouldNotCreateAdvisorIfNoPropertySet() {
		this.contextRunner
				.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class))
				.run((context) -> assertThat(context)
						.doesNotHaveBean("sleuthDynamicAspectJAdvisor"));
	}

	@Test
	public void shouldNotCreateAdvisorIfNoTracerBeanInContext() {
		this.contextRunner.withPropertyValues("spring.sleuth.dynamic.tracing.expression="
				+ "execution(* org.springframework.cloud.sleuth.dynamic.TestClass.testMethodA(..))")
				.run((context) -> assertThat(context)
						.doesNotHaveBean("sleuthDynamicAspectJAdvisor"));
	}

	@Test
	public void shouldCreateAdvisor() {
		this.contextRunner
				.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class))
				.withPropertyValues("spring.sleuth.dynamic.tracing.expression="
						+ "execution(* org.springframework.cloud.sleuth.dynamic.TestClass.testMethodA(..))")
				.run((context) -> assertThat(context)
						.hasBean("sleuthDynamicAspectJAdvisor"));
	}

}
