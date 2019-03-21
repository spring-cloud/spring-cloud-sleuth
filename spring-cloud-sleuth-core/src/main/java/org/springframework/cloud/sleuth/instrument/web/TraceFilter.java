/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanReporter;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.UrlPathHelper;

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
@Order(TraceFilter.ORDER)
public class TraceFilter extends GenericFilterBean {

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

	private Tracer tracer;
	private TraceKeys traceKeys;
	private final Pattern skipPattern;
	private final boolean supportsJoin;
	private SpanReporter spanReporter;
	private HttpSpanExtractor spanExtractor;
	private HttpTraceKeysInjector httpTraceKeysInjector;
	private ErrorParser errorParser;
	private final BeanFactory beanFactory;
	private Boolean hasErrorController;
	private ExceptionToIgnoreInTraceFilterProvider exceptionToIgnoreInTraceFilterProvider;

	private final UrlPathHelper urlPathHelper = new UrlPathHelper();

	public TraceFilter(BeanFactory beanFactory) {
		this(beanFactory, skipPattern(beanFactory));
	}

	public TraceFilter(BeanFactory beanFactory, Pattern skipPattern) {
		this.beanFactory = beanFactory;
		this.supportsJoin = beanFactory.getBean(SleuthProperties.class).isSupportsJoin();
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
			continueSpan(request, spanFromRequest);
		}
		if (log.isDebugEnabled()) {
			log.debug("Received a request to uri [" + uri + "] that should not be sampled [" + skip + "]");
		}
		// in case of a response with exception status a exception controller will close the span
		if (!httpStatusSuccessful(response) && isSpanContinued(request)) {
			Span parentSpan = parentSpan(spanFromRequest);
			processErrorRequest(filterChain, request, new TraceHttpServletResponse(response, parentSpan), spanFromRequest);
			return;
		}
		String name = HTTP_COMPONENT + ":" + uri;
		Throwable exception = null;
		try {
			spanFromRequest = createSpan(request, skip, spanFromRequest, name);
			filterChain.doFilter(request, new TraceHttpServletResponse(response, spanFromRequest));
		} catch (Throwable e) {
			exception = e;
			errorParser().parseErrorTags(tracer().getCurrentSpan(), e);
			if (log.isErrorEnabled()) {
				log.error("Uncaught exception thrown", e);
			}
			throw e;
		} finally {
			if (isAsyncStarted(request) || request.isAsyncStarted()) {
				if (log.isDebugEnabled()) {
					log.debug("The span " + this.tracer.getCurrentSpan() + " will get detached");
				}
				this.tracer.detach(this.tracer.getCurrentSpan());
				return;
			}
			detachOrCloseSpans(request, response, spanFromRequest, exception);
		}
	}

	private Span parentSpan(Span span) {
		if (span == null) {
			return null;
		}
		if (span.hasSavedSpan()) {
			return span.getSavedSpan();
		}
		return span;
	}

	private void processErrorRequest(FilterChain filterChain, HttpServletRequest request,
			HttpServletResponse response, Span spanFromRequest)
			throws IOException, ServletException {
		if (log.isDebugEnabled()) {
			log.debug("The span " + spanFromRequest + " was already detached once and we're processing an error");
		}
		try {
			filterChain.doFilter(request, response);
		} finally {
			request.setAttribute(TRACE_ERROR_HANDLED_REQUEST_ATTR, true);
			addResponseTags(response, null);
			if (request.getAttribute(TraceRequestAttributes.ERROR_HANDLED_SPAN_REQUEST_ATTR) == null) {
				tracer().close(spanFromRequest);
			}
		}
	}

	private void continueSpan(HttpServletRequest request, Span spanFromRequest) {
		tracer().continueSpan(spanFromRequest);
		request.setAttribute(TraceRequestAttributes.SPAN_CONTINUED_REQUEST_ATTR, "true");
		if (log.isDebugEnabled()) {
			log.debug("There has already been a span in the request " + spanFromRequest);
		}
	}

	private boolean requestHasAlreadyBeenHandled(HttpServletRequest request) {
		return request.getAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR) != null;
	}

	private void detachOrCloseSpans(HttpServletRequest request,
			HttpServletResponse response, Span spanFromRequest, Throwable exception) {
		Span span = spanFromRequest;
		if (span != null) {
			addResponseTags(response, exception);
			addResponseTagsForSpanWithoutParent(request, response);
			if (span.hasSavedSpan() && requestHasAlreadyBeenHandled(request)) {
				recordParentSpan(span.getSavedSpan());
			}
			recordParentSpan(span);
			// in case of a response with exception status will close the span when exception dispatch is handled
			// checking if tracing is in progress due to async / different order of view controller processing
			if (httpStatusSuccessful(response) && tracer().isTracing()) {
				if (log.isDebugEnabled()) {
					log.debug("Closing the span " + span + " since the response was successful");
				}
				if (exception == null || !hasErrorController()) {
					tracer().close(span);
					clearTraceAttribute(request);
				} else if(exception != null && needIgnoreException(exception)){
					tracer().close(span);
					clearTraceAttribute(request);
				}
			} else if (errorAlreadyHandled(request) && tracer().isTracing() && !shouldCloseSpan(request)) {
				if (log.isDebugEnabled()) {
					log.debug(
							"Won't detach the span " + span + " since error has already been handled");
				}
			}  else if ((shouldCloseSpan(request) || isRootSpan(span)) && tracer().isTracing() && stillTracingCurrentSpan(span)) {
				if (log.isDebugEnabled()) {
					log.debug("Will close span " + span + " since " + (shouldCloseSpan(request) ? "some component marked it for closure" : "response was unsuccessful for the root span"));
				}
				if (!httpStatusSuccessful(response) && !hasErrorController()) {
					tracer().close(span);
				} else if (exception == null || !hasErrorController()) {
					clearTraceAttribute(request);
					tracer().close(span);
				}
			} else if (tracer().isTracing()) {
				if (log.isDebugEnabled()) {
					log.debug("Detaching the span " + span + " since the response was unsuccessful");
				}
				if (!httpStatusSuccessful(response) && !hasErrorController()) {
					clearTraceAttribute(request);
				}
				if (exception == null || !hasErrorController()) {
					tracer().detach(span);
				}
			}
		}
	}


	private boolean needIgnoreException(Throwable exception) {
		for (ExceptionToIgnoreInTraceFilter filter : exceptionToIgnoreInTraceFilterProvider().exceptionsToIgnoreInTraceFilters()) {
			if(exception.getClass().getName().equals(filter.exceptionClassName())){
				return true;
			}
		}
		return false;
	}

	// null check is only for tests
	private boolean hasErrorController() {
		if (this.hasErrorController == null) {
			try {
				this.hasErrorController = this.beanFactory.getBean(ErrorController.class) != null;
			} catch (NoSuchBeanDefinitionException e) {
				this.hasErrorController = false;
			}
		}
		return this.hasErrorController;
	}

	private void addResponseTagsForSpanWithoutParent(HttpServletRequest request,
			HttpServletResponse response) {
		if (spanWithoutParent(request) && response.getStatus() >= 100) {
			tracer().addTag(traceKeys().getHttp().getStatusCode(),
					String.valueOf(response.getStatus()));
		}
	}

	private boolean spanWithoutParent(HttpServletRequest request) {
		return request.getAttribute(TRACE_SPAN_WITHOUT_PARENT) != null;
	}

	private boolean isRootSpan(Span span) {
		return span.getTraceId() == span.getSpanId();
	}

	private boolean stillTracingCurrentSpan(Span span) {
		return tracer().getCurrentSpan().equals(span);
	}

	private void recordParentSpan(Span parent) {
		if (parent == null) {
			return;
		}
		if (parent.isRemote()) {
			if (log.isDebugEnabled()) {
				log.debug("Trying to send the parent span " + parent + " to Zipkin");
			}
			parent.stop();
			// should be already done by HttpServletResponse wrappers
			SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(parent);
			spanReporter().report(parent);
		} else {
			// should be already done by HttpServletResponse wrappers
			SsLogSetter.annotateWithServerSendIfLogIsNotAlreadyPresent(parent);
		}
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
	 * In order not to send unnecessary data we're not adding request tags to the server
	 * side spans. All the tags are there on the client side.
	 */
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
		Span parent = spanExtractor().joinTrace(new HttpServletRequestTextMap(request));
		if (parent != null) {
			if (log.isDebugEnabled()) {
				log.debug("Found a parent span " + parent + " in the request");
			}
			if (!this.supportsJoin) { // create a child span for this side of the RPC
				spanFromRequest = tracer().createSpan(parent.getName(), parent);
			} else {
				spanFromRequest = parent;
			}
			addRequestTagsForParentSpan(request, spanFromRequest);
			tracer().continueSpan(spanFromRequest);
			if (parent.isRemote()) { // then we are in a server span
				parent.logEvent(Span.SERVER_RECV);
			}
			request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
			if (log.isDebugEnabled()) {
				log.debug("Parent span is " + parent + "");
			}
		} else {
			if (skip) {
				spanFromRequest = tracer().createSpan(name, NeverSampler.INSTANCE);
			}
			else {
				String header = request.getHeader(Span.SPAN_FLAGS);
				if (Span.SPAN_SAMPLED.equals(header)) {
					spanFromRequest = tracer().createSpan(name, new AlwaysSampler());
				} else {
					spanFromRequest = tracer().createSpan(name);
				}
				addRequestTags(spanFromRequest, request);
				request.setAttribute(TRACE_SPAN_WITHOUT_PARENT, spanFromRequest);
			}
			spanFromRequest.logEvent(Span.SERVER_RECV);
			request.setAttribute(TRACE_REQUEST_ATTR, spanFromRequest);
			if (log.isDebugEnabled()) {
				log.debug("No parent span present - creating a new span");
			}
		}
		return spanFromRequest;
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addRequestTags(Span span, HttpServletRequest request) {
		String uri = this.urlPathHelper.getPathWithinApplication(request);
		keysInjector().addRequestTags(span, getFullUrl(request),
				request.getServerName(), uri, request.getMethod());
		for (String name : traceKeys().getHttp().getHeaders()) {
			Enumeration<String> values = request.getHeaders(name);
			if (values.hasMoreElements()) {
				String key = traceKeys().getHttp().getPrefix() + name.toLowerCase();
				ArrayList<String> list = Collections.list(values);
				String value = list.size() == 1 ? list.get(0)
						: StringUtils.collectionToDelimitedString(list, ",", "'", "'");
				keysInjector().tagSpan(span, key, value);
			}
		}
	}

	/** Override to add annotations not defined in {@link TraceKeys}. */
	protected void addResponseTags(HttpServletResponse response, Throwable e) {
		int httpStatus = response.getStatus();
		if (httpStatus == HttpServletResponse.SC_OK && e != null) {
			// Filter chain threw exception but the response status may not have been set
			// yet, so we have to guess.
			tracer().addTag(traceKeys().getHttp().getStatusCode(),
					String.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
		}
		// only tag valid http statuses
		else if (httpStatus >= 100 && (httpStatus < 200) || (httpStatus > 399)) {
			tracer().addTag(traceKeys().getHttp().getStatusCode(),
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

	Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	TraceKeys traceKeys() {
		if (this.traceKeys == null) {
			this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
		}
		return this.traceKeys;
	}

	SpanReporter spanReporter() {
		if (this.spanReporter == null) {
			this.spanReporter = this.beanFactory.getBean(SpanReporter.class);
		}
		return this.spanReporter;
	}

	HttpSpanExtractor spanExtractor() {
		if (this.spanExtractor == null) {
			this.spanExtractor = this.beanFactory.getBean(HttpSpanExtractor.class);
		}
		return this.spanExtractor;
	}

	HttpTraceKeysInjector keysInjector() {
		if (this.httpTraceKeysInjector == null) {
			this.httpTraceKeysInjector = this.beanFactory.getBean(HttpTraceKeysInjector.class);
		}
		return this.httpTraceKeysInjector;
	}

	ErrorParser errorParser() {
		if (this.errorParser == null) {
			this.errorParser = this.beanFactory.getBean(ErrorParser.class);
		}
		return this.errorParser;
	}

	ExceptionToIgnoreInTraceFilterProvider exceptionToIgnoreInTraceFilterProvider() {
		if (this.exceptionToIgnoreInTraceFilterProvider == null) {
			this.exceptionToIgnoreInTraceFilterProvider = this.beanFactory.getBean(ExceptionToIgnoreInTraceFilterProvider.class);
		}
		return this.exceptionToIgnoreInTraceFilterProvider;
	}
}

