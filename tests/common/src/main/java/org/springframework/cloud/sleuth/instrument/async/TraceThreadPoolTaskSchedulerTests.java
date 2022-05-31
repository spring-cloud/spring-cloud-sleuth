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

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public abstract class TraceThreadPoolTaskSchedulerTests implements TestTracingAwareSupplier {

	ThreadPoolTaskScheduler delegate = new ThreadPoolTaskScheduler();

	BeanFactory beanFactory = beanFactory();

	LazyTraceThreadPoolTaskScheduler traceThreadPoolTaskExecutor = new LazyTraceThreadPoolTaskScheduler(
			this.beanFactory, this.delegate, "foo");

	@BeforeEach
	void setup() {
		this.delegate.initialize();
		SleuthContextListenerAccessor.set(this.beanFactory, true);
	}

	@AfterEach
	void clear() {
		this.delegate.shutdown();
		this.traceThreadPoolTaskExecutor.shutdown();
	}

	private BeanFactory beanFactory() {
		BeanFactory beanFactory = BDDMockito.mock(BeanFactory.class);
		BDDMockito.given(beanFactory.getBean(Tracer.class)).willReturn(tracerTest().tracing().tracer());
		BDDMockito.given(beanFactory.getBean(SpanNamer.class)).willReturn(new DefaultSpanNamer());
		return beanFactory;
	}

	@Test
	public void should_initialize_wrapped_executor() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			ExecutorService executorService = this.traceThreadPoolTaskExecutor.initializeExecutor(new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					return new Thread(r);
				}
			}, new RejectedExecutionHandler() {
				@Override
				public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

				}
			});
			executorService.submit(aRunnable(executed, span)).get(1, TimeUnit.SECONDS);
			executorService.shutdown();
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_create_wrapped_executor() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.createExecutor(1, new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					return new Thread(r);
				}
			}, new RejectedExecutionHandler() {
				@Override
				public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

				}
			}).submit(aRunnable(executed, span)).get(1, TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_get_scheduled_wrapped_executor() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.getScheduledExecutor().submit(aRunnable(executed, span)).get(1,
					TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_get_scheduled_thread_pool_wrapped_executor() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.getScheduledThreadPoolExecutor().submit(aRunnable(executed, span)).get(1,
					TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_create_thread_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.createThread(aRunnable(executed, span)).start();
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_schedule_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.schedule(aRunnable(executed, span), Instant.now()).get(1,
					TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		then(executed.get()).isTrue();
	}

	@Test
	public void should_schedule_trace_runnable_with_start_time() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.schedule(aRunnable(executed, span), Date.from(Instant.now())).get(1,
					TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		then(executed.get()).isTrue();
	}

	@Test
	public void should_schedule_trace_runnable_with_trigger() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.schedule(aRunnable(executed, span), new Trigger() {
				@Override
				public java.util.Date nextExecutionTime(TriggerContext triggerContext) {
					return java.util.Date.from(Instant.now());
				}
			}).get(1, TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		then(executed.get()).isTrue();
	}

	@Test
	public void should_schedule_at_fixed_rate_trace_runnable_with_date() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleAtFixedRate(aRunnable(executed, span), Date.from(Instant.now()),
					1L);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_schedule_at_fixed_rate_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleAtFixedRate(aRunnable(executed, span), 1L);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_schedule_at_fixed_rate_trace_runnable_with_instant() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleAtFixedRate(aRunnable(executed, span), Instant.now(),
					Duration.ofMillis(10));
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_schedule_at_fixed_rate_trace_runnable_with_duration() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleAtFixedRate(aRunnable(executed, span), Duration.ofMillis(10));
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_schedule_with_fixed_delay_trace_runnable_with_date() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleWithFixedDelay(aRunnable(executed, span), Date.from(Instant.now()),
					1L);
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
			this.traceThreadPoolTaskExecutor.scheduleWithFixedDelay(aRunnable(executed, span), 1L);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_schedule_with_fixed_delay_trace_runnable_with_instant() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleWithFixedDelay(aRunnable(executed, span), Instant.now(),
					Duration.ofMillis(10));
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_schedule_with_fixed_delay_trace_runnable_with_duration() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.scheduleWithFixedDelay(aRunnable(executed, span), Duration.ofMillis(10));
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_submit_listenable_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.submitListenable(aRunnable(executed, span)).get(1, TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_submit_listenable_trace_callable() throws Exception {
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");
		Span spanFromListenable;

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			spanFromListenable = this.traceThreadPoolTaskExecutor.submitListenable(aCallable(span)).get(1,
					TimeUnit.SECONDS);
		}
		finally {
			span.end();
		}

		then(spanFromListenable).isNotNull();
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
	public void should_execute_with_timeout_a_trace_runnable() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.execute(aRunnable(executed, span), 1L);
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

	@Test
	public void should_submit_trace_runnable_via_new_thread() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.newThread(aRunnable(executed, span)).start();
		}
		finally {
			span.end();
		}

		Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			then(executed.get()).isTrue();
		});
	}

	@Test
	public void should_submit_trace_runnable_via_create_thread() throws Exception {
		AtomicBoolean executed = new AtomicBoolean();
		Span span = tracerTest().tracing().tracer().nextSpan().name("foo");

		try (Tracer.SpanInScope ws = tracerTest().tracing().tracer().withSpan(span.start())) {
			this.traceThreadPoolTaskExecutor.createThread(aRunnable(executed, span)).start();
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
