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
package org.springframework.cloud.sleuth.web;

import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that takes the value of the {@link CorrelationIdHolder#CORRELATION_ID_HEADER}
 * header from either request or response and sets it in the {@link CorrelationIdHolder}.
 * It also provides that value in {@link MDC} logging related class so that logger prints
 * the value of correlation id at each log.
 *
 * @see Trace
 * @see MDC
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 */
public class TraceFilter extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup()
			.lookupClass());
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
		String spanIdFromRequest = getSpanIdFrom(request);
		String spanId = (hasText(spanIdFromRequest)) ? spanIdFromRequest
				: getSpanIdFrom(response);

		TraceScope traceScope = null;
		if (spanId != null) {
			addCorrelationIdToResponseIfNotPresent(response, spanId);

			Span span = MilliSpan.builder().traceId("") // FIXME get traceId from request
					.parents(Collections.singletonList(spanId))
					// TODO: use parent() when lombok plugin supports it
					.build();
			traceScope = trace.startSpan("traceFilter", span);
		}
		else {
			traceScope = trace.startSpan("traceFilter");
		}

		try {
			filterChain.doFilter(request, response);
		}
		finally {
			traceScope.close();
		}
	}

	private String getSpanIdFrom(final HttpServletResponse response) {
		return response.getHeader(SPAN_ID_NAME);
	}

	private String getSpanIdFrom(final HttpServletRequest request) {
		return request.getHeader(SPAN_ID_NAME);
	}

	private void addCorrelationIdToResponseIfNotPresent(HttpServletResponse response,
			String spanId) {
		if (!hasText(response.getHeader(SPAN_ID_NAME))) {
			response.addHeader(SPAN_ID_NAME, spanId);
		}
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}
}
