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

package org.springframework.cloud.sleuth.instrument.web;

import javax.servlet.http.HttpServletRequest;
import java.lang.invoke.MethodHandles;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Span.SpanBuilder;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * Creates a {@link SpanBuilder} from {@link HttpServletRequest}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class HttpServletRequestExtractor implements SpanExtractor<HttpServletRequest> {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());
	private static final String HTTP_COMPONENT = "http";

	private final Pattern skipPattern;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	public HttpServletRequestExtractor(Pattern skipPattern) {
		this.skipPattern = skipPattern;
	}

	@Override
	public Span joinTrace(HttpServletRequest carrier) {
		if (carrier.getHeader(Span.TRACE_ID_NAME) == null) {
			// can't build a Span without trace id
			return null;
		}
		String uri = this.urlPathHelper.getPathWithinApplication(carrier);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| Span.SPAN_NOT_SAMPLED.equals(carrier.getHeader(Span.SAMPLED_NAME));
		long traceId = Span
				.hexToId(carrier.getHeader(Span.TRACE_ID_NAME));
		long spanId = spanId(carrier, traceId);
		return buildParentSpan(carrier, uri, skip, traceId, spanId);
	}

	private long spanId(HttpServletRequest carrier, long traceId) {
		String spanId = carrier.getHeader(Span.SPAN_ID_NAME);
		if (spanId == null) {
			log.debug("Request is missing a span id but it has a trace id. We'll assume that this is "
					+ "a root span with span id equal to trace id");
			return traceId;
		} else {
			return Span.hexToId(spanId);
		}
	}

	private Span buildParentSpan(HttpServletRequest carrier, String uri, boolean skip,
			long traceId, long spanId) {
		SpanBuilder span = Span.builder().traceId(traceId).spanId(spanId);
		String processId = carrier.getHeader(Span.PROCESS_ID_NAME);
		String parentName = carrier.getHeader(Span.SPAN_NAME_NAME);
		if (StringUtils.hasText(parentName)) {
			span.name(parentName);
		}
		else {
			span.name(HTTP_COMPONENT + ":" + "/parent" + uri);
		}
		if (StringUtils.hasText(processId)) {
			span.processId(processId);
		}
		if (carrier.getHeader(Span.PARENT_ID_NAME) != null) {
			span.parent(Span
					.hexToId(carrier.getHeader(Span.PARENT_ID_NAME)));
		}
		span.remote(true);
		if (skip) {
			span.exportable(false);
		}
		return span.build();
	}
}
