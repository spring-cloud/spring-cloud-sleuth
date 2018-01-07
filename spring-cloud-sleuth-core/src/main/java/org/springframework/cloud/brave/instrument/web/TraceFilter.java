/*
 * Copyright 2013-2017 the original author or authors.
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
package org.springframework.cloud.brave.instrument.web;

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

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.servlet.HttpServletAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.brave.ErrorParser;
import org.springframework.cloud.brave.TraceKeys;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.UrlPathHelper;

/**
 * Filter that takes the value of the headers from either request and uses them to
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
 * @see TraceWebServletAutoConfiguration#traceFilter
 */
@Order(TraceFilter.ORDER)
public class TraceFilter extends GenericFilterBean {
	static final Propagation.Getter<HttpServletRequest, String> GETTER = new Propagation.Getter<HttpServletRequest, String>() {
		public String get(HttpServletRequest carrier, String key) {
			return carrier.getHeader(key);
		}

		public String toString() {
			return "HttpServletRequest::getHeader";
		}
	};

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private static final String HTTP_COMPONENT = "http";

	/**
	 * If you register your filter before the {@link TraceFilter} then you will not
	 * have the tracing context passed for you out of the box. That means that e.g. your
	 * logs will not get correlated.
	 */
	public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	protected static final String TRACE_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".TRACE";

	protected static final String TRACE_ERROR_HANDLED_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".ERROR_HANDLED";

	protected static final String TRACE_CLOSE_SPAN_REQUEST_ATTR = TraceFilter.class.getName()
			+ ".CLOSE_SPAN";

	private static final String TRACE_SPAN_WITHOUT_PARENT = TraceFilter.class.getName()
			+ ".SPAN_WITH_NO_PARENT";

	private static final String SAMPLED_NAME = "X-B3-Sampled";
	private static final String SPAN_NOT_SAMPLED = "1";

	private HttpTracing tracing;
	private TraceKeys traceKeys;
	private final Pattern skipPattern;
	private ErrorParser errorParser;
	private final BeanFactory beanFactory;
	private HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;
	private TraceContext.Extractor<HttpServletRequest> extractor;

	private final UrlPathHelper urlPathHelper = new UrlPathHelper();

	public TraceFilter(BeanFactory beanFactory) {
		this(beanFactory, skipPattern(beanFactory));
	}

	public TraceFilter(BeanFactory beanFactory, Pattern skipPattern) {
		this.beanFactory = beanFactory;
		this.skipPattern = skipPattern;
	}

	private static Pattern skipPattern(BeanFactory beanFactory) {
		try {
			SkipPatternProvider patternProvider = beanFactory
					.getBean(SkipPatternProvider.class);
			// the null value will not happen on production but might happen in tests
			if (patternProvider != null) {
				return patternProvider.skipPattern();
			}
		} catch (NoSuchBeanDefinitionException e) {
			if (log.isDebugEnabled()) {
				log.debug("The default SkipPatternProvider implementation is missing, will fallback to a default value of patterns");
			}
		}
		return Pattern.compile(SleuthWebProperties.DEFAULT_SKIP_PATTERN);
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
			FilterChain filterChain) throws IOException, ServletException {
		if (!(servletRequest instanceof HttpServletRequest) ||
				!(servletResponse instanceof HttpServletResponse)) {
			throw new ServletException("Filter just supports HTTP requests");
		}
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		boolean skip = this.skipPattern.matcher(uri).matches()
				|| SPAN_NOT_SAMPLED.equals(ServletUtils.getHeader(request, response, SAMPLED_NAME));
		Span spanFromRequest = getSpanFromAttribute(request);
		Tracer.SpanInScope ws = null;
		if (spanFromRequest != null) {
			ws = continueSpan(request, spanFromRequest);
		}
		if (log.isDebugEnabled()) {
			log.debug("Received a request to uri [" + uri + "] that should not be sampled [" + skip + "]");
		}
		// in case of a response with exception status a exception controller will close the span
		if (!httpStatusSuccessful(response) && isSpanContinued(request)) {
			processErrorRequest(filterChain, request, response, spanFromRequest, ws);
			return;
		}
		String name = HTTP_COMPONENT + ":" + uri;
		Throwable exception = null;
		try {
			spanFromRequest = createSpan(request, skip, spanFromRequest, name);
			filterChain.doFilter(request, response);
		} catch (Throwable e) {
			exception = e;
			errorParser().parseErrorTags(spanFromRequest, e);
			if (log.isErrorEnabled()) {
				log.error("Uncaught exception thrown", e);
			}
			throw e;
		} finally {
			if (isAsyncStarted(request) || request.isAsyncStarted()) {
				if (log.isDebugEnabled()) {
					log.debug("The span " + spanFromRequest + " will get detached by a HandleInterceptor");
				}
				// TODO: how to deal with response annotations and async?
				return;
			}
			detachOrCloseSpans(request, response, spanFromRequest, exception);
			if (ws != null) {
				ws.close();
			}
		}
	}

