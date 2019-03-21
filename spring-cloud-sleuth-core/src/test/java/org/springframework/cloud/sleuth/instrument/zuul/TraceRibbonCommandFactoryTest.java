/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandContext;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.HttpTraceKeysInjector;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;

import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.context.RequestContext;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceRibbonCommandFactoryTest {

	@Mock Tracer tracer;
	@Mock SpringClientFactory springClientFactory;
	@Mock BeanFactory beanFactory;
	HttpTraceKeysInjector httpTraceKeysInjector;
	@Mock RibbonCommandFactory ribbonCommandFactory;
	TraceRibbonCommandFactory traceRibbonCommandFactory;
	Span span = Span.builder().name("name").spanId(1L).traceId(2L).parent(3L)
			.processId("processId").build();

	@Before
	@SuppressWarnings({ "deprecation", "unchecked" })
	public void setup() {
		this.httpTraceKeysInjector = new HttpTraceKeysInjector(this.tracer, new TraceKeys());
		this.traceRibbonCommandFactory = new TraceRibbonCommandFactory(
				this.ribbonCommandFactory, this.beanFactory);
		given(this.springClientFactory.getClient(anyString(), any(Class.class)))
				.willReturn(new RestClient());
		given(this.tracer.getCurrentSpan()).willReturn(span);
		given(this.tracer.isTracing()).willReturn(true);
		given(this.beanFactory.getBean(Tracer.class))
				.willReturn(this.tracer);
		given(this.beanFactory.getBean(HttpTraceKeysInjector.class))
				.willReturn(this.httpTraceKeysInjector);
	}

	@After
	public void cleanup() {
		RequestContext.getCurrentContext().unset();
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void should_attach_trace_headers_to_the_span() throws Exception {
		this.traceRibbonCommandFactory.create(ribbonCommandContext());

		then(this.span).hasATag("http.method", "GET");
		then(this.span).hasATag("http.url", "http://localhost:1234/foo");
	}

	private RibbonCommandContext ribbonCommandContext() {
		return new RibbonCommandContext("serviceId", "GET", "http://localhost:1234/foo",
				false, new HttpHeaders(), new LinkedMultiValueMap<>(), null, new ArrayList<>());
	}
}
