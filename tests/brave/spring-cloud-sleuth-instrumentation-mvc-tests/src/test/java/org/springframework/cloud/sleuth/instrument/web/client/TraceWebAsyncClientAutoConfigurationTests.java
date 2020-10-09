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

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Marcin Grzejszczak
 */

@SpringBootTest(classes = { TraceWebAsyncClientAutoConfigurationTests.TestConfiguration.class },
		webEnvironment = RANDOM_PORT)
public class TraceWebAsyncClientAutoConfigurationTests {

	@Autowired
	AsyncRestTemplate asyncRestTemplate;

	@Autowired
	Environment environment;

	@Autowired
	TestSpanHandler spans;

	@Autowired
	Tracing tracer;

	@BeforeEach
	public void setup() {
		this.spans.clear();
	}

	@Test
	public void should_close_span_upon_success_callback() throws ExecutionException, InterruptedException {
		brave.Span initialSpan = this.tracer.tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = this.tracer.tracer().withSpanInScope(initialSpan.start())) {
			ListenableFuture<ResponseEntity<String>> future = this.asyncRestTemplate
					.getForEntity("http://localhost:" + port() + "/foo", String.class);
			String result = future.get().getBody();

			then(result).isEqualTo("foo");
		}
		finally {
			initialSpan.finish();
		}

		Awaitility.await().untilAsserted(() -> {
			then(this.spans.spans().stream().filter(span -> Span.Kind.CLIENT == span.kind()).findFirst().get()).matches(
					span -> span.finishTimestamp() - span.startTimestamp() >= TimeUnit.MILLISECONDS.toMicros(100));
			then(this.tracer.tracer().currentSpan()).isNull();
		});
	}

	@Test
	public void should_close_span_upon_failure_callback() {
		ListenableFuture<ResponseEntity<String>> future;
		try {
			future = this.asyncRestTemplate.getForEntity("http://localhost:" + port() + "/blowsup", String.class);
			future.get();
			BDDAssertions.fail("should throw an exception from the controller");
		}
		catch (Exception e) {
		}

		Awaitility.await().untilAsserted(() -> {
			MutableSpan reportedRpcSpan = new ArrayList<>(this.spans.spans()).stream()
					.filter(span -> Span.Kind.CLIENT == span.kind()).findFirst().get();
			then(reportedRpcSpan).matches(
					span -> span.finishTimestamp() - span.startTimestamp() >= TimeUnit.MILLISECONDS.toMicros(100));
			then(reportedRpcSpan.tags()).containsKey("error");
			then(this.tracer.tracer().currentSpan()).isNull();
		});
	}

	int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

	@EnableAutoConfiguration(
			// spring boot test will otherwise instrument the client and server with the
			// same bean factory which isn't expected
			excludeName = "org.springframework.cloud.sleuth.brave.instrument.web.TraceWebServletAutoConfiguration")
	@Configuration(proxyBeanMethods = false)
	public static class TestConfiguration {

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		MyController myController() {
			return new MyController();
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		AsyncRestTemplate restTemplate() {
			return new AsyncRestTemplate();
		}

	}

	@RestController
	public static class MyController {

		@RequestMapping("/foo")
		String foo() throws Exception {
			Thread.sleep(100);
			return "foo";
		}

		@RequestMapping("/blowsup")
		String blowsup() throws Exception {
			Thread.sleep(100);
			throw new RuntimeException("boom");
		}

	}

}
