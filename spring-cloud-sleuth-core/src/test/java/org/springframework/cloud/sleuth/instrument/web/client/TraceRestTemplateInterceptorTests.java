/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.instrument.web.HttpRequestInjector;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.SpanInjectorComposite;
import org.springframework.cloud.sleuth.trace.SpanJoinerComposite;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
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

	@Before
	public void setup() {
		this.tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				new DefaultSpanNamer(), new NoOpSpanLogger(), new NoOpSpanReporter(), new SpanJoinerComposite(),
				new SpanInjectorComposite(Collections.singletonList(new HttpRequestInjector())));
		this.template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
				new TraceRestTemplateInterceptor(this.tracer)));
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

	@Test
	public void notSampledHeaderAddedWhenNotExportable() {
		this.tracer.continueSpan(Span.builder().traceId(1L).spanId(2L).exportable(false).build());
		@SuppressWarnings("unchecked")
		Map<String, String> headers = this.template.getForEntity("/", Map.class)
				.getBody();
		then(Span.hexToId(headers.get(Span.TRACE_ID_NAME))).isEqualTo(1L);
		then(Span.hexToId(headers.get(Span.SPAN_ID_NAME))).isNotEqualTo(2L);
		then(headers.get(Span.NOT_SAMPLED_NAME)).isEqualTo("true");
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

	@RestController
	public class TestController {

		Span span;

		@RequestMapping("/")
		public Map<String, String> home(@RequestHeader HttpHeaders headers) {
			this.span = TraceRestTemplateInterceptorTests.this.tracer.getCurrentSpan();
			Map<String, String> map = new HashMap<String, String>();
			addHeaders(map, headers, Span.SPAN_ID_NAME, Span.TRACE_ID_NAME,
					Span.PARENT_ID_NAME, Span.NOT_SAMPLED_NAME);
			return map;
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
