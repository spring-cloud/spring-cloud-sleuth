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

import java.util.Random;

import com.netflix.zuul.context.RequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.verify;

/**
 * @author Dave Syer
 *
 */
public class TracePostZuulFilterTests {

	private ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);

	private DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(),
			new Random(), Mockito.mock(ApplicationEventPublisher.class),
			new DefaultSpanNamer());

	private TracePostZuulFilter filter = new TracePostZuulFilter(this.tracer);

	@After
	@Before
	public void clean() {
		RequestContext.getCurrentContext().unset();
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void filterPublishesEvent() throws Exception {
		this.filter.setApplicationEventPublisher(this.publisher);
		this.tracer.createSpan("http:start");
		this.filter.run();
		verify(this.publisher).publishEvent(isA(ClientReceivedEvent.class));
	}
}
