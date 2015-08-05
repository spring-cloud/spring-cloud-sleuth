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

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import lombok.SneakyThrows;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * @author Spencer Gibb
 */
public class TraceFilterTests {

	@Mock
	private Trace trace;

	@Mock
	private TraceScope traceScope;

	@Mock
	private Span span;

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain filterChain;

	@Before
	@SneakyThrows
	public void init() {
		initMocks(this);
		request = builder()
				.buildRequest(new MockServletContext());
		response = new MockHttpServletResponse();
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		filterChain = new MockFilterChain();
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/")
				.accept(MediaType.APPLICATION_JSON)
				.header("User-Agent", "MockMvc");
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(trace);

		when(this.trace.startSpan(anyString())).thenReturn(traceScope);

		filter.doFilter(request, response, filterChain);

		verify(this.trace).startSpan(anyString());

		verifyHttpAnnotations();

		verify(this.traceScope).close();
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {
		request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, this.traceScope);

		TraceFilter filter = new TraceFilter(trace);
		filter.doFilter(request, response, filterChain);

		verify(this.trace).continueSpan((Span) anyObject());

		verifyHttpAnnotations();

		verify(this.traceScope).close();
	}


	@Test
	public void continuesSpanFromHeaders() throws Exception {
		request = builder()
				.header(SPAN_ID_NAME, "myspan")
				.header(TRACE_ID_NAME, "mytrace")
				.buildRequest(new MockServletContext());

		when(this.trace.startSpan(anyString(), (Span) anyObject())).thenReturn(traceScope);
		when(this.traceScope.getSpan()).thenReturn(this.span);
		when(this.span.getSpanId()).thenReturn("myspan");
		when(this.span.getTraceId()).thenReturn("mytrace");

		TraceFilter filter = new TraceFilter(trace);
		filter.doFilter(request, response, filterChain);

		verify(this.trace).startSpan(anyString(), (Span) anyObject());

		verifyHttpAnnotations();

		verify(this.traceScope).close();
	}


	public void verifyHttpAnnotations() {
		verify(this.trace).addKVAnnotation("/http/request/uri", "http://localhost/");
		verify(this.trace).addKVAnnotation("/http/request/endpoint", "/");
		verify(this.trace).addKVAnnotation("/http/request/method", "GET");
		verify(this.trace).addKVAnnotation("/http/request/headers/accept", MediaType.APPLICATION_JSON_VALUE);
		verify(this.trace).addKVAnnotation("/http/request/headers/user-agent", "MockMvc");

		verify(this.trace).addKVAnnotation("/http/response/status_code", HttpStatus.OK.toString());
		verify(this.trace).addKVAnnotation("/http/response/headers/content-type", MediaType.APPLICATION_JSON_VALUE);
	}
}
