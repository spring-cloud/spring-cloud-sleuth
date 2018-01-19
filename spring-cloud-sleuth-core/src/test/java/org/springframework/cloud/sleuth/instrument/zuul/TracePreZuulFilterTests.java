/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.monitoring.MonitoringHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dave Syer
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TracePreZuulFilterTests {
	static final String TRACE_ID_NAME = "X-B3-TraceId";
	static final String SAMPLED_NAME = "X-B3-Sampled";

	@Mock HttpServletRequest httpServletRequest;

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.spanReporter(this.reporter)
			.build();
	Tracer tracer = this.tracing.tracer();
	TraceKeys traceKeys = new TraceKeys();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(SleuthHttpParserAccessor.getClient(this.traceKeys))
			.serverParser(SleuthHttpParserAccessor.getServer(this.traceKeys, new ExceptionMessageErrorParser()))
			.build();
	ErrorParser errorParser = new ExceptionMessageErrorParser();

	private TracePreZuulFilter filter = new TracePreZuulFilter(this.httpTracing, this.errorParser);

	@After
	public void clean() {
		RequestContext.getCurrentContext().unset();
		this.tracing.close();
		RequestContext.testSetCurrentContext(null);
	}

	@Before
	public void setup() {
		MonitoringHelper.initMocks();
		RequestContext requestContext = new RequestContext();
		BDDMockito.given(this.httpServletRequest.getRequestURI()).willReturn("http://foo.bar");
		BDDMockito.given(this.httpServletRequest.getMethod()).willReturn("GET");
		requestContext.setRequest(this.httpServletRequest);
		RequestContext.testSetCurrentContext(requestContext);
	}

	@Test
	public void filterAddsHeaders() throws Exception {
		Span span = this.tracer.nextSpan().name("http:start").start();

		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(span)) {
			this.filter.runFilter();
		} finally {
			span.finish();
		}

		RequestContext ctx = RequestContext.getCurrentContext();
		then(ctx.getZuulRequestHeaders().get(TRACE_ID_NAME))
				.isNotNull();
		then(ctx.getZuulRequestHeaders().get(SAMPLED_NAME))
				.isEqualTo("1");
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void notSampledIfNotExportable() throws Exception {
		Tracing tracing = Tracing.newBuilder()
				.sampler(Sampler.NEVER_SAMPLE)
				.currentTraceContext(CurrentTraceContext.Default.create())
				.spanReporter(this.reporter)
				.build();
		HttpTracing httpTracing = HttpTracing.create(tracing);
		this.filter = new TracePreZuulFilter(httpTracing, this.errorParser);
		
		Span span = tracing.tracer().nextSpan().name("http:start").start();

		try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
			this.filter.runFilter();
		} finally {
			span.finish();
		}

		RequestContext ctx = RequestContext.getCurrentContext();
		then(ctx.getZuulRequestHeaders().get(TRACE_ID_NAME))
				.isNotNull();
		then(ctx.getZuulRequestHeaders().get(SAMPLED_NAME))
				.isEqualTo("0");
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldCloseSpanWhenExceptionIsThrown() throws Exception {
		Span startedSpan = this.tracer.nextSpan().name("http:start").start();
		final AtomicReference<Span> span = new AtomicReference<>();

		try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(startedSpan)) {
			new TracePreZuulFilter(this.httpTracing, this.errorParser) {
				@Override
				public Object run() {
					super.run();
					span.set(
							TracePreZuulFilterTests.this.tracer.currentSpan());
					throw new RuntimeException("foo");
				}
			}.runFilter();
		} finally {
			startedSpan.finish();
		}

		then(startedSpan).isNotEqualTo(span.get());
		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(2);
		// initial span
		then(spans.get(0).tags())
				.containsEntry("http.method", "GET")
				.containsEntry("error", "foo");
		// span from zuul
		then(spans.get(1).name()).isEqualTo("http:start");
		then(this.tracer.currentSpan()).isNull();
	}

	@Test
	public void shouldNotCloseSpanWhenNoExceptionIsThrown() throws Exception {
		Span startedSpan = this.tracer.nextSpan().name("http:start").start();
		final AtomicReference<Span> span = new AtomicReference<>();

		try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(startedSpan)) {
			new TracePreZuulFilter(this.httpTracing, this.errorParser) {
				@Override
				public Object run() {
					span.set(
							TracePreZuulFilterTests.this.tracer.currentSpan());
					return super.run();
				}
			}.runFilter();
		} finally {
			startedSpan.finish();
		}

		then(startedSpan).isNotEqualTo(span.get());
		then(this.tracer.currentSpan()).isNull();
		then(this.reporter.getSpans()).isNotEmpty();
	}

}
