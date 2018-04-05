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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * @author Marcin Grzejszczak
 */
public class TraceAsyncListenableTaskExecutorTest {

	AsyncListenableTaskExecutor delegate = new SimpleAsyncTaskExecutor();
	Tracing tracing = Tracing.newBuilder()
			.currentTraceContext(new StrictCurrentTraceContext())
			.build();
	Tracer tracer = this.tracing.tracer();
	TraceAsyncListenableTaskExecutor traceAsyncListenableTaskExecutor = new TraceAsyncListenableTaskExecutor(
			this.delegate, this.tracing);

	@Test
	public void should_submit_listenable_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = this.tracer.nextSpan().name("foo");

		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceAsyncListenableTaskExecutor.submitListenable(aRunnable(this.tracing, executed)).get();
		} finally {
			span.finish();
		}

		BDDAssertions.then(executed.get()).isTrue();
	}

	@Test
	public void should_submit_listenable_trace_callable() throws Exception {
		Span span = this.tracer.nextSpan().name("foo");
		Span spanFromListenable;

		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			spanFromListenable = this.traceAsyncListenableTaskExecutor
					.submitListenable(aCallable(this.tracing)).get();
		} finally {
			span.finish();
		}

		BDDAssertions.then(spanFromListenable).isNotNull();
	}

	@Test
	public void should_execute_a_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = this.tracer.nextSpan().name("foo");

		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceAsyncListenableTaskExecutor.execute(aRunnable(this.tracing, executed));
		} finally {
			span.finish();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS)
				.untilAsserted(() -> {
					BDDAssertions.then(executed.get()).isTrue();
				});
	}

	@Test
	public void should_execute_with_timeout_a_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = this.tracer.nextSpan().name("foo");

		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceAsyncListenableTaskExecutor.execute(aRunnable(this.tracing, executed), 1L);
		} finally {
			span.finish();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS)
				.untilAsserted(() -> {
					BDDAssertions.then(executed.get()).isTrue();
				});
	}

	@Test
	public void should_submit_trace_callable() throws Exception {
		Span span = this.tracer.nextSpan().name("foo");
		Span spanFromListenable;

		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			spanFromListenable = this.traceAsyncListenableTaskExecutor
					.submit(aCallable(this.tracing)).get();
		} finally {
			span.finish();
		}

		BDDAssertions.then(spanFromListenable).isNotNull();
	}

	@Test
	public void should_submit_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = this.tracer.nextSpan().name("foo");

		try(Tracer.SpanInScope ws = this.tracer.withSpanInScope(span.start())) {
			this.traceAsyncListenableTaskExecutor.submit(aRunnable(this.tracing, executed)).get();
		} finally {
			span.finish();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS)
				.untilAsserted(() -> {
					BDDAssertions.then(executed.get()).isTrue();
				});
	}

	Runnable aRunnable(Tracing tracing, AtomicBoolean executed) {
		return () -> {
			BDDAssertions.then(tracing.tracer().currentSpan()).isNotNull();
			executed.set(true);
		};
	}

	Callable<Span> aCallable(Tracing tracing) {
		return () -> tracing.tracer().currentSpan();
	}
}