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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.invoke.MethodHandles;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.util.SpanNameUtil;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * {@link org.springframework.web.servlet.HandlerInterceptor} that wraps handling of a
 * request in a Span. Adds tags related to the class and method name.
 *
 * The interceptor will not create spans for error controller related paths.
 *
 * It's important to note that this implementation will set the request attribute
 * {@link TraceRequestAttributes#HANDLED_SPAN_REQUEST_ATTR} when the request is processed.
 * That way the {@link TraceFilter} will not create the "fallback" span.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.3
 */
public class TraceHandlerInterceptor extends HandlerInterceptorAdapter {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final BeanFactory beanFactory;

	private Tracer tracer;
	private TraceKeys traceKeys;
	private ErrorController errorController;

	public TraceHandlerInterceptor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
			Object handler) throws Exception {
		if (isErrorControllerRelated(request)) {
			log.debug("Skipping creation of a span for error controller processing");
			return true;
		}
		if (isSpanContinued(request)) {
			log.debug("Skipping creation of a span since the span is continued");
			return true;
		}
		String spanName = spanName(handler);
		Span span = getTracer().createSpan(spanName);
		if (log.isDebugEnabled()) {
			log.debug("Created new span " + span + " with name [" + spanName + "]");
		}
		addClassMethodTag(handler, span);
		addClassNameTag(handler, span);
		setSpanInAttribute(request, span);
		return true;
	}

	private boolean isErrorControllerRelated(HttpServletRequest request) {
		return getErrorController() != null && getErrorController().getErrorPath()
				.equals(request.getRequestURI());
	}

	private void addClassMethodTag(Object handler, Span span) {
		if (handler instanceof HandlerMethod) {
			String methodName = SpanNameUtil.toLowerHyphen(
					((HandlerMethod) handler).getMethod().getName());
			getTracer().addTag(getTraceKeys().getMvc().getControllerMethod(), methodName);
			if (log.isDebugEnabled()) {
				log.debug("Adding a method tag with value [" + methodName + "] to a span " + span);
			}
		}
	}

	private void addClassNameTag(Object handler, Span span) {
		String className;
		if (handler instanceof HandlerMethod) {
			className = SpanNameUtil.toLowerHyphen(
					((HandlerMethod) handler).getBeanType().getSimpleName());
		} else {
			className = SpanNameUtil.toLowerHyphen(handler.getClass().getSimpleName());
		}
		if (log.isDebugEnabled()) {
			log.debug("Adding a class tag with value [" + className + "] to a span " + span);
		}
		getTracer().addTag(getTraceKeys().getMvc().getControllerClass(), className);
	}

	private String spanName(Object handler) {
		if (handler instanceof HandlerMethod) {
			return SpanNameUtil.toLowerHyphen(((HandlerMethod) handler).getMethod().getName());
		}
		return SpanNameUtil.toLowerHyphen(handler.getClass().getSimpleName());
	}

	@Override
	public void afterConcurrentHandlingStarted(HttpServletRequest request,
			HttpServletResponse response, Object handler) throws Exception {
		Span spanFromRequest = getSpanFromAttribute(request);
		Span rootSpanFromRequest = getRootSpanFromAttribute(request);
		if (log.isDebugEnabled()) {
			log.debug("Closing the span " + spanFromRequest + " and detaching its parent " + rootSpanFromRequest + " since the request is asynchronous");
		}
		getTracer().close(spanFromRequest);
		getTracer().detach(rootSpanFromRequest);
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response,
			Object handler, ModelAndView modelAndView) throws Exception {
		if (isErrorControllerRelated(request)) {
			log.debug("Skipping closing of a span for error controller processing");
			return;
		}
		if (isSpanContinued(request)) {
			log.debug("Skipping closing of a span since it's been continued");
			return;
		}
		Span span = getSpanFromAttribute(request);
		if (log.isDebugEnabled()) {
			log.debug("Closing span " + span);
		}
		getTracer().close(span);
	}

	private boolean isSpanContinued(HttpServletRequest request) {
		return request.getAttribute(TraceRequestAttributes.SPAN_CONTINUED_REQUEST_ATTR) != null;
	}

	private Span getSpanFromAttribute(HttpServletRequest request) {
		return (Span) request.getAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR);
	}

	private Span getRootSpanFromAttribute(HttpServletRequest request) {
		return (Span) request.getAttribute(TraceFilter.TRACE_REQUEST_ATTR);
	}

	private void setSpanInAttribute(HttpServletRequest request, Span span) {
		request.setAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR, span);
	}

	private Tracer getTracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	private TraceKeys getTraceKeys() {
		if (this.traceKeys == null) {
			this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
		}
		return this.traceKeys;
	}

	private ErrorController getErrorController() {
		if (this.errorController == null) {
			this.errorController = this.beanFactory.getBean(ErrorController.class);
		}
		return this.errorController;
	}
}
