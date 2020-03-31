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

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import brave.http.HttpTracing;
import brave.test.http.ITHttpAsyncClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import zipkin2.Callback;

import org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfigurationAccessorConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This runs Brave's integration tests, ensuring common instrumentation bugs aren't
 * present.
 */
// Function of spring context so that shutdown hooks happen!
abstract class ITSpringConfiguredReactorClient
		extends ITHttpAsyncClient<AnnotationConfigApplicationContext> {

	@BeforeAll
	@AfterAll
	public static void clear() {
		TraceReactorAutoConfigurationAccessorConfiguration.close();
	}

	final Class<?>[] componentClasses;

	/**
	 * @param componentClasses configure instrumentation given {@linkplain URI baseUrl},
	 * {@link HttpClient} and {@link HttpTracing} bindings exist.
	 */
	ITSpringConfiguredReactorClient(Class<?>... componentClasses) {
		this.componentClasses = componentClasses;
	}

	@Override
	final protected AnnotationConfigApplicationContext newClient(int port) {
		AnnotationConfigApplicationContext result = new AnnotationConfigApplicationContext();
		URI baseUrl = URI.create("http://127.0.0.1:" + server.getPort());
		result.registerBean(HttpTracing.class, () -> httpTracing);
		result.registerBean(HttpClient.class, () -> testHttpClient(baseUrl));
		result.registerBean(URI.class, () -> baseUrl);
		result.register(componentClasses);
		result.refresh();
		return result;
	}

	static HttpClient testHttpClient(URI baseUrl) {
		return HttpClient.create().baseUrl(baseUrl.toString())
				.tcpConfiguration(tcpClient -> tcpClient
						.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
						.doOnConnected(conn -> conn
								.addHandler(new ReadTimeoutHandler(1, TimeUnit.SECONDS))))
				.followRedirect(true);
	}

	@Override
	final protected void closeClient(AnnotationConfigApplicationContext context) {
		context.close(); // ensures shutdown hooks fire
	}

	@Override
	final protected void get(AnnotationConfigApplicationContext context,
			String pathIncludingQuery) {
		getMono(context, pathIncludingQuery).block();
	}

	@Override
	final protected void post(AnnotationConfigApplicationContext context,
			String pathIncludingQuery, String body) {
		postMono(context, pathIncludingQuery, body).block();
	}

	@Override
	final protected void getAsync(AnnotationConfigApplicationContext context, String path,
			Callback<Integer> callback) {
		TestHttpCallbackSubscriber.subscribe(getMono(context, path), callback);
	}

	/** Returns a {@link Mono} of the HTTP status code from the given "POST" request. */
	abstract Mono<Integer> postMono(AnnotationConfigApplicationContext context,
			String pathIncludingQuery, String body);

	/** Returns a {@link Mono} of the HTTP status code. */
	abstract Mono<Integer> getMono(AnnotationConfigApplicationContext context,
			String pathIncludingQuery);

	/**
	 * This assumes that implementations do not issue an HTTP request until
	 * {@link Subscription#request(long)} is called. Since a client span is only for
	 * remote operations, we should not create one when we know a network request won't
	 * happen. In this case, we ensure a canceled subscription doesn't end up traced.
	 */
	@Test
	public void cancelledSubscription_doesntTrace() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);

		BaseSubscriber<Integer> subscriber = new BaseSubscriber<Integer>() {
			@Override
			protected void hookOnSubscribe(Subscription subscription) {
				subscription.cancel();
				latch.countDown();
			}
		};

		getMono(client, "/foo").subscribe(subscriber);

		latch.await();

		assertThat(server.getRequestCount()).isZero();
		// post-conditions will prove no span was created
	}

	@Test
	public void cancelInFlight() throws Exception {
		BaseSubscriber<Integer> subscriber = new BaseSubscriber<Integer>() {
		};

		CountDownLatch latch = new CountDownLatch(1);

		server.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				subscriber.cancel();
				latch.countDown();
				return new MockResponse();
			}
		});

		getMono(client, "/foo").subscribe(subscriber);

		latch.await();

		assertThat(server.getRequestCount()).isOne();
		assertThat(takeSpan().tags()).containsKey("error");
	}

}
