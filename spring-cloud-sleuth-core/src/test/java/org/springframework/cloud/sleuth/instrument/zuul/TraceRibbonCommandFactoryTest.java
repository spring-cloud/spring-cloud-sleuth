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

import brave.ErrorParser;
import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import com.netflix.zuul.context.RequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.ribbon.support.RibbonCommandContext;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.cloud.sleuth.instrument.web.SleuthHttpParserAccessor;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceRibbonCommandFactoryTest {

	ArrayListSpanReporter reporter = new ArrayListSpanReporter();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.spanReporter(this.reporter)
			.build();
	HttpTracing httpTracing = HttpTracing.newBuilder(this.tracing)
			.clientParser(SleuthHttpParserAccessor.getClient())
			.serverParser(SleuthHttpParserAccessor.getServer(new ErrorParser()))
			.build();
	@Mock BeanFactory beanFactory;
	@Mock RibbonCommandFactory ribbonCommandFactory;
	@Mock RibbonCommand ribbonCommand;
	TraceRibbonCommandFactory traceRibbonCommandFactory;
	Span span = this.tracing.tracer().nextSpan().name("name");

	@Before
	@SuppressWarnings({ "deprecation", "unchecked" })
	public void setup() {
		BDDMockito.given(this.beanFactory.getBean(HttpTracing.class))
				.willReturn(this.httpTracing);
		this.traceRibbonCommandFactory = new TraceRibbonCommandFactory(
				this.ribbonCommandFactory, this.beanFactory);
		BDDMockito.given(this.ribbonCommandFactory
				.create(BDDMockito.any(RibbonCommandContext.class)))
				.willReturn(this.ribbonCommand);
	}

	@After
	public void cleanup() {
		RequestContext.getCurrentContext().unset();
		this.tracing.close();
	}

	@Test
	public void should_attach_trace_headers_to_the_span() throws Exception {
		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(this.span)) {
			RibbonCommand ribbonCommand = this.traceRibbonCommandFactory
					.create(ribbonCommandContext());
			ribbonCommand.execute();
		} finally {
			this.span.finish();
		}

		then(this.reporter.getSpans()).hasSize(2);
		// RPC
		zipkin2.Span span = this.reporter.getSpans().get(0);
		then(span.tags())
				.containsEntry("http.method", "GET")
				.containsEntry("http.url", "http://localhost:1234/foo");
		zipkin2.Span main = this.reporter.getSpans().get(1);
		then(main.name()).isEqualTo("name");
	}

	private RibbonCommandContext ribbonCommandContext() {
		return new RibbonCommandContext("serviceId", "GET", "http://localhost:1234/foo",
				false, new HttpHeaders(), new LinkedMultiValueMap<>(), null, new ArrayList<>());
	}
}
