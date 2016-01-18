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
package org.springframework.cloud.sleuth.instrument.web.client;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceAccessor;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * Interceptor that verifies whether the trance and span id has been set on the request
 * and sets them if one or both of them are missing.
 *
 * @see org.springframework.web.client.RestTemplate
 * @see TraceAccessor
 *
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 */
public class TraceRestTemplateInterceptor implements ClientHttpRequestInterceptor,
ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	private TraceAccessor accessor;

	public TraceRestTemplateInterceptor(TraceAccessor accessor) {
		this.accessor = accessor;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body,
			ClientHttpRequestExecution execution) throws IOException {
		Span span = getCurrentSpan();
		if (span == null) {
			setHeader(request, Trace.NOT_SAMPLED_NAME, "");
			return execution.execute(request, body);
		}
		setHeader(request, Trace.TRACE_ID_NAME, span.getTraceId());
		setHeader(request, Trace.SPAN_ID_NAME, span.getSpanId());
		setHeader(request, Trace.SPAN_NAME_NAME, span.getName());
		setHeader(request, Trace.PARENT_ID_NAME, getParentId(span));
		setHeader(request, Trace.PROCESS_ID_NAME, span.getProcessId());
		publish(new ClientSentEvent(this, span));
		return new TraceHttpResponse(this, execution.execute(request, body));
	}

	public void close() {
		if (getCurrentSpan() == null) {
			return;
		}
		publish(new ClientReceivedEvent(this, getCurrentSpan()));
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span
				.getParents().get(0) : null;
	}

	public void setHeader(HttpRequest request, String name, String value) {
		if (StringUtils.hasText(value) && !request.getHeaders().containsKey(name) && this.accessor.isTracing()) {
			request.getHeaders().add(name, value);
		}
	}

	public void setHeader(HttpRequest request, String name, Long value) {
		if (value != null) {
			setHeader(request, name, Span.IdConverter.toHex(value));
		}
	}

	private Span getCurrentSpan() {
		return this.accessor.getCurrentSpan();
	}

}
