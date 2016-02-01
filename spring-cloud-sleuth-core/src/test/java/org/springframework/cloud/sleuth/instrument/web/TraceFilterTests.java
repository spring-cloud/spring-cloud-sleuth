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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import lombok.SneakyThrows;

/**
 * @author Spencer Gibb
 */
public class TraceFilterTests {

	@Mock
	private ApplicationEventPublisher publisher;

	private Tracer tracer;
	private TraceKeys traceKeys = new TraceKeys();

	private Span span;

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain filterChain;
	private Sampler sampler = new AlwaysSampler();

	@Before
	@SneakyThrows
	public void init() {
		initMocks(this);
		this.tracer = new DefaultTracer(new DelegateSampler(), new Random(),
				this.publisher) {
			@Override
			protected Span createSpan(Span span, Span saved) {
				TraceFilterTests.this.span = super.createSpan(span, saved);
				return TraceFilterTests.this.span;
			}
		};
		this.request = builder().buildRequest(new MockServletContext());
		this.response = new MockHttpServletResponse();
		this.response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.filterChain = new MockFilterChain();
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/?foo=bar").accept(MediaType.APPLICATION_JSON).header("User-Agent",
				"MockMvc");
	}

	@Test
	public void notTraced() throws Exception {
		this.sampler = new IsTracingSampler();
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);

		this.request = get("/favicon.ico").accept(MediaType.ALL)
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		assertFalse(this.span.isExportable());
		assertNull(SpanContextHolder.getCurrentSpan());
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);
		filter.doFilter(this.request, this.response, this.filterChain);
		verifyHttpTags();
		assertNull(SpanContextHolder.getCurrentSpan());
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {

		Span span = this.tracer.startTrace("foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);

		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyHttpTags();

		assertNull(SpanContextHolder.getCurrentSpan());
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());

		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyHttpTags();

		assertNull(SpanContextHolder.getCurrentSpan());
	}

	@Test
	public void addsAdditionalHeaders() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());

		this.traceKeys.getHttp().getHeaders().add("x-foo");
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);
		this.request.addHeader("X-Foo", "bar");
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(this.span.tags()).contains(entry("http.x-foo", "bar"));

		assertNull(SpanContextHolder.getCurrentSpan());
	}

	@Test
	public void additionalMultiValuedHeader() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, 10L)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());

		this.traceKeys.getHttp().getHeaders().add("x-foo");
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);
		this.request.addHeader("X-Foo", "bar");
		this.request.addHeader("X-Foo", "spam");
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(this.span.tags()).contains(entry("http.x-foo", "'bar','spam'"));

		assertNull(SpanContextHolder.getCurrentSpan());
	}

	@Test
	public void catchesException() throws Exception {
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);
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
		verifyHttpTags(HttpStatus.INTERNAL_SERVER_ERROR);

		assertNull(SpanContextHolder.getCurrentSpan());
	}

	public void verifyHttpTags() {
		verifyHttpTags(HttpStatus.OK);
	}

	/**
	 * Shows the expansion of {@link import
	 * org.springframework.cloud.sleuth.instrument.TraceKeys}.
	 */
	public void verifyHttpTags(HttpStatus status) {
		assertThat(this.span.tags()).contains(entry("http.host", "localhost"),
				entry("http.url", "http://localhost/?foo=bar"), entry("http.path", "/"),
				entry("http.method", "GET"));

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
