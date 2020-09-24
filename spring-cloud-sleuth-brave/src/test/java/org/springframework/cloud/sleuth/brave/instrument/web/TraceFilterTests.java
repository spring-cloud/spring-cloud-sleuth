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

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.servlet.Filter;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientParser;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import brave.servlet.TracingFilter;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
public class TraceFilterTests {

	TestSpanHandler spans = new TestSpanHandler();

	Tracing tracing = Tracing.newBuilder().currentTraceContext(
			ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(StrictScopeDecorator.create()).build())
			.addSpanHandler(this.spans).build();

	Tracer tracer = this.tracing.tracer();

	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing).clientParser(new HttpClientParser())
			.serverParser(new HttpServerParser())
			.serverSampler(new SkipPatternHttpServerSampler(() -> Pattern.compile(""))).build();

	Filter filter = TracingFilter.create(this.httpTracing);

	MockHttpServletRequest request;

	MockHttpServletResponse response;

	MockFilterChain filterChain;

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
		Tracing.current().close();
	}

	@Test
	public void notTraced() throws Exception {
		this.request = get("/favicon.ico").accept(MediaType.ALL).buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).isEmpty();
	}

	private Filter neverSampleFilter() {
		Tracing tracing = Tracing.newBuilder()
				.currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
						.addScopeDecorator(StrictScopeDecorator.create()).build())
				.addSpanHandler(this.spans).sampler(Sampler.NEVER_SAMPLE).supportsJoin(false).build();
		HttpTracing httpTracing = HttpTracing.newBuilder(tracing).clientParser(new HttpClientParser())
				.serverParser(new HttpServerParser())
				.serverSampler(new SkipPatternHttpServerSampler(() -> Pattern.compile(""))).build();
		return TracingFilter.create(httpTracing);
	}

	@Test
	public void startsNewTrace() throws Exception {
		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags()).containsEntry("http.path", "/").containsEntry("http.method",
				HttpMethod.GET.toString());
		// we don't check for status_code anymore cause Brave doesn't support it oob
		// .containsEntry("http.status_code", "200")
	}

	@Test
	public void shouldNotStoreHttpStatusCodeWhenResponseCodeHasNotYetBeenSet() throws Exception {
		this.response.setStatus(0);
		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags()).doesNotContainKey("http.status_code");
	}

	@Test
	public void startsNewTraceWithParentIdInHeaders() throws Exception {
		this.request = builder().header("b3", "0000000000000002-0000000000000003-1-000000000000000a")
				.buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).hasSize(1);
		then(this.spans.get(0).id()).isEqualTo("0000000000000003");
		then(this.spans.get(0).tags()).containsEntry("http.path", "/").containsEntry("http.method",
				HttpMethod.GET.toString());
	}

	@Test
	public void continuesATraceWhenSpanNotSampled() throws Exception {
		AtomicReference<Span> span = new AtomicReference<>();
		this.request = builder().header("b3", "0000000000000014-000000000000000a-0")
				.buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, (req, resp) -> {
			this.filterChain.doFilter(req, resp);
			span.set(this.tracing.tracer().currentSpan());
		});

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(span.get().context().traceIdString()).isEqualTo("0000000000000014");
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
	}

	@Test
	public void closesSpanInRequestAttrIfStatusCodeNotSuccessful() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.response.setStatus(404);

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).hasSize(1);
	}

	@Test
	public void doesntDetachASpanIfStatusCodeNotSuccessfulAndRequestWasProcessed() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.response.setStatus(404);

		then(Tracing.current().tracer().currentSpan()).isNull();
		this.filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		verifyParentSpanHttpTags();
	}

	@Test
	public void createsChildFromHeadersWhenJoinUnsupported() throws Exception {
		Tracing tracing = Tracing.newBuilder()
				.currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
						.addScopeDecorator(StrictScopeDecorator.create()).build())
				.addSpanHandler(this.spans).supportsJoin(false).build();
		HttpTracing httpTracing = HttpTracing.create(tracing);
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());

		TracingFilter.create(httpTracing).doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).hasSize(1);
		then(this.spans.get(0).parentId()).isEqualTo("000000000000000a");
	}

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

		then(Tracing.current().tracer().currentSpan()).isNull();
		verifyParentSpanHttpTags();
		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags()).containsEntry("error", "Planned");
	}

	@Test
	public void detachesSpanWhenResponseStatusIsNot2xx() throws Exception {
		this.request = builder().header("b3", "14-a").buildRequest(new MockServletContext());

		this.response.setStatus(404);

		then(Tracing.current().tracer().currentSpan()).isNull();
		this.filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void closesSpanWhenResponseStatusIs2xx() throws Exception {
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());
		this.response.setStatus(200);

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).hasSize(1);
	}

	@Test
	public void closesSpanWhenResponseStatusIs3xx() throws Exception {
		this.request = builder().header("b3", "0000000000000014-000000000000000a")
				.buildRequest(new MockServletContext());
		this.response.setStatus(302);

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).hasSize(1);
	}

	@Test
	public void returns400IfSpanIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header("b3", "asd").buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).isNotEmpty();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void returns200IfSpanParentIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header("b3", "asd").buildRequest(new MockServletContext());

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).isNotEmpty();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void samplesASpanRegardlessOfTheSamplerWhenDebugIsPresent() throws Exception {
		this.request = builder().header("b3", "d").buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).isNotEmpty();
	}

	@SuppressWarnings("Duplicates")
	@Test
	public void usesSamplingMechanismWhenIncomingTraceIsMalformed() throws Exception {
		this.request = builder().header("b3", "asd").buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).isEmpty();
	}

	// #668
	@Test
	public void shouldSetTraceKeysForAnUntracedRequest() throws Exception {
		this.request = builder().param("foo", "bar").buildRequest(new MockServletContext());
		this.response.setStatus(295);

		this.filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).hasSize(1);
		then(this.spans.get(0).tags()).containsEntry("http.path", "/").containsEntry("http.method",
				HttpMethod.GET.toString());
		// we don't check for status_code anymore cause Brave doesn't support it oob
		// .containsEntry("http.status_code", "295")
	}

	@Test
	public void samplesASpanDebugFlagWithInterceptor() throws Exception {
		this.request = builder().header("b3", "d").buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.spans).hasSize(1);
		then(this.spans.get(0).name()).isEqualTo("GET");
	}

	public void verifyParentSpanHttpTags() {
		then(this.spans).isNotEmpty();
		then(this.spans.get(0).tags()).containsEntry("http.path", "/").containsEntry("http.method",
				HttpMethod.GET.toString());
	}

}
