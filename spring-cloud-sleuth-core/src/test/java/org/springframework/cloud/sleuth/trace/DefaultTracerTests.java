/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.trace;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.metric.SpanDurationReporterService;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author Spencer Gibb
 */
public class DefaultTracerTests {

	public static final String CREATE_SIMPLE_TRACE = "createSimpleTrace";
	public static final String IMPORTANT_WORK_1 = "important work 1";
	public static final String IMPORTANT_WORK_2 = "important work 2";
	public static final int NUM_SPANS = 3;
	private ApplicationEventPublisher publisher;

	@Before
	public void setup() {
		TestSpanContextHolder.removeCurrentSpan();
		this.publisher = mock(ApplicationEventPublisher.class);
	}

	@After
	public void clean() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void tracingWorks() {

		DefaultTracer tracer = new DefaultTracer(NeverSampler.INSTANCE, new Random(),
				this.publisher);

		Span span = tracer.startTrace(CREATE_SIMPLE_TRACE, new AlwaysSampler());
		try {
			importantWork1(tracer);
		}
		finally {
			tracer.close(span);
		}

		verify(this.publisher, times(NUM_SPANS))
				.publishEvent(isA(SpanAcquiredEvent.class));
		verify(this.publisher, times(NUM_SPANS))
				.publishEvent(isA(SpanReleasedEvent.class));

		ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor
				.forClass(ApplicationEvent.class);
		verify(this.publisher, atLeast(NUM_SPANS)).publishEvent(captor.capture());

		List<Span> spans = new ArrayList<>();
		for (ApplicationEvent event : captor.getAllValues()) {
			if (event instanceof SpanReleasedEvent) {
				spans.add(((SpanReleasedEvent) event).getSpan());
			}
		}

		assertThat("spans was wrong size", spans.size(), is(NUM_SPANS));

		Span root = assertSpan(spans, null, CREATE_SIMPLE_TRACE);
		Span child = assertSpan(spans, root.getSpanId(), IMPORTANT_WORK_1);
		Span grandChild = assertSpan(spans, child.getSpanId(), IMPORTANT_WORK_2);

		List<Span> gen4 = findSpans(spans, grandChild.getSpanId());
		assertThat("gen4 was non-empty", gen4.isEmpty(), is(true));
	}

	@Test
	public void nonExportable() {
		DefaultTracer tracer = new DefaultTracer(NeverSampler.INSTANCE, new Random(),
				this.publisher);
		Span span = tracer.startTrace(CREATE_SIMPLE_TRACE);
		assertThat(span.isExportable(), is(false));
	}

	@Test
	public void exportable() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.publisher);
		Span span = tracer.startTrace(CREATE_SIMPLE_TRACE);
		assertThat(span.isExportable(), is(true));
	}

	@Test
	public void exportableInheritedFromParent() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.publisher);
		Span span = tracer.startTrace(CREATE_SIMPLE_TRACE, NeverSampler.INSTANCE);
		assertThat(span.isExportable(), is(false));
		Span child = tracer.joinTrace(CREATE_SIMPLE_TRACE + "/child", span);
		assertThat(child.isExportable(), is(false));
	}

	@Test
	public void parentNotRemovedIfActiveOnJoin() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.publisher);
		Span parent = tracer.startTrace(CREATE_SIMPLE_TRACE);
		Span span = tracer.joinTrace(IMPORTANT_WORK_1, parent);
		tracer.close(span);
		assertThat(tracer.getCurrentSpan(), is(equalTo(parent)));
	}

	@Test
	public void parentRemovedIfNotActiveOnJoin() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.publisher);
		Span parent = Span.builder().name(CREATE_SIMPLE_TRACE).traceId(1L).spanId(1L)
				.build();
		Span span = tracer.joinTrace(IMPORTANT_WORK_1, parent);
		tracer.close(span);
		assertThat(tracer.getCurrentSpan(), is(equalTo(null)));
	}

	@Test
	public void grandParentRestoredAfterAutoClose() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.publisher);
		Span grandParent = tracer.startTrace(CREATE_SIMPLE_TRACE);
		Span parent = Span.builder().name(IMPORTANT_WORK_1).traceId(1L).spanId(1L)
				.build();
		Span span = tracer.joinTrace(IMPORTANT_WORK_2, parent);
		tracer.close(span);
		assertThat(tracer.getCurrentSpan(), is(equalTo(grandParent)));
	}

	@Test
	public void metricsAreSentOnClose() {
		SpanDurationReporterService spanDurationReporterService = Mockito.mock(SpanDurationReporterService.class);
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.publisher, spanDurationReporterService);
		Span span = tracer.startTrace(CREATE_SIMPLE_TRACE);

		tracer.close(span);

		BDDMockito.then(spanDurationReporterService).should().submitDuration(anyString(), anyDouble());
	}

	private Span assertSpan(List<Span> spans, Long parentId, String name) {
		List<Span> found = findSpans(spans, parentId);
		assertThat("more than one span with parentId " + parentId, found.size(), is(1));
		Span span = found.get(0);
		assertThat("name is wrong for span with parentId " + parentId, span.getName(),
				is(name));
		return span;
	}

	private List<Span> findSpans(List<Span> spans, Long parentId) {
		List<Span> found = new ArrayList<>();
		for (Span span : spans) {
			if (parentId == null && span.getParents().isEmpty()) {
				found.add(span);
			}
			else if (span.getParents().contains(parentId)) {
				found.add(span);
			}
		}
		return found;
	}

	private void importantWork1(Tracer tracer) {
		Span cur = tracer.startTrace(IMPORTANT_WORK_1);
		try {
			Thread.sleep((long) (50 * Math.random()));
			importantWork2(tracer);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			tracer.close(cur);
		}
	}

	private void importantWork2(Tracer tracer) {
		Span cur = tracer.startTrace(IMPORTANT_WORK_2);
		try {
			Thread.sleep((long) (50 * Math.random()));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			tracer.close(cur);
		}
	}

}
