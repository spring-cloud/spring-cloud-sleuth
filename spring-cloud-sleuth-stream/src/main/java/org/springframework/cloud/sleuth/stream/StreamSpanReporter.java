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

package org.springframework.cloud.sleuth.stream;

import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.commons.util.IdUtils;
import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAdjuster;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.core.env.Environment;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Poller;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A message source for spans. Also handles RPC flavoured annotations.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
@MessageEndpoint
public class StreamSpanReporter implements SpanReporter {

	private static final org.apache.commons.logging.Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private static final List<String> RPC_EVENTS = Arrays.asList(
			Span.CLIENT_RECV, Span.CLIENT_SEND, Span.SERVER_RECV, Span.SERVER_SEND
	);

	/**
	 * Bean name for the
	 * {@link org.springframework.integration.scheduling.PollerMetadata
	 * PollerMetadata}
	 */
	public static final String POLLER = "streamSpanReporterPoller";

	private BlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
	private final HostLocator endpointLocator;
	private final SpanMetricReporter spanMetricReporter;
	private final Environment environment;
	private final List<SpanAdjuster> spanAdjusters;

	public StreamSpanReporter(HostLocator endpointLocator,
			SpanMetricReporter spanMetricReporter, Environment environment, List<SpanAdjuster> spanAdjusters) {
		this.endpointLocator = endpointLocator;
		this.spanMetricReporter = spanMetricReporter;
		this.environment = environment;
		this.spanAdjusters = spanAdjusters;
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
		if (log.isDebugEnabled()) {
			log.debug("Processed [" + result.size() + "] spans");
		}
		this.spanMetricReporter.incrementAcceptedSpans(result.size());
		return new Spans(this.endpointLocator.locate(result.get(0)), result);
	}

	@Override
	public void report(Span span) {
		Span spanToReport = span;
		if (spanToReport.isExportable()) {
			try {
				if (this.environment != null) {
					processLogs(spanToReport);
				}
				for (SpanAdjuster adjuster : this.spanAdjusters) {
					spanToReport = adjuster.adjust(spanToReport);
				}
				this.queue.add(spanToReport);
			} catch (Exception e) {
				this.spanMetricReporter.incrementDroppedSpans(1);
				if (log.isDebugEnabled()) {
					log.debug("The span " + spanToReport + " will not be sent to Zipkin due to [" + e + "]");
				}
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug("The span " + spanToReport + " will not be sent to Zipkin due to sampling");
			}
		}
	}

	private void processLogs(Span span) {
		for (Log spanLog : span.logs()) {
			if (RPC_EVENTS.contains(spanLog.getEvent())) {
				span.tag(Span.INSTANCEID, IdUtils.getDefaultInstanceId(this.environment));
			}
		}
	}

}
