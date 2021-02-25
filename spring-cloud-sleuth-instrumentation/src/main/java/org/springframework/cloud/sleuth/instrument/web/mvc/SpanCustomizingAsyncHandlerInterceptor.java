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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.instrument.web.servlet.TracingFilter;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import static org.springframework.cloud.sleuth.instrument.web.mvc.SpanCustomizingHandlerInterceptor.setErrorAttribute;
import static org.springframework.cloud.sleuth.instrument.web.mvc.SpanCustomizingHandlerInterceptor.setHttpRouteAttribute;

/**
 * Same as {@link SpanCustomizingHandlerInterceptor} except it can be used as both an
 * {@link AsyncHandlerInterceptor} or a normal {@link HandlerInterceptor}.
 */
public final class SpanCustomizingAsyncHandlerInterceptor extends HandlerInterceptorAdapter {

	@Autowired(required = false)
	HandlerParser handlerParser = new HandlerParser();

	SpanCustomizingAsyncHandlerInterceptor() { // hide the ctor so we can change later if
												// needed
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) {
		Object span = request.getAttribute(SpanCustomizer.class.getName());
		if (span instanceof SpanCustomizer) {
			handlerParser.preHandle(request, o, (SpanCustomizer) span);
		}
		return true;
	}

	/**
	 * Sets the "error" and "http.route" attributes so that the {@link TracingFilter} can
	 * read them.
	 */
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		Object span = request.getAttribute(SpanCustomizer.class.getName());
		if (span instanceof SpanCustomizer) {
			setErrorAttribute(request, ex);
			setHttpRouteAttribute(request);
		}
	}

}
