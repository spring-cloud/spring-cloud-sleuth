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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceHeaders;

import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Builder;

/**
 * Span injector that injects tracing info to {@link Builder}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class RequestBuilderContextInjector implements SpanInjector<Builder> {

	private final TraceHeaders traceHeaders;

	RequestBuilderContextInjector(TraceHeaders traceHeaders) {
		this.traceHeaders = traceHeaders;
	}

	@Override
	public void inject(Span span, Builder carrier) {
		if (span == null) {
			setHeader(carrier, this.traceHeaders.getSampled(),
					TraceHeaders.SPAN_NOT_SAMPLED);
			return;
		}
		setHeader(carrier, this.traceHeaders.getTraceId(), Span.idToHex(span.getTraceId()));
		setHeader(carrier, this.traceHeaders.getSpanId(), Span.idToHex(span.getSpanId()));
		setHeader(carrier, this.traceHeaders.getSleuth().getSpanName(), span.getName());
		if (getParentId(span) != null) {
			setHeader(carrier, this.traceHeaders.getParentSpanId(),
					Span.idToHex(getParentId(span)));
		}
		setHeader(carrier, this.traceHeaders.getProcessId(),
				span.getProcessId());
		setHeader(carrier, this.traceHeaders.getSampled(), span.isExportable() ? TraceHeaders.SPAN_SAMPLED :
				TraceHeaders.SPAN_NOT_SAMPLED);
	}

	private Long getParentId(Span span) {
		return !span.getParents().isEmpty()
				? span.getParents().get(0) : null;
	}

	public void setHeader(HttpRequest.Builder builder, String name, String value) {
		if (value != null) {
			builder.header(name, value);
		}
	}
}
