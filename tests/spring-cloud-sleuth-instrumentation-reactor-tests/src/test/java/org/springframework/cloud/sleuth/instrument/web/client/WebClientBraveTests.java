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

package org.springframework.cloud.sleuth.instrument.web.client;

import brave.http.HttpTracing;
import brave.test.http.ITHttpAsyncClient;
import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import zipkin2.Callback;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * This runs Brave's integration tests without underlying instrumentation, which would
 * happen when a 3rd party client like Jetty is in use.
 */
// Function of spring context so that shutdown hooks happen!
public class WebClientBraveTests
		extends ITHttpAsyncClient<AnnotationConfigApplicationContext> {

	/**
	 * This uses Spring to instrument the {@link WebClient} using a
	 * {@link BeanPostProcessor}.
	 */
	@Override
	protected AnnotationConfigApplicationContext newClient(int port) {
		AnnotationConfigApplicationContext result = new AnnotationConfigApplicationContext();
		result.registerBean(HttpTracing.class, () -> httpTracing);
		result.register(WebClientBuilderConfiguration.class);
		result.register(TraceWebClientBeanPostProcessor.class);
		result.refresh();
		return result;
	}

	@Override
	protected void closeClient(AnnotationConfigApplicationContext context) {
		context.close(); // ensures shutdown hooks fire
	}

	@Override
	protected void get(AnnotationConfigApplicationContext context,
			String pathIncludingQuery) {
		client(context).get().uri(pathIncludingQuery).exchange().block();
	}

	@Override
	protected void post(AnnotationConfigApplicationContext context,
			String pathIncludingQuery, String body) {
		client(context).post().uri(pathIncludingQuery).body(BodyInserters.fromValue(body))
				.exchange().block();
	}

	@Override
	protected void getAsync(AnnotationConfigApplicationContext context, String path,
			Callback<Void> callback) {
		Mono<ClientResponse> request = client(context).get().uri(path).exchange();

		TestCallbackSubscriber.subscribe(request, callback);
	}

	@Test
	@Ignore("TODO: reactor/reactor-netty#1000")
	@Override
	public void redirect() {
	}

	@Test
	@Ignore("WebClient has no portable function to retrieve the server address")
	@Override
	public void reportsServerAddress() {
	}

	WebClient client(AnnotationConfigApplicationContext context) {
		return context.getBean(WebClient.Builder.class)
				.baseUrl("http://127.0.0.1:" + server.getPort()).build();
	}

	/**
	 * This fakes auto-configuration which wouldn't configure reactor's trace
	 * instrumentation.
	 */
	@Configuration
	static class WebClientBuilderConfiguration {

		@Bean
		HttpClient httpClient() {
			return ReactorNettyHttpClientBraveTests.testHttpClient();
		}

		@Bean
		WebClient.Builder webClientBuilder(HttpClient httpClient) {
			return WebClient.builder()
					.clientConnector(new ReactorClientHttpConnector(httpClient));
		}

	}

}
