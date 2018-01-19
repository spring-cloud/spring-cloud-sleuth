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

import java.util.ArrayList;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.ExceptionMessageErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.cloud.netflix.ribbon.support.RibbonCommandContext;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;

import com.netflix.zuul.context.RequestContext;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceRibbonCommandFactoryTest {


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
	@Mock RibbonCommandFactory ribbonCommandFactory;
	TraceRibbonCommandFactory traceRibbonCommandFactory;
	Span span = this.tracing.tracer().nextSpan().name("name");

	@Before
	@SuppressWarnings({ "deprecation", "unchecked" })
	public void setup() {
		this.traceRibbonCommandFactory = new TraceRibbonCommandFactory(
				this.ribbonCommandFactory, this.httpTracing);
	}

	@After
	public void cleanup() {
		RequestContext.getCurrentContext().unset();
		this.tracing.close();
	}

	@Test
	public void should_attach_trace_headers_to_the_span() throws Exception {
		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(this.span)) {
			this.traceRibbonCommandFactory.create(ribbonCommandContext());
		} finally {
			this.span.finish();
		}

		then(this.reporter.getSpans()).hasSize(1);
		zipkin2.Span span = this.reporter.getSpans().get(0);
		then(span.tags())
				.containsEntry("http.method", "GET")
				.containsEntry("http.url", "http://localhost:1234/foo");
	}

	private RibbonCommandContext ribbonCommandContext() {
		return new RibbonCommandContext("serviceId", "GET", "http://localhost:1234/foo",
				false, new HttpHeaders(), new LinkedMultiValueMap<>(), null, new ArrayList<>());
	}
}
