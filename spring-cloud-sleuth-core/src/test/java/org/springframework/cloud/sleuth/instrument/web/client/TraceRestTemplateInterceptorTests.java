/*
 * Copyright 2013-2017 the original author or authors.
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
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.instrument.web.ZipkinHttpSpanInjector;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.web.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Dave Syer
 *
 */
public class TraceRestTemplateInterceptorTests {

	private TestController testController = new TestController();

	private MockMvc mockMvc = MockMvcBuilders.standaloneSetup(this.testController)
			.build();

	private RestTemplate template = new RestTemplate(
			new MockMvcClientHttpRequestFactory(this.mockMvc));

	private DefaultTracer tracer;

	private ArrayListSpanAccumulator spanAccumulator = new ArrayListSpanAccumulator();

	@Before
	public void setup() {
		this.tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				new DefaultSpanNamer(), new NoOpSpanLogger(), this.spanAccumulator, new TraceKeys());
		this.template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
				new TraceRestTemplateInterceptor(this.tracer, new ZipkinHttpSpanInjector(),
						new HttpTraceKeysInjector(this.tracer, new TraceKeys()),
						new ExceptionMessageErrorParser())));
		TestSpanContextHolder.removeCurrentSpan();
	}

	@After
	public void clean() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void headersAddedWhenNoTracingWasPresent() {
		@SuppressWarnings("unchecked")
		Map<String, String> headers = this.template.getForEntity("/", Map.class)
				.getBody();

		then(Span.hexToId(headers.get(Span.TRACE_ID_NAME))).isNotNull();
		then(Span.hexToId(headers.get(Span.SPAN_ID_NAME))).isNotNull();
	}

	@Test
	public void headersAddedWhenTracing() {
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).parent(3L).build());
		@SuppressWarnings("unchecked")
		Map<String, String> headers = this.template.getForEntity("/", Map.class)
				.getBody();
		then(Span.hexToId(headers.get(Span.TRACE_ID_NAME))).isEqualTo(1L);
		then(Span.hexToId(headers.get(Span.SPAN_ID_NAME))).isNotEqualTo(2L);
		then(Span.hexToId(headers.get(Span.PARENT_ID_NAME))).isEqualTo(2L);
	}

	// Issue #290
	@Test
	public void requestHeadersAddedWhenTracing() {
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).parent(3L).build());

		this.template.getForEntity("/foo?a=b", Map.class);

		List<Span> spans = spanAccumulator.getSpans();
		then(spans).isNotEmpty();
		then(spans.get(0))
				.hasATag("http.url", "/foo?a=b")
				.hasATag("http.path", "/foo")
				.hasATag("http.method", "GET");
	}

	@Test
	public void notSampledHeaderAddedWhenNotExportable() {
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).exportable(false).build());
		@SuppressWarnings("unchecked")
		Map<String, String> headers = this.template.getForEntity("/", Map.class)
				.getBody();
		then(Span.hexToId(headers.get(Span.TRACE_ID_NAME))).isEqualTo(1L);
		then(Span.hexToId(headers.get(Span.SPAN_ID_NAME))).isNotEqualTo(2L);
		then(headers.get(Span.SAMPLED_NAME)).isEqualTo(Span.SPAN_NOT_SAMPLED);
	}

	// issue #198
	@Test
	public void spanRemovedFromThreadUponException() {
		Span span = this.tracer.createSpan("new trace");

		try {
			this.template.getForEntity("/exception", Map.class).getBody();
			Assert.fail("should throw an exception");
		} catch (RuntimeException e) {
			then(e).hasMessage("500 Internal Server Error");
		}

		then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);
	}

	@Test
	public void createdSpanNameDoesNotHaveNullInName() {
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).exportable(false).build());

		this.template.getForEntity("/", Map.class).getBody();

		then(this.testController.span).hasNameEqualTo("http:/");
	}

	@Test
	public void createdSpanNameHasOnlyPrintableAsciiCharactersForNonEncodedURIWithNonAsciiChars() {
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).exportable(false).build());

		try {
			this.template.getForEntity("/cas~fs~åˆ’", Map.class).getBody();
		}
		catch (Exception e) {

		}

		String spanName = this.spanAccumulator.getSpans().get(0).getName();
		then(this.spanAccumulator.getSpans().get(0).getName()).isEqualTo("http:/cas~fs~%C3%A5%CB%86%E2%80%99");
		then(StringUtils.isAsciiPrintable(spanName));
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void willShortenTheNameOfTheSpan() {
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).exportable(false).build());

		try {
			this.template.getForEntity("/" + bigName(), Map.class).getBody();
		} catch (Exception e) {

		}

		then(this.spanAccumulator.getSpans().get(0).getName()).hasSize(50);
		then(ExceptionUtils.getLastException()).isNull();
	}

	private String bigName() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("a");
		}
		return sb.toString();
	}

	@RestController
	public class TestController {

		Span span;

		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			this.span = TraceRestTemplateInterceptorTests.this.tracer.getCurrentSpan();
			Map<String, String> map = new HashMap<String, String>();
			addHeaders(map, headers, Span.SPAN_ID_NAME, Span.TRACE_ID_NAME,
					Span.PARENT_ID_NAME, Span.SAMPLED_NAME);
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
