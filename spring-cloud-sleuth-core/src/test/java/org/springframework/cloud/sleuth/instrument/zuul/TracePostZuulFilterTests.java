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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.netflix.zuul.metrics.EmptyTracerFactory;

import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.monitoring.TracerFactory;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dave Syer
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TracePostZuulFilterTests {

	@Mock HttpServletRequest httpServletRequest;
	@Mock HttpServletResponse httpServletResponse;

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(CurrentTraceContext.Default.create())
			.spanReporter(this.reporter)
			.build();
	TraceKeys traceKeys = new TraceKeys();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(SleuthHttpParserAccessor.getClient(this.traceKeys))
			.serverParser(SleuthHttpParserAccessor.getServer(this.traceKeys, new ExceptionMessageErrorParser()))
			.build();
	private TracePostZuulFilter filter = new TracePostZuulFilter(this.httpTracing);
	RequestContext requestContext = new RequestContext();

	@After
	public void clean() {
		RequestContext.getCurrentContext().unset();
		this.httpTracing.tracing().close();
		RequestContext.testSetCurrentContext(null);
	}

	@Before
	public void setup() {
		BDDMockito.given(this.httpServletResponse.getStatus()).willReturn(200);
		this.requestContext.setRequest(this.httpServletRequest);
		this.requestContext.setResponse(this.httpServletResponse);
		RequestContext.testSetCurrentContext(this.requestContext);
		TracerFactory.initialize(new EmptyTracerFactory());
	}

	@Test
	public void filterPublishesEventAndClosesSpan() throws Exception {
		Span span = this.tracing.tracer().nextSpan().name("http:start").start();
		BDDMockito.given(this.httpServletRequest
				.getAttribute(TracePostZuulFilter.ZUUL_CURRENT_SPAN)).willReturn(span);
		BDDMockito.given(this.httpServletResponse.getStatus()).willReturn(456);

		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			this.filter.runFilter();
		} finally {
			span.finish();
		}

		List<zipkin2.Span> spans = this.reporter.getSpans();
		then(spans).hasSize(1);
		// initial span
		then(spans.get(0).tags())
				.containsEntry("http.status_code", "456");
		then(spans.get(0).name()).isEqualTo("http:start");
		then(this.tracing.tracer().currentSpan()).isNull();
	}
}
