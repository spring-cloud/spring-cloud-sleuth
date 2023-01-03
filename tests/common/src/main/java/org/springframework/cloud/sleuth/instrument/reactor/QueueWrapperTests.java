/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.assertj.core.presentation.StandardRepresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth.traceQueue;

/**
 * @author Oleh Dokuka
 */
public abstract class QueueWrapperTests {

	static {
		// AssertJ will recognise QueueSubscription implements queue and try to invoke
		// iterator. That's not allowed, and will cause an exception
		// Fuseable$QueueSubscription.NOT_SUPPORTED_MESSAGE.
		// This ensures AssertJ uses normal toString.
		StandardRepresentation.registerFormatterForType(ScopePassingSpanSubscriber.class, Objects::toString);
	}

	protected abstract CurrentTraceContext currentTraceContext();

	protected abstract TraceContext context();

	AnnotationConfigApplicationContext springContext = new AnnotationConfigApplicationContext();

	@BeforeEach
	public void setup() {
		Hooks.removeQueueWrappers();
		Hooks.resetOnLastOperator();
		Schedulers.resetOnScheduleHooks();
	}

	@AfterEach
	public void close() {
		springContext.close();
		Hooks.removeQueueWrappers();
		Hooks.resetOnLastOperator();
		Schedulers.resetOnScheduleHooks();
	}

	@Test
	void checkContextIsRestoredAndOnNullCleaned() {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.refresh();

		final Queue queue = traceQueue(this.springContext, Queues.get(128).get());

		TraceContext context;
		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context())) {
			context = currentTraceContext().context();
			queue.offer(1);
		}

		Assertions.assertThat(queue.poll()).isEqualTo(1);
		Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

		Assertions.assertThat(queue.poll()).isNull();
		Assertions.assertThat(currentTraceContext().context()).isNull();
	}

	@Test
	void checkContextIsNotCleanOnNullCleanedIfContextWasAvailableOnThread() {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.refresh();

		final Queue queue = traceQueue(this.springContext, Queues.get(128).get());

		TraceContext context;
		CurrentTraceContext.Scope ws = currentTraceContext().newScope(context());
		context = currentTraceContext().context();
		queue.offer(1);

		Assertions.assertThat(queue.poll()).isEqualTo(1);
		Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

		Assertions.assertThat(queue.poll()).isNull();
		Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

		ws.close();
		Assertions.assertThat(currentTraceContext().context()).isNull();
	}

	@Test
	void checkContextIsRestoredAndOnNullCleanedInCaseOfSubsequentPolls() {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.refresh();

		final Queue queue = traceQueue(this.springContext, Queues.get(128).get());

		TraceContext context;
		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context())) {
			context = currentTraceContext().context();
			queue.offer(1);
			queue.offer(2);
		}

		Assertions.assertThat(queue.poll()).isEqualTo(1);
		Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

		Assertions.assertThat(queue.poll()).isEqualTo(2);
		Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

		Assertions.assertThat(queue.poll()).isNull();
		Assertions.assertThat(currentTraceContext().context()).isNull();
	}

	@Test
	void checkContextIsRestoredAndOnNullCleanedInCaseOfSubsequentPollsByAnotherThread() throws InterruptedException {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.refresh();

		final Queue queue = traceQueue(this.springContext, Queues.get(128).get());

		TraceContext context;
		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context())) {
			context = currentTraceContext().context();
			queue.offer(1);
			queue.offer(2);
		}

		Assertions.assertThat(queue.poll()).isEqualTo(1);
		Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			Assertions.assertThat(queue.poll()).isEqualTo(2);
			Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

			Assertions.assertThat(queue.poll()).isNull();
			Assertions.assertThat(currentTraceContext().context()).isNull();
			latch.countDown();
		}).start();

		Assertions.assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

		Assertions.assertThat(queue.poll()).isNull();
		Assertions.assertThat(currentTraceContext().context()).isNull();
	}

	@Test
	void checkContextIsNotCleanOnNullCleanedIfContextWasAvailableOnThreadAnotherThreadCase()
			throws InterruptedException {
		springContext.registerBean(CurrentTraceContext.class, this::currentTraceContext);
		springContext.refresh();

		final Queue queue = traceQueue(this.springContext, Queues.get(128).get());

		TraceContext context;
		try (CurrentTraceContext.Scope ws = currentTraceContext().newScope(context())) {
			context = currentTraceContext().context();
			queue.offer(1);
			queue.offer(2);
		}

		Assertions.assertThat(queue.poll()).isEqualTo(1);
		Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			CurrentTraceContext.Scope ws = currentTraceContext().maybeScope(context);
			Assertions.assertThat(queue.poll()).isEqualTo(2);
			Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

			Assertions.assertThat(queue.poll()).isNull();
			Assertions.assertThat(currentTraceContext().context()).isNotNull().isEqualTo(context);

			ws.close();
			Assertions.assertThat(currentTraceContext().context()).isNull();
			latch.countDown();
		}).start();

		Assertions.assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

		Assertions.assertThat(queue.poll()).isNull();
		Assertions.assertThat(currentTraceContext().context()).isNull();
	}

}
