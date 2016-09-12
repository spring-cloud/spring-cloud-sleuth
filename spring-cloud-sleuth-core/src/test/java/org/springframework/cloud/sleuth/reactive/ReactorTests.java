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

package org.springframework.cloud.sleuth.reactive;

import java.util.Random;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.NoOpSpanReporter;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.reactive.TracePublisher;
import org.springframework.cloud.sleuth.log.NoOpSpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;

import com.jayway.awaitility.Awaitility;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
//@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(classes = ReactorTests.Config.class)
public class ReactorTests {

	@Autowired Tracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
			new DefaultSpanNamer(), new NoOpSpanLogger(), new NoOpSpanReporter());

	@Before public void setup() {
		ExceptionUtils.setFail(true);
		TestSpanContextHolder.removeCurrentSpan();
	}

	@After public void cleanup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test public void should_pass_tracing_info_when_using_reactor() {
		Span span = this.tracer.createSpan("foo");
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();
		Publisher<Integer> traced = TracePublisher.from(Flux.just(1, 2, 3), this.tracer);

		Flux.from(traced)
				.map( d -> d + 1)
				.map( d -> d + 1)
				.map( (d) -> {
					spanInOperation.set(ReactorTests.this.tracer.getCurrentSpan());
					return d + 1;
				})
				.map( d -> d + 1)
				.subscribe(System.out::println);


		then(this.tracer.getCurrentSpan()).isNull();
		then(spanInOperation.get()).isEqualTo(span);
		then(ExceptionUtils.getLastException()).isNull();
	}

	void initHook(Tracer tracer) {
		Schedulers.setFactory(new Schedulers.Factory() {
			@Override public Scheduler newSingle(ThreadFactory threadFactory) {
				return new TraceScheduler(Schedulers.Factory.super.newSingle(threadFactory), tracer);
			}

		});
	}

	@Test public void should_pass_tracing_info_when_using_reactor_async() {
		initHook(this.tracer);
		Span span = this.tracer.createSpan("foo");
		final AtomicReference<Span> spanInOperation = new AtomicReference<>();

		Flux.just(1, 2, 3)
				.publishOn(Schedulers.single())
				.log("reactor.")
				.map( d -> d + 1)
				.map( d -> d + 1)
				.map( (d) -> {
					spanInOperation.set(ReactorTests.this.tracer.getCurrentSpan());
					return d + 1;
				})
				.map( d -> d + 1)
				.subscribe();

		Awaitility.await().until(() -> {
				then(spanInOperation.get()).isEqualTo(span);
				then(this.tracer.getCurrentSpan()).isNull();
				then(ExceptionUtils.getLastException()).isNull();
		});
		
		Span foo2 = this.tracer.createSpan("foo2");

		Flux.just(1, 2, 3)
				.publishOn(Schedulers.single())
				.log("reactor.")
				.map( d -> d + 1)
				.map( d -> d + 1)
				.map( (d) -> {
					spanInOperation.set(ReactorTests.this.tracer.getCurrentSpan());
					return d + 1;
				})
				.map( d -> d + 1)
				.blockLast();

		then(this.tracer.getCurrentSpan()).isNull();
		then(ExceptionUtils.getLastException()).isNull();
		then(spanInOperation.get()).isEqualTo(foo2);
	}

	@EnableAutoConfiguration static class Config {

	}
}
