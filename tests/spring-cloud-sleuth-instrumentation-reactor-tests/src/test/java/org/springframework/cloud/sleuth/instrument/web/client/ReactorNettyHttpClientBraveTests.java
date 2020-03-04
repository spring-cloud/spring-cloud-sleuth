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

import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * This runs Brave's integration tests, ensuring common instrumentation bugs aren't
 * present.
 */
// Function of spring context so that shutdown hooks happen!
public class ReactorNettyHttpClientBraveTests extends ITSpringConfiguredReactorClient {

	/**
	 * This uses Spring to instrument the {@link HttpClient} using a
	 * {@link BeanPostProcessor}.
	 */
	public ReactorNettyHttpClientBraveTests() {
		super(HttpClientBeanPostProcessor.class);
	}

	@Test
	@Ignore("TODO: NPE reading context: consider integrating TracingMapConnect with ScopePassingSpanSubscriber")
	@Override
	public void callbackContextIsFromInvocationTime() {
	}

	@Test
	@Ignore("TODO: reactor/reactor-netty#1000")
	@Override
	public void redirect() {
	}

	@Test
	@Ignore("TODO: reactor/reactor-netty#1000")
	@Override
	public void supportsPortableCustomization() {
	}

	@Test
	@Ignore("TODO: reactor/reactor-netty#1000")
	@Override
	@Deprecated
	public void supportsDeprecatedPortableCustomization() {
	}

	@Test
	@Ignore("TODO: reactor/reactor-netty#1000")
	@Override
	public void post() {
	}

	@Test
	@Ignore("TODO: reactor/reactor-netty#1000")
	@Override
	public void customSampler() {
	}

	@Test
	@Ignore("TODO: reactor/reactor-netty#1000")
	@Override
	public void httpPathTagExcludesQueryParams() {
	}

	@Test
	@Ignore("HttpClient has no function to retrieve the wire request from the response")
	@Override
	public void readsRequestAtResponseTime() {
	}

	@Override
	Mono<Integer> postMono(AnnotationConfigApplicationContext context,
			String pathIncludingQuery, String body) {
		return context.getBean(HttpClient.class).post()
				.send(ByteBufFlux.fromString(Mono.just(body))).uri(pathIncludingQuery)
				.response().map(r -> r.status().code());
	}

	@Override
	Mono<Integer> getMono(AnnotationConfigApplicationContext context,
			String pathIncludingQuery) {
		return context.getBean(HttpClient.class).get().uri(pathIncludingQuery).response()
				.map(r -> r.status().code());
	}

}
