/*
 * Copyright 2013-2021 the original author or authors.
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
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.cloud.sleuth.internal.SleuthContextListenerAccessor;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public abstract class TraceScheduledThreadPoolExecutorTests implements TestTracingAwareSupplier {

	ScheduledThreadPoolExecutor delegate = new ScheduledThreadPoolExecutor(1);

	BeanFactory beanFactory = beanFactory();

	LazyTraceScheduledThreadPoolExecutor traceThreadPoolTaskExecutor = executor();

	protected LazyTraceScheduledThreadPoolExecutor executor() {
		return new LazyTraceScheduledThreadPoolExecutor(1, this.beanFactory, this.delegate, "foo");
	}

	@BeforeEach
	void setup() {
		SleuthContextListenerAccessor.set(this.beanFactory, true);
	}

	@AfterEach
	void clear() {
		this.delegate.shutdown();
	}

	private BeanFactory beanFactory() {
		BeanFactory beanFactory = BDDMockito.mock(BeanFactory.class);
		BDDMockito.given(beanFactory.getBean(Tracer.class)).willReturn(tracerTest().tracing().tracer());
		BDDMockito.given(beanFactory.getBean(SpanNamer.class)).willReturn(new DefaultSpanNamer());
		return beanFactory;
	}

	@Test
	public void should_schedule_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.schedule(aRunnable(executed, span), 1, TimeUnit.MILLISECONDS).get(1,
					TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		then(executed.get()).isTrue();
	}

	@Test
	public void should_decorate_task_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			Runnable runnable = aRunnable(executed, span);
			this.traceThreadPoolTaskExecutor.decorateTask(runnable, runnableScheduledFuture(runnable)).run();
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_decorate_task_trace_callable() throws Exception {
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");
		RunnableScheduledFuture<Span> fromCallable;

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			Callable<Span> callable = aCallable(span);
			fromCallable = this.traceThreadPoolTaskExecutor.decorateTask(callable, runnableScheduledFuture(callable));
			fromCallable.run();
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(fromCallable.get(10, TimeUnit.MILLISECONDS)).isNotNull();
		});
	}

	private RunnableScheduledFuture<Span> runnableScheduledFuture(Callable<Span> run) {
		return new RunnableScheduledFuture<Span>() {

			private Span result;

			@Override
			public boolean isPeriodic() {
				return false;
			}

			@Override
			public long getDelay(TimeUnit unit) {
				return 0;
			}

			@Override
			public int compareTo(Delayed o) {
				return 0;
			}

			@Override
			public void run() {
				try {
					this.result = run.call();
				}
				catch (Exception exception) {
				}
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return false;
			}

			@Override
			public boolean isCancelled() {
				return false;
			}

			@Override
			public boolean isDone() {
				return false;
			}

			@Override
			public Span get() throws InterruptedException, ExecutionException {
				return this.result;
			}

			@Override
			public Span get(long timeout, TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				return this.result;
			}
		};
	}

	private RunnableScheduledFuture<Object> runnableScheduledFuture(Runnable run) {
		return new RunnableScheduledFuture<Object>() {

			@Override
			public boolean isPeriodic() {
				return false;
			}

			@Override
			public long getDelay(TimeUnit unit) {
				return 0;
			}

			@Override
			public int compareTo(Delayed o) {
				return 0;
			}

			@Override
			public void run() {
				run.run();
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return false;
			}

			@Override
			public boolean isCancelled() {
				return false;
			}

			@Override
			public boolean isDone() {
				return false;
			}

			@Override
			public Object get() throws InterruptedException, ExecutionException {
				return null;
			}

			@Override
			public Object get(long timeout, TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				return null;
			}
		};
	}

	@Test
	public void should_schedule_trace_callable() throws Exception {
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");
		Span spanFromCallable;

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			spanFromCallable = this.traceThreadPoolTaskExecutor.schedule(aCallable(span), 1, TimeUnit.MILLISECONDS)
					.get(1, TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		then(spanFromCallable).isNotNull();
	}

	@Test
	public void should_schedule_at_fixed_rate_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleAtFixedRate(aRunnable(executed, span), 1L, 1L,
					TimeUnit.MILLISECONDS);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_schedule_with_fixed_delay_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleWithFixedDelay(aRunnable(executed, span), 1L, 1L,
					TimeUnit.MILLISECONDS);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_execute_a_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.execute(aRunnable(executed, span));
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_submit_trace_callable() throws Exception {
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");
		Span spanFromListenable;

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			spanFromListenable = this.traceThreadPoolTaskExecutor.submit(aCallable(span)).get(1, TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		then(spanFromListenable).isNotNull();
	}

	@Test
	public void should_submit_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.submit(aRunnable(executed, span)).get(1, TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	Runnable aRunnable(AtomicBoolean executed, Span currentSpan) {
		return () -> {
			Span span = tracerTest().tracing().tracer().currentSpan();
			then(span).isNotNull();
			then(span.context().traceId()).isEqualTo(currentSpan.context().traceId());
			executed.set(true);
		};
	}

	Callable<Span> aCallable(Span currentSpan) {
		return () -> {
			Span span = tracerTest().tracing().tracer().currentSpan();
			then(span.context().traceId()).isEqualTo(currentSpan.context().traceId());
			return span;
		};
	}

}
