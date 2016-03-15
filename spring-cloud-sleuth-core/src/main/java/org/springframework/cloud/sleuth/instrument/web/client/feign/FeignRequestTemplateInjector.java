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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.util.StringUtils;

import feign.RequestTemplate;

/**
 * Span injector that injects tracing info to {@link RequestTemplate}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class FeignRequestTemplateInjector implements SpanInjector<RequestTemplate> {

	private final TraceHeaders traceHeaders;

	FeignRequestTemplateInjector(TraceHeaders traceHeaders) {
		this.traceHeaders = traceHeaders;
	}

	@Override
	public void inject(Span span, RequestTemplate carrier) {
		if (span == null) {
			setHeader(carrier, this.traceHeaders.getSampled(), TraceHeaders.SPAN_NOT_SAMPLED);
			return;
		}
		carrier.header(this.traceHeaders.getTraceId(), Span.idToHex(span.getTraceId()));
		setHeader(carrier, this.traceHeaders.getSleuth().getSpanName(), span.getName());
		setHeader(carrier, this.traceHeaders.getSpanId(), Span.idToHex(span.getSpanId()));
		setHeader(carrier, this.traceHeaders.getSampled(), span.isExportable() ?
				TraceHeaders.SPAN_SAMPLED : TraceHeaders.SPAN_NOT_SAMPLED);
		Long parentId = getParentId(span);
		if (parentId != null) {
			setHeader(carrier, this.traceHeaders.getParentSpanId(), Span.idToHex(parentId));
		}
		setHeader(carrier, this.traceHeaders.getProcessId(), span.getProcessId());
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	protected void setHeader(RequestTemplate request, String name, String value) {
		if (StringUtils.hasText(value) && !request.headers().containsKey(name)) {
			request.header(name, value);
		}
	}
}
