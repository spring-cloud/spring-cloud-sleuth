/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.SpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.assertThat;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.entry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author Spencer Gibb
 */
public class TraceFilterTests {

	@Mock SpanLogger spanLogger;
	ArrayListSpanAccumulator spanReporter = new ArrayListSpanAccumulator();
	SpanExtractor<HttpServletRequest> spanExtractor = new HttpServletRequestExtractor(Pattern
			.compile(TraceFilter.DEFAULT_SKIP_PATTERN));
	SpanInjector<HttpServletResponse> spanInjector = new HttpServletResponseInjector();

	private Tracer tracer;
	private TraceKeys traceKeys = new TraceKeys();
	private HttpTraceKeysInjector httpTraceKeysInjector;

	private Span span;

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain filterChain;
	private Sampler sampler = new AlwaysSampler();

	@Before
	public void init() {
		initMocks(this);
		this.tracer = new DefaultTracer(new DelegateSampler(), new Random(),
				new DefaultSpanNamer(), this.spanLogger, this.spanReporter) {
			@Override
			public Span continueSpan(Span span) {
				TraceFilterTests.this.span = super.continueSpan(span);
				return TraceFilterTests.this.span;
			}
		};
		this.request = builder().buildRequest(new MockServletContext());
		this.response = new MockHttpServletResponse();
		this.response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.filterChain = new MockFilterChain();
		this.httpTraceKeysInjector = new HttpTraceKeysInjector(this.tracer, this.traceKeys);
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/?foo=bar").accept(MediaType.APPLICATION_JSON).header("User-Agent",
				"MockMvc");
	}

	@After
	public void cleanup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void notTraced() throws Exception {
		this.sampler = NeverSampler.INSTANCE;
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);

		this.request = get("/favicon.ico").accept(MediaType.ALL)
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.span.isExportable()).isFalse();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyCurrentSpanStatusCode(HttpStatus.OK);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void startsNewTraceWithParentIdInHeaders() throws Exception {
		this.request = builder()
				.header(Span.SPAN_ID_NAME, Span.idToHex(1L))
				.header(Span.TRACE_ID_NAME, Span.idToHex(2L))
				.header(Span.PARENT_ID_NAME, Span.idToHex(3L))
				.buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);

		filter.doFilter(this.request, this.response, this.filterChain);

		// this creates a child span which is why we'd expect the parents to include 1L)
		assertThat(this.span.getParents()).containsOnly(1L);
		assertThat(parentSpan())
				.hasATag("http.url", "http://localhost/?foo=bar")
				.hasATag("http.host", "localhost")
				.hasATag("http.path", "/")
				.hasATag("http.method", "GET");
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	private Span parentSpan() {
		Optional<Span> parent = this.spanReporter.getSpans().stream()
				.filter(span -> span.getName().contains("parent")).findFirst();
		assertThat(parent.isPresent()).isTrue();
		return parent.get();
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {
		Span span = this.tracer.createSpan("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		// It should have been removed from the thread local context so simulate that
		TestSpanContextHolder.removeCurrentSpan();

		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(this.request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)).isNull();
	}

	@Test
	public void closesSpanInRequestAttrIfStatusCodeNotSuccessful() throws Exception {
		Span span = this.tracer.createSpan("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		this.response.setStatus(404);
		// It should have been removed from the thread local context so simulate that
		TestSpanContextHolder.removeCurrentSpan();

		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(this.request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)).isNotNull();
	}

	@Test
	public void doesntDetachASpanIfStatusCodeNotSuccessfulAndRequestWasProcessed() throws Exception {
		Span span = this.tracer.createSpan("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		this.request.setAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR, true);
		this.response.setStatus(404);
		// It should have been removed from the thread local context so simulate that
		TestSpanContextHolder.removeCurrentSpan();

		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());

		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyParentSpanHttpTags();

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void addsAdditionalHeaders() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());

		this.traceKeys.getHttp().getHeaders().add("x-foo");
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		this.request.addHeader("X-Foo", "bar");
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(parentSpan().tags()).contains(entry("http.x-foo", "bar"));
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void ensuresThatParentSpanIsStoppedWhenReported() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, spanIsStoppedVeryfingReporter(),
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);

		filter.doFilter(this.request, this.response, this.filterChain);
	}

	SpanReporter spanIsStoppedVeryfingReporter() {
		return (span) -> assertThat(span.getEnd()).as("Span has to be stopped before reporting").isNotZero();
	}

	@Test
	public void additionalMultiValuedHeader() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());

		this.traceKeys.getHttp().getHeaders().add("x-foo");
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		this.request.addHeader("X-Foo", "bar");
		this.request.addHeader("X-Foo", "spam");
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(parentSpan().tags()).contains(entry("http.x-foo", "'bar','spam'"));

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void catchesException() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		this.filterChain = new MockFilterChain() {
			@Override
			public void doFilter(javax.servlet.ServletRequest request,
					javax.servlet.ServletResponse response)
							throws java.io.IOException, javax.servlet.ServletException {
				throw new RuntimeException("Planned");
			};
		};
		try {
			filter.doFilter(this.request, this.response, this.filterChain);
		}
		catch (RuntimeException e) {
			assertEquals("Planned", e.getMessage());
		}
		verifyParentSpanHttpTags(HttpStatus.INTERNAL_SERVER_ERROR);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void detachesSpanWhenResponseStatusIsNot2xx() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys, this.spanReporter,
				this.spanExtractor, this.spanInjector, this.httpTraceKeysInjector);
		this.response.setStatus(404);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	public void verifyParentSpanHttpTags() {
		verifyParentSpanHttpTags(HttpStatus.OK);
	}

	/**
	 * Shows the expansion of {@link import
	 * org.springframework.cloud.sleuth.instrument.TraceKeys}.
	 */
	public void verifyParentSpanHttpTags(HttpStatus status) {
		assertThat(parentSpan().tags()).contains(entry("http.host", "localhost"),
				entry("http.url", "http://localhost/?foo=bar"), entry("http.path", "/"),
				entry("http.method", "GET"));
		verifyCurrentSpanStatusCode(status);

	}

	private void verifyCurrentSpanStatusCode(HttpStatus status) {
		// Status is only interesting in non-success case. Omitting it saves at least
		// 20bytes per span.
		if (status.is2xxSuccessful()) {
			assertThat(this.span.tags()).doesNotContainKey("http.status_code");
		}
		else {
			assertThat(this.span.tags()).containsEntry("http.status_code",
					status.toString());
		}
	}

	private class DelegateSampler implements Sampler {
		@Override
		public boolean isSampled(Span span) {
			return TraceFilterTests.this.sampler.isSampled(span);
		}
	}
}
