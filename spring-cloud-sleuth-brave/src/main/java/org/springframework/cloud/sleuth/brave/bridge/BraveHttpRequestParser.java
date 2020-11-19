/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.bridge;

import org.springframework.cloud.sleuth.api.SpanCustomizer;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.api.http.HttpRequest;
import org.springframework.cloud.sleuth.api.http.HttpRequestParser;

/**
 * Brave implementation of a {@link HttpRequestParser}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class BraveHttpRequestParser implements HttpRequestParser {

	final brave.http.HttpRequestParser delegate;

	public BraveHttpRequestParser(brave.http.HttpRequestParser delegate) {
		this.delegate = delegate;
	}

	@Override
	public void parse(HttpRequest request, TraceContext context, SpanCustomizer span) {
		this.delegate.parse(BraveHttpRequest.toBrave(request), BraveTraceContext.toBrave(context),
				BraveSpanCustomizer.toBrave(span));
	}

	/**
	 * @param parser Sleuth's API parser
	 * @return Brave version of the parser
	 */
	public static brave.http.HttpRequestParser toBrave(HttpRequestParser parser) {
		if (parser instanceof BraveHttpRequestParser) {
			return ((BraveHttpRequestParser) parser).delegate;
		}
		return (request, context, span) -> parser.parse(BraveHttpRequest.fromBrave(request),
				BraveTraceContext.fromBrave(context), BraveSpanCustomizer.fromBrave(span));
	}

}
