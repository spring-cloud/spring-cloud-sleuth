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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.util.StringUtils;

import com.netflix.zuul.context.RequestContext;

/**
 * Span injector that injects tracing info to {@link RequestContext}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class RequestContextInjector implements SpanInjector<RequestContext> {

	private final TraceHeaders traceHeaders;

	RequestContextInjector(TraceHeaders traceHeaders) {
		this.traceHeaders = traceHeaders;
	}

	@Override
	public void inject(Span span, RequestContext carrier) {
		Map<String, String> requestHeaders = carrier.getZuulRequestHeaders();
		if (span == null) {
			setHeader(requestHeaders, this.traceHeaders.getSampled(), TraceHeaders.SPAN_NOT_SAMPLED);
			return;
		}
		setHeader(requestHeaders, this.traceHeaders.getSpanId(), span.getSpanId());
		setHeader(requestHeaders, this.traceHeaders.getTraceId(), span.getTraceId());
		setHeader(requestHeaders, this.traceHeaders.getSleuth().getSpanName(), span.getName());
		setHeader(requestHeaders, this.traceHeaders.getSampled(), span.isExportable() ?
				TraceHeaders.SPAN_SAMPLED : TraceHeaders.SPAN_NOT_SAMPLED);
		setHeader(requestHeaders, this.traceHeaders.getParentSpanId(), getParentId(span));
		setHeader(requestHeaders, this.traceHeaders.getProcessId(), span.getProcessId());
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	public void setHeader(Map<String, String> request, String name, String value) {
		if (StringUtils.hasText(value) && !request.containsKey(name)) {
			request.put(name, value);
		}
	}

	public void setHeader(Map<String, String> request, String name, Long value) {
		if (value != null) {
			setHeader(request, name, Span.idToHex(value));
		}
	}
}
