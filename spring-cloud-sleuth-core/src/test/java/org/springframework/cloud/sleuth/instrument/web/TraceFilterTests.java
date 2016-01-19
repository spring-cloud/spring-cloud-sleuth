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

import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author Spencer Gibb
 */
public class TraceFilterTests {

	@Mock
	private ApplicationEventPublisher publisher;

	private TraceManager traceManager;

	private Span span;

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain filterChain;
	private Sampler<Void> sampler = new AlwaysSampler();

	@Before
	@SneakyThrows
	public void init() {
		initMocks(this);
		this.traceManager = new DefaultTraceManager(new DelegateSampler(), new Random(), this.publisher) {
			@Override
			protected Trace createTrace(Trace trace, Span span) {
				TraceFilterTests.this.span = span;
				return super.createTrace(trace, span);
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
		TraceFilter filter = new TraceFilter(this.traceManager);

		this.request = get("/favicon.ico").accept(MediaType.ALL)
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		assertFalse(this.span.isExportable());
		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(this.traceManager);
		filter.doFilter(this.request, this.response, this.filterChain);
		verifyHttpTags();
		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {

		Trace trace = this.traceManager.startSpan("foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, trace);

		TraceFilter filter = new TraceFilter(this.traceManager);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyHttpTags();

		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(Trace.SPAN_ID_NAME, 10L)
				.header(Trace.TRACE_ID_NAME, 20L)
				.buildRequest(new MockServletContext());

		TraceFilter filter = new TraceFilter(this.traceManager);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyHttpTags();

		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Test
	public void catchesException() throws Exception {
		TraceFilter filter = new TraceFilter(this.traceManager);
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

		assertNull(TraceContextHolder.getCurrentTrace());
	}

	public void verifyHttpTags() {
		verifyHttpTags(HttpStatus.OK);
	}

	/**
	 * Shows the expansion of {@link import org.springframework.cloud.sleuth.instrument.TraceKeys}.
	 */
	public void verifyHttpTags(HttpStatus status) {
		assertThat(this.span.tags()).contains(
				entry("http/host", "localhost"),
				entry("http/url", "http://localhost/?foo=bar"),
				entry("http/path", "/"),
				entry("http/method", "GET")
		);

		// Status is only interesting in non-success case. Omitting it saves at least 20bytes per span.
		if (status.is2xxSuccessful()) {
			assertThat(this.span.tags())
					.doesNotContainKey("http/status_code");
		} else {
			assertThat(this.span.tags())
					.containsEntry("http/status_code", status.toString());
		}
	}

	private class DelegateSampler implements Sampler<Void> {
		@Override
		public boolean next() {
			return TraceFilterTests.this.sampler.next();
		}
	}
}
