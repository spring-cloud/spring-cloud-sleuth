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

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.B3SinglePropagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.test.IntegrationTestSpanHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.ClassRule;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This tests {@link HttpClient} instrumentation performed by
 * {@link HttpClientBeanPostProcessor}, as wired by auto-configuration.
 *
 * <p>
 * <em>Note:</em> {@link HttpClient} can be an implementation of {@link WebClient}, so
 * care should be taken to also test that integration. For example, it would be easy to
 * create duplicate client spans for the same request.
 */
@SpringBootTest(classes = ReactorNettyHttpClientSpringBootTests.TestConfiguration.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ReactorNettyHttpClientSpringBootTests {

	@ClassRule
	public static IntegrationTestSpanHandler spanHandler = new IntegrationTestSpanHandler();

	DisposableServer disposableServer;

	@Autowired
	HttpClient httpClient;

	@Autowired
	CurrentTraceContext currentTraceContext;

	TraceContext context = TraceContext.newBuilder().traceId(1).spanId(1).sampled(true).build();

	@AfterEach
	public void tearDown() {
		if (disposableServer != null) {
			disposableServer.disposeNow();
		}
	}

	@Test
	public void shouldRecordRemoteEndpoint() throws Exception {
		disposableServer = HttpServer.create().port(0).handle((in, out) -> out.sendString(Flux.just("foo"))).bindNow();

		HttpClientResponse response = httpClient.port(disposableServer.port()).get().uri("/").response().block();

		assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);

		MutableSpan clientSpan = spanHandler.takeRemoteSpan(CLIENT);

		assertThat(clientSpan.remoteIp()).isNotNull();
		assertThat(clientSpan.remotePort()).isNotZero();
	}

	@Test
	public void shouldUseInvocationContext() throws Exception {
		disposableServer = HttpServer.create().port(0)
				// this reads the trace context header, b3, returning it in the response
				.handle((in, out) -> out.sendString(Flux.just(in.requestHeaders().get("b3")))).bindNow();

		String b3SingleHeaderReadByServer;
		try (Scope ws = currentTraceContext.newScope(context)) {
			b3SingleHeaderReadByServer = httpClient.port(disposableServer.port()).get().uri("/").responseContent()
					.aggregate().asString().block();
		}

		MutableSpan clientSpan = spanHandler.takeRemoteSpan(CLIENT);

		assertThat(b3SingleHeaderReadByServer)
				.isEqualTo(context.traceIdString() + "-" + clientSpan.id() + "-1-" + context.spanIdString());
	}

	@Test
	public void shouldSendTraceContextToServer_rootSpan() throws Exception {
		disposableServer = HttpServer.create().port(0)
				// this reads the trace context header, b3, returning it in the response
				.handle((in, out) -> out.sendString(Flux.just(in.requestHeaders().get("b3")))).bindNow();

		Mono<String> request = httpClient.port(disposableServer.port()).get().uri("/").responseContent().aggregate()
				.asString();

		String b3SingleHeaderReadByServer = request.block();

		MutableSpan clientSpan = spanHandler.takeRemoteSpan(CLIENT);

		assertThat(b3SingleHeaderReadByServer).isEqualTo(clientSpan.traceId() + "-" + clientSpan.id() + "-1");
	}

	@Test
	public void shouldRecordRequestError() {
		disposableServer = HttpServer.create().port(0).handle((req, resp) -> {
			throw new RuntimeException("test");
		}).bindNow();

		Mono<String> request = httpClient.port(disposableServer.port()).get().uri("/").responseContent().aggregate()
				.asString();

		assertThatThrownBy(request::block).hasCauseInstanceOf(PrematureCloseException.class);

		spanHandler.takeRemoteSpanWithError(CLIENT);
	}

	@Configuration
	@EnableAutoConfiguration
	static class TestConfiguration {

		@Bean
		Propagation.Factory propagationFactory() {
			return B3SinglePropagation.FACTORY;
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return spanHandler;
		}

		@Bean
		HttpClient reactorHttpClient() {
			return HttpClient.create();
		}

	}

}
