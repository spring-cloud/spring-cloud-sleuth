/*
 * Copyright 2013-2015 the original author or authors.
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
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.event.ServerReceivedEvent;
import org.springframework.cloud.sleuth.event.ServerSentEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Filter that takes the value of the {@link Trace#SPAN_ID_NAME} and
 * {@link Trace#TRACE_ID_NAME} header from either request or response and uses them to
 * create a new span.
 *
 * @see TraceManager
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak, 4financeIT
 * @author Spencer Gibb
 * @author Dave Syer
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceFilter extends OncePerRequestFilter
		implements ApplicationEventPublisherAware {

	protected static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".TRACE";

	public static final Pattern DEFAULT_SKIP_PATTERN = Pattern.compile(
			"/api-docs.*|/autoconfig|/configprops|/dump|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream");

	private final TraceManager traceManager;
	private final Pattern skipPattern;
	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	private ApplicationEventPublisher publisher;

	public TraceFilter(TraceManager traceManager) {
		this.traceManager = traceManager;
		this.skipPattern = DEFAULT_SKIP_PATTERN;
	}

	public TraceFilter(TraceManager traceManager, Pattern skipPattern) {
		this.traceManager = traceManager;
		this.skipPattern = skipPattern;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
					throws ServletException, IOException {

		String uri = this.urlPathHelper.getPathWithinApplication(request);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| getHeader(request, response, Trace.NOT_SAMPLED_NAME) != null;

		Trace trace = (Trace) request.getAttribute(TRACE_REQUEST_ATTR);
		if (trace != null) {
			this.traceManager.continueSpan(trace.getSpan());
		}
		else if (skip) {
			addToResponseIfNotPresent(response, Trace.NOT_SAMPLED_NAME, "");
		}

		String spanId = getHeader(request, response, Trace.SPAN_ID_NAME);
		String traceId = getHeader(request, response, Trace.TRACE_ID_NAME);
		String name = "http" + uri;
		if (hasText(traceId)) {

			MilliSpanBuilder span = MilliSpan.builder().traceId(traceId).spanId(spanId);
			if (skip) {
				span.exportable(false);
			}
			String parentId = getHeader(request, response, Trace.PARENT_ID_NAME);
			String processId = getHeader(request, response, Trace.PROCESS_ID_NAME);
			String parentName = getHeader(request, response, Trace.SPAN_NAME_NAME);
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

			Span parent = span.build();
			trace = this.traceManager.startSpan(name, parent);
			publish(new ServerReceivedEvent(this, parent, trace.getSpan()));
			request.setAttribute(TRACE_REQUEST_ATTR, trace);

		}
		else {
			trace = this.traceManager.startSpan(name);
			request.setAttribute(TRACE_REQUEST_ATTR, trace);
		}

		// Send new trace id back to the caller
		addToResponseIfNotPresent(response, Trace.TRACE_ID_NAME,
				trace.getSpan().getTraceId());
		addToResponseIfNotPresent(response, Trace.SPAN_ID_NAME,
				trace.getSpan().getSpanId());

		try {

			addRequestAnnotations(request);
			filterChain.doFilter(request, response);

		}
		finally {
			if (isAsyncStarted(request) || request.isAsyncStarted()) {
				// TODO: how to deal with response annotations and async?
				return;
			}
			if (trace != null) {
				addResponseHeaders(response, trace.getSpan());
				addResponseAnnotations(response);
				if (trace.getSavedTrace() != null) {
					publish(new ServerSentEvent(this, trace.getSavedTrace().getSpan(),
							trace.getSpan()));
				}
				// Double close to clean up the parent (remote span as well)
				this.traceManager.close(this.traceManager.close(trace));
			}
		}
	}

	private void addResponseHeaders(HttpServletResponse response, Span span) {
		if (span != null) {
			response.addHeader(Trace.SPAN_ID_NAME, span.getSpanId());
			response.addHeader(Trace.TRACE_ID_NAME, span.getTraceId());
		}
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	// TODO: move annotation keys to constants
	protected void addRequestAnnotations(HttpServletRequest request) {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		this.traceManager.addAnnotation("/http/request/uri",
				request.getRequestURL().toString());
		this.traceManager.addAnnotation("/http/request/endpoint", uri);
		this.traceManager.addAnnotation("/http/request/method", request.getMethod());

		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			Enumeration<String> values = request.getHeaders(name);
			while (values.hasMoreElements()) {
				String value = values.nextElement();
				String key = "/http/request/headers/" + name.toLowerCase();
				this.traceManager.addAnnotation(key, value);

			}
		}
	}

	private void addResponseAnnotations(HttpServletResponse response) {
		this.traceManager.addAnnotation("/http/response/status_code",
				String.valueOf(response.getStatus()));

		for (String name : response.getHeaderNames()) {
			for (String value : response.getHeaders(name)) {
				String key = "/http/response/headers/" + name.toLowerCase();
				this.traceManager.addAnnotation(key, value);
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
