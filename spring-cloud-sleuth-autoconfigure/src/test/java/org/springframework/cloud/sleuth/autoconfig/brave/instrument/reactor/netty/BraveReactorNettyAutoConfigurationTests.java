/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.sleuth.autoconfig.brave.instrument.reactor.netty;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.sleuth.autoconfig.brave.BraveAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class BraveReactorNettyAutoConfigurationTests {
	@Test
	void should_not_auto_configure_brave_reactor_netty_by_default() {
		new ApplicationContextRunner()
				.withConfiguration(
						AutoConfigurations.of(BraveAutoConfiguration.class, BraveReactorNettyAutoConfiguration.class))
				.run(context -> assertThat(context).doesNotHaveBean(NettyServerCustomizer.class).doesNotHaveBean(HttpClientCustomizer.class));
	}
	@Test
	void should_not_auto_configure_brave_reactor_netty_when_no_http_tracing_on_classpath() {
		new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.reactor.netty.debug.enabled=true")
				.withClassLoader(new FilteredClassLoader("brave.http.HttpTracing"))
				.withConfiguration(
						AutoConfigurations.of(BraveAutoConfiguration.class, BraveReactorNettyAutoConfiguration.class))
				.run(context -> assertThat(context).doesNotHaveBean(NettyServerCustomizer.class).doesNotHaveBean(HttpClientCustomizer.class));
	}
	@Test
	void should_not_auto_configure_brave_reactor_netty_when_no_reactor_netty_brave_on_classpath() {
		new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.reactor.netty.debug.enabled=true")
				.withClassLoader(new FilteredClassLoader("reactor.netty.http.brave.ReactorNettyHttpTracing"))
				.withConfiguration(
						AutoConfigurations.of(BraveAutoConfiguration.class, BraveReactorNettyAutoConfiguration.class))
				.run(context -> assertThat(context).doesNotHaveBean(NettyServerCustomizer.class).doesNotHaveBean(HttpClientCustomizer.class));
	}

	@Test
	void should_auto_configure_brave_reactor_netty_when_property_set() {
		new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.reactor.netty.debug.enabled=true")
				.withConfiguration(
						AutoConfigurations.of(BraveAutoConfiguration.class, BraveReactorNettyAutoConfiguration.class))
				.run(context -> assertThat(context).hasSingleBean(NettyServerCustomizer.class).hasSingleBean(HttpClientCustomizer.class));
	}
}
