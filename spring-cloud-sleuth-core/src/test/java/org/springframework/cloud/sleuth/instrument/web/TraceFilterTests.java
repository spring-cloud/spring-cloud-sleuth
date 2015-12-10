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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
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
import org.springframework.util.JdkIdGenerator;

import lombok.SneakyThrows;

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

	@Before
	@SneakyThrows
	public void init() {
		initMocks(this);
		this.traceManager = new DefaultTraceManager(new AlwaysSampler(),
				new JdkIdGenerator(), this.publisher) {
			@Override
			protected Trace createTrace(Trace trace, Span span) {
				TraceFilterTests.this.span= span;
				return super.createTrace(trace, span);
			}
		};
		this.request = builder().buildRequest(new MockServletContext());
		this.response = new MockHttpServletResponse();
		this.response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.filterChain = new MockFilterChain();
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/").accept(MediaType.APPLICATION_JSON).header("User-Agent",
				"MockMvc");
	}

	@Test
	public void notTraced() throws Exception {
		TraceManager mockTraceManager = Mockito.mock(TraceManager.class);
		TraceFilter filter = new TraceFilter(mockTraceManager);

		this.request = get("/favicon.ico").accept(MediaType.ALL)
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		verify(mockTraceManager, never()).startSpan(anyString());
		verify(mockTraceManager, never()).close(any(Trace.class));
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(this.traceManager);
		filter.doFilter(this.request, this.response, this.filterChain);
		verifyHttpAnnotations();
		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {

		Trace trace = this.traceManager.startSpan("foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, trace);

		TraceFilter filter = new TraceFilter(this.traceManager);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyHttpAnnotations();

		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(Trace.SPAN_ID_NAME, "myspan")
				.header(Trace.TRACE_ID_NAME, "mytrace")
				.buildRequest(new MockServletContext());

		TraceFilter filter = new TraceFilter(this.traceManager);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyHttpAnnotations();

		assertNull(TraceContextHolder.getCurrentTrace());
	}

	public void verifyHttpAnnotations() {
		hasAnnotation(this.span, "/http/request/uri", "http://localhost/");
		hasAnnotation(this.span, "/http/request/endpoint", "/");
		hasAnnotation(this.span, "/http/request/method", "GET");
		hasAnnotation(this.span, "/http/request/headers/accept",
				MediaType.APPLICATION_JSON_VALUE);
		hasAnnotation(this.span, "/http/request/headers/user-agent", "MockMvc");

		hasAnnotation(this.span, "/http/response/status_code",
				HttpStatus.OK.toString());
		hasAnnotation(this.span, "/http/response/headers/content-type",
				MediaType.APPLICATION_JSON_VALUE);
	}

	private void hasAnnotation(Span span, String name, String value) {
		assertEquals(value, span.getAnnotations().get(name));
	}
}
