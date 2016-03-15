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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.util.StringUtils;

import static java.util.Collections.singletonList;

/**
 * Span injector that injects tracing info to
 * {@link FeignResponseHeadersHolder#responseHeaders}
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
class FeignResponseHeadersInjector implements SpanInjector<FeignResponseHeadersHolder> {

	@Override
	public void inject(Span span, FeignResponseHeadersHolder carrier) {
		Map<String, Collection<String>> headers = carrier.responseHeaders;
		headersWithTraceId(span, headers);
	}

	private Map<String, Collection<String>> headersWithTraceId(Span span,
			Map<String, Collection<String>> headers) {
		Map<String, Collection<String>> newHeaders = new HashMap<>();
		newHeaders.putAll(headers);
		if (span == null) {
			setHeader(newHeaders, Span.NOT_SAMPLED_NAME, "true");
			return newHeaders;
		}
		setHeader(newHeaders, Span.TRACE_ID_NAME, span.getTraceId());
		setHeader(newHeaders, Span.SPAN_ID_NAME, span.getSpanId());
		return newHeaders;
	}

	void setHeader(Map<String, Collection<String>> headers, String name,
			String value) {
		if (StringUtils.hasText(value) && !headers.containsKey(name)) {
			headers.put(name, singletonList(value));
		}
	}

	void setHeader(Map<String, Collection<String>> headers, String name,
			Long value) {
		if (value != null) {
			setHeader(headers, name, Span.idToHex(value));
		}
	}
}
