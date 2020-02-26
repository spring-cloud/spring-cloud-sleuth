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

import brave.http.HttpTracing;
import brave.test.http.ITHttpAsyncClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import zipkin2.Callback;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * This runs Brave's integration tests, ensuring common instrumentation bugs aren't
 * present.
 */
// Function of spring context so that shutdown hooks happen!
public class ReactorNettyHttpClientBraveTests
		extends ITHttpAsyncClient<AnnotationConfigApplicationContext> {

	/**
	 * This uses Spring to instrument the {@link HttpClient} using a
	 * {@link BeanPostProcessor}.
	 */
	@Override
	protected AnnotationConfigApplicationContext newClient(int port) {
		AnnotationConfigApplicationContext result = new AnnotationConfigApplicationContext();
		result.registerBean(HttpTracing.class, () -> httpTracing);
		result.registerBean(HttpClient.class,
				() -> testHttpClient().baseUrl("http://127.0.0.1:" + port));
		result.register(HttpClientBeanPostProcessor.class);
		result.refresh();
		return result;
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
	protected void closeClient(AnnotationConfigApplicationContext context) {
		context.close(); // ensures shutdown hooks fire
	}

	@Override
	protected void get(AnnotationConfigApplicationContext context,
			String pathIncludingQuery) {
		context.getBean(HttpClient.class).get().uri(pathIncludingQuery).response()
				.block();
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
	protected void post(AnnotationConfigApplicationContext context,
			String pathIncludingQuery, String body) {
		context.getBean(HttpClient.class).post()
				.send(ByteBufFlux.fromString(Mono.just(body))).uri(pathIncludingQuery)
				.response().block();
	}

	@Override
	protected void getAsync(AnnotationConfigApplicationContext context, String path,
			Callback<Void> callback) {
		Mono<HttpClientResponse> request = context.getBean(HttpClient.class).get()
				.uri(path).response();

		TestCallbackSubscriber.subscribe(request, callback);
	}

}
