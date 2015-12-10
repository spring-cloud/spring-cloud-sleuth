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

package org.springframework.cloud.sleuth;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.trace.DefaultTraceManager;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.JdkIdGenerator;

/**
 * @author Spencer Gibb
 */
public class DefaultTraceManagerTests {

	public static final String CREATE_SIMPLE_TRACE = "createSimpleTrace";
	public static final String IMPORTANT_WORK_1 = "important work 1";
	public static final String IMPORTANT_WORK_2 = "important work 2";
	public static final int NUM_SPANS = 3;

	@Test
	public void tracingWorks() {
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

		DefaultTraceManager traceManager = new DefaultTraceManager(new IsTracingSampler(),
				new JdkIdGenerator(), publisher);

		Trace trace = traceManager.startSpan(CREATE_SIMPLE_TRACE, new AlwaysSampler(), null);
		try {
			importantWork1(traceManager);
		}
		finally {
			traceManager.close(trace);
		}

		verify(publisher, times(NUM_SPANS)).publishEvent(isA(SpanAcquiredEvent.class));
		verify(publisher, times(NUM_SPANS)).publishEvent(isA(SpanReleasedEvent.class));

		ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor
				.forClass(ApplicationEvent.class);
		verify(publisher, atLeast(NUM_SPANS)).publishEvent(captor.capture());

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

	private Span assertSpan(List<Span> spans, String parentId, String name) {
		List<Span> found = findSpans(spans, parentId);
		assertThat("more than one span with parentId " + parentId, found.size(), is(1));
		Span span = found.get(0);
		assertThat("name is wrong for span with parentId " + parentId,
				span.getName(), is(name));
		return span;
	}

	private List<Span> findSpans(List<Span> spans, String parentId) {
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

	private void importantWork1(TraceManager traceManager) {
		Trace cur = traceManager.startSpan(IMPORTANT_WORK_1);
		try {
			Thread.sleep((long) (50 * Math.random()));
			importantWork2(traceManager);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			traceManager.close(cur);
		}
	}

	private void importantWork2(TraceManager traceManager) {
		Trace cur = traceManager.startSpan(IMPORTANT_WORK_2);
		try {
			Thread.sleep((long) (50 * Math.random()));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			traceManager.close(cur);
		}
	}

}
