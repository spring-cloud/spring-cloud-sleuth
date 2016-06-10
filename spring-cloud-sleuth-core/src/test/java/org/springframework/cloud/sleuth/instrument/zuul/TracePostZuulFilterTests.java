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

import javax.servlet.http.HttpServletResponse;
import java.util.Random;

import com.netflix.zuul.context.RequestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Dave Syer
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TracePostZuulFilterTests {

	@Mock HttpServletResponse httpServletResponse;

	private DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(),
			new Random(), new DefaultSpanNamer(), new NoOpSpanLogger(), new NoOpSpanReporter());

	private TracePostZuulFilter filter = new TracePostZuulFilter(this.tracer, new TraceKeys());

	@After
	public void clean() {
		RequestContext.getCurrentContext().unset();
		TestSpanContextHolder.removeCurrentSpan();
		RequestContext.testSetCurrentContext(null);
	}

	@Before
	public void setup() {
		RequestContext requestContext = new RequestContext();
		BDDMockito.given(this.httpServletResponse.getStatus()).willReturn(200);
		requestContext.setResponse(this.httpServletResponse);
		RequestContext.testSetCurrentContext(requestContext);
	}

	@Test
	public void filterPublishesEventAndClosesSpan() throws Exception {
		Span span = this.tracer.createSpan("http:start");
		this.filter.run();

		then(span)
				.hasLoggedAnEvent(Span.CLIENT_RECV)
				.hasATag("http.status_code", "200");
		then(this.tracer.getCurrentSpan()).isNull();
	}
}
