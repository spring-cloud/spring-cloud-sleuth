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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;

import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.monitoring.MonitoringHelper;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Dave Syer
 *
 */
public class TracePreZuulFilterTests {

	private DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
			new DefaultSpanNamer(), new NoOpSpanLogger(), new NoOpSpanReporter());

	private TracePreZuulFilter filter = new TracePreZuulFilter(this.tracer, new RequestContextInjector());

	@Before
	public void setup() {
		MonitoringHelper.initMocks();
	}

	@After
	@Before
	public void clean() {
		RequestContext.getCurrentContext().unset();
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void filterAddsHeaders() throws Exception {
		this.tracer.createSpan("http:start");

		this.filter.runFilter();

		RequestContext ctx = RequestContext.getCurrentContext();
		then(ctx.getZuulRequestHeaders().get(Span.TRACE_ID_NAME))
				.isNotNull();
		then(ctx.getZuulRequestHeaders().get(Span.SAMPLED_NAME))
				.isEqualTo(Span.SPAN_SAMPLED);
	}

	@Test
	public void notSampledIfNotExportable() throws Exception {
		this.tracer.createSpan("http:start", NeverSampler.INSTANCE);

		this.filter.runFilter();

		RequestContext ctx = RequestContext.getCurrentContext();
		then(ctx.getZuulRequestHeaders().get(Span.TRACE_ID_NAME))
				.isNotNull();
		then(ctx.getZuulRequestHeaders().get(Span.SAMPLED_NAME))
				.isEqualTo(Span.SPAN_NOT_SAMPLED);
	}


	@Test
	public void shouldCloseSpanWhenExceptionIsThrown() throws Exception {
		Span startedSpan = this.tracer.createSpan("http:start", NeverSampler.INSTANCE);
		final AtomicReference<Span> span = new AtomicReference<>();

		new TracePreZuulFilter(this.tracer, new RequestContextInjector()) {
			@Override
			public Object run() {
				super.run();
				span.set(TracePreZuulFilterTests.this.tracer.getCurrentSpan());
				throw new RuntimeException();
			}
		}.runFilter();

		then(startedSpan).isNotEqualTo(span.get());
		then(span.get().logs()).extracting("event").contains(Span.CLIENT_SEND);
		then(this.tracer.getCurrentSpan()).isEqualTo(startedSpan);
	}

	@Test
	public void shouldNotCloseSpanWhenNoExceptionIsThrown() throws Exception {
		Span startedSpan = this.tracer.createSpan("http:start", NeverSampler.INSTANCE);
		final AtomicReference<Span> span = new AtomicReference<>();

		new TracePreZuulFilter(this.tracer, new RequestContextInjector()) {
			@Override
			public Object run() {
				span.set(TracePreZuulFilterTests.this.tracer.getCurrentSpan());
				return super.run();
			}
		}.runFilter();

		then(startedSpan).isNotEqualTo(span.get());
		then(span.get().logs()).extracting("event").contains(Span.CLIENT_SEND);
		then(span.get().tags()).containsKey(Span.SPAN_LOCAL_COMPONENT_TAG_NAME);
		then(this.tracer.getCurrentSpan()).isEqualTo(span.get());
	}

}
