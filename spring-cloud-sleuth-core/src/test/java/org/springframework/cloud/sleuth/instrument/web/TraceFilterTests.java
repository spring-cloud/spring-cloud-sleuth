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

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;
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
	static final String SPAN_FLAGS = "X-B3-Flags";

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.spanReporter(this.reporter)
			.build();
	Tracer tracer = this.tracing.tracer();
	TraceKeys traceKeys = new TraceKeys();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(new SleuthHttpClientParser(this.traceKeys))
			.serverParser(new SleuthHttpServerParser(this.traceKeys,
					new ExceptionMessageErrorParser()))
			.build();
	SleuthProperties properties = new SleuthProperties();

	MockHttpServletRequest request;
	MockHttpServletResponse response;
	MockFilterChain filterChain;
	BeanFactory beanFactory = Mockito.mock(BeanFactory.class);

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
		BeanFactory beanFactory = neverSampleTracing();
		TraceFilter filter = new TraceFilter(beanFactory);

		this.request = get("/favicon.ico").accept(MediaType.ALL)
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isEmpty();
	}

	private BeanFactory neverSampleTracing() {
		Tracing tracing = Tracing.newBuilder()
				.currentTraceContext(CurrentTraceContext.Default.create())
				.spanReporter(this.reporter)
				.sampler(Sampler.NEVER_SAMPLE)
				.supportsJoin(false)
				.build();
		HttpTracing httpTracing = HttpTracing.newBuilder(tracing)
				.clientParser(new SleuthHttpClientParser(this.traceKeys))
				.serverParser(new SleuthHttpServerParser(this.traceKeys,
						new ExceptionMessageErrorParser()))
				.build();
		BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(HttpTracing.class)).willReturn(httpTracing);
		return beanFactory;
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(beanFactory());
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
	public void startsNewTraceWithTraceHandlerInterceptor() throws Exception {
		final BeanFactory beanFactory = beanFactory();
		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, (req, resp) -> {
			this.filterChain.doFilter(req, resp);
			// Simulate execution of the TraceHandlerInterceptor
			request.setAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR, 
					tracing.tracer().currentSpan());
		});

		then(Tracing.current().tracer().currentSpan()).isNull();
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
		TraceFilter filter = new TraceFilter(beanFactory());
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
		BeanFactory beanFactory = beanFactory();

		TraceFilter filter = new TraceFilter(beanFactory);
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
	public void continuesSpanInRequestAttr() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);

		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)).isNull();
	}

	@Test
	public void closesSpanInRequestAttrIfStatusCodeNotSuccessful() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		this.response.setStatus(404);

		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)).isNotNull();
		then(this.reporter.getSpans())
				.hasSize(1);
	}

	@Test
	public void doesntDetachASpanIfStatusCodeNotSuccessfulAndRequestWasProcessed() throws Exception {
		Span span = this.tracer.nextSpan().name("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		this.request.setAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR, true);
		this.response.setStatus(404);

		TraceFilter filter = new TraceFilter(beanFactory());

		then(Tracing.current().tracer().currentSpan()).isNull();
		filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());
		BeanFactory beanFactory = beanFactory();
		TraceFilter filter = new TraceFilter(beanFactory);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		verifyParentSpanHttpTags();
	}

	@Test
	public void createsChildFromHeadersWhenJoinUnsupported() throws Exception {
		Tracing tracing = Tracing.newBuilder()
				.currentTraceContext(CurrentTraceContext.Default.create())
				.spanReporter(this.reporter)
				.supportsJoin(false)
				.build();
		HttpTracing httpTracing = HttpTracing.create(tracing);
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());
		BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(HttpTracing.class)).willReturn(httpTracing);
		TraceFilter filter = new TraceFilter(beanFactory);

		filter.doFilter(this.request, this.response, this.filterChain);

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
		BeanFactory beanFactory = beanFactory();
		TraceFilter filter = new TraceFilter(beanFactory);
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
		this.traceKeys.getHttp().getHeaders().add("x-foo");BeanFactory beanFactory = beanFactory();
		TraceFilter filter = new TraceFilter(beanFactory);
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
		BeanFactory beanFactory = beanFactory();
		TraceFilter filter = new TraceFilter(beanFactory);

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
		TraceFilter filter = new TraceFilter(beanFactory());

		this.response.setStatus(404);

		then(Tracing.current().tracer().currentSpan()).isNull();
		filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void closesSpanWhenResponseStatusIs2xx() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(20L))
				.buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
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
		TraceFilter filter = new TraceFilter(beanFactory());
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
		TraceFilter filter = new TraceFilter(beanFactory());

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
		TraceFilter filter = new TraceFilter(beanFactory());

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
		TraceFilter filter = new TraceFilter(neverSampleTracing());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
	}

	@Test
	public void doesNotOverrideTheSampledFlagWhenXB3FlagIsSetToOtherValueThan1() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 0)
				.buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());

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

		TraceFilter filter = new TraceFilter(neverSampleTracing());
		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		// Brave doesn't work like Sleuth. No trace will be created for an invalid span
		// where invalid means that there is no trace id
		then(this.reporter.getSpans()).isEmpty();
	}

	@SuppressWarnings("Duplicates")
	@Test
	public void samplesWhenDebugFlagIsSetTo1AndTraceIdIsAlsoSet() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.header(TRACE_ID_NAME, SpanUtil.idToHex(10L))
				.buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(neverSampleTracing());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(Tracing.current().tracer().currentSpan()).isNull();
		then(this.reporter.getSpans())
				.hasSize(1);
		// Brave creates a new trace if there was no span id
		then(this.reporter.getSpans().get(0).traceId())
				.isNotEqualTo(SpanUtil.idToHex(10L));
	}

	// #668
	@Test
	public void shouldSetTraceKeysForAnUntracedRequest() throws Exception {
		this.request = builder()
				.param("foo", "bar")
				.buildRequest(new MockServletContext());
		this.response.setStatus(295);
		TraceFilter filter = new TraceFilter(beanFactory());

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
		TraceFilter filter = new TraceFilter(neverSampleTracing());

		filter.doFilter(this.request, this.response, this.filterChain);

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

	private BeanFactory beanFactory() {
		BDDMockito.given(beanFactory.getBean(SkipPatternProvider.class))
				.willThrow(new NoSuchBeanDefinitionException("foo"));
		BDDMockito.given(beanFactory.getBean(SleuthProperties.class))
				.willReturn(this.properties);
		BDDMockito.given(beanFactory.getBean(HttpTracing.class))
				.willReturn(this.httpTracing);
		BDDMockito.given(beanFactory.getBean(TraceKeys.class))
				.willReturn(this.traceKeys);
		BDDMockito.given(beanFactory.getBean(ErrorParser.class))
				.willReturn(new ExceptionMessageErrorParser());
		return beanFactory;
	}
}
