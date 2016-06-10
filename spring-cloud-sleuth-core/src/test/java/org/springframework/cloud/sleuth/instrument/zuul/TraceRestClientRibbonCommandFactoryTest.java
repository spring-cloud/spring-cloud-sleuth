/*
 * Copyright 2013-2016 the original author or authors.
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

import com.netflix.client.http.HttpRequest;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.context.RequestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RestClientRibbonCommand;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceRestClientRibbonCommandFactoryTest {

	@Mock Tracer tracer;
	@Mock SpringClientFactory springClientFactory;
	SpanInjector<HttpRequest.Builder> spanInjector = new RequestBuilderContextInjector();
	@Mock HttpTraceKeysInjector httpTraceKeysInjector;
	TraceRestClientRibbonCommandFactory traceRestClientRibbonCommandFactory;

	@Before
	@SuppressWarnings({ "deprecation", "unchecked" })
	public void setup() {
		this.traceRestClientRibbonCommandFactory = new TraceRestClientRibbonCommandFactory(
				this.springClientFactory, this.tracer, this.spanInjector,
				httpTraceKeysInjector);
		given(this.springClientFactory.getClient(anyString(), any(Class.class)))
				.willReturn(new RestClient());
		Span span = Span.builder().name("name").spanId(1L).traceId(2L).parent(3L)
				.processId("processId").build();
		given(this.tracer.getCurrentSpan()).willReturn(span);
		given(this.tracer.isTracing()).willReturn(true);
	}

	@After
	public void cleanup() {
		RequestContext.getCurrentContext().unset();
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_wrap_ribbon_command_in_a_sleuth_representation() throws Exception {
		RestClientRibbonCommand restClientRibbonCommand = this.traceRestClientRibbonCommandFactory
				.create(ribbonCommandContext());

		then(restClientRibbonCommand).isInstanceOf(
				TraceRestClientRibbonCommandFactory.TraceRestClientRibbonCommand.class);
	}

	@Test
	public void should_attach_trace_headers_to_the_sent_request() throws Exception {
		RestClientRibbonCommand restClientRibbonCommand = this.traceRestClientRibbonCommandFactory
				.create(ribbonCommandContext());
		TraceRestClientRibbonCommandFactory.TraceRestClientRibbonCommand traceRestClientRibbonCommand = (TraceRestClientRibbonCommandFactory.TraceRestClientRibbonCommand) restClientRibbonCommand;
		HttpRequest.Builder builder = new HttpRequest.Builder();

		traceRestClientRibbonCommand.customizeRequest(builder);

		HttpRequest httpRequest = builder.build();
		then(httpRequest.getHttpHeaders().getFirstValue(Span.SPAN_ID_NAME))
				.isEqualTo("1");
		then(httpRequest.getHttpHeaders().getFirstValue(Span.TRACE_ID_NAME))
				.isEqualTo("2");
		then(httpRequest.getHttpHeaders().getFirstValue(Span.SPAN_NAME_NAME))
				.isEqualTo("name");
		then(httpRequest.getHttpHeaders().getFirstValue(Span.PARENT_ID_NAME))
				.isEqualTo("3");
		then(httpRequest.getHttpHeaders().getFirstValue(Span.PROCESS_ID_NAME))
				.isEqualTo("processId");
	}

	private RibbonCommandContext ribbonCommandContext() {
		return new RibbonCommandContext("serviceId", "GET", "http://localhost:1234/foo",
				false, null, null, null);
	}
}