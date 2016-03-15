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

import java.util.Random;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Span.SpanBuilder;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.TraceHeaders;
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

	private static final String HTTP_COMPONENT = "http";

	private final Random random;
	private final Pattern skipPattern;
	private final TraceHeaders traceHeaders;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	public HttpServletRequestExtractor(Random random, Pattern skipPattern,
			TraceHeaders traceHeaders) {
		this.random = random;
		this.skipPattern = skipPattern;
		this.traceHeaders = traceHeaders;
	}

	@Override
	public Span joinTrace(HttpServletRequest carrier) {
		String uri = this.urlPathHelper.getPathWithinApplication(carrier);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| TraceHeaders.SPAN_NOT_SAMPLED.equals(
					carrier.getHeader(this.traceHeaders.getSampled()));
		long traceId = Span
				.hexToId(carrier.getHeader(this.traceHeaders.getTraceId()));
		long spanId = carrier.getHeader(this.traceHeaders.getSpanId()) != null
				? Span.hexToId(carrier.getHeader(this.traceHeaders.getSpanId()))
				: this.random.nextLong();

		SpanBuilder span = Span.builder().traceId(traceId).spanId(spanId);
		String processId = carrier.getHeader(this.traceHeaders.getProcessId());
		String parentName = carrier.getHeader(this.traceHeaders.getSleuth().getSpanName());
		if (StringUtils.hasText(parentName)) {
			span.name(parentName);
		}
		else {
			span.name(HTTP_COMPONENT + ":" + "/parent" + uri);
		}
		if (StringUtils.hasText(processId)) {
			span.processId(processId);
		}
		if (carrier.getHeader(this.traceHeaders.getParentSpanId()) != null) {
			span.parent(Span
					.hexToId(carrier.getHeader(this.traceHeaders.getParentSpanId())));
		}
		span.remote(true);
		if (skip) {
			span.exportable(false);
		}
		return span.build();
	}
}
