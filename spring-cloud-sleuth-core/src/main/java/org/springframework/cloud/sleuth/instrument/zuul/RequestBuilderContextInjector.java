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

	@Override
	public void inject(Span span, Builder carrier) {
		if (span == null) {
			setHeader(carrier, Span.NOT_SAMPLED_NAME, "true");
			return;
		}
		setHeader(carrier, Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		setHeader(carrier, Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
		setHeader(carrier, Span.SPAN_NAME_NAME, span.getName());
		if (getParentId(span) != null) {
			setHeader(carrier, Span.PARENT_ID_NAME,
					Span.idToHex(getParentId(span)));
		}
		setHeader(carrier, Span.PROCESS_ID_NAME,
				span.getProcessId());
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
