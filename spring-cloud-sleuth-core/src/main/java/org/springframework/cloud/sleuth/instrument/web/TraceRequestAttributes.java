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

/**
 * Utility class containing values of {@link javax.servlet.http.HttpServletRequest} attributes
 *
 * @author Marcin Grzejszczak
 * @since 1.0.3
 */
public final class TraceRequestAttributes {

	/**
	 * Attribute containing a Span set on a request when it got handled by a Sleuth component.
	 * If that attribute is set then {@link TraceFilter} will not create a "fallback" server-side span.
	 */
	public static final String HANDLED_SPAN_REQUEST_ATTR = TraceRequestAttributes.class.getName()
			+ ".TRACE_HANDLED";

	/**
	 * Attribute containing a Span set on a request when it got handled by a Sleuth component.
	 * If that attribute is set then {@link TraceFilter} will not close a span processed by the Error Controller.
	 */
	public static final String ERROR_HANDLED_SPAN_REQUEST_ATTR = TraceRequestAttributes.class.getName()
			+ ".ERROR_TRACE_HANDLED";

	/**
	 * Set if Handler interceptor has executed some logic
	 */
	public static final String NEW_SPAN_REQUEST_ATTR = TraceRequestAttributes.class.getName()
			+ ".TRACE_HANDLED_NEW_SPAN";

	/**
	 * Attribute set when the Span got continued in the {@link TraceFilter}.
	 * The Sleuth tracing components will most likely continue the current Span instead of creating a new one.
	 */
	public static final String SPAN_CONTINUED_REQUEST_ATTR = TraceRequestAttributes.class.getName()
					+ ".TRACE_CONTINUED";

	private TraceRequestAttributes() {}
}
