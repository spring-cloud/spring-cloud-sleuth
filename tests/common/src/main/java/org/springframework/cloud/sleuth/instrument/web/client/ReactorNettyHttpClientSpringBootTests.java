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

import io.netty.handler.codec.http.HttpResponseStatus;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.client.PrematureCloseException;
import reactor.netty.http.server.HttpServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * This tests {@link HttpClient} instrumentation performed by
 * {@link HttpClientBeanPostProcessor}, as wired by auto-configuration.
 *
 * <p>
 * <em>Note:</em> {@link HttpClient} can be an implementation of {@link WebClient}, so
 * care should be taken to also test that integration. For example, it would be easy to
 * create duplicate client spans for the same request.
 */
@ContextConfiguration(classes = ReactorNettyHttpClientSpringBootTests.TestConfiguration.class)
public abstract class ReactorNettyHttpClientSpringBootTests {

	DisposableServer disposableServer;

	@Autowired
	HttpClient httpClient;

	@Autowired
	CurrentTraceContext currentTraceContext;

	@Autowired
	TestSpanHandler handler;

	TraceContext parent = traceContext();

	@AfterEach
	public void tearDown() {
		if (disposableServer != null) {
			disposableServer.disposeNow();
		}
		this.handler.clear();
	}

	public abstract TraceContext traceContext();

	@Test
	public void shouldRecordRemoteEndpoint() throws Exception {
		disposableServer = HttpServer.create().port(0).handle((in, out) -> out.sendString(Flux.just("foo"))).bindNow();

		HttpClientResponse response = httpClient.port(disposableServer.port()).get().uri("/").response().block();

		Assertions.assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);

		FinishedSpan clientSpan = this.handler.takeRemoteSpan(Span.Kind.CLIENT);

		Assertions.assertThat(clientSpan.getRemoteIp()).isNotNull();
		Assertions.assertThat(clientSpan.getRemotePort()).isNotZero();
	}

	@Test
	public void shouldUseInvocationContext() throws Exception {
		disposableServer = HttpServer.create().port(0)
				// this reads the trace context header, b3, returning it in the response
				.handle((in, out) -> out.sendString(Flux.just(in.requestHeaders().get("b3")))).bindNow();

		String b3SingleHeaderReadByServer;
		try (CurrentTraceContext.Scope ws = currentTraceContext.newScope(parent)) {
			b3SingleHeaderReadByServer = httpClient.port(disposableServer.port()).get().uri("/").responseContent()
					.aggregate().asString().block();
		}

		FinishedSpan clientSpan = this.handler.takeRemoteSpan(Span.Kind.CLIENT);
		assertSingleB3Header(b3SingleHeaderReadByServer, clientSpan, parent);
	}

	public void assertSingleB3Header(String b3SingleHeaderReadByServer, FinishedSpan clientSpan, TraceContext parent) {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	@Test
	public void shouldSendTraceContextToServer_rootSpan() throws Exception {
		disposableServer = HttpServer.create().port(0)
				// this reads the trace context header, b3, returning it in the response
				.handle((in, out) -> out.sendString(Flux.just(in.requestHeaders().get("b3")))).bindNow();

		Mono<String> request = httpClient.port(disposableServer.port()).get().uri("/").responseContent().aggregate()
				.asString();

		String b3SingleHeaderReadByServer = request.block();

		FinishedSpan clientSpan = this.handler.takeRemoteSpan(Span.Kind.CLIENT);

		Assertions.assertThat(b3SingleHeaderReadByServer)
				.isEqualTo(clientSpan.getTraceId() + "-" + clientSpan.getSpanId() + "-1");
	}

	@Test
	public void shouldRecordRequestError() {
		disposableServer = HttpServer.create().port(0).handle((req, resp) -> {
			throw new RuntimeException("test");
		}).bindNow();

		Mono<String> request = httpClient.port(disposableServer.port()).get().uri("/").responseContent().aggregate()
				.asString();

		Assertions.assertThatThrownBy(request::block).hasCauseInstanceOf(PrematureCloseException.class);

		this.handler.takeRemoteSpanWithError(Span.Kind.CLIENT);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	public static class TestConfiguration {

		@Bean
		HttpClient reactorHttpClient() {
			return HttpClient.create();
		}

	}

}
