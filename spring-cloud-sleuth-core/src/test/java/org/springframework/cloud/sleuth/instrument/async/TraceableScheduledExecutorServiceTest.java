/*
 * Copyright 2013-2017 the original author or authors.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;

import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceableScheduledExecutorServiceTest {

	@Mock
	Tracer tracer;
	@Mock
	TraceKeys traceKeys;
	@Mock
	SpanNamer spanNamer;
	@Mock
	ScheduledExecutorService scheduledExecutorService;
	@InjectMocks
	TraceableScheduledExecutorService traceableScheduledExecutorService;

	@Test
	public void should_schedule_a_trace_runnable() throws Exception {
		this.traceableScheduledExecutorService.schedule(aRunnable(), 1L, TimeUnit.DAYS);

		then(this.scheduledExecutorService).should().schedule(
				BDDMockito
						.<Runnable>argThat(instanceOf(SpanContinuingTraceRunnable.class)),
				anyLong(), any(TimeUnit.class));
	}

	@Test
	public void should_schedule_a_trace_callable() throws Exception {
		this.traceableScheduledExecutorService.schedule(aCallable(), 1L, TimeUnit.DAYS);

		then(this.scheduledExecutorService).should().schedule(
				BDDMockito.<Callable<?>>argThat(
						instanceOf(SpanContinuingTraceCallable.class)),
				anyLong(), any(TimeUnit.class));
	}

	@Test
	public void should_schedule_at_fixed_rate_a_trace_runnable()
			throws Exception {
		this.traceableScheduledExecutorService.scheduleAtFixedRate(aRunnable(), 1L, 1L,
				TimeUnit.DAYS);

		then(this.scheduledExecutorService).should().scheduleAtFixedRate(
				BDDMockito
						.<Runnable>argThat(instanceOf(SpanContinuingTraceRunnable.class)),
				anyLong(), anyLong(), any(TimeUnit.class));
	}

	@Test
	public void should_schedule_with_fixed_delay_a_trace_runnable()
			throws Exception {
		this.traceableScheduledExecutorService.scheduleWithFixedDelay(aRunnable(), 1L, 1L,
				TimeUnit.DAYS);

		then(this.scheduledExecutorService).should().scheduleWithFixedDelay(
				BDDMockito
						.<Runnable>argThat(instanceOf(SpanContinuingTraceRunnable.class)),
				anyLong(), anyLong(), any(TimeUnit.class));
	}

	Runnable aRunnable() {
		return () -> {
		};
	}

	Callable<?> aCallable() {
		return () -> null;
	}
}