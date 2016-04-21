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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import static org.springframework.util.StringUtils.hasText;

/**
 * Filter that takes the value of the {@link Span#SPAN_ID_NAME} and
 * {@link Span#TRACE_ID_NAME} header from either request or response and uses them to
 * create a new span.
 *
 * <p>
 * In order to keep the size of spans manageable, this only add tags defined in
 * {@link TraceKeys}. If you need to add additional tags, such as headers subtype this and
 * override {@link #addRequestTags} or {@link #addResponseTags}.
 *
 * @author Jakub Nabrdalik, 4financeIT
 * @author Tomasz Nurkiewicz, 4financeIT
 * @author Marcin Grzejszczak
 * @author Spencer Gibb
 * @author Dave Syer
 * @since 1.0.0
 *
 * @see Tracer
 * @see TraceKeys
 * @see TraceWebAutoConfiguration#traceFilter
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceFilter extends OncePerRequestFilter {

	private static final String HTTP_COMPONENT = "http";

	protected static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".TRACE";

	public static final String DEFAULT_SKIP_PATTERN =
			"/api-docs.*|/autoconfig|/configprops|/dump|/health|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream";

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final Pattern skipPattern;
	private final SpanReporter spanReporter;
	private final SpanExtractor<HttpServletRequest> spanExtractor;
	private final SpanInjector<HttpServletResponse> spanInjector;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	public TraceFilter(Tracer tracer, TraceKeys traceKeys, SpanReporter spanReporter,
			SpanExtractor<HttpServletRequest> spanExtractor, SpanInjector<HttpServletResponse> spanInjector) {
		this(tracer, traceKeys, Pattern.compile(DEFAULT_SKIP_PATTERN), spanReporter,
				spanExtractor, spanInjector);
	}

	public TraceFilter(Tracer tracer, TraceKeys traceKeys, Pattern skipPattern,
			SpanReporter spanReporter, SpanExtractor<HttpServletRequest> spanExtractor,
			SpanInjector<HttpServletResponse> spanInjector) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.skipPattern = skipPattern;
		this.spanReporter = spanReporter;
		this.spanExtractor = spanExtractor;
		this.spanInjector = spanInjector;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| Span.SPAN_NOT_SAMPLED.equals(ServletUtils.getHeader(request, response, Span.SAMPLED_NAME));
		Span spanFromRequest = (Span) request.getAttribute(TRACE_REQUEST_ATTR);
		if (spanFromRequest != null) {
			this.tracer.continueSpan(spanFromRequest);
		}
		addToResponseIfNotPresent(response, Span.SAMPLED_NAME, skip ? Span.SPAN_NOT_SAMPLED : Span.SPAN_SAMPLED);
		String name = HTTP_COMPONENT + ":" + uri;
		spanFromRequest = createSpan(request, skip, spanFromRequest, name);
		Throwable exception = null;
		try {
			addRequestTags(request);
			// Add headers before filter chain in case one of the filters flushes the
			// response...
			this.spanInjector.inject(spanFromRequest, response);
			filterChain.doFilter(request, response);
		}
		catch (Throwable e) {
			exception = e;
			throw e;
		}
		finally {
			if (isAsyncStarted(request) || request.isAsyncStarted()) {
				this.tracer.detach(spanFromRequest);
				// TODO: how to deal with response annotations and async?
				return;
			}
			addToResponseIfNotPresent(response, Span.SAMPLED_NAME, skip ? Span.SPAN_NOT_SAMPLED : Span.SPAN_SAMPLED);
			if (spanFromRequest != null) {
				addResponseTags(response, exception);
				if (spanFromRequest.hasSavedSpan()) {
					Span parent =  spanFromRequest.getSavedSpan();
					if (parent.isRemote()) {
						parent.logEvent(Span.SERVER_SEND);
						parent.stop();
						this.spanReporter.report(parent);
					}
				} else {
					spanFromRequest.logEvent(Span.SERVER_SEND);
				}
				this.tracer.close(spanFromRequest);
			}
		}
	}

	/**
	 * Creates a span and appends it as the current request's attribute
	 */
	private Span createSpan(HttpServletRequest request,
			boolean skip, Span spanFromRequest, String name) {
		if (spanFromRequest != null) {
			return spanFromRequest;
		}
		Span parent = this.spanExtractor
				.joinTrace(request);
		if (parent != null) {
			spanFromRequest = this.tracer.createSpan(name, parent);
			if (parent.isRemote()) {
				parent.logEvent(Span.SERVER_RECV);
			}
			request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
		}
		else {
			if (skip) {
				spanFromRequest = this.tracer.createSpan(name, NeverSampler.INSTANCE);
			}
			else {
				spanFromRequest = this.tracer.createSpan(name);
			}
			spanFromRequest.logEvent(Span.SERVER_RECV);
			request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
		}
		return spanFromRequest;
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addRequestTags(HttpServletRequest request) {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		this.tracer.addTag(this.traceKeys.getHttp().getUrl(), getFullUrl(request));
		this.tracer.addTag(this.traceKeys.getHttp().getHost(), request.getServerName());
		this.tracer.addTag(this.traceKeys.getHttp().getPath(), uri);
		this.tracer.addTag(this.traceKeys.getHttp().getMethod(), request.getMethod());
		for (String name : this.traceKeys.getHttp().getHeaders()) {
			Enumeration<String> values = request.getHeaders(name);
			if (values.hasMoreElements()) {
				String key = this.traceKeys.getHttp().getPrefix() + name.toLowerCase();
				ArrayList<String> list = Collections.list(values);
				String value = list.size() == 1 ? list.get(0)
						: StringUtils.collectionToDelimitedString(list, ",", "'", "'");
				this.tracer.addTag(key, value);
			}
		}
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addResponseTags(HttpServletResponse response, Throwable e) {
		int httpStatus = response.getStatus();
		if (httpStatus == HttpServletResponse.SC_OK && e != null) {
			// Filter chain threw exception but the response status may not have been set
			// yet, so we have to guess.
			this.tracer.addTag(this.traceKeys.getHttp().getStatusCode(),
					String.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
		}
		else if ((httpStatus < 200) || (httpStatus > 299)) {
			this.tracer.addTag(this.traceKeys.getHttp().getStatusCode(),
					String.valueOf(response.getStatus()));
		}
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

	private String getFullUrl(HttpServletRequest request) {
		StringBuffer requestURI = request.getRequestURL();
		String queryString = request.getQueryString();
		if (queryString == null) {
			return requestURI.toString();
		}
		else {
			return requestURI.append('?').append(queryString).toString();
		}
	}
}
