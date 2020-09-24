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

package org.springframework.cloud.sleuth.brave.instrument.web.client;

import org.junit.After;
import org.junit.Ignore;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfigurationAccessorConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * This tests the reactor {@link HttpClient} in isolation of {@link WebClient} as it could
 * be used directly.
 */
@Ignore("TODO: Fix me")
public class ReactorNettyHttpClientBraveTests extends ITSpringConfiguredReactorClient {

	/**
	 * This borrows hooks from
	 * {@code org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfiguration}
	 * to ensure that the invocation trace context is set in scope for hooks like
	 * {@link Subscriber#onNext}.
	 *
	 * <p>
	 * We do this implicitly until
	 * <a href="https://github.com/reactor/reactor-netty/issues/1036">issue 1036</a>.
	 * Until then, there's no known way to directly instrument the
	 * {@code Mono<Connection>} created in
	 * {@code reactor.netty.http.client.MonoConnect$MonoHttpConnect} with
	 * {@code ScopePassingSpanSubscriber}. While this looks like cheating the test, Sleuth
	 * will always setup these hooks anyway unless "spring.sleuth.reactor.enabled=false".
	 */
	@Override
	protected AnnotationConfigApplicationContext newClient(int port) {
		TraceReactorAutoConfigurationAccessorConfiguration.close();
		AnnotationConfigApplicationContext context = super.newClient(port);
		TraceReactorAutoConfigurationAccessorConfiguration.setup(context);
		return context;
	}

	@After
	public void cleanupHooks() {
		TraceReactorAutoConfigurationAccessorConfiguration.close();
	}

	/**
	 * This uses Spring to instrument the {@link HttpClient} using a
	 * {@link BeanPostProcessor}.
	 */
	public ReactorNettyHttpClientBraveTests() {
		super(HttpClientBeanPostProcessor.class);
	}

	@Override
	Mono<Integer> postMono(AnnotationConfigApplicationContext context, String pathIncludingQuery, String body) {
		return context.getBean(HttpClient.class).post().send(ByteBufFlux.fromString(Mono.just(body)))
				.uri(pathIncludingQuery).response().map(r -> r.status().code());
	}

	@Override
	Mono<Integer> getMono(AnnotationConfigApplicationContext context, String pathIncludingQuery) {
		return context.getBean(HttpClient.class).get().uri(pathIncludingQuery).response().map(r -> r.status().code());
	}

}
