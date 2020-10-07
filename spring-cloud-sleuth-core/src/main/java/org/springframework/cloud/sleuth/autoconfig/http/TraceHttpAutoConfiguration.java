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

package org.springframework.cloud.sleuth.autoconfig.http;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.autoconfig.noop.NoOpHttpClientHandler;
import org.springframework.cloud.sleuth.autoconfig.noop.NoOpHttpServerHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable HTTP tracing via Spring Cloud Sleuth.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @author Tim Ysewyn
 * @since 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.http.enabled", matchIfMissing = true)
public class TraceHttpAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	HttpClientHandler defaultHttpClientHandler() {
		return new NoOpHttpClientHandler();
	}

	@Bean
	@ConditionalOnMissingBean
	HttpServerHandler defaultHttpServerHandler() {
		return new NoOpHttpServerHandler();
	}
}


