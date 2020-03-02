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

import brave.http.HttpTracing;
import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * This runs Brave's integration tests with duplicate instrumentation. This is not a
 * situation that happens by default. However, if people follow instructions to customize
 * Reactor, it can.
 *
 * <p>
 * Ex.
 * https://github.com/spring-projects/spring-boot/blob/8190b8eafbc00258197e89376c92d081207688aa/spring-boot-project/spring-boot-docs/src/main/java/org/springframework/boot/docs/web/reactive/function/client/ReactorNettyClientCustomizationExample.java
 */
public class WebClientUsingInstrumentedReactorNettyHttpClientBraveTests
		extends WebClientBraveTests {

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

	/** This fakes auto-configuration which would configure reactor's HttpClient */
	@Configuration
	@Import(HttpClientBeanPostProcessor.class)
	static class WebClientBuilderConfiguration {

		@Bean
		HttpClient httpClient() {
			return ReactorNettyHttpClientBraveTests.testHttpClient();
		}

		@Bean
		ClientHttpConnector clientHttpConnector(HttpClient httpClient) {
			return new ReactorClientHttpConnector(httpClient);
		}

		@Bean
		WebClient.Builder webClientBuilder(ClientHttpConnector clientHttpConnector) {
			return WebClient.builder().clientConnector(clientHttpConnector);
		}

	}

}
