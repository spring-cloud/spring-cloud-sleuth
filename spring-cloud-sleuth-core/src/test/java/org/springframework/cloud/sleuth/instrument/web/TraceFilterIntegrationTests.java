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

import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.autoconfig.RandomUuidGenerator;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.cloud.sleuth.trace.TraceContextHolder;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import lombok.SneakyThrows;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 */
public class TraceFilterIntegrationTests {

	private StaticApplicationContext context = new StaticApplicationContext();

	private TraceManager traceManager = new DefaultTraceManager(new AlwaysSampler(),
			new RandomUuidGenerator(), this.context);

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain filterChain;

	@Before
	@SneakyThrows
	public void init() {
		TraceContextHolder.removeCurrentTrace();
		this.context.refresh();
		this.request = builder().buildRequest(new MockServletContext());
		this.response = new MockHttpServletResponse();
		this.response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.filterChain = new MockFilterChain();
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/").accept(MediaType.APPLICATION_JSON)
				.header("User-Agent", "MockMvc");
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(this.traceManager, new RandomUuidGenerator());
		filter.doFilter(this.request, this.response, this.filterChain);
		assertNull(TraceContextHolder.getCurrentTrace());
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(Trace.SPAN_ID_NAME, "myspan")
				.header(Trace.TRACE_ID_NAME, "mytraceManager").buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(this.traceManager, new RandomUuidGenerator());
		filter.doFilter(this.request, this.response, this.filterChain);
		assertNull(TraceContextHolder.getCurrentSpan());
	}

}
