package org.springframework.cloud.sleuth.trace;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.springframework.cloud.sleuth.trace.receiver.ArrayListSpanReceiver;
import org.springframework.cloud.sleuth.trace.sampler.AlwaysSampler;

/**
 * @author Spencer Gibb
 */
public class DefaultTraceTests {

	public static final String CREATE_SIMPLE_TRACE = "createSimpleTrace";
	public static final String IMPORTANT_WORK_1 = "important work 1";
	public static final String IMPORTANT_WORK_2 = "important work 2";

	@Test
	public void tracingWorks() {
		DefaultTrace trace = new DefaultTrace();
		ArrayListSpanReceiver spanReceiver = new ArrayListSpanReceiver();
		trace.setSpanReceivers(Collections.<SpanReceiver> singletonList(spanReceiver));

		TraceScope scope = trace.startSpan(CREATE_SIMPLE_TRACE, new AlwaysSampler());
		try {
			importantWork1(trace);
		}
		finally {
			scope.close();
		}

		List<Span> spans = spanReceiver.getSpans();
		assertThat("spans was null", spans, is(notNullValue()));
		assertThat("spans was empty", spans.isEmpty(), not(true));
		assertThat("spans was wrong size", spans.size(), is(3));

		Span root = assertSpan(spans, null, CREATE_SIMPLE_TRACE);
		Span child = assertSpan(spans, root.getSpanId(), IMPORTANT_WORK_1);
		Span grandChild = assertSpan(spans, child.getSpanId(), IMPORTANT_WORK_2);

		List<Span> gen4 = findSpans(spans, grandChild.getSpanId());
		assertThat("gen4 was non-empty", gen4.isEmpty(), is(true));
	}

	private Span assertSpan(List<Span> spans, String parentId, String desc) {
		List<Span> found = findSpans(spans, parentId);
		assertThat("more than one span with parentId " + parentId, found.size(), is(1));
		Span span = found.get(0);
		assertThat("description is wrong for span with parentId " + parentId,
				span.getDescription(), is(desc));
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
