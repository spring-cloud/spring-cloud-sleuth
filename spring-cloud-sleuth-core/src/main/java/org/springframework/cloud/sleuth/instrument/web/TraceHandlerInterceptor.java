/*
 * Copyright 2013-2018 the original author or authors.
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
import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.servlet.HttpServletAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.util.SpanNameUtil;
import org.springframework.web.method.HandlerMethod;
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

	private static final Log log = LogFactory.getLog(TraceHandlerInterceptor.class);

	private final BeanFactory beanFactory;

	private HttpTracing tracing;
	private TraceKeys traceKeys;
	private ErrorParser errorParser;
	private AtomicReference<ErrorController> errorController;
	private HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;

	public TraceHandlerInterceptor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
			Object handler) throws Exception {
		String spanName = spanName(handler);
		boolean continueSpan = getRootSpanFromAttribute(request) != null;
		Span span = continueSpan ? getRootSpanFromAttribute(request) :
				httpTracing().tracing().tracer().nextSpan().name(spanName).start();
		try (Tracer.SpanInScope ws = httpTracing().tracing().tracer().withSpanInScope(span)) {
			if (log.isDebugEnabled()) {
				log.debug("Handling span " + span);
			}
			addClassMethodTag(handler, span);
			addClassNameTag(handler, span);
			setSpanInAttribute(request, span);
			if (!continueSpan) {
				setNewSpanCreatedAttribute(request, span);
			}
		}
		return true;
	}

	private boolean isErrorControllerRelated(HttpServletRequest request) {
		return errorController() != null && errorController().getErrorPath()
				.equals(request.getRequestURI());
	}

	private void addClassMethodTag(Object handler, Span span) {
		if (handler instanceof HandlerMethod) {
			String methodName = ((HandlerMethod) handler).getMethod().getName();
			span.tag(traceKeys().getMvc().getControllerMethod(), methodName);
			if (log.isDebugEnabled()) {
				log.debug("Adding a method tag with value [" + methodName + "] to a span " + span);
			}
		}
	}

	private void addClassNameTag(Object handler, Span span) {
		String className;
		if (handler instanceof HandlerMethod) {
			className = ((HandlerMethod) handler).getBeanType().getSimpleName();
		} else {
			className = handler.getClass().getSimpleName();
		}
		if (log.isDebugEnabled()) {
			log.debug("Adding a class tag with value [" + className + "] to a span " + span);
		}
		span.tag(traceKeys().getMvc().getControllerClass(), className);
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
		Span spanFromRequest = getNewSpanFromAttribute(request);
		if (spanFromRequest != null) {
			try (Tracer.SpanInScope ws = httpTracing().tracing().tracer().withSpanInScope(spanFromRequest)) {
				if (log.isDebugEnabled()) {
					log.debug("Closing the span " + spanFromRequest);
				}
			} finally {
				spanFromRequest.finish();
			}
		}
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
			Object handler, Exception ex) throws Exception {
		if (isErrorControllerRelated(request)) {
			if (log.isDebugEnabled()) {
				log.debug("Skipping closing of a span for error controller processing");
			}
			return;
		}
		Span span = getRootSpanFromAttribute(request);
		if (ex != null) {
			errorParser().parseErrorTags(span, ex);
		}
		if (getNewSpanFromAttribute(request) != null) {
			if (log.isDebugEnabled()) {
				log.debug("Closing span " + span);
			}
			Span newSpan = getNewSpanFromAttribute(request);
			handler().handleSend(response, ex, newSpan);
			clearNewSpanCreatedAttribute(request);
		}
	}

	private Span getNewSpanFromAttribute(HttpServletRequest request) {
		return (Span) request.getAttribute(TraceRequestAttributes.NEW_SPAN_REQUEST_ATTR);
	}

	private Span getRootSpanFromAttribute(HttpServletRequest request) {
		return (Span) request.getAttribute(TraceFilter.TRACE_REQUEST_ATTR);
	}

	private void setSpanInAttribute(HttpServletRequest request, Span span) {
		request.setAttribute(TraceRequestAttributes.HANDLED_SPAN_REQUEST_ATTR, span);
	}

	private void setNewSpanCreatedAttribute(HttpServletRequest request, Span span) {
		request.setAttribute(TraceRequestAttributes.NEW_SPAN_REQUEST_ATTR, span);
	}

	private void clearNewSpanCreatedAttribute(HttpServletRequest request) {
		request.removeAttribute(TraceRequestAttributes.NEW_SPAN_REQUEST_ATTR);
	}

	private HttpTracing httpTracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(HttpTracing.class);
		}
		return this.tracing;
	}

	private TraceKeys traceKeys() {
		if (this.traceKeys == null) {
			this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
		}
		return this.traceKeys;
	}

	@SuppressWarnings("unchecked")
	HttpServerHandler<HttpServletRequest, HttpServletResponse> handler() {
		if (this.handler == null) {
			this.handler = HttpServerHandler.create(this.beanFactory.getBean(HttpTracing.class),
					new HttpServletAdapter());
		}
		return this.handler;
	}

	private ErrorParser errorParser() {
		if (this.errorParser == null) {
			this.errorParser = this.beanFactory.getBean(ErrorParser.class);
		}
		return this.errorParser;
	}

	ErrorController errorController() {
		if (this.errorController == null) {
			try {
				ErrorController errorController = this.beanFactory.getBean(ErrorController.class);
				this.errorController = new AtomicReference<>(errorController);
			} catch (NoSuchBeanDefinitionException e) {
				if (log.isTraceEnabled()) {
					log.trace("ErrorController bean not found");
				}
				this.errorController = new AtomicReference<>();
			}
		}
		return this.errorController.get();
	}

}
