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
import java.util.Iterator;
import java.util.List;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.cloud.sleuth.event.ServerReceivedEvent;
import org.springframework.cloud.sleuth.event.ServerSentEvent;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
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

	private List<Span> queue = new ArrayList<>();
	private HostLocator endpointLocator;

	public StreamSpanListener(HostLocator endpointLocator) {
		this.endpointLocator = endpointLocator;
	}

	public void setQueue(List<Span> queue) {
		this.queue = queue;
	}

	@EventListener
	@Order(0)
	public void start(SpanAcquiredEvent event) {
		event.getSpan().addTimelineAnnotation("acquire");
	}

	@EventListener
	@Order(0)
	public void serverReceived(ServerReceivedEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			event.getParent().addTimelineAnnotation(SERVER_RECV);
		}
	}

	@EventListener
	@Order(0)
	public void clientSend(ClientSentEvent event) {
		event.getSpan().addTimelineAnnotation(CLIENT_SEND);
	}

	@EventListener
	@Order(0)
	public void clientReceive(ClientReceivedEvent event) {
		event.getSpan().addTimelineAnnotation(CLIENT_RECV);
	}

	@EventListener
	@Order(0)
	public void serverSend(ServerSentEvent event) {
		if (event.getParent() != null && event.getParent().isRemote()) {
			event.getParent().addTimelineAnnotation(SERVER_SEND);
			this.queue.add(event.getParent());
		}
	}

	@EventListener
	@Order(0)
	public void release(SpanReleasedEvent event) {
		event.getSpan().addTimelineAnnotation("release");
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
			if (span.getName() != null && span.getName().equals("message/zipkin")) {
				iterator.remove();
			}
		}
		return result.isEmpty() ? null
				: new Spans(this.endpointLocator.locate(result.get(0)), result);
	}

}
