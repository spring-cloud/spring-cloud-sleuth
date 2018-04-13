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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import brave.ErrorParser;
import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.monitoring.TracerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.netflix.zuul.metrics.EmptyTracerFactory;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;

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
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(SleuthHttpParserAccessor.getClient())
			.serverParser(SleuthHttpParserAccessor.getServer(new ErrorParser()))
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
	public void should_run_when_status_is_unsuccessful() throws Exception {
		BDDMockito.given(this.httpServletResponse.getStatus()).willReturn(456);

		then(this.filter.shouldFilter()).isTrue();
	}

	@Test
	public void should_run_when_status_is_unknown() throws Exception {
		BDDMockito.given(this.httpServletResponse.getStatus()).willReturn(0);

		then(this.filter.shouldFilter()).isTrue();
	}

	@Test
	public void should_handle_span_and_mark_it_as_handled() throws Exception {
		Span span = this.tracing.tracer().nextSpan().name("http:start").start();
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