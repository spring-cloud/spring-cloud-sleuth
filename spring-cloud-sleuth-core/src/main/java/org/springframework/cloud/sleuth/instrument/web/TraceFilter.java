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

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanExtractor;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.filter.GenericFilterBean;
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
public class TraceFilter extends GenericFilterBean {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private static final String HTTP_COMPONENT = "http";

	protected static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".TRACE";

	protected static final String TRACE_ERROR_HANDLED_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".ERROR_HANDLED";

	public static final String DEFAULT_SKIP_PATTERN =
			"/api-docs.*|/autoconfig|/configprops|/dump|/health|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream";

	private final Tracer tracer;
	private final TraceKeys traceKeys;
	private final Pattern skipPattern;
	private final SpanReporter spanReporter;
	private final SpanExtractor<HttpServletRequest> spanExtractor;
	private final SpanInjector<HttpServletResponse> spanInjector;
	private final HttpTraceKeysInjector httpTraceKeysInjector;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	public TraceFilter(Tracer tracer, TraceKeys traceKeys, SpanReporter spanReporter,
			SpanExtractor<HttpServletRequest> spanExtractor,
			SpanInjector<HttpServletResponse> spanInjector,
			HttpTraceKeysInjector httpTraceKeysInjector) {
		this(tracer, traceKeys, Pattern.compile(DEFAULT_SKIP_PATTERN), spanReporter,
				spanExtractor, spanInjector, httpTraceKeysInjector);
	}

