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

package org.springframework.cloud.sleuth.autoconfig.instrument.web;

import org.junit.Test;
import org.mockito.BDDMockito;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.instrument.web.TraceWebAspect;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michał Ziemba
 */
public class TraceWebServletConfigurationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TraceWebAutoConfiguration.class))
			.withUserConfiguration(TestConfig.class);

	@Test
	public void shouldNotCreateTracedWebBeansWhenServletClassMissing() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(HandlerInterceptorAdapter.class)).run((context) -> {
			assertThat(context).doesNotHaveBean(TraceWebAspect.class);
		});
	}

	@Test
	public void shouldCreateTracedWebBeansWhenServletClassNotMissing() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(TraceWebAspect.class);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		Tracer tracer() {
			return BDDMockito.mock(Tracer.class);
		}

		@Bean
		CurrentTraceContext currentTraceContext() {
			return BDDMockito.mock(CurrentTraceContext.class);
		}

		@Bean
		SpanNamer spanNamer() {
			return new DefaultSpanNamer();
		}

		@Bean
		HttpServerHandler httpServerHandler() {
			return BDDMockito.mock(HttpServerHandler.class);
		}

	}

}
