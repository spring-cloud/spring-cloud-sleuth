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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import brave.Span;
import brave.Tracer;
import brave.baggage.BaggagePropagation;
import brave.handler.SpanHandler;
import brave.propagation.B3Propagation;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration;
import org.springframework.cloud.sleuth.DisableSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static brave.Span.Kind.CLIENT;
import static brave.propagation.B3Propagation.Format.SINGLE_NO_PARENT;
import static org.assertj.core.api.BDDAssertions.then;

@SpringBootTest(classes = WebClientTests.TestConfiguration.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.sleuth.web.servlet.enabled=false", "spring.application.name=fooservice",
				"spring.sleuth.web.client.skip-pattern=/skip.*" })
@DirtiesContext
public class WebClientTests {

	@Autowired
	HttpClientBuilder httpClientBuilder; // #845

	@Autowired
	HttpAsyncClientBuilder httpAsyncClientBuilder; // #845

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracer tracer;

	@LocalServerPort
	int port;

	@Autowired
	FooController fooController;

	@AfterEach
	@BeforeEach
	public void close() {
		this.spans.clear();
		this.fooController.clear();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherServiceForHttpClient() throws Exception {
		then(this.spans).isEmpty();
		Span span = this.tracer.nextSpan().name("foo").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			String response = this.httpClientBuilder.build().execute(new HttpGet("http://localhost:" + this.port),
					new BasicResponseHandler());

			then(response).isNotEmpty();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.spans).isNotEmpty().extracting("traceId", String.class).containsOnly(span.context().traceIdString());
		then(this.spans).extracting("kind.name").contains("CLIENT");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void shouldAttachTraceIdWhenCallingAnotherServiceForAsyncHttpClient() throws Exception {
		Span span = this.tracer.nextSpan().name("foo").start();

		CloseableHttpAsyncClient client = this.httpAsyncClientBuilder.build();
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			client.start();
			Future<HttpResponse> future = client.execute(new HttpGet("http://localhost:" + this.port),
					new FutureCallback<HttpResponse>() {
						@Override
						public void completed(HttpResponse result) {

						}

						@Override
						public void failed(Exception ex) {

						}

						@Override
						public void cancelled() {

						}
					});
			then(future.get()).isNotNull();
		}
		finally {
			client.close();
		}

		then(this.tracer.currentSpan()).isNull();
		then(this.spans).isNotEmpty().extracting("traceId", String.class).containsOnly(span.context().traceIdString());
		then(this.spans).extracting("kind.name").contains("CLIENT");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(
			exclude = { GatewayClassPathWarningAutoConfiguration.class, GatewayAutoConfiguration.class })
	@DisableSecurity
	public static class TestConfiguration {

		@Bean
		BaggagePropagation.FactoryBuilder baggagePropagationFactoryBuilder() {
			// Use b3 single format as it is less verbose
			return BaggagePropagation.newFactoryBuilder(
					B3Propagation.newFactoryBuilder().injectFormat(CLIENT, SINGLE_NO_PARENT).build());
		}

		@Bean
		FooController fooController() {
			return new FooController();
		}

		@Bean
		Sampler testSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

	}

	@RestController
	public static class FooController {

		Span span;

		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			Map<String, String> map = new HashMap<>();
			for (String key : headers.keySet()) {
				map.put(key, headers.getFirst(key));
			}
			return map;
		}

		public Span getSpan() {
			return this.span;
		}

		public void clear() {
			this.span = null;
		}

	}

}
