/*
 * Copyright 2013-2015 the original author or authors.
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

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.catalina.connector.ClientAbortException;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.ListOfSpans;
import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;
import org.springframework.cloud.sleuth.log.SpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ArrayListSpanAccumulator;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.assertThat;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.entry;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author Spencer Gibb
 */
public class TraceFilterTests {

	public static final long PARENT_ID = 10L;

	@Mock SpanLogger spanLogger;
	ArrayListSpanAccumulator spanReporter = new ArrayListSpanAccumulator();
	HttpSpanExtractor spanExtractor = new ZipkinHttpSpanExtractor(Pattern
			.compile(SleuthWebProperties.DEFAULT_SKIP_PATTERN));

	private Tracer tracer;
	private TraceKeys traceKeys = new TraceKeys();
	private SleuthProperties properties = new SleuthProperties();
	private HttpTraceKeysInjector httpTraceKeysInjector;

	private Span span;

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain filterChain;
	private Sampler sampler = new AlwaysSampler();
	BeanFactory beanFactory = Mockito.mock(BeanFactory.class);

	@Before
	public void init() {
		initMocks(this);
		this.tracer = new DefaultTracer(new DelegateSampler(), new Random(),
				new DefaultSpanNamer(), this.spanLogger, this.spanReporter, new TraceKeys()) {
			@Override
			public Span continueSpan(Span span) {
				TraceFilterTests.this.span = super.continueSpan(span);
				return TraceFilterTests.this.span;
			}
		};
		this.request = builder().buildRequest(new MockServletContext());
		this.response = new MockHttpServletResponse();
		this.response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.filterChain = new MockFilterChain();
		this.httpTraceKeysInjector = new HttpTraceKeysInjector(this.tracer, this.traceKeys);
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/?foo=bar").accept(MediaType.APPLICATION_JSON).header("User-Agent",
				"MockMvc");
	}

	@After
	public void cleanup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void notTraced() throws Exception {
		this.sampler = NeverSampler.INSTANCE;
		TraceFilter filter = new TraceFilter(beanFactory());

		this.request = get("/favicon.ico").accept(MediaType.ALL)
				.buildRequest(new MockServletContext());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(this.span.isExportable()).isFalse();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(this.span.tags()).containsEntry("http.status_code", HttpStatus.OK.toString());

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(new ListOfSpans(this.spanReporter.getSpans()))
				.hasSize(1)
				.hasASpanWithTagEqualTo("http.url", "http://localhost/?foo=bar")
				.hasASpanWithTagEqualTo("http.host", "localhost")
				.hasASpanWithTagEqualTo("http.path", "/")
				.hasASpanWithTagEqualTo("http.method", HttpMethod.GET.toString())
				.hasASpanWithTagEqualTo("http.status_code", HttpStatus.OK.toString())
				.allSpansAreExportable();
	}

	@Test
	public void startsNewTraceWithTraceHandlerInterceptor() throws Exception {
		final BeanFactory beanFactory = beanFactory();
		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, (req, resp) -> {
			this.filterChain.doFilter(req, resp);
			// Simulate execution of the TraceHandlerInterceptor
			request.setAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR, tracer.getCurrentSpan());
		});

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(new ListOfSpans(this.spanReporter.getSpans()))
				.hasSize(1)
				.hasASpanWithTagEqualTo("http.url", "http://localhost/?foo=bar")
				.hasASpanWithTagEqualTo("http.host", "localhost")
				.hasASpanWithTagEqualTo("http.path", "/")
				.hasASpanWithTagEqualTo("http.method", HttpMethod.GET.toString())
				.hasASpanWithTagEqualTo("http.status_code", HttpStatus.OK.toString())
				.allSpansAreExportable();
	}

	@Test
	public void shouldNotStoreHttpStatusCodeWhenResponseCodeHasNotYetBeenSet() throws Exception {
		TraceFilter filter = new TraceFilter(beanFactory());
		this.response.setStatus(0);
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(this.span.tags()).doesNotContainKey("http.status_code");

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void startsNewTraceWithParentIdInHeaders() throws Exception {
		this.request = builder()
				.header(Span.SPAN_ID_NAME, Span.idToHex(PARENT_ID))
				.header(Span.TRACE_ID_NAME, Span.idToHex(2L))
				.header(Span.PARENT_ID_NAME, Span.idToHex(3L))
				.buildRequest(new MockServletContext());BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(this.spanReporter);

		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(this.span.getSpanId()).isEqualTo(PARENT_ID);
		assertThat(this.span)
				.hasATag("http.url", "http://localhost/?foo=bar")
				.hasATag("http.host", "localhost")
				.hasATag("http.path", "/")
				.hasATag("http.method", "GET");
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void continuesSpanInRequestAttr() throws Exception {
		Span span = this.tracer.createSpan("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		// It should have been removed from the thread local context so simulate that
		TestSpanContextHolder.removeCurrentSpan();

		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(this.request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)).isNull();
	}

	@Test
	public void closesSpanInRequestAttrIfStatusCodeNotSuccessful() throws Exception {
		Span span = this.tracer.createSpan("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		this.response.setStatus(404);
		// It should have been removed from the thread local context so simulate that
		TestSpanContextHolder.removeCurrentSpan();

		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(this.request.getAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR)).isNotNull();
	}

	@Test
	public void doesntDetachASpanIfStatusCodeNotSuccessfulAndRequestWasProcessed() throws Exception {
		Span span = this.tracer.createSpan("http:foo");
		this.request.setAttribute(TraceFilter.TRACE_REQUEST_ATTR, span);
		this.request.setAttribute(TraceFilter.TRACE_ERROR_HANDLED_REQUEST_ATTR, true);
		this.response.setStatus(404);
		// It should have been removed from the thread local context so simulate that
		TestSpanContextHolder.removeCurrentSpan();

		TraceFilter filter = new TraceFilter(beanFactory());
		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(this.spanReporter);

		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, this.filterChain);

		verifyParentSpanHttpTags();

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void createsChildFromHeadersWhenJoinUnsupported() throws Exception {
		this.properties.setSupportsJoin(false);
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(this.spanReporter);

		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(this.spanReporter.getSpans().get(0).getParents().get(0))
				.isEqualTo(16); // test data is in hex!
	}

	@Test
	public void addsAdditionalHeaders() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		this.traceKeys.getHttp().getHeaders().add("x-foo");
		BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(Sampler.class)).willReturn(new AlwaysSampler());
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(this.spanReporter);

		TraceFilter filter = new TraceFilter(beanFactory);
		this.request.addHeader("X-Foo", "bar");
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(this.span.tags()).contains(entry("http.x-foo", "bar"));
		assertThat(this.span.tags()).contains(entry("http.x-foo", "bar"));
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void ensuresThatParentSpanIsStoppedWhenReported() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(spanIsStoppedVeryfingReporter());

		filter.doFilter(this.request, this.response, this.filterChain);
	}

	SpanReporter spanIsStoppedVeryfingReporter() {
		return (span) -> assertThat(span.getEnd()).as("Span has to be stopped before reporting").isNotZero();
	}

	@Test
	public void additionalMultiValuedHeader() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		this.traceKeys.getHttp().getHeaders().add("x-foo");BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(this.spanReporter);

		TraceFilter filter = new TraceFilter(beanFactory);
		this.request.addHeader("X-Foo", "bar");
		this.request.addHeader("X-Foo", "spam");
		filter.doFilter(this.request, this.response, this.filterChain);

		assertThat(this.span.tags()).contains(entry("http.x-foo", "'bar','spam'"));

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void shouldAnnotateSpanWithErrorWhenExceptionIsThrown() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(this.spanReporter);

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

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(new ListOfSpans(this.spanReporter.getSpans()))
				.hasASpanWithTagEqualTo(Span.SPAN_ERROR_TAG_NAME, "Planned");
	}

	@Test
	public void detachesSpanWhenResponseStatusIsNot2xx() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		this.response.setStatus(404);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void closesSpanWhenResponseStatusIs2xx() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		this.response.setStatus(200);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void closesSpanWhenResponseStatusIs2xxAndExceptionIsClientAbortException() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		BDDMockito.given(beanFactory.getBean(ErrorController.class)).willReturn(() -> "/error");
		List<ExceptionToIgnoreInTraceFilter> filters = Lists.newArrayList(getClientAbortExpcetionToIgnoreInTraceFilter());
		BDDMockito.given(beanFactory.getBean(ExceptionToIgnoreInTraceFilterProvider.class))
				.willReturn(getExceptionToIgnoreInTraceFilterProvider(filters));
		this.response = new MockHttpServletResponse(){
			@Override
			public ServletOutputStream getOutputStream() {
				ServletOutputStream outputStream = super.getOutputStream();
				return new ServletOutputStream() {
					@Override
					public boolean isReady() {
						return outputStream.isReady();
					}

					@Override
					public void setWriteListener(WriteListener listener) {
						outputStream.setWriteListener(listener);
					}

					@Override
					public void write(int b) throws IOException {
						outputStream.write(b);
					}

					@Override
					public void flush() throws IOException {
						throw new ClientAbortException("Broken pipe");
					}
				};
			}
		};
		response.setStatus(200);
		this.filterChain = new MockFilterChain(){
			@Override
			public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
				ServletOutputStream outputStream = response.getOutputStream();
				outputStream.write(1);
				outputStream.flush();
			}
		};
		try {
			filter.doFilter(this.request, this.response, this.filterChain);
		}catch (ClientAbortException e){
			// ig
		}
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(spanReporter.getSpans()).hasSize(1);
	}
	@Test
	public void closesSpanWhenResponseStatusIs2xxAndClientAbortExceptionThrowAfterTraceFilter() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		BDDMockito.given(beanFactory.getBean(ErrorController.class)).willReturn(() -> "/error");
		List<ExceptionToIgnoreInTraceFilter> filters = Lists.newArrayList(getClientAbortExpcetionToIgnoreInTraceFilter());
		BDDMockito.given(beanFactory.getBean(ExceptionToIgnoreInTraceFilterProvider.class))
				.willReturn(getExceptionToIgnoreInTraceFilterProvider(filters));
		this.response = new MockHttpServletResponse();
		response.setStatus(200);
		this.filterChain = new MockFilterChain(){
			@Override
			public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
				throw new ClientAbortException();
			}
		};
		try {
			filter.doFilter(this.request, this.response, this.filterChain);
		}catch (ClientAbortException e){
			// ig
		}
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(spanReporter.getSpans()).hasSize(1);
	}

	private ExceptionToIgnoreInTraceFilter getClientAbortExpcetionToIgnoreInTraceFilter() {
		return new ExceptionToIgnoreInTraceFilter(){
			@Override
			public String exceptionClassName() {
				return ClientAbortException.class.getName();
			}
		};
	}

	private ExceptionToIgnoreInTraceFilterProvider getExceptionToIgnoreInTraceFilterProvider(List<ExceptionToIgnoreInTraceFilter> filters) {
		return new ExceptionToIgnoreInTraceFilterProvider(filters);
	}

	@Test
	public void closesSpanWhenResponseStatusIs3xx() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());
		this.response.setStatus(302);

		filter.doFilter(this.request, this.response, this.filterChain);

		then(TestSpanContextHolder.getCurrentSpan()).isNull();
	}

	@Test
	public void returns400IfSpanIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, "asd")
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(new ArrayList<>(this.spanReporter.getSpans())).isNotEmpty();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void returns200IfSpanParentIsMalformedAndCreatesANewSpan() throws Exception {
		this.request = builder().header(Span.SPAN_ID_NAME, PARENT_ID)
				.header(Span.PARENT_ID_NAME, "-")
				.header(Span.TRACE_ID_NAME, 20L).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(new ArrayList<>(this.spanReporter.getSpans())).isNotEmpty();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
		then(this.response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	public void samplesASpanRegardlessOfTheSamplerWhenXB3FlagsIsPresentAndSetTo1() throws Exception {
		this.request = builder()
				.header(Span.SPAN_FLAGS, 1)
				.buildRequest(new MockServletContext());
		this.sampler = new NeverSampler();
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(new ListOfSpans(this.spanReporter.getSpans())).allSpansAreExportable();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void doesNotOverrideTheSampledFlagWhenXB3FlagIsSetToOtherValueThan1() throws Exception {
		this.request = builder()
				.header(Span.SPAN_FLAGS, 0)
				.buildRequest(new MockServletContext());
		this.sampler = new AlwaysSampler();
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(new ListOfSpans(this.spanReporter.getSpans())).allSpansAreExportable();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void samplesWhenDebugFlagIsSetTo1AndOnlySpanIdIsSet() throws Exception {
		this.request = builder()
				.header(Span.SPAN_FLAGS, 1)
				.header(Span.SPAN_ID_NAME, 10L)
				.buildRequest(new MockServletContext());
		this.sampler = new NeverSampler();
		BeanFactory beanFactory = beanFactory();
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(this.spanReporter);

		TraceFilter filter = new TraceFilter(beanFactory);
		filter.doFilter(this.request, this.response, this.filterChain);

		then(new ListOfSpans(this.spanReporter.getSpans()))
				.allSpansAreExportable().hasSize(1).hasASpanWithSpanId(Span.hexToId("10"));
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void samplesWhenDebugFlagIsSetTo1AndTraceIdIsAlsoSet() throws Exception {
		this.request = builder()
				.header(Span.SPAN_FLAGS, 1)
				.header(Span.TRACE_ID_NAME, 10L)
				.buildRequest(new MockServletContext());
		this.sampler = new NeverSampler();
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(new ListOfSpans(this.spanReporter.getSpans()))
				.allSpansAreExportable().allSpansHaveTraceId(Span.hexToId("10"));
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
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

		then(new ListOfSpans(this.spanReporter.getSpans()))
				.hasASpanWithName("http:/")
				.hasASpanWithTagEqualTo("http.url", "http://localhost/?foo=bar")
				.hasASpanWithTagEqualTo("http.host", "localhost")
				.hasASpanWithTagEqualTo("http.path", "/")
				.hasASpanWithTagEqualTo("http.method", "GET")
				.hasASpanWithTagEqualTo("http.status_code", "295")
				.allSpansAreExportable();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	@Test
	public void samplesASpanDebugFlagWithInterceptor() throws Exception {
		this.request = builder()
				.header(Span.SPAN_FLAGS, 1)
				.buildRequest(new MockServletContext());
		this.sampler = new NeverSampler();
		TraceFilter filter = new TraceFilter(beanFactory());

		filter.doFilter(this.request, this.response, this.filterChain);

		then(new ListOfSpans(this.spanReporter.getSpans()))
				.doesNotHaveASpanWithName("http:/parent/")
				.hasASpanWithName("http:/")
				.hasSize(1)
				.allSpansAreExportable();
		then(TestSpanContextHolder.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
	}

	public void verifyParentSpanHttpTags() {
		verifyParentSpanHttpTags(HttpStatus.OK);
	}

	/**
	 * Shows the expansion of {@link import
	 * org.springframework.cloud.sleuth.instrument.TraceKeys}.
	 */
	public void verifyParentSpanHttpTags(HttpStatus status) {
		assertThat(this.span.tags()).contains(entry("http.host", "localhost"),
				entry("http.url", "http://localhost/?foo=bar"), entry("http.path", "/"),
				entry("http.method", "GET"));
		verifyCurrentSpanStatusCodeForAContinuedSpan(status);

	}

	private void verifyCurrentSpanStatusCodeForAContinuedSpan(HttpStatus status) {
		// Status is only interesting in non-success case. Omitting it saves at least
		// 20bytes per span.
		if (status.is2xxSuccessful()) {
			assertThat(this.span.tags()).doesNotContainKey("http.status_code");
		}
		else {
			assertThat(this.span.tags()).containsEntry("http.status_code",
					status.toString());
		}
	}

	private class DelegateSampler implements Sampler {
		@Override
		public boolean isSampled(Span span) {
			return TraceFilterTests.this.sampler.isSampled(span);
		}
	}

	private BeanFactory beanFactory() {
		BDDMockito.given(beanFactory.getBean(SkipPatternProvider.class))
				.willThrow(new NoSuchBeanDefinitionException("foo"));
		BDDMockito.given(beanFactory.getBean(SleuthProperties.class)).willReturn(this.properties);
		BDDMockito.given(beanFactory.getBean(Tracer.class)).willReturn(this.tracer);
		BDDMockito.given(beanFactory.getBean(TraceKeys.class)).willReturn(this.traceKeys);
		BDDMockito.given(beanFactory.getBean(HttpSpanExtractor.class)).willReturn(this.spanExtractor);
		BDDMockito.given(beanFactory.getBean(SpanReporter.class)).willReturn(this.spanReporter);
		BDDMockito.given(beanFactory.getBean(HttpTraceKeysInjector.class)).willReturn(this.httpTraceKeysInjector);
		BDDMockito.given(beanFactory.getBean(ErrorParser.class)).willReturn(new ExceptionMessageErrorParser());
		return beanFactory;
	}
}
