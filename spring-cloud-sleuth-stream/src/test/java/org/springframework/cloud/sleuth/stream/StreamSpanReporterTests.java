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

package org.springframework.cloud.sleuth.stream;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

/**
 * @author Marcin Grzejszczak
 */
public class StreamSpanReporterTests {

	HostLocator endpointLocator = Mockito.mock(HostLocator.class);
	SpanMetricReporter spanMetricReporter = Mockito.mock(SpanMetricReporter.class);
	MockEnvironment mockEnvironment = new MockEnvironment();
	StreamSpanReporter reporter;

	@Before
	public void setup() {
		this.reporter = new StreamSpanReporter(this.endpointLocator, this.spanMetricReporter,
				this.mockEnvironment, new ArrayList<>());
	}

	@Test
	public void should_not_throw_an_exception_when_queue_size_is_exceeded() throws Exception {
		ArrayBlockingQueue<Span> queue = new ArrayBlockingQueue<>(1);
		queue.add(Span.builder().name("foo").build());
		this.reporter.setQueue(queue);

		this.reporter.report(Span.builder().name("bar").exportable(true).build());

		then(this.spanMetricReporter).should().incrementDroppedSpans(1);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_append_client_serviceid_when_span_has_rpc_event() throws Exception {
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		this.mockEnvironment.setProperty("vcap.application.instance_id", "foo");
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.CLIENT_RECV);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::tags)
				.extracting(o -> ((Map<String, String>) o).get(Span.INSTANCEID))
				.containsExactly("foo");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_not_append_server_serviceid_when_span_has_rpc_event_and_there_is_no_environment() throws Exception {
		this.reporter = new StreamSpanReporter(this.endpointLocator, this.spanMetricReporter,
				null, new ArrayList<>());
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.CLIENT_SEND);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::tags)
				.filteredOn(o -> ((Map<String, String>) o).containsKey(Span.INSTANCEID))
				.isNullOrEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_adjust_span_before_reporting_it() throws Exception {
		this.reporter = new StreamSpanReporter(this.endpointLocator, this.spanMetricReporter, null,
				Collections.<SpanAdjuster>singletonList(span -> Span.builder().from(span).name("foo").build()));
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.CLIENT_SEND);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::getName)
				.filteredOn(o -> o.equals("foo"))
				.isNotEmpty();
	}

}