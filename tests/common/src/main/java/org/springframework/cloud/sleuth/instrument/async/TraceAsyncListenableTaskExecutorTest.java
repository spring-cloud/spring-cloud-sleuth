/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * @author Marcin Grzejszczak
 */
public abstract class TraceAsyncListenableTaskExecutorTest implements TestTracingAwareSupplier {

	AsyncListenableTaskExecutor delegate = new SimpleAsyncTaskExecutor();

	TraceAsyncListenableTaskExecutor traceAsyncListenableTaskExecutor = new TraceAsyncListenableTaskExecutor(
			this.delegate, tracerTest().tracing().tracer(), new DefaultSpanNamer());

	@Test
	public void should_submit_listenable_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceAsyncListenableTaskExecutor.submitListenable(aRunnable(executed)).get();
		}
		finally {
			span.end();
		}

		BDDAssertions.then(executed.get()).isTrue();
	}

	@Test
	public void should_submit_listenable_trace_callable() throws Exception {
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");
		Span spanFromListenable;

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			spanFromListenable = this.traceAsyncListenableTaskExecutor.submitListenable(aCallable()).get();
		}
		finally {
			span.end();
		}

		BDDAssertions.then(spanFromListenable).isNotNull();
	}

	@Test
	public void should_execute_a_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceAsyncListenableTaskExecutor.execute(aRunnable(executed));
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_execute_with_timeout_a_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceAsyncListenableTaskExecutor.execute(aRunnable(executed), 1L);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_submit_trace_callable() throws Exception {
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");
		Span spanFromListenable;

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			spanFromListenable = this.traceAsyncListenableTaskExecutor.submit(aCallable()).get();
		}
		finally {
			span.end();
		}

		BDDAssertions.then(spanFromListenable).isNotNull();
	}

	@Test
	public void should_submit_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceAsyncListenableTaskExecutor.submit(aRunnable(executed)).get();
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			BDDAssertions.then(executed.get()).isTrue();
		});
	}

	Runnable aRunnable(AtomicBoolean executed) {
		return () -> {
			BDDAssertions.then(tracerTest().tracing().tracer().currentSpan()).isNotNull();
			executed.set(true);
		};
	}

	Callable<Span> aCallable() {
		return () -> tracerTest().tracing().tracer().currentSpan();
	}

}