	private void processErrorRequest(FilterChain filterChain, HttpServletRequest request,
			HttpServletResponse response, Span spanFromRequest, Tracer.SpanInScope ws)
			throws IOException, ServletException {
		if (log.isDebugEnabled()) {
			log.debug("The span " + spanFromRequest + " was already detached once and we're processing an error");
		}
		try {
			filterChain.doFilter(request, response);
		} finally {
			request.setAttribute(TRACE_ERROR_HANDLED_REQUEST_ATTR, true);
			addResponseTags(response, spanFromRequest, null);
			if (request.getAttribute(TraceRequestAttributes.ERROR_HANDLED_SPAN_REQUEST_ATTR) == null) {
				spanFromRequest.finish();
				if (ws != null) {
					ws.close();
				}
			}
		}
	}

	private Tracer.SpanInScope continueSpan(HttpServletRequest request, Span spanFromRequest) {
		request.setAttribute(TraceRequestAttributes.SPAN_CONTINUED_REQUEST_ATTR, "true");
		if (log.isDebugEnabled()) {
			log.debug("There has already been a span in the request " + spanFromRequest);
		}
		return httpTracing().tracing().tracer().withSpanInScope(spanFromRequest);
	}

	private boolean requestHasAlreadyBeenHandled(HttpServletRequest request) {
		return request.getAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR) != null;
	}

	private void detachOrCloseSpans(HttpServletRequest request,
			HttpServletResponse response, Span spanFromRequest, Throwable exception) {
		Span span = spanFromRequest;
		if (span != null) {
			addResponseTags(response, span, exception);
			addResponseTagsForSpanWithoutParent(request, response, span);
			// recordParentSpan(span);
			// in case of a response with exception status will close the span when exception dispatch is handled
			// checking if tracing is in progress due to async / different order of view controller processing
			if (httpStatusSuccessful(response)) {
				if (log.isDebugEnabled()) {
					log.debug("Closing the span " + span + " since the response was successful");
				}
				span.finish();
				clearTraceAttribute(request);
			} else if (errorAlreadyHandled(request) && !shouldCloseSpan(request)) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Won't detach the span " + span + " since error has already been handled");
				}
			}  else if ((shouldCloseSpan(request) || isRootSpan(span)) && stillTracingCurrentSpan(span)) {
				if (log.isDebugEnabled()) {
					log.debug("Will close span " + span + " since " + (shouldCloseSpan(request) ? "some component marked it for closure" : "response was unsuccessful for the root span"));
				}
				span.finish();
				clearTraceAttribute(request);
			} else if (httpTracing().tracing().tracer().currentSpan() != null) {
				if (log.isDebugEnabled()) {
					log.debug("Detaching the span " + span + " since the response was unsuccessful");
				}
				httpTracing().tracing().tracer().currentSpan().abandon();
				clearTraceAttribute(request);
			}
		}
	}

	private void addResponseTagsForSpanWithoutParent(HttpServletRequest request,
			HttpServletResponse response, Span span) {
		if (spanWithoutParent(request) && response.getStatus() >= 100) {
			span.tag(traceKeys().getHttp().getStatusCode(),
					String.valueOf(response.getStatus()));
		}
	}

	private boolean spanWithoutParent(HttpServletRequest request) {
		return request.getAttribute(TRACE_SPAN_WITHOUT_PARENT) != null;
	}

	private boolean isRootSpan(Span span) {
		return span.context().traceId() == span.context().spanId();
	}

	private boolean stillTracingCurrentSpan(Span span) {
		Span currentSpan = httpTracing().tracing().tracer().currentSpan();
		return currentSpan != null && currentSpan.equals(span);
	}

	private void recordParentSpan(Span parent) {
		if (parent == null) {
			return;
		}
//		if (parent.isRemote()) {
//			if (log.isDebugEnabled()) {
//				log.debug("Trying to send the parent span " + parent + " to Zipkin");
//			}
//			parent.stop();
//			spanReporter().report(parent);
//		} else {
//			// should be already done by HttpServletResponse wrappers
//		}
	}

	private boolean httpStatusSuccessful(HttpServletResponse response) {
		if (response.getStatus() == 0) {
			return false;
		}
		HttpStatus.Series httpStatusSeries = HttpStatus.Series.valueOf(response.getStatus());
		return httpStatusSeries == HttpStatus.Series.SUCCESSFUL || httpStatusSeries == HttpStatus.Series.REDIRECTION;
	}

	private Span getSpanFromAttribute(HttpServletRequest request) {
		return (Span) request.getAttribute(TRACE_REQUEST_ATTR);
	}

	private void clearTraceAttribute(HttpServletRequest request) {
		request.setAttribute(TRACE_REQUEST_ATTR, null);
	}

	private boolean errorAlreadyHandled(HttpServletRequest request) {
		return Boolean.valueOf(
				String.valueOf(request.getAttribute(TRACE_ERROR_HANDLED_REQUEST_ATTR)));
	}

	private boolean shouldCloseSpan(HttpServletRequest request) {
		return Boolean.valueOf(
				String.valueOf(request.getAttribute(TRACE_CLOSE_SPAN_REQUEST_ATTR)));
	}

	private boolean isSpanContinued(HttpServletRequest request) {
		return getSpanFromAttribute(request) != null;
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
		TraceContextOrSamplingFlags contextFromRequest = extractContext(request);
		if (contextFromRequest != null &&
				contextFromRequest != TraceContextOrSamplingFlags.EMPTY &&
				contextFromRequest.context() != null) {
			if (log.isDebugEnabled()) {
				log.debug("Found a parent span " + contextFromRequest.context() + " in the request");
			}
			spanFromRequest = httpTracing().tracing().tracer().joinSpan(contextFromRequest.context());
			request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
			if (log.isDebugEnabled()) {
				log.debug("Parent span is " + spanFromRequest + "");
			}
		} else {
			if (skip) {
				spanFromRequest = httpTracing().tracing().tracer()
						.nextSpan(TraceContextOrSamplingFlags.create(SamplingFlags.NOT_SAMPLED))
						.name(name);
			}
			else {
				if (contextFromRequest != null && contextFromRequest.context() != null) {
					spanFromRequest = httpTracing().tracing().tracer().joinSpan(contextFromRequest.context());
				} else {
					spanFromRequest = httpTracing().tracing().tracer().nextSpan().name(name).start();

				}
				request.setAttribute(TRACE_SPAN_WITHOUT_PARENT, spanFromRequest);
			}
			request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
			if (log.isDebugEnabled()) {
				log.debug("No parent span present - creating a new span");
			}
		}
		return spanFromRequest;
	}

	private TraceContextOrSamplingFlags extractContext(HttpServletRequest request) {
		try {
			return httpTracing().tracing().propagation()
					.extractor(HttpServletRequest::getHeader).extract(request);
		} catch (Exception e) {
			log.error("Exception occurred while trying to extract racing context from request", e);
			return null;
		}
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addResponseTags(HttpServletResponse response, Span span, Throwable e) {
		int httpStatus = response.getStatus();
		if (httpStatus == HttpServletResponse.SC_OK && e != null) {
			// Filter chain threw exception but the response status may not have been set
			// yet, so we have to guess.
			span.tag(traceKeys().getHttp().getStatusCode(),
					String.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
		}
		// only tag valid http statuses
		else if (httpStatus >= 100 && (httpStatus < 200) || (httpStatus > 399)) {
			span.tag(traceKeys().getHttp().getStatusCode(),
					String.valueOf(response.getStatus()));
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

	@SuppressWarnings("unchecked")
	HttpServerHandler<HttpServletRequest, HttpServletResponse> handler() {
		if (this.handler == null) {
			this.handler = HttpServerHandler.create(this.beanFactory.getBean(HttpTracing.class), 
					new HttpServletAdapter());
		}
		return this.handler;
	}

	TraceContext.Extractor<HttpServletRequest> extractor() {
		if (this.extractor == null) {
			this.extractor = httpTracing().tracing().propagation().extractor(GETTER);
		}
		return this.extractor;
	}

	HttpTracing httpTracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.tracing;
	}

	TraceKeys traceKeys() {
		if (this.traceKeys == null) {
			this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
		}
		return this.traceKeys;
	}

	ErrorParser errorParser() {
		if (this.errorParser == null) {
			this.errorParser = this.beanFactory.getBean(ErrorParser.class);
		}
		return this.errorParser;
	}
}

