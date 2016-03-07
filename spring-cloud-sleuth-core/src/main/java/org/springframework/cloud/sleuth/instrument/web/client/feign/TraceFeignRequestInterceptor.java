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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.net.URI;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.event.ClientSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.StringUtils;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * A request interceptor that sets tracing information in the headers
 * and retrieves the span from the current {@link FeignRequestContext}.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignRequestInterceptor implements RequestInterceptor,
		ApplicationEventPublisherAware {

	private final Tracer tracer;
	private final FeignRequestContext feignRequestContext = FeignRequestContext.getInstance();

	private ApplicationEventPublisher publisher;

	TraceFeignRequestInterceptor(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void apply(RequestTemplate template) {
		String spanName = getSpanName(template);
		Span span = getSpan(spanName);
		if (span == null) {
			setHeader(template, Span.NOT_SAMPLED_NAME, "true");
			return;
		}
		template.header(Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		setHeader(template, Span.SPAN_NAME_NAME, span.getName());
		setHeader(template, Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		if (!span.isExportable()) {
			setHeader(template, Span.NOT_SAMPLED_NAME, "true");
		}
		Long parentId = getParentId(span);
		if (parentId != null) {
			setHeader(template, Span.PARENT_ID_NAME, Span.idToHex(parentId));
		}
		setHeader(template, Span.PROCESS_ID_NAME, span.getProcessId());
		publish(new ClientSentEvent(this, span));
	}

	protected String getSpanName(RequestTemplate template) {
		URI uri = URI.create(template.url());
		return uriScheme(uri) + ":" + uri.getPath();
	}

	/**
	 * Depending on the presence of a Span in context, either starts a new Span
	 * or continues an existing one.
	 */
	protected Span getSpan(String spanName) {
		if (!this.feignRequestContext.hasSpanInProcess()) {
			Span span = this.tracer.createSpan(spanName);
			this.feignRequestContext.putSpan(span, false);
			return span;
		} else {
			if (this.feignRequestContext.wasSpanRetried()) {
				return this.tracer.continueSpan(this.feignRequestContext.getCurrentSpan());
			}
		}
		return this.tracer.createSpan(spanName);
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	protected void setHeader(RequestTemplate request, String name, String value) {
		if (StringUtils.hasText(value) && !request.headers().containsKey(name)
				&& this.tracer.isTracing()) {
			request.header(name, value);
		}
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	@Override
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		this.publisher = applicationEventPublisher;
	}
}
