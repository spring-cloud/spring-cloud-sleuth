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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import brave.http.HttpTracing;
import brave.test.http.ITHttpAsyncClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.util.context.Context;
import zipkin2.Callback;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * This runs Brave's integration tests, ensuring common instrumentation bugs aren't
 * present.
 */
public class ReactorNettyHttpClientBraveTests extends ITHttpAsyncClient<HttpClient> {

	/**
	 * This uses Spring to instrument the {@link HttpClient} using a
	 * {@link BeanPostProcessor}.
	 */
	@Override
	protected HttpClient newClient(int port) {
		AnnotationConfigApplicationContext result = new AnnotationConfigApplicationContext();
		result.registerBean(HttpTracing.class, () -> httpTracing);
		result.registerBean(HttpClient.class,
				ReactorNettyHttpClientBraveTests::testHttpClient);
		result.register(HttpClientBeanPostProcessor.class);
		result.refresh();
		return result.getBean(HttpClient.class).baseUrl("http://127.0.0.1:" + port);
	}

	static HttpClient testHttpClient() {
		return HttpClient.create()
				.tcpConfiguration(tcpClient -> tcpClient
						.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
						.doOnConnected(conn -> conn
								.addHandler(new ReadTimeoutHandler(1, TimeUnit.SECONDS))))
				.followRedirect(true);
	}

	@Override
	protected void closeClient(HttpClient client) {
		// HttpClient is not Closeable
	}

	@Override
	protected void get(HttpClient client, String pathIncludingQuery) {
		client.get().uri(pathIncludingQuery).response().block();
	}

	@Test
	@Ignore("TODO: consider integrating TracingMapConnect with ScopePassingSpanSubscriber")
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

	@Override
	protected void post(HttpClient client, String pathIncludingQuery, String body) {
		client.post().send(ByteBufFlux.fromString(Mono.just(body)))
				.uri(pathIncludingQuery).response().block();
	}

	@Override
	protected void getAsync(HttpClient client, String path, Callback<Void> callback) {
		Mono<HttpClientResponse> request = client.get().uri(path).response();

		request.subscribe(new CoreSubscriber<HttpClientResponse>() {

			final AtomicReference<Subscription> ref = new AtomicReference<>();

			@Override
			public void onSubscribe(Subscription s) {
				if (Operators.validate(ref.getAndSet(s), s)) {
					s.request(Long.MAX_VALUE);
				}
				else {
					s.cancel();
				}
			}

			@Override
			public void onNext(HttpClientResponse t) {
				Subscription s = ref.getAndSet(null);
				if (s != null) {
					callback.onSuccess(null);
					s.cancel();
				}
				else {
					Operators.onNextDropped(t, currentContext());
				}
			}

			@Override
			public void onError(Throwable t) {
				if (ref.getAndSet(null) != null) {
					callback.onError(t);
				}
			}

			@Override
			public void onComplete() {
				if (ref.getAndSet(null) != null) {
					callback.onSuccess(null);
				}
			}

			@Override
			public Context currentContext() {
				return Context.empty();
			}
		});
	}

}
