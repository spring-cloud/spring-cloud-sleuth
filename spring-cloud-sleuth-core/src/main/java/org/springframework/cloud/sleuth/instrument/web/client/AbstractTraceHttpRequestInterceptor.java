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

package org.springframework.cloud.sleuth.instrument.web.client;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.HttpRequest;
import org.springframework.util.StringUtils;

/**
 * Abstraction over classes that interact with Http requests. Allows you
 * to enrich the request headers with trace related information.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
abstract class AbstractTraceHttpRequestInterceptor
		implements ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;
	private final SpanAccessor accessor;

	protected AbstractTraceHttpRequestInterceptor(SpanAccessor accessor) {
		this.accessor = accessor;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	private void enrichWithTraceHeaders(HttpRequest request, Span span) {
		setIdHeader(request, Span.TRACE_ID_NAME, span.getTraceId());
		setIdHeader(request, Span.SPAN_ID_NAME, span.getSpanId());
		if (!span.isExportable()) {
			setHeader(request, Span.NOT_SAMPLED_NAME, "true");
		}
		setHeader(request, Span.SPAN_NAME_NAME, span.getName());
		setIdHeader(request, Span.PARENT_ID_NAME, getParentId(span));
		setHeader(request, Span.PROCESS_ID_NAME, span.getProcessId());
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	protected void doNotSampleThisSpan(HttpRequest request) {
		setHeader(request, Span.NOT_SAMPLED_NAME, "true");
	}

	private void setHeader(HttpRequest request, String name, String value) {
		if (StringUtils.hasText(value) && !request.getHeaders().containsKey(name) &&
				this.accessor.isTracing()) {
			request.getHeaders().add(name, value);
		}
	}

	private void setIdHeader(HttpRequest request, String name, Long value) {
		if (value != null) {
			setHeader(request, name, Span.idToHex(value));
		}
	}

	/**
	 * Enriches the request with proper headers and publishes
	 * the client sent event
	 */
	protected void publishStartEvent(HttpRequest request) {
		Span span = currentSpan();
		enrichWithTraceHeaders(request, span);
		publish(new ClientSentEvent(this, span));
	}

	/**
	 * Close the current span and emit the ClientReceivedEvent
	 */
	public void finish() {
		if (!isTracing()) {
			return;
		}
		publish(new ClientReceivedEvent(this, currentSpan()));
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	private Span currentSpan() {
		return this.accessor.getCurrentSpan();
	}

	protected boolean isTracing() {
		return this.accessor.isTracing();
	}

}
