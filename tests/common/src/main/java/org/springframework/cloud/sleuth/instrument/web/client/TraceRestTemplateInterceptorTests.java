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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpClientHandler;
import org.springframework.cloud.sleuth.instrument.web.mvc.TracingClientHttpRequestInterceptor;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.cloud.sleuth.test.TracerAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.web.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dave Syer
 *
 */
public abstract class TraceRestTemplateInterceptorTests implements TestTracingAwareSupplier {

	private TestController testController = new TestController();

	private MockMvc mockMvc = MockMvcBuilders.standaloneSetup(this.testController).build();

	private RestTemplate template = new RestTemplate(new MockMvcClientHttpRequestFactory(this.mockMvc));

	Tracer tracer = tracerTest().tracing().tracer();

	TestSpanHandler spans = tracerTest().handler();

	@BeforeEach
	public void setup() {
		setInterceptors(tracerTest().tracing().httpClientHandler());
	}

	private void setInterceptors(HttpClientHandler httpClientHandler) {
		this.template.setInterceptors(Arrays.asList(TracingClientHttpRequestInterceptor
				.create(tracerTest().tracing().currentTraceContext(), httpClientHandler)));
	}

	@AfterEach
	public void clean() {
		tracerTest().close();
	}

	@Test
	public void headersAddedWhenNoTracingWasPresent() {
		@SuppressWarnings("unchecked")
		Map<String, String> headers = this.template.getForEntity("/", Map.class).getBody();

		// Default inject format for client spans is B3 multi
		then(headers.get("X-B3-TraceId")).isNotNull();
		then(headers.get("X-B3-SpanId")).isNotNull();
	}

	@Test
	public void headersAddedWhenTracing() {
		Span span = this.tracer.nextSpan().name("new trace");
		Map<String, String> headers;

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			headers = this.template.getForEntity("/", Map.class).getBody();
		}
		finally {
			span.end();
		}

		// Default inject format for client spans is B3 multi
		then(headers.get("X-B3-TraceId")).isEqualTo(span.context().traceId());
		then(headers.get("X-B3-SpanId")).isNotEqualTo(span.context().spanId());
		assertThatParentSpanIdSet(span, headers);
	}

	public void assertThatParentSpanIdSet(Span span, Map<String, String> headers) {
		throw new UnsupportedOperationException("Implement this assertion");
	}

	// Issue #290
	@Test
	public void requestHeadersAddedWhenTracing() {
		setInterceptors(tracerTest().tracing()
				.clientRequestParser((request, context, span) -> span.tag("http.url", request.url())
						.tag("http.method", request.method()).tag("http.path", request.path()).name(request.method()))
				.httpClientHandler());
		Span span = this.tracer.nextSpan().name("new trace");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			this.template.getForEntity("/foo?a=b", Map.class);
		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.spans).isNotEmpty();
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("http.url", "/foo?a=b")
				.containsEntry("http.path", "/foo").containsEntry("http.method", "GET");
	}

	@Test
	public void notSampledHeaderAddedWhenNotSampled() {
		this.template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
				TracingClientHttpRequestInterceptor.create(tracerTest().tracing().currentTraceContext(),
						tracerTest().tracing().sampler(TracerAware.TraceSampler.OFF).httpClientHandler())));
		this.spans = tracerTest().handler();

		Span span = tracerTest().tracing().tracer().nextSpan().name("new trace");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.template.getForEntity("/", Map.class).getBody();
		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.spans).isEmpty();
	}

	// issue #198
	@Test
	public void spanRemovedFromThreadUponException() {
		Span span = this.tracer.nextSpan().name("new trace");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			this.template.getForEntity("/exception", Map.class).getBody();
			fail("should throw an exception");
		}
		catch (RuntimeException e) {
			then(e).hasMessageStartingWith("500 Internal Server Error");
		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void createdSpanNameHasOnlyPrintableAsciiCharactersForNonEncodedURIWithNonAsciiChars() {
		Span span = this.tracer.nextSpan().name("new trace");

		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			this.template.getForEntity("/cas~fs~åˆ’", Map.class).getBody();
		}
		catch (Exception e) {

		}
		finally {
			span.end();
		}

		BDDAssertions.then(this.spans).hasSize(2);
	}

	@RestController
	public class TestController {

		Span span;

		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			this.span = TraceRestTemplateInterceptorTests.this.tracer.currentSpan();
			Map<String, String> map = new HashMap<>();
			// Default inject format for client spans is B3 multi
			addHeaders(map, headers, "X-B3-SpanId", "X-B3-TraceId", "X-B3-ParentSpanId");
			return map;
		}

		@RequestMapping("/foo")
		public void foo() {
		}

		@RequestMapping("/exception")
		public Map<String, String> exception() {
			throw new RuntimeException("foo");
		}

		private void addHeaders(Map<String, String> map, HttpHeaders headers, String... names) {
			if (headers != null) {
				for (String name : names) {
					String value = headers.getFirst(name);
					if (value != null) {
						map.put(name, value);
					}
				}
			}
		}

	}

}