	public TraceFilter(Tracer tracer, TraceKeys traceKeys, Pattern skipPattern,
			SpanReporter spanReporter, SpanExtractor<HttpServletRequest> spanExtractor,
			SpanInjector<HttpServletResponse> spanInjector,
			HttpTraceKeysInjector httpTraceKeysInjector) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.skipPattern = skipPattern;
		this.spanReporter = spanReporter;
		this.spanExtractor = spanExtractor;
		this.spanInjector = spanInjector;
		this.httpTraceKeysInjector = httpTraceKeysInjector;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
			FilterChain filterChain) throws IOException, ServletException {
		if (!(servletRequest instanceof HttpServletRequest) || !(servletResponse instanceof HttpServletResponse)) {
			throw new ServletException("Filter just supports HTTP requests");
		}
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| Span.SPAN_NOT_SAMPLED.equals(ServletUtils.getHeader(request, response, Span.SAMPLED_NAME));
		Span spanFromRequest = getSpanFromAttribute(request);
		if (spanFromRequest != null) {
			this.tracer.continueSpan(spanFromRequest);
			if (log.isDebugEnabled()) {
				log.debug("There has already been a span in the request " + spanFromRequest + "");
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("Received a request to uri [" + uri + "] that should be skipped [" + skip + "]");
		}
		// in case of a response with exception status a exception controller will close the span
		if (!httpStatusSuccessful(response) && isSpanContinued(request)) {
			if (log.isDebugEnabled()) {
				log.debug(
						"The span was already detached once and we're processing an error");
			}
			try {
				filterChain.doFilter(request, response);
			} finally {
				request.setAttribute(TRACE_ERROR_HANDLED_REQUEST_ATTR, true);
				this.tracer.close(spanFromRequest);
			}
			return;
		}
		addToResponseIfNotPresent(response, Span.SAMPLED_NAME, skip ? Span.SPAN_NOT_SAMPLED : Span.SPAN_SAMPLED);
		String name = HTTP_COMPONENT + ":" + uri;
		spanFromRequest = createSpan(request, skip, spanFromRequest, name);
		Throwable exception = null;
		try {
			this.spanInjector.inject(spanFromRequest, response);
			// Add headers before filter chain in case one of the filters flushes the
			// response...
			filterChain.doFilter(request, response);
		}
		catch (Throwable e) {
			exception = e;
			throw e;
		}
		finally {
			if (isAsyncStarted(request) || request.isAsyncStarted()) {
				if (log.isDebugEnabled()) {
					log.debug("Detaching the span " + spanFromRequest + " since the request is asynchronous");
				}
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
						if (log.isDebugEnabled()) {
							log.debug("Sending the parent span " + parent + " to Zipkin");
						}
						parent.logEvent(Span.SERVER_SEND);
						parent.stop();
						this.spanReporter.report(parent);
					}
				} else {
					spanFromRequest.logEvent(Span.SERVER_SEND);
				}
				// in case of a response with exception status will close the span when exception dispatch is handled
				if (httpStatusSuccessful(response)) {
					if (log.isDebugEnabled()) {
						log.debug("Closing the span " + spanFromRequest + " since the response was successful");
					}
					this.tracer.close(spanFromRequest);
				} else if (errorAlreadyHandled(request)) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Won't detach the span since error has already been handled");
					}
				} else {
					if (log.isDebugEnabled()) {
						log.debug("Detaching the span " + spanFromRequest + " since the response was unsuccessful");
					}
					this.tracer.detach(spanFromRequest);
				}
			}
		}
	}

	private boolean httpStatusSuccessful(HttpServletResponse response) {
		HttpStatus httpStatus = HttpStatus.valueOf(response.getStatus());
		return httpStatus.is2xxSuccessful() || httpStatus.is3xxRedirection();
	}

	private Span getSpanFromAttribute(HttpServletRequest request) {
		return (Span) request.getAttribute(TRACE_REQUEST_ATTR);
	}

	private boolean errorAlreadyHandled(HttpServletRequest request) {
		return Boolean.valueOf(
				String.valueOf(request.getAttribute(TRACE_ERROR_HANDLED_REQUEST_ATTR)));
	}

	private boolean isSpanContinued(HttpServletRequest request) {
		return getSpanFromAttribute(request) != null;
	}

	private void addRequestTagsForParentSpan(HttpServletRequest request, Span spanFromRequest) {
		if (spanFromRequest.getName().contains("parent")) {
			addRequestTags(spanFromRequest, request);
		}
	}

	/**
	 * Creates a span and appends it as the current request's attribute
	 */
	private Span createSpan(HttpServletRequest request,
			boolean skip, Span spanFromRequest, String name) {
		if (spanFromRequest != null) {
			if (log.isDebugEnabled()) {
				log.debug("Span has already been created - continuing with the previous one");
			}
			return spanFromRequest;
		}
		Span parent = this.spanExtractor.joinTrace(request);
		if (parent != null) {
			if (log.isDebugEnabled()) {
				log.debug("Found a parent span " + parent + " in the request");
			}
			addRequestTagsForParentSpan(request, parent);
			spanFromRequest = this.tracer.createSpan(name, parent);
			if (log.isDebugEnabled()) {
				log.debug("Started a new span " + spanFromRequest + " with parent " + parent);
			}
			if (parent.isRemote()) {
				parent.logEvent(Span.SERVER_RECV);
			}
			request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
			if (log.isDebugEnabled()) {
				log.debug("Parent span is " + parent + "");
			}
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
			log.debug("No parent span present - creating a new span");
		}
		return spanFromRequest;
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addRequestTags(Span span, HttpServletRequest request) {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		this.httpTraceKeysInjector.addRequestTags(span, getFullUrl(request),
				request.getServerName(), uri, request.getMethod());
		for (String name : this.traceKeys.getHttp().getHeaders()) {
			Enumeration<String> values = request.getHeaders(name);
			if (values.hasMoreElements()) {
				String key = this.traceKeys.getHttp().getPrefix() + name.toLowerCase();
				ArrayList<String> list = Collections.list(values);
				String value = list.size() == 1 ? list.get(0)
						: StringUtils.collectionToDelimitedString(list, ",", "'", "'");
				this.httpTraceKeysInjector.tagSpan(span, key, value);
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
		else if ((httpStatus < 200) || (httpStatus > 399)) {
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

	protected boolean isAsyncStarted(HttpServletRequest request) {
		return WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted();
	}

	private String getFullUrl(HttpServletRequest request) {
		StringBuffer requestURI = request.getRequestURL();
		String queryString = request.getQueryString();
		if (queryString == null) {
			return requestURI.toString();
		} else {
			return requestURI.append('?').append(queryString).toString();
		}
	}
}
