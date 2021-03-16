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

package org.springframework.cloud.sleuth.autoconfig.instrument.web.client;

import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.autoconfig.TraceNoOpAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.client.TraceRequestHttpHeadersFilter;
import org.springframework.cloud.sleuth.instrument.web.client.TraceResponseHttpHeadersFilter;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.sleuth.noop.enabled=true").withConfiguration(
					AutoConfigurations.of(TraceNoOpAutoConfiguration.class, TraceWebClientAutoConfiguration.class));

	@Test
	void should_not_create_gateway_trace_filters_when_reactor_netty_client_on_classpath() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(HttpHeadersFilter.class));
	}

	@Test
	void should_create_gateway_trace_filters_when_reactor_netty_client_not_on_classpath() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(HttpClient.class))
				.run(context -> assertThat(context).hasSingleBean(TraceResponseHttpHeadersFilter.class)
						.hasSingleBean(TraceRequestHttpHeadersFilter.class));
	}

}
