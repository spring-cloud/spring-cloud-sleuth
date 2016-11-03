/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.stream;

import java.util.concurrent.ArrayBlockingQueue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;

import static org.mockito.BDDMockito.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(MockitoJUnitRunner.class)
public class StreamSpanReporterTests {

	@Mock HostLocator endpointLocator;
	@Mock SpanMetricReporter spanMetricReporter;
	@InjectMocks StreamSpanReporter reporter;

	@Test
	public void should_not_throw_an_exception_when_queue_size_is_exceeded() throws Exception {
		ArrayBlockingQueue<Span> queue = new ArrayBlockingQueue<>(1);
		queue.add(Span.builder().name("foo").build());
		this.reporter.setQueue(queue);

		this.reporter.report(Span.builder().name("bar").exportable(true).build());

		then(spanMetricReporter).should().incrementDroppedSpans(1);
	}

}