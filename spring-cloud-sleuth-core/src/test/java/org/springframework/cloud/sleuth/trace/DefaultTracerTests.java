/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.trace;

import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.sleuth.DefaultSpanNamer;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.log.SpanLogger;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.NeverSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Spencer Gibb
 */
public class DefaultTracerTests {

	public static final String CREATE_SIMPLE_TRACE_SPAN_NAME = "createSimpleTrace";
	public static final String CREATE_SIMPLE_TRACE = "http"  + ":" +
			CREATE_SIMPLE_TRACE_SPAN_NAME;
	public static final String IMPORTANT_WORK_1 = "http:important work 1";
	public static final String IMPORTANT_WORK_2 = "http:important work 2";
	public static final int NUM_SPANS = 3;
	private SpanNamer spanNamer = new DefaultSpanNamer();
	private SpanLogger spanLogger = Mockito.mock(SpanLogger.class);
	private SpanReporter spanReporter = Mockito.mock(SpanReporter.class);
	@Rule public OutputCapture capture = new OutputCapture();

	@Before
	public void setup() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@After
	public void clean() {
		TestSpanContextHolder.removeCurrentSpan();
	}

	@Test
	public void tracingWorks() {
		DefaultTracer tracer = new DefaultTracer(NeverSampler.INSTANCE, new Random(),
				new DefaultSpanNamer(), this.spanLogger, this.spanReporter, new TraceKeys());

		Span span = tracer.createSpan(CREATE_SIMPLE_TRACE, new AlwaysSampler());
		try {
			importantWork1(tracer);
		}
		finally {
			tracer.close(span);
		}

		verify(this.spanLogger, times(NUM_SPANS))
				.logStartedSpan(Mockito.any(Span.class), Mockito.any(Span.class));
		verify(this.spanReporter, times(NUM_SPANS))
				.report(Mockito.any(Span.class));

		ArgumentCaptor<Span> captor = ArgumentCaptor
				.forClass(Span.class);
		verify(this.spanReporter, atLeast(NUM_SPANS)).report(captor.capture());

		List<Span> spans = new ArrayList<>(captor.getAllValues());

		assertThat(spans).hasSize(NUM_SPANS);

		Span root = assertSpan(spans, null, CREATE_SIMPLE_TRACE);
		Span child = assertSpan(spans, root.getSpanId(), IMPORTANT_WORK_1);
		Span grandChild = assertSpan(spans, child.getSpanId(), IMPORTANT_WORK_2);

		List<Span> gen4 = findSpans(spans, grandChild.getSpanId());
		assertThat(gen4).isEmpty();
	}

	@Test
	public void nonExportable() {
		DefaultTracer tracer = new DefaultTracer(NeverSampler.INSTANCE, new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span span = tracer.createSpan(CREATE_SIMPLE_TRACE);
		assertThat(span.isExportable()).isFalse();
	}

	@Test
	public void exportable() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span span = tracer.createSpan(CREATE_SIMPLE_TRACE);
		assertThat(span.isExportable()).isTrue();
	}

