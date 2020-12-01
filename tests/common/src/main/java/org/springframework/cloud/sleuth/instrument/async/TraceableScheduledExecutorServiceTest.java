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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.cloud.sleuth.internal.SleuthContextListenerAccessor;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith(MockitoExtension.class)
public abstract class TraceableScheduledExecutorServiceTest implements TestTracingAwareSupplier {

	@Mock(lenient = true)
	BeanFactory beanFactory;

	@Mock
	ScheduledExecutorService scheduledExecutorService;

	@InjectMocks
	TraceableScheduledExecutorService traceableScheduledExecutorService;

	@BeforeEach
	public void setup() {
		beanFactory();
	}

	@Test
	public void should_schedule_a_trace_runnable() throws Exception {
		this.traceableScheduledExecutorService.schedule(aRunnable(), 1L, TimeUnit.DAYS);

		BDDMockito.then(this.scheduledExecutorService).should().schedule(
				BDDMockito.argThat(matcher(Runnable.class, instanceOf(TraceRunnable.class))),
				ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
	}

	@Test
	public void should_schedule_a_trace_callable() throws Exception {
		this.traceableScheduledExecutorService.schedule(aCallable(), 1L, TimeUnit.DAYS);

		BDDMockito.then(this.scheduledExecutorService).should().schedule(
				BDDMockito.argThat(matcher(Callable.class, instanceOf(TraceCallable.class))),
				ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
	}

	@Test
	public void should_schedule_at_fixed_rate_a_trace_runnable() throws Exception {
		this.traceableScheduledExecutorService.scheduleAtFixedRate(aRunnable(), 1L, 1L, TimeUnit.DAYS);

		BDDMockito.then(this.scheduledExecutorService).should().scheduleAtFixedRate(
				BDDMockito.argThat(matcher(Runnable.class, instanceOf(TraceRunnable.class))),
				ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
	}

	@Test
	public void should_schedule_with_fixed_delay_a_trace_runnable() throws Exception {
		this.traceableScheduledExecutorService.scheduleWithFixedDelay(aRunnable(), 1L, 1L, TimeUnit.DAYS);

		BDDMockito.then(this.scheduledExecutorService).should().scheduleWithFixedDelay(
				BDDMockito.argThat(matcher(Runnable.class, instanceOf(TraceRunnable.class))),
				ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
	}

	@Test
	public void should_not_schedule_a_trace_runnable_when_context_not_ready() throws Exception {
		SleuthContextListenerAccessor.set(this.beanFactory, false);
		this.traceableScheduledExecutorService.schedule(aRunnable(), 1L, TimeUnit.DAYS);

		BDDMockito.then(this.scheduledExecutorService).should(Mockito.never()).schedule(
				BDDMockito.argThat(matcher(Runnable.class, instanceOf(TraceRunnable.class))),
				ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
	}

	@Test
	public void should_not_schedule_a_trace_callable_when_context_not_ready() throws Exception {
		SleuthContextListenerAccessor.set(this.beanFactory, false);
		this.traceableScheduledExecutorService.schedule(aCallable(), 1L, TimeUnit.DAYS);

		BDDMockito.then(this.scheduledExecutorService).should(Mockito.never()).schedule(
				BDDMockito.argThat(matcher(Callable.class, instanceOf(TraceCallable.class))),
				ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
	}

	@Test
	public void should_not_schedule_at_fixed_rate_a_trace_runnable_when_context_not_ready() throws Exception {
		SleuthContextListenerAccessor.set(this.beanFactory, false);
		this.traceableScheduledExecutorService.scheduleAtFixedRate(aRunnable(), 1L, 1L, TimeUnit.DAYS);

		BDDMockito.then(this.scheduledExecutorService).should(Mockito.never()).scheduleAtFixedRate(
				BDDMockito.argThat(matcher(Runnable.class, instanceOf(TraceRunnable.class))),
				ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
	}

	@Test
	public void should_not_schedule_with_fixed_delay_a_trace_runnable_when_context_not_ready() throws Exception {
		SleuthContextListenerAccessor.set(this.beanFactory, false);
		this.traceableScheduledExecutorService.scheduleWithFixedDelay(aRunnable(), 1L, 1L, TimeUnit.DAYS);

		BDDMockito.then(this.scheduledExecutorService).should(Mockito.never()).scheduleWithFixedDelay(
				BDDMockito.argThat(matcher(Runnable.class, instanceOf(TraceRunnable.class))),
				ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));
	}

	Predicate<Object> instanceOf(Class clazz) {
		return (argument) -> argument.getClass().isAssignableFrom(clazz);
	}

	<T> ArgumentMatcher<T> matcher(Class<T> clazz, Predicate predicate) {
		return predicate::test;
	}

	Runnable aRunnable() {
		return () -> {
		};
	}

	Callable<?> aCallable() {
		return () -> null;
	}

	BeanFactory beanFactory() {
		BDDMockito.given(this.beanFactory.getBean(Tracer.class)).willReturn(tracerTest().tracing().tracer());
		BDDMockito.given(this.beanFactory.getBean(SpanNamer.class)).willReturn(new DefaultSpanNamer());
		SleuthContextListenerAccessor.set(this.beanFactory, true);
		return this.beanFactory;
	}

}
