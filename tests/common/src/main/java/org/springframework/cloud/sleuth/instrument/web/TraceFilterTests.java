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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.Filter;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;
import org.springframework.cloud.sleuth.api.http.HttpServerHandler;
import org.springframework.cloud.sleuth.instrument.web.servlet.TracingFilter;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.cloud.sleuth.test.TracerAware;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author Spencer Gibb
 */
public abstract class TraceFilterTests implements TestTracingAwareSupplier {

	protected Tracer tracer = tracerTest().tracing().tracer();

	protected CurrentTraceContext currentTraceContext = tracerTest().tracing().currentTraceContext();

	protected Filter filter = TracingFilter.create(this.currentTraceContext,
			tracerTest().tracing().httpServerHandler());

	protected TestSpanHandler spans = tracerTest().handler();

	protected MockHttpServletRequest request;

	protected MockHttpServletResponse response;

	protected MockFilterChain filterChain;

	@BeforeEach
	public void init() {
		this.request = builder().buildRequest(new MockServletContext());
		this.response = new MockHttpServletResponse();
		this.response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.filterChain = new MockFilterChain();
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/?foo=bar").accept(MediaType.APPLICATION_JSON).header("User-Agent", "MockMvc");
	}

	@AfterEach
	public void cleanup() {
		tracerTest().close();
	}

	@Test
	public void notTraced() throws Exception {
		this.request = get("/favicon.ico").accept(MediaType.ALL).buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).isEmpty();
	}

	protected Filter neverSampleFilter() {
		return TracingFilter.create(tracerTest().tracing().currentTraceContext(),
				tracerTest().tracing().sampler(TracerAware.TraceSampler.OFF).httpServerHandler());
	}

	@Test
	public void startsNewTrace() throws Exception {
		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("http.path", "/").containsEntry("http.method",
				HttpMethod.GET.toString());
		// we don't check for status_code anymore cause Brave doesn't support it oob
		// .containsEntry("http.status_code", "200")
	}

	@Test
	public void continuesATraceWhenSpanNotSampled() throws Exception {
		AtomicReference<Span> span = new AtomicReference<>();
		this.request = builder().header("b3", "0000000000000014-000000000000000a-0")
				.buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, (req, resp) -> {
			this.filterChain.doFilter(req, resp);
			span.set(tracerTest().tracing().tracer().currentSpan());
		});

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(span.get().context().traceId())
				.isEqualTo(tracerTest().assertions().or128Bit("0000000000000014"));
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void closesSpanInRequestAttrIfStatusCodeNotSuccessful() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.response.setStatus(404);

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
	}

	@Test
	public void doesntDetachASpanIfStatusCodeNotSuccessfulAndRequestWasProcessed() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.response.setStatus(404);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		this.filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		verifyParentSpanHttpTags();
	}

	public abstract HttpServerHandler httpServerHandler();

	@Test
	public void shouldAnnotateSpanWithErrorWhenExceptionIsThrown() throws Exception {
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());

		this.filterChain = new MockFilterChain() {
			@Override
			public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response)
					throws java.io.IOException, javax.servlet.ServletException {
				throw new RuntimeException("Planned");
			}
		};
		try {
			this.filter.doFilter(this.request, this.response, this.filterChain);
		}
		catch (RuntimeException e) {
			assertThat(e.getMessage()).isEqualTo("Planned");
		}

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		verifyParentSpanHttpTags();
		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getError()).hasMessageContaining("Planned");
	}

	@Test
	public void detachesSpanWhenResponseStatusIsNot2xx() throws Exception {
		this.request = builder().header("b3", "14-a").buildRequest(new MockServletContext());

		this.response.setStatus(404);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		this.filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void closesSpanWhenResponseStatusIs2xx() throws Exception {
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());
		this.response.setStatus(200);

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
	}

	@Test
	public void closesSpanWhenResponseStatusIs3xx() throws Exception {
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());
		this.response.setStatus(302);

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
	}

	@Test
	public void returns400IfSpanIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header("b3", "asd").buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).isNotEmpty();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void returns200IfSpanParentIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header("b3", "asd").buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).isNotEmpty();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@SuppressWarnings("Duplicates")
	@Test
	public void usesSamplingMechanismWhenIncomingTraceIsMalformed() throws Exception {
		this.request = builder().header("b3", "asd").buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(tracerTest().tracing().tracer().currentSpan()).isNull();
		BDDAssertions.then(tracerTest().handler()).isEmpty();
	}

	// #668
	@Test
	public void shouldSetTraceKeysForAnUntracedRequest() throws Exception {
		this.request = builder().param("foo", "bar").buildRequest(new MockServletContext());
		this.response.setStatus(295);

		this.filter.doFilter(this.request, this.response, this.filterChain);

		BDDAssertions.then(this.tracer.currentSpan()).isNull();
		BDDAssertions.then(this.spans).hasSize(1);
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("http.path", "/").containsEntry("http.method",
				HttpMethod.GET.toString());
		// we don't check for status_code anymore cause Brave doesn't support it oob
		// .containsEntry("http.status_code", "295")
	}

	public void verifyParentSpanHttpTags() {
		BDDAssertions.then(this.spans).isNotEmpty();
		BDDAssertions.then(this.spans.get(0).getTags()).containsEntry("http.path", "/").containsEntry("http.method",
				HttpMethod.GET.toString());
	}

}
