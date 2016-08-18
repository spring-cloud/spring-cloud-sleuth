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

package org.springframework.cloud.sleuth.stream;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Poller;

/**
 * A message source for spans. Also handles RPC flavoured annotations.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@MessageEndpoint
public class StreamSpanReporter implements SpanReporter {

	/**
	 * Bean name for the
	 * {@link org.springframework.integration.scheduling.PollerMetadata
	 * PollerMetadata}
	 */
	public static final String POLLER = "streamSpanReporterPoller";

	private BlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
	private final HostLocator endpointLocator;
	private final SpanMetricReporter spanMetricReporter;

	public StreamSpanReporter(HostLocator endpointLocator, SpanMetricReporter spanMetricReporter) {
		this.endpointLocator = endpointLocator;
		this.spanMetricReporter = spanMetricReporter;
	}

	public void setQueue(BlockingQueue<Span> queue) {
		this.queue = queue;
	}

	@InboundChannelAdapter(value = SleuthSource.OUTPUT, poller = @Poller(POLLER))
	public Spans poll() {
		List<Span> result = new LinkedList<>();
		this.queue.drainTo(result);
		for (Iterator<Span> iterator = result.iterator(); iterator.hasNext();) {
			Span span = iterator.next();
			if (span.getName() != null && span.getName().equals("message/" + SleuthSource.OUTPUT)) {
				iterator.remove();
			}
		}
		if (result.isEmpty()) {
			return null;
		}
		this.spanMetricReporter.incrementAcceptedSpans(result.size());
		return new Spans(this.endpointLocator.locate(result.get(0)), result);
	}

	@Override
	public void report(Span span) {
		if (span.isExportable()) {
			this.queue.add(span);
		}
	}
}
