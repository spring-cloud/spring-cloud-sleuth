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

package org.springframework.cloud.sleuth.brave.instrument.web;

import java.util.regex.Pattern;

import brave.http.HttpTracing;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.brave.BraveTestTracing;
import org.springframework.cloud.sleuth.brave.bridge.http.BraveHttpServerHandler;
import org.springframework.cloud.sleuth.instrument.web.servlet.TracingFilter;
import org.springframework.cloud.sleuth.test.TestTracingAware;
import org.springframework.cloud.sleuth.test.TracerAware;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;

/**
 * @author Spencer Gibb
 */
public class TraceFilterTests extends org.springframework.cloud.sleuth.instrument.web.TraceFilterTests {

	BraveTestTracing testTracing;

	@Override
	public TestTracingAware tracerTest() {
		if (this.testTracing == null) {
			this.testTracing = new BraveTestTracing();
		}
		return this.testTracing;
	}

	@Override
	public HttpServerHandler httpServerHandler() {
		HttpTracing httpTracing = this.testTracing.httpTracingBuilder()
				.serverSampler(new SkipPatternHttpServerSampler(() -> Pattern.compile(""))).build();
		return new BraveHttpServerHandler(brave.http.HttpServerHandler.create(httpTracing));
	}

	@Test
	public void createsChildFromHeadersWhenJoinUnsupported() throws Exception {
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());
		TracerAware aware = tracerTest().tracing();
		BraveTestTracing braveTestTracing = ((BraveTestTracing) aware);
		braveTestTracing.tracingBuilder(braveTestTracing.tracingBuilder().supportsJoin(false)).reset();

		TracingFilter.create(aware.currentTraceContext(), httpServerHandler()).doFilter(this.request, this.response,
				this.filterChain);

		BDDAssertions.then(tracerTest().tracing().tracer().currentSpan()).isNull();
		BDDAssertions.then(tracerTest().handler()).hasSize(1);
		BDDAssertions.then(tracerTest().handler().get(0).getParentId()).isEqualTo("000000000000000a");
	}

	@Test
	public void samplesASpanDebugFlagWithInterceptor() throws Exception {
		this.request = builder().header("b3", "d").buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getName()).isEqualTo("GET");
	}

	@Test
	public void shouldNotStoreHttpStatusCodeWhenResponseCodeHasNotYetBeenSet() throws Exception {
		this.response.setStatus(0);
		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getTags()).doesNotContainKey("http.status_code");
	}

	@Test
	public void samplesASpanRegardlessOfTheSamplerWhenDebugIsPresent() throws Exception {
		this.request = builder().header("b3", "d").buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).isNotEmpty();
	}

	@Test
	public void startsNewTraceWithParentIdInHeaders() throws Exception {
		this.request = builder().header("b3", "0000000000000002-0000000000000003-1-000000000000000a")
				.buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getSpanId()).isEqualTo("0000000000000003");
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("http.path", "/").containsEntry("http.method",
				HttpMethod.GET.toString());
	}

}
