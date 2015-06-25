/*
 * Copyright 2012-2015 the original author or authors.
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

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.util.Collections;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that takes the value of the {@link Trace#SPAN_ID_NAME} and
 * {@link Trace#TRACE_ID_NAME} header from either request or response and uses them to
 * create a new span.
 *
 * @see Trace
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 */
public class TraceFilter extends OncePerRequestFilter {

	public static final Pattern DEFAULT_SKIP_PATTERN = Pattern
			.compile("/api-docs.*|/autoconfig|/configprops|/dump|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html");

	private final Trace trace;
	private final Pattern skipPattern;

	public TraceFilter(Trace trace) {
		this.trace = trace;
		this.skipPattern = DEFAULT_SKIP_PATTERN;
	}

	public TraceFilter(Trace trace, Pattern skipPattern) {
		this.trace = trace;
		this.skipPattern = skipPattern;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String uri = hasText(request.getRequestURI()) ? request.getRequestURI() : "";
		boolean skip = skipPattern.matcher(uri).matches();

		TraceScope traceScope = null;
		if (!skip) {
			String spanId = getHeader(request, response, SPAN_ID_NAME);
			String traceId = getHeader(request, response, TRACE_ID_NAME);
			if (hasText(spanId) && hasText(traceId)) {

				Span span = MilliSpan.builder()
						.traceId(traceId)
						.parents(Collections.singletonList(spanId))
						// TODO: use parent() when lombok plugin supports it
						.build();
				// Send new span id back
				addToResponseIfNotPresent(response, SPAN_ID_NAME, span.getSpanId());

				traceScope = trace.startSpan("traceFilter", span);
			}
			else {
				traceScope = trace.startSpan("traceFilter");
			}
		}

		try {
			filterChain.doFilter(request, response);
		}
		finally {
			if (traceScope != null) {
				traceScope.close();
			}
		}
	}

	private String getHeader(HttpServletRequest request, HttpServletResponse response,
			String name) {
		String value = request.getHeader(name);
		return hasText(value) ? value : response.getHeader(name);
	}

	private void addToResponseIfNotPresent(HttpServletResponse response, String name,
			String value) {
		if (!hasText(response.getHeader(name))) {
			response.addHeader(name, value);
		}
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}
}
