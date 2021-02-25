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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.net.URI;

import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * This runs Brave's integration tests without underlying instrumentation, which is
 * default in Spring Boot due to static instantiation in
 * {@link org.springframework.boot.autoconfigure.web.reactive.function.client.ClientHttpConnectorConfiguration}.
 */
@Ignore("TODO: Fix me")
public class WebClientBraveTests extends ITSpringConfiguredReactorClient {

	/**
	 * This uses Spring to instrument the {@link WebClient} using a
	 * {@link BeanPostProcessor}.
	 */
	public WebClientBraveTests() {
		super(WebClientConfiguration.class, WebClientAutoConfiguration.class, TraceWebClientBeanPostProcessor.class);
	}

	@Override
	Mono<Integer> getMono(AnnotationConfigApplicationContext context, String pathIncludingQuery) {
		return context.getBean(WebClient.Builder.class).build().get().uri(pathIncludingQuery).exchange()
				.map(ClientResponse::rawStatusCode);
	}

	@Override
	Mono<Integer> optionsMono(AnnotationConfigApplicationContext context, String path) {
		return context.getBean(WebClient.Builder.class).build().options().uri(path).exchange()
				.map(ClientResponse::rawStatusCode);
	}

	@Override
	Mono<Integer> postMono(AnnotationConfigApplicationContext context, String pathIncludingQuery, String body) {
		return context.getBean(WebClient.Builder.class).build().post().uri(pathIncludingQuery)
				.body(BodyInserters.fromValue(body)).exchange().map(ClientResponse::rawStatusCode);
	}

	@Test
	@Ignore("WebClient is blind to the implementation of redirects")
	@Override
	public void redirect() {
	}

	@Test
	@Ignore("WebClient has no portable function to retrieve the server address")
	@Override
	public void reportsServerAddress() {
	}

	@Test
	@Ignore("TODO: maybe refactor as an ExchangeFilterFunction to get the request from response")
	@Override
	public void readsRequestAtResponseTime() {
	}

	@Configuration(proxyBeanMethods = false)
	static class WebClientConfiguration {

		/**
		 * Normally, the HTTP connector would be statically initialized. This ensures the
		 * {@link HttpClient} is configured for the mock endpoint.
		 */
		@Bean
		@Order(0)
		public WebClientCustomizer clientConnectorCustomizer(HttpClient httpClient, URI baseUrl) {
			return (builder) -> builder.baseUrl(baseUrl.toString())
					.clientConnector(new ReactorClientHttpConnector(httpClient));
		}

	}

}
