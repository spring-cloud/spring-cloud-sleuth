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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.event.ServerReceivedEvent;
import org.springframework.cloud.sleuth.event.ServerSentEvent;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.metric.SpanReporterService;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.MessageEndpoint;


/**
 * A message source for spans. Also handles RPC flavoured annotations.
 *
 * @author Dave Syer
 */
@MessageEndpoint
public class StreamSpanListener {

	public static final String CLIENT_RECV = "cr";
	public static final String CLIENT_SEND = "cs";
	public static final String SERVER_RECV = "sr";
	public static final String SERVER_SEND = "ss";

	private Collection<Span> queue = new ConcurrentLinkedQueue<>();
	private final HostLocator endpointLocator;
	private final SpanReporterService spanReporterService;

	public StreamSpanListener(HostLocator endpointLocator, SpanReporterService spanReporterService) {
		this.endpointLocator = endpointLocator;
		this.spanReporterService = spanReporterService;
	}

	public void setQueue(Collection<Span> queue) {
		this.queue = queue;
	}

	@EventListener
	@Order(0)
	public void start(SpanAcquiredEvent event) {
		event.getSpan().logEvent("acquire");
	}

	@EventListener
	@Order(0)
	public void serverReceived(ServerReceivedEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			event.getParent().logEvent(SERVER_RECV);
		}
	}

	@EventListener
	@Order(0)
	public void clientSend(ClientSentEvent event) {
		event.getSpan().logEvent(CLIENT_SEND);
	}

	@EventListener
	@Order(0)
	public void clientReceive(ClientReceivedEvent event) {
		event.getSpan().logEvent(CLIENT_RECV);
	}

	@EventListener
	@Order(0)
	public void serverSend(ServerSentEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			event.getParent().logEvent(SERVER_SEND);
			this.queue.add(event.getParent());
		}
	}

	@EventListener
	@Order(0)
	public void release(SpanReleasedEvent event) {
		event.getSpan().logEvent("release");
		if (event.getSpan().isExportable()) {
			this.queue.add(event.getSpan());
		}
	}

	@InboundChannelAdapter(value = SleuthSource.OUTPUT)
	public Spans poll() {
		List<Span> result = new ArrayList<>(this.queue);
		this.queue.clear();
		for (Iterator<Span> iterator = result.iterator(); iterator.hasNext();) {
			Span span = iterator.next();
			if (span.getName() != null && span.getName().equals("message/" + SleuthSource.OUTPUT)) {
				iterator.remove();
			}
		}
		if (result.isEmpty()) {
			return null;
		}
		this.spanReporterService.incrementAcceptedSpans(result.size());
		return new Spans(this.endpointLocator.locate(result.get(0)), result);
	}

}
