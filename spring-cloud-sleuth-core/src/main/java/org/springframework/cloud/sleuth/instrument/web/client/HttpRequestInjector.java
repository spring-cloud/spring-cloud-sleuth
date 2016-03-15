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
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.util.StringUtils;

/**
 * Span injector that injects tracing info to {@link HttpRequest}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class HttpRequestInjector implements SpanInjector<HttpRequest> {

	private final TraceHeaders traceHeaders;

	HttpRequestInjector(TraceHeaders traceHeaders) {
		this.traceHeaders = traceHeaders;
	}

	@Override
	public void inject(Span span, HttpRequest carrier) {
		setIdHeader(carrier, this.traceHeaders.getTraceId(), span.getTraceId());
		setIdHeader(carrier, this.traceHeaders.getSpanId(), span.getSpanId());
		setHeader(carrier, this.traceHeaders.getSampled(), span.isExportable() ?
				TraceHeaders.SPAN_SAMPLED : TraceHeaders.SPAN_NOT_SAMPLED);
		setHeader(carrier, this.traceHeaders.getSleuth().getSpanName(), span.getName());
		setIdHeader(carrier, this.traceHeaders.getParentSpanId(), getParentId(span));
		setHeader(carrier, this.traceHeaders.getProcessId(), span.getProcessId());
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
	}

	private void setHeader(HttpRequest request, String name, String value) {
		if (StringUtils.hasText(value) && !request.getHeaders().containsKey(name)) {
			request.getHeaders().add(name, value);
		}
	}

	private void setIdHeader(HttpRequest request, String name, Long value) {
		if (value != null) {
			setHeader(request, name, Span.idToHex(value));
		}
	}
}
