/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.mvc;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.docs.AssertingSpanCustomizer;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Spring MVC specific type used to customize traced requests based on the handler.
 *
 * <p>
 * Note: This should not duplicate data. For example, this should not add the tag
 * "http.url".
 *
 * <p>
 * Tagging policy adopted from spring cloud sleuth 1.3.x
 */
public class HandlerParser {

	/** Adds no tags to the span representing the request. */
	public static final HandlerParser NOOP = new HandlerParser() {
		@Override
		protected void preHandle(HttpServletRequest request, Object handler, SpanCustomizer customizer) {
		}

		@Override
		protected void postHandle(HttpServletRequest request, Object handler, ModelAndView modelAndView, SpanCustomizer customizer) {
		}
	};

	/** Simple class name that processed the request. ex BookController */
	// TODO: Remove me
	public static final String CONTROLLER_CLASS = SleuthMvcSpan.Tags.CLASS.getKey();

	/** Method name that processed the request. ex listOfBooks */
	// TODO: Remove me
	public static final String CONTROLLER_METHOD = SleuthMvcSpan.Tags.METHOD.getKey();

	/**
	 * Invoked prior to request invocation during
	 * {@link HandlerInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)}.
	 *
	 * <p>
	 * Adds the tags {@link #CONTROLLER_CLASS} and {@link #CONTROLLER_METHOD}. Override or
	 * use {@link #NOOP} to change this behavior.
	 * @param request request
	 * @param handler handler
	 * @param customizer span customizer
	 */
	protected void preHandle(HttpServletRequest request, Object handler, SpanCustomizer customizer) {
		AssertingSpanCustomizer span = SleuthMvcSpan.MVC_HANDLER_INTERCEPTOR_SPAN.wrap(customizer);
		if (WebMvcRuntime.get().isHandlerMethod(handler)) {
			HandlerMethod handlerMethod = ((HandlerMethod) handler);
			span.tag(SleuthMvcSpan.Tags.CLASS, handlerMethod.getBeanType().getSimpleName());
			span.tag(SleuthMvcSpan.Tags.METHOD, handlerMethod.getMethod().getName());
		}
		else {
			span.tag(SleuthMvcSpan.Tags.CLASS, handler.getClass().getSimpleName());
		}
	}

	/**
	 * Invoked posterior to request invocation during
	 * {@link HandlerInterceptor#postHandle(HttpServletRequest, HttpServletResponse, Object, ModelAndView)}.
	 *
	 * @param request request
	 * @param handler handler
	 * @param customizer span customizer
	 */
	protected void postHandle(HttpServletRequest request, Object handler, ModelAndView modelAndView, SpanCustomizer customizer) {
	}

	/*
	 * Intentionally public for @Autowired to work without explicit binding
	 */
	public HandlerParser() {
	}

}
