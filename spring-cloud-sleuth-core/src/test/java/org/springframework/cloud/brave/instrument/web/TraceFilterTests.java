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

package org.springframework.cloud.brave.instrument.web;

import brave.Span;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.brave.ErrorParser;
import org.springframework.cloud.brave.ExceptionMessageErrorParser;
import org.springframework.cloud.brave.TraceKeys;
import org.springframework.cloud.brave.autoconfig.SleuthProperties;
import org.springframework.cloud.brave.util.ArrayListSpanReporter;
import org.springframework.cloud.brave.util.SpanUtil;
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

	static final long PARENT_ID = 10L;
	static final String TRACE_ID_NAME = "X-B3-TraceId";
	static final String SPAN_ID_NAME = "X-B3-SpanId";
	static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
	static final String SPAN_FLAGS = "X-B3-Flags";

	ArrayListSpanReporter spanReporter = new ArrayListSpanReporter();

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.spanReporter(this.reporter)
			.build();
	HttpTracing httpTracing = HttpTracing.create(this.tracing);
	TraceKeys traceKeys = new TraceKeys();
	SleuthProperties properties = new SleuthProperties();

	MockHttpServletRequest request;
	MockHttpServletResponse response;
	MockFilterChain filterChain;
	Sampler sampler = Sampler.ALWAYS_SAMPLE;
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

	@Before
	public void cleanup() {
		this.spanReporter.clear();
	}

	@Test
	public void notTraced() throws Exception {
		this.sampler = Sampler.NEVER_SAMPLE;
		TraceFilter filter = new TraceFilter(beanFactory());

		this.request = get("/favicon.ico").accept(MediaType.ALL)
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.reporter.getSpans()).isEmpty();
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);
		
		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).tags())
				.containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost")
				.containsEntry("http.path", "/")
				.containsEntry("http.method", HttpMethod.GET.toString())
				.containsEntry("http.status_code", HttpStatus.OK.toString());
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

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).tags())
				.containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost")
				.containsEntry("http.path", "/")
				.containsEntry("http.method", HttpMethod.GET.toString())
				.containsEntry("http.status_code", HttpStatus.OK.toString());
	}

	@Test
	public void shouldNotStoreHttpStatusCodeWhenResponseCodeHasNotYetBeenSet() throws Exception {
		TraceFilter filter = new TraceFilter(beanFactory());
		this.response.setStatus(0);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).tags())
				.doesNotContainKey("http.status_code");
	}

	@Test
	public void startsNewTraceWithParentIdInHeaders() throws Exception {
		this.request = builder()
				.header(SPAN_ID_NAME, SpanUtil.idToHex(PARENT_ID))
				.header(TRACE_ID_NAME, SpanUtil.idToHex(2L))
				.header(PARENT_SPAN_ID_NAME, SpanUtil.idToHex(3L))
				.buildRequest(new MockServletContext());
		BeanFactory beanFactory = beanFactory();

		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).id()).isEqualTo(PARENT_ID);
		then(this.spanReporter.getSpans().get(0).tags())
				.containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost")
				.containsEntry("http.path", "/")
				.containsEntry("http.method", HttpMethod.GET.toString());
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {
		Span span = this.tracing.tracer().nextSpan().name("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);

		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)).isNull();
	}

	@Test
	public void closesSpanInRequestAttrIfStatusCodeNotSuccessful() throws Exception {
		Span span = this.tracing.tracer().nextSpan().name("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		this.response.setStatus(404);

		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)).isNotNull();
		then(this.spanReporter.getSpans())
				.hasSize(1);
	}

	@Test
	public void doesntDetachASpanIfStatusCodeNotSuccessfulAndRequestWasProcessed() throws Exception {
		Span span = this.tracing.tracer().nextSpan().name("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		this.request.setAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR, true);
		this.response.setStatus(404);

		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		BeanFactory beanFactory = beanFactory();

		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyParentSpanHttpTags();
	}

	@Test
	public void createsChildFromHeadersWhenJoinUnsupported() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		BeanFactory beanFactory = beanFactory();

		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).parentId())
				.isEqualTo(SpanUtil.idToHex(16));
	}

	@Test
	public void addsAdditionalHeaders() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		this.traceKeys.getHttp().getHeaders().add("x-foo");
		BeanFactory beanFactory = beanFactory();

		TraceFilter filter = new TraceFilter(beanFactory);
		this.request.addHeader("X-Foo", "bar");
		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).tags())
				.containsEntry("http.x-foo", "bar");
	}

	@Test
	public void additionalMultiValuedHeader() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		this.traceKeys.getHttp().getHeaders().add("x-foo");BeanFactory beanFactory = beanFactory();

		TraceFilter filter = new TraceFilter(beanFactory);
		this.request.addHeader("X-Foo", "bar");
		this.request.addHeader("X-Foo", "spam");
		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).tags())
				.containsEntry("http.x-foo", "'bar','spam'");

	}

	@Test
	public void shouldAnnotateSpanWithErrorWhenExceptionIsThrown() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
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
		verifyParentSpanHttpTags(HttpStatus.INTERNAL_SERVER_ERROR);

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).tags())
				.containsEntry("error", "Planned");
	}

	@Test
	public void detachesSpanWhenResponseStatusIsNot2xx() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		this.response.setStatus(404);

		filter.doFilter(this.request, this.response, this.filterChain);

	}

	@Test
	public void closesSpanWhenResponseStatusIs2xx() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		this.response.setStatus(200);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
	}

	@Test
	public void closesSpanWhenResponseStatusIs3xx() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		this.response.setStatus(302);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
	}

	@Test
	public void returns400IfSpanIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, "asd")
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans()).isNotEmpty();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void returns200IfSpanParentIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header(SPAN_ID_NAME, PARENT_ID)
				.header(PARENT_SPAN_ID_NAME, "-")
				.header(TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans()).isNotEmpty();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void samplesASpanRegardlessOfTheSamplerWhenXB3FlagsIsPresentAndSetTo1() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.buildRequest(new MockServletContext());
		this.sampler = Sampler.NEVER_SAMPLE;
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans()).isNotEmpty();
	}

	@Test
	public void doesNotOverrideTheSampledFlagWhenXB3FlagIsSetToOtherValueThan1() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 0)
				.buildRequest(new MockServletContext());
		this.sampler = Sampler.ALWAYS_SAMPLE;
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans()).isNotEmpty();
	}

	@Test
	public void samplesWhenDebugFlagIsSetTo1AndOnlySpanIdIsSet() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.header(SPAN_ID_NAME, 10L)
				.buildRequest(new MockServletContext());
		this.sampler = Sampler.NEVER_SAMPLE;
		BeanFactory beanFactory = beanFactory();

		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).id())
				.isEqualTo(SpanUtil.idToHex(10L));
	}

	@Test
	public void samplesWhenDebugFlagIsSetTo1AndTraceIdIsAlsoSet() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.header(TRACE_ID_NAME, 10L)
				.buildRequest(new MockServletContext());
		this.sampler = Sampler.NEVER_SAMPLE;
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).traceId())
				.isEqualTo(SpanUtil.idToHex(10L));
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

		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).tags())
				.containsEntry("http.url", "http://localhost/?foo=bar")
				.containsEntry("http.host", "localhost")
				.containsEntry("http.path", "/")
				.containsEntry("http.status_code", "295")
				.containsEntry("http.method", HttpMethod.GET.toString());
	}

	@Test
	public void samplesASpanDebugFlagWithInterceptor() throws Exception {
		this.request = builder()
				.header(SPAN_FLAGS, 1)
				.buildRequest(new MockServletContext());
		this.sampler = Sampler.NEVER_SAMPLE;
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);


		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).name()).isEqualTo("http:/");
	}

	public void verifyParentSpanHttpTags() {
		verifyParentSpanHttpTags(HttpStatus.OK);
	}

	/**
	 * Shows the expansion of {@link import
	 * org.springframework.cloud.sleuth.instrument.TraceKeys}.
	 */
	public void verifyParentSpanHttpTags(HttpStatus status) {
		then(this.spanReporter.getSpans())
				.hasSize(1);
		then(this.spanReporter.getSpans().get(0).tags())
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
			then(this.spanReporter.getSpans())
					.hasSize(1);
			then(this.spanReporter.getSpans().get(0).tags())
					.doesNotContainKey("http.status_code");
		}
		else {
			then(this.spanReporter.getSpans())
					.hasSize(1);
			then(this.spanReporter.getSpans().get(0).tags())
					.containsEntry("http.status_code", status.toString());
		}
	}

	private BeanFactory beanFactory() {
		BDDMockito.given(beanFactory.getBean(SkipPatternProvider.class))
				.willThrow(new NoSuchBeanDefinitionException("foo"));
		BDDMockito.given(beanFactory.getBean(SleuthProperties.class))
				.willReturn(this.properties);
		BDDMockito.given(beanFactory.getBean(Tracing.class))
				.willReturn(this.tracing);
		BDDMockito.given(beanFactory.getBean(HttpTracing.class))
				.willReturn(this.httpTracing);
		BDDMockito.given(beanFactory.getBean(TraceKeys.class))
				.willReturn(this.traceKeys);
		BDDMockito.given(beanFactory.getBean(ErrorParser.class))
				.willReturn(new ExceptionMessageErrorParser());
		return beanFactory;
	}
}
