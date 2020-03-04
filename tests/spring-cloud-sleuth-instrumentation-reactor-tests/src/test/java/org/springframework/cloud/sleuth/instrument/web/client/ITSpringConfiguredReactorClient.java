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
import java.util.concurrent.TimeUnit;

import brave.http.HttpTracing;
import brave.test.http.ITHttpAsyncClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import zipkin2.Callback;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * This runs Brave's integration tests, ensuring common instrumentation bugs aren't
 * present.
 */
// Function of spring context so that shutdown hooks happen!
abstract class ITSpringConfiguredReactorClient
		extends ITHttpAsyncClient<AnnotationConfigApplicationContext> {

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

}
