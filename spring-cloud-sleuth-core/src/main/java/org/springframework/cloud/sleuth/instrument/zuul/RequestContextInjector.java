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

	@Override
	public void inject(Span span, RequestContext carrier) {
		Map<String, String> requestHeaders = carrier.getZuulRequestHeaders();
		if (span == null) {
			setHeader(requestHeaders, Span.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
			return;
		}
		setHeader(requestHeaders, Span.SPAN_ID_NAME, span.getSpanId());
		setHeader(requestHeaders, Span.TRACE_ID_NAME, span.getTraceId());
		setHeader(requestHeaders, Span.SPAN_NAME_NAME, span.getName());
		setHeader(requestHeaders, Span.SAMPLED_NAME, span.isExportable() ?
				Span.SPAN_SAMPLED : Span.SPAN_NOT_SAMPLED);
		setHeader(requestHeaders, Span.PARENT_ID_NAME, getParentId(span));
		setHeader(requestHeaders, Span.PROCESS_ID_NAME, span.getProcessId());
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
