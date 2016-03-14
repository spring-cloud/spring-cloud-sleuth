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
import org.springframework.cloud.sleuth.SpanJoiner;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * Creates a {@link SpanBuilder} from {@link HttpServletDataHolder}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public class HttpServletRequestJoiner implements SpanJoiner {

	private static final String HTTP_COMPONENT = "http";

	private final Random random;
	private final Pattern skipPattern;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	public HttpServletRequestJoiner(Random random, Pattern skipPattern) {
		this.random = random;
		this.skipPattern = skipPattern;
	}

	@Override
	public <T> SpanBuilder join(T carrier) {
		if (!(carrier instanceof HttpServletRequest)) {
			return null;
		}
		HttpServletRequest request = (HttpServletRequest) carrier;
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| request.getHeader(Span.NOT_SAMPLED_NAME) != null;
		long traceId = Span
				.hexToId(request.getHeader(Span.TRACE_ID_NAME));
		long spanId = request.getHeader(Span.SPAN_ID_NAME) != null
				? Span.hexToId(request.getHeader(Span.SPAN_ID_NAME))
				: this.random.nextLong();

		SpanBuilder span = Span.builder().traceId(traceId).spanId(spanId);
		String processId = request.getHeader(Span.PROCESS_ID_NAME);
		String parentName = request.getHeader(Span.SPAN_NAME_NAME);
		if (StringUtils.hasText(parentName)) {
			span.name(parentName);
		}
		else {
			span.name(HTTP_COMPONENT + ":" + "/parent" + uri);
		}
		if (StringUtils.hasText(processId)) {
			span.processId(processId);
		}
		if (request.getHeader(Span.PARENT_ID_NAME) != null) {
			span.parent(Span
					.hexToId(request.getHeader(Span.PARENT_ID_NAME)));
		}
		span.remote(true);
		if (skip) {
			span.exportable(false);
		}
		return span;
	}
}
