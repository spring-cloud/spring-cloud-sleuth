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

import static org.springframework.cloud.sleuth.Trace.PARENT_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.PROCESS_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_ID_NAME;
import static org.springframework.cloud.sleuth.Trace.SPAN_NAME_NAME;
import static org.springframework.cloud.sleuth.Trace.TRACE_ID_NAME;
import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.MilliSpan.MilliSpanBuilder;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceScope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

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
 * @author Dave Syer
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceFilter extends OncePerRequestFilter {

	protected static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".TRACE";

	public static final Pattern DEFAULT_SKIP_PATTERN = Pattern
			.compile("/api-docs.*|/autoconfig|/configprops|/dump|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico");

	private final Trace trace;
	private final Pattern skipPattern;
	private UrlPathHelper urlPathHelper = new UrlPathHelper();

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

		String uri = this.urlPathHelper.getPathWithinApplication(request);
		boolean skip = this.skipPattern.matcher(uri).matches();

		TraceScope traceScope = (TraceScope) request.getAttribute(TRACE_REQUEST_ATTR);
		if (traceScope != null) {
			this.trace.continueSpan(traceScope.getSpan());
		}
		else if (!skip) {
			String spanId = getHeader(request, response, SPAN_ID_NAME);
			String traceId = getHeader(request, response, TRACE_ID_NAME);
			String name = "http" + uri;
			if (hasText(spanId) && hasText(traceId)) {

				MilliSpanBuilder span = MilliSpan.builder().traceId(traceId)
						.spanId(spanId);
				String parentId = getHeader(request, response, PARENT_ID_NAME);
				String processId = getHeader(request, response, PROCESS_ID_NAME);
				String parentName = getHeader(request, response, SPAN_NAME_NAME);
				if (parentName != null) {
					span.name(parentName);
				}
				if (processId != null) {
					span.processId(processId);
				}
				if (parentId != null) {
					span.parent(parentId);
				}
				span.remote(true);

				// TODO: trace description?
				traceScope = this.trace.startSpan(name, span.build());
				request.setAttribute(TRACE_REQUEST_ATTR, traceScope);
				// Send new span id back
				addToResponseIfNotPresent(response, TRACE_ID_NAME, traceScope.getSpan()
						.getTraceId());
				addToResponseIfNotPresent(response, SPAN_ID_NAME, traceScope.getSpan()
						.getSpanId());
			}
			else {
				traceScope = this.trace.startSpan(name);
			}
		}

		try {

			addRequestAnnotations(request);

			filterChain.doFilter(request, response);
		}
		finally {
			if (request.isAsyncSupported() && request.isAsyncStarted()) {
				//TODO: howto deal with response annotations and async?
				return;
			}
			if (traceScope != null) {
				addResponseAnnotations(response);

				traceScope.close();
			}
		}
	}

	protected void addRequestAnnotations(HttpServletRequest request) {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		this.trace.addKVAnnotation("/http/request/uri",
				request.getRequestURL().toString());
		this.trace.addKVAnnotation("/http/request/endpoint", uri);
		this.trace.addKVAnnotation("/http/request/method",
				request.getMethod());

		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			Enumeration<String> values = request.getHeaders(name);
			while (values.hasMoreElements()) {
				String value = values.nextElement();
				String key = "/http/request/headers/"+name.toLowerCase();
				this.trace.addKVAnnotation(key, value);

			}
		}
	}

	private void addResponseAnnotations(HttpServletResponse response) {
		this.trace.addKVAnnotation("/http/response/status_code",
				String.valueOf(response.getStatus()));

		for (String name : response.getHeaderNames()) {
			for (String value : response.getHeaders(name)) {
				String key = "/http/response/headers/"+name.toLowerCase();
				this.trace.addKVAnnotation(key, value);
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
