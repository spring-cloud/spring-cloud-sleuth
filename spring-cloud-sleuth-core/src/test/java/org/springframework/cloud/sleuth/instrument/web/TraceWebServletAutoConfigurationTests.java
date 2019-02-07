/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web;

import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michał Ziemba
 */
public class TraceWebServletAutoConfigurationTests {

	private static final String EXCEPTION_LOGGING_FILTER_BEAN_NAME = "exceptionThrowingFilter";

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class,
					TraceHttpAutoConfiguration.class, TraceWebAutoConfiguration.class,
					TraceWebServletAutoConfiguration.class));

	@Test
	public void shouldCreateExceptionLoggingFilterBeanByDefault() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasBean(EXCEPTION_LOGGING_FILTER_BEAN_NAME);
		});
	}

	@Test
	public void shouldCreateExceptionLoggingFilterBeanIfExplicitlyEnabled() {
		this.contextRunner
				.withPropertyValues(
						"spring.sleuth.web.exception-logging-filter-enabled=true")
				.run((context) -> {
					assertThat(context).hasBean(EXCEPTION_LOGGING_FILTER_BEAN_NAME);
				});
	}

	@Test
	public void shouldNotCreateExceptionLoggingFilterBeanIfDisabledInProperties() {
		this.contextRunner
				.withPropertyValues(
						"spring.sleuth.web.exception-logging-filter-enabled=false")
				.run((context) -> {
					assertThat(context)
							.doesNotHaveBean(EXCEPTION_LOGGING_FILTER_BEAN_NAME);
				});
	}

	@Test
	public void shouldNotCreateExceptionLoggingFilterBeanIfDisabledInPropertiesUsingCamelCase() {
		this.contextRunner
				.withPropertyValues(
						"spring.sleuth.web.exceptionLoggingFilterEnabled=false")
				.run((context) -> {
					assertThat(context)
							.doesNotHaveBean(EXCEPTION_LOGGING_FILTER_BEAN_NAME);
				});
	}

}
