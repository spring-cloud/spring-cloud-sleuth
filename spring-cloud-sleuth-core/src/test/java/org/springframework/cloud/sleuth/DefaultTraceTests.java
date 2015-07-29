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
import org.springframework.cloud.sleuth.event.SpanStartedEvent;
import org.springframework.cloud.sleuth.event.SpanStoppedEvent;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.IsTracingSampler;
import org.springframework.cloud.sleuth.trace.DefaultTrace;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author Spencer Gibb
 */
public class DefaultTraceTests {

	public static final String CREATE_SIMPLE_TRACE = "createSimpleTrace";
	public static final String IMPORTANT_WORK_1 = "important work 1";
	public static final String IMPORTANT_WORK_2 = "important work 2";
	public static final int NUM_SPANS = 3;

	@Test
	public void tracingWorks() {
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

		DefaultTrace trace = new DefaultTrace(new IsTracingSampler(),
				new RandomUuidGenerator(), publisher);

		TraceScope scope = trace.startSpan(CREATE_SIMPLE_TRACE, new AlwaysSampler(), null);
		try {
			importantWork1(trace);
		}
		finally {
			scope.close();
		}

		verify(publisher, times(NUM_SPANS)).publishEvent(isA(SpanStartedEvent.class));
		verify(publisher, times(NUM_SPANS)).publishEvent(isA(SpanStoppedEvent.class));

		ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor
				.forClass(ApplicationEvent.class);
		verify(publisher, atLeast(NUM_SPANS)).publishEvent(captor.capture());

		List<Span> spans = new ArrayList<>();
		for (ApplicationEvent event : captor.getAllValues()) {
			if (event instanceof SpanStoppedEvent) {
				spans.add(((SpanStoppedEvent) event).getSpan());
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

	private void importantWork1(Trace trace) {
		TraceScope cur = trace.startSpan(IMPORTANT_WORK_1);
		try {
			Thread.sleep((long) (50 * Math.random()));
			importantWork2(trace);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			cur.close();
		}
	}

	private void importantWork2(Trace trace) {
		TraceScope cur = trace.startSpan(IMPORTANT_WORK_2);
		try {
			Thread.sleep((long) (50 * Math.random()));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			cur.close();
		}
	}
}
