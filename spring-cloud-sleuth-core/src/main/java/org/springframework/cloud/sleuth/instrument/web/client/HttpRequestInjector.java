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

	@Override
	public void inject(Span span, HttpRequest carrier) {
		setIdHeader(carrier, Span.TRACE_ID_NAME, span.getTraceId());
		setIdHeader(carrier, Span.SPAN_ID_NAME, span.getSpanId());
		if (!span.isExportable()) {
			setHeader(carrier, Span.NOT_SAMPLED_NAME, "true");
		}
		setHeader(carrier, Span.SPAN_NAME_NAME, span.getName());
		setIdHeader(carrier, Span.PARENT_ID_NAME, getParentId(span));
		setHeader(carrier, Span.PROCESS_ID_NAME, span.getProcessId());
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
