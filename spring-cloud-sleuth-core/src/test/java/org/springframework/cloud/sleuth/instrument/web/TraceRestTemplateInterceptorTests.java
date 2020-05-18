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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientParser;
import brave.http.HttpRequestParser;
import brave.http.HttpTags;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
public class TraceRestTemplateInterceptorTests {

	StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

	TestSpanHandler spans = new TestSpanHandler();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(this.currentTraceContext)
			.addSpanHandler(this.spans).build();

	Tracer tracer = this.tracing.tracer();

	private TestController testController = new TestController();

	private MockMvc mockMvc = MockMvcBuilders.standaloneSetup(this.testController)
			.build();

	private RestTemplate template = new RestTemplate(
			new MockMvcClientHttpRequestFactory(this.mockMvc));

	@BeforeEach
	public void setup() {
		setInterceptors(HttpTracing.create(this.tracing));
	}

	private void setInterceptors(HttpTracing httpTracing) {
		this.template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
				TracingClientHttpRequestInterceptor.create(httpTracing)));
	}

	@AfterEach
	public void clean() {
		this.tracing.close();
		this.currentTraceContext.close();
	}

	@Test
	public void headersAddedWhenNoTracingWasPresent() {
		@SuppressWarnings("unchecked")
		Map<String, String> headers = this.template.getForEntity("/", Map.class)
				.getBody();

		// Default inject format for client spans is B3 multi
		then(headers.get("X-B3-TraceId")).isNotNull();
		then(headers.get("X-B3-SpanId")).isNotNull();
	}

	@Test
	public void headersAddedWhenTracing() {
		Span span = this.tracer.nextSpan().name("new trace");
		Map<String, String> headers;

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			headers = this.template.getForEntity("/", Map.class).getBody();
		}
		finally {
			span.finish();
		}

		// Default inject format for client spans is B3 multi
		then(headers.get("X-B3-TraceId")).isEqualTo(span.context().traceIdString());
		then(headers.get("X-B3-SpanId")).isNotEqualTo(span.context().spanIdString());
		then(headers.get("X-B3-ParentSpanId")).isEqualTo(span.context().spanIdString());
	}

	// Issue #290
	@Test
	public void requestHeadersAddedWhenTracing() {
		setInterceptors(HttpTracing.newBuilder(this.tracing)
				.clientRequestParser((request, context, span) -> {
					HttpTags.URL.tag(request, context, span);
					HttpRequestParser.DEFAULT.parse(request, context, span);
				}).build());
		Span span = this.tracer.nextSpan().name("new trace");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.template.getForEntity("/foo?a=b", Map.class);
		}
		finally {
			span.finish();
		}

		then(this.spans).isNotEmpty();
		then(this.spans.get(0).tags()).containsEntry("http.url", "/foo?a=b")
				.containsEntry("http.path", "/foo").containsEntry("http.method", "GET");
	}

	@Test
	public void notSampledHeaderAddedWhenNotSampled() {
		this.tracing.close();
		this.tracing = Tracing.newBuilder().currentTraceContext(this.currentTraceContext)
				.addSpanHandler(this.spans).sampler(Sampler.NEVER_SAMPLE).build();
		this.template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
				TracingClientHttpRequestInterceptor.create(HttpTracing.create(tracing))));

		Span span = tracing.tracer().nextSpan().name("new trace");
		Map<String, String> headers;

		try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span.start())) {
			headers = this.template.getForEntity("/", Map.class).getBody();
		}
		finally {
			span.finish();
		}

		then(this.spans).isEmpty();
	}

	// issue #198
	@Test
	public void spanRemovedFromThreadUponException() {
		Span span = this.tracer.nextSpan().name("new trace");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.template.getForEntity("/exception", Map.class).getBody();
			fail("should throw an exception");
		}
		catch (RuntimeException e) {
			then(e).hasMessageStartingWith("500 Internal Server Error");
		}
		finally {
			span.finish();
		}

		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void createdSpanNameHasOnlyPrintableAsciiCharactersForNonEncodedURIWithNonAsciiChars() {
		setInterceptors(HttpTracing.newBuilder(this.tracing)
				.clientParser(new HttpClientParser()).build());
		Span span = this.tracer.nextSpan().name("new trace");

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.template.getForEntity("/cas~fs~åˆ’", Map.class).getBody();
		}
		catch (Exception e) {

		}
		finally {
			span.finish();
		}

		then(this.spans).hasSize(2);
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

		private void addHeaders(Map<String, String> map, HttpHeaders headers,
				String... names) {
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
