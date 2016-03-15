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

import javax.servlet.http.HttpServletResponse;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;

/**
 * Span injector that injects tracing info to {@link HttpServletResponse}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class HttpServletResponseInjector implements SpanInjector<HttpServletResponse> {

	@Override
	public void inject(Span span, HttpServletResponse carrier) {
		if (span == null) {
			return;
		}
		if (!carrier.containsHeader(Span.SPAN_ID_NAME)) {
			carrier.addHeader(Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
			carrier.addHeader(Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
		}
	}

}
