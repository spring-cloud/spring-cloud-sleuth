/*
 * Copyright 2013-2018 the original author or authors.
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

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.servlet.Filter;

import brave.ErrorParser;
import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import brave.servlet.TracingFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.sleuth.util.SpanUtil;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author Spencer Gibb
 */
public class TraceFilterTests {

	static final String PARENT_ID = SpanUtil.idToHex(10L);
	static final String TRACE_ID_NAME = "X-B3-TraceId";
	static final String SPAN_ID_NAME = "X-B3-SpanId";
	static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
	static final String SAMPLED_ID_NAME = "X-B3-Sampled";
	static final String SPAN_FLAGS = "X-B3-Flags";

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();
	Tracer tracer = this.tracing.tracer();
	TraceKeys traceKeys = new TraceKeys();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(new SleuthHttpClientParser(this.traceKeys))
			.serverParser(new SleuthHttpServerParser(this.traceKeys,
					new ErrorParser()))
			.serverSampler(new SleuthHttpSampler(() -> Pattern.compile("")))
			.build();
	Filter filter = TracingFilter.create(this.httpTracing);

	MockHttpServletRequest request;
	MockHttpServletResponse response;
	MockFilterChain filterChain;

	@Before
	public void init() {
		this.request = builder().buildRequest(new MockServletContext());
		this.response = new MockHttpServletResponse();
		this.response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.filterChain = new MockFilterChain();
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/?foo=bar").accept(MediaType.APPLICATION_JSON).header("User-Agent",
				"MockMvc");
	}

	@After
	public void cleanup() {
		Tracing.current().close();
	}

	@Test
	public void notTraced() throws Exception {
		this.request = get("/favicon.ico").accept(MediaType.ALL)
				.buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isEmpty();
	}

	private Filter neverSampleFilter() {
		Tracing tracing = Tracing.newBuilder()
				.currentTraceContext(new StrictCurrentTraceContext())
				.spanReporter(this.reporter)
				.sampler(Sampler.NEVER_SAMPLE)
				.supportsJoin(false)
				.build();
		HttpTracing httpTracing = HttpTracing.newBuilder(tracing)
				.clientParser(new SleuthHttpClientParser(this.traceKeys))
				.serverParser(new SleuthHttpServerParser(this.traceKeys,
						new ErrorParser()))
				.serverSampler(new SleuthHttpSampler(() -> Pattern.compile("")))
				.build();
		return TracingFilter.create(httpTracing);
	}

	@Test
	public void startsNewTrace() throws Exception {
		filter.doFilter(this.request, this.response, this.filterChain);
		
		then(this.reporter.getSpans())
				.hasSize(1);
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost")
				.containsEntry("http.path", "/")
				.containsEntry("http.method", HttpMethod.GET.toString());
				// we don't check for status_code anymore cause Brave doesn't support it oob
				//.containsEntry("http.status_code", "200")
	}

	@Test
	public void shouldNotStoreHttpStatusCodeWhenResponseCodeHasNotYetBeenSet() throws Exception {
		this.response.setStatus(0);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
		then(this.reporter.getSpans().get(0).tags())
				.doesNotContainKey("http.status_code");
	}

	@Test
	public void startsNewTraceWithParentIdInHeaders() throws Exception {
		this.request = builder()
				.header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(2L))
				.header(PARENT_SPAN_ID_NAME, SpanUtil.idToHex(3L))
				.buildRequest(new MockServletContext());
		
		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
		then(this.reporter.getSpans().get(0).id()).isEqualTo(PARENT_ID);
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost")
				.containsEntry("http.path", "/")
				.containsEntry("http.method", HttpMethod.GET.toString());
	}

	@Test
	public void continuesATraceWhenSpanNotSampled() throws Exception {
		AtomicReference<Span> span = new AtomicReference<>();
		this.request = builder()
				.header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(2L))
				.header(PARENT_SPAN_ID_NAME, SpanUtil.idToHex(3L))
				.header(SAMPLED_ID_NAME, 0)
				.buildRequest(new MockServletContext());
		
		filter.doFilter(this.request, this.response, (req, resp) -> {
			this.filterChain.doFilter(req, resp);
			span.set(this.tracing.tracer().currentSpan());
		});

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(span.get().context().traceIdString())
				.isEqualTo(SpanUtil.idToHex(2L));
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
	}

	@Test
	public void closesSpanInRequestAttrIfStatusCodeNotSuccessful() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.response.setStatus(404);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
	}

	@Test
	public void doesntDetachASpanIfStatusCodeNotSuccessfulAndRequestWasProcessed() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.response.setStatus(404);

		then(Tracing.current().tracer().currentSpan()).isNull();
		filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		verifyParentSpanHttpTags();
	}

	@Test
	public void createsChildFromHeadersWhenJoinUnsupported() throws Exception {
		Tracing tracing = Tracing.newBuilder()
				.currentTraceContext(new StrictCurrentTraceContext())
				.spanReporter(this.reporter)
				.supportsJoin(false)
				.build();
		HttpTracing httpTracing = HttpTracing.create(tracing);
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());

		TracingFilter.create(httpTracing).doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
		then(this.reporter.getSpans().get(0).parentId())
				.isEqualTo(PARENT_ID);
	}

	@Test
	public void addsAdditionalHeaders() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());
		this.traceKeys.getHttp().getHeaders().add("x-foo");
				this.request.addHeader("X-Foo", "bar");

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("http.x-foo", "bar");
	}

	@Test
	public void additionalMultiValuedHeader() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());
		this.traceKeys.getHttp().getHeaders().add("x-foo");
		this.request.addHeader("X-Foo", "bar");
		this.request.addHeader("X-Foo", "spam");
		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
		// We no longer support multi value headers
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("http.x-foo", "bar");
	}

	@Test
	public void shouldAnnotateSpanWithErrorWhenExceptionIsThrown() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());

		this.filterChain = new MockFilterChain() {
			@Override
			public void doFilter(javax.servlet.ServletRequest request,
					javax.servlet.ServletResponse response)
							throws java.io.IOException, javax.servlet.ServletException {
				throw new RuntimeException("Planned");
			}
		};
		try {
			filter.doFilter(this.request, this.response, this.filterChain);
		}
		catch (RuntimeException e) {
			assertEquals("Planned", e.getMessage());
		}

		then(Tracing.current().tracer().currentSpan()).isNull();
		verifyParentSpanHttpTags(HttpStatus.INTERNAL_SERVER_ERROR);
		then(this.reporter.getSpans())
				.hasSize(1);
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("error", "Planned");
	}

	@Test
	public void detachesSpanWhenResponseStatusIsNot2xx() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());

		this.response.setStatus(404);

		then(Tracing.current().tracer().currentSpan()).isNull();
		filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void closesSpanWhenResponseStatusIs2xx() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());
		this.response.setStatus(200);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
	}

	@Test
	public void closesSpanWhenResponseStatusIs3xx() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());
		this.response.setStatus(302);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
	}

	@Test
	public void returns400IfSpanIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, "asd")
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void returns200IfSpanParentIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(PARENT_SPAN_ID_NAME, "-")
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void samplesASpanRegardlessOfTheSamplerWhenXB3FlagsIsPresentAndSetTo1() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
	}

	@Test
	public void doesNotOverrideTheSampledFlagWhenXB3FlagIsSetToOtherValueThan1() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 0)
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
	}

	@SuppressWarnings("Duplicates")
	@Test
	public void samplesWhenDebugFlagIsSetTo1AndOnlySpanIdIsSet() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.header(SPAN_ID_NAME, SpanUtil.idToHex(10L))
				.buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		// It is ok to go without a trace ID, if sampling or debug is set
		then(this.reporter.getSpans())
				.hasSize(1)
				.extracting("id").isNotEqualTo(SpanUtil.idToHex(10L));
	}

	@SuppressWarnings("Duplicates")
	@Test
	public void usesSamplingMechanismWhenIncomingTraceIsMalformed() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(10L))
				.buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isEmpty();
	}

	// #668
	@Test
	public void shouldSetTraceKeysForAnUntracedRequest() throws Exception {
		this.request = builder()
				.param("foo", "bar")
				.buildRequest(new MockServletContext());
		this.response.setStatus(295);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost")
				.containsEntry("http.path", "/")
				.containsEntry("http.method", HttpMethod.GET.toString());
				// we don't check for status_code anymore cause Brave doesn't support it oob
				//.containsEntry("http.status_code", "295")
	}

	@Test
	public void samplesASpanDebugFlagWithInterceptor() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.buildRequest(new MockServletContext());

		neverSampleFilter().doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
		then(this.reporter.getSpans().get(0).name()).isEqualTo("http:/");
	}

	public void verifyParentSpanHttpTags() {
		verifyParentSpanHttpTags(HttpStatus.OK);
	}

	/**
	 * Shows the expansion of {@link import
	 * org.springframework.cloud.sleuth.instrument.TraceKeys}.
	 */
	public void verifyParentSpanHttpTags(HttpStatus status) {
		then(this.reporter.getSpans().size()).isGreaterThan(0);
		then(this.reporter.getSpans().get(0).tags())
				.containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost")
				.containsEntry("http.path", "/")
				.containsEntry("http.method", HttpMethod.GET.toString());
		verifyCurrentSpanStatusCodeForAContinuedSpan(status);

	}

	private void verifyCurrentSpanStatusCodeForAContinuedSpan(HttpStatus status) {
		// Status is only interesting in non-success case. Omitting it saves at least
		// 20bytes per span.
		if (status.is2xxSuccessful()) {
			then(this.reporter.getSpans())
					.hasSize(1);
			then(this.reporter.getSpans().get(0).tags())
					.doesNotContainKey("http.status_code");
		}
		else {
			then(this.reporter.getSpans())
					.hasSize(1);
			then(this.reporter.getSpans().get(0).tags())
					.containsEntry("http.status_code", status.toString());
		}
	}
}