	@Test
	public void exportableInheritedFromParent() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span span = tracer.createSpan(CREATE_SIMPLE_TRACE, NeverSampler.INSTANCE);
		assertThat(span.isExportable()).isFalse();
		Span child = tracer.createSpan(CREATE_SIMPLE_TRACE_SPAN_NAME + "/child", span);
		assertThat(child.isExportable()).isFalse();
	}

	@Test
	public void parentNotRemovedIfActiveOnJoin() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span parent = tracer.createSpan(CREATE_SIMPLE_TRACE);
		Span span = tracer.createSpan(IMPORTANT_WORK_1, parent);
		tracer.close(span);
		assertThat(tracer.getCurrentSpan()).isEqualTo(parent);
	}

	@Test
	public void parentRemovedIfNotActiveOnJoin() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span parent = Span.builder().name(CREATE_SIMPLE_TRACE).traceId(1L).spanId(1L)
				.build();
		Span span = tracer.createSpan(IMPORTANT_WORK_1, parent);
		tracer.close(span);
		assertThat(tracer.getCurrentSpan()).isNull();
	}

	@Test
	public void grandParentRestoredAfterAutoClose() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span grandParent = tracer.createSpan(CREATE_SIMPLE_TRACE);
		Span parent = Span.builder().name(IMPORTANT_WORK_1).traceId(1L).spanId(1L)
				.build();
		Span span = tracer.createSpan(IMPORTANT_WORK_2, parent);
		tracer.close(span);
		assertThat(tracer.getCurrentSpan()).isEqualTo(grandParent);
	}

	@Test
	public void samplingIsRanAgainstChildSpanWhenThereIsNoParent() {
		DefaultTracer tracer = new DefaultTracer(new NeverSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());

		Span span = tracer.createChild(null, "childName");

		assertThat(span.isExportable()).isFalse();
	}

	@Test
	public void shouldUpdateLogsInSpanWhenItGetsContinued() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span span = Span.builder().name(IMPORTANT_WORK_1).traceId(1L).spanId(1L)
				.build();
		Span continuedSpan = tracer.continueSpan(span);

		tracer.addTag("key", "value");
		continuedSpan.logEvent("event");

		then(span).hasATag("key", "value").hasLoggedAnEvent("event");
		then(continuedSpan).hasATag("key", "value").hasLoggedAnEvent("event");
		then(span).isEqualTo(continuedSpan);
		tracer.close(span);
	}

	@Test
	public void shouldPropagateBaggageFromParentToChild() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span parent = Span.builder().name(IMPORTANT_WORK_1).traceId(1L).spanId(1L)
				.baggage("foo", "bar").build();
		Span child = tracer.createSpan("child", parent);

		then(parent).hasBaggageItem("foo", "bar");
		then(child).hasBaggageItem("foo", "bar");
	}

	@Test
	public void shouldPropagateBaggageToContinuedSpan() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span parent = Span.builder().name(IMPORTANT_WORK_1).traceId(1L).spanId(1L)
				.baggage("foo", "bar").build();
		Span continuedSpan = tracer.continueSpan(parent);

		parent.setBaggageItem("baz1", "baz1");
		continuedSpan.setBaggageItem("baz2", "baz2");

		then(parent).hasBaggageItem("foo", "bar")
				.hasBaggageItem("baz1", "baz1")
				.hasBaggageItem("baz2", "baz2");
		then(continuedSpan).hasBaggageItem("foo", "bar")
				.hasBaggageItem("baz1", "baz1")
				.hasBaggageItem("baz2", "baz2");
		then(parent).isEqualTo(continuedSpan);
	}

	@Test
	public void shouldCreateNewSpanWithShortenedName() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span span = tracer.createSpan(bigName());

		then(span.getName().length()).isEqualTo(50);
		tracer.close(span);
	}


  /**
   * To support conversion to Amazon trace IDs, the first 32 bits of the trace ID are epoch seconds.
   */
  @Test
  public void creates128bitTraceIdWithEncodedTimestamp() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, true, new TraceKeys());
		Span span = tracer.createSpan(bigName());
		String traceId = span.traceIdString();
		long epochSeconds = Long.parseLong(traceId.substring(0, 8), 16);
		then(new Date(epochSeconds * 1000)).isToday();
		tracer.close(span);
	}

	@Test
	public void shouldCreateChildOfSpanWithShortenedName() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span span = Span.builder().name(bigName()).traceId(1L).spanId(1L).build();

		Span child = tracer.createChild(span, bigName());

		then(child.getName().length()).isEqualTo(50);
	}

	@Test
	public void shouldNotProduceAWarningMessageWhenThereIsNoSpanInContextAndWeDetachASpan() {
		DefaultTracer tracer = new DefaultTracer(new AlwaysSampler(), new Random(),
				this.spanNamer, this.spanLogger, this.spanReporter, new TraceKeys());
		Span span = Span.builder().name("foo").traceId(1L).spanId(1L).build();

		Span child = tracer.detach(span);

		then(child).isNull();
		then(this.capture.toString()).doesNotContain("Tried to detach trace span");
	}

	private String bigName() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("a");
		}
		return sb.toString();
	}

	private Span assertSpan(List<Span> spans, Long parentId, String name) {
		List<Span> found = findSpans(spans, parentId);
		assertThat(found).as("More than one span with parentId %s", parentId).hasSize(1);
		Span span = found.get(0);
		assertThat(span.getName()).as("Name should be %s", name).isEqualTo(name);
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
		Span cur = tracer.createSpan(IMPORTANT_WORK_1);
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
		Span cur = tracer.createSpan(IMPORTANT_WORK_2);
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
