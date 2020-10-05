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

package org.springframework.cloud.sleuth.instrument.web.client;

import reactor.netty.http.client.HttpClient;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.commons.httpclient.HttpClientConfiguration;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.api.propagation.Propagator;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} enables span information propagation when using
 * {@link RestTemplate}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.web.client.enabled", matchIfMissing = true)
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
@AutoConfigureBefore(HttpClientConfiguration.class)
class TraceWebClientAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpHeadersFilter.class)
	static class HttpHeadersFilterConfig {

		@Bean
		HttpHeadersFilter traceRequestHttpHeadersFilter(Tracer tracer, HttpClientHandler handler,
				Propagator propagator) {
			return TraceRequestHttpHeadersFilter.create(tracer, handler, propagator);
		}

		@Bean
		HttpHeadersFilter traceResponseHttpHeadersFilter(Tracer tracer, HttpClientHandler handler,
				Propagator propagator) {
			return TraceResponseHttpHeadersFilter.create(tracer, handler, propagator);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpClient.class)
	static class NettyConfiguration {

		@Bean
		static HttpClientBeanPostProcessor httpClientBeanPostProcessor(ConfigurableApplicationContext springContext) {
			return new HttpClientBeanPostProcessor(springContext);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(WebClient.class)
	@ConditionalOnProperty(value = "spring.sleuth.web.webclient.enabled", matchIfMissing = true)
	static class WebClientConfig {

		@Bean
		static TraceWebClientBeanPostProcessor traceWebClientBeanPostProcessor(
				ConfigurableApplicationContext springContext) {
			return new TraceWebClientBeanPostProcessor(springContext);
		}

	}

}
