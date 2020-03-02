/*
 * Copyright 2013-2019 the original author or authors.
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

import java.net.URI;

import brave.SpanCustomizer;
import brave.http.HttpRequest;
import brave.http.HttpRequestParser;
import brave.propagation.TraceContext;

import org.springframework.cloud.sleuth.util.SpanNameUtil;

/**
 * An {@link HttpRequestParser} for clients that behaves like Sleuth in versions 1.x.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class SleuthHttpClientParser implements HttpRequestParser {

	private static final String HOST_KEY = "http.host";

	private static final String METHOD_KEY = "http.method";

	private static final String PATH_KEY = "http.path";

	private static final String URL_KEY = "http.url";

	private final TraceKeys traceKeys;

	SleuthHttpClientParser(TraceKeys traceKeys) {
		this.traceKeys = traceKeys;
	}

	@Override
	public void parse(HttpRequest request, TraceContext context, SpanCustomizer span) {
		HttpRequestParser.DEFAULT.parse(request, context, span);

		String url = request.url();
		if (url != null) {
			URI uri = URI.create(url);
			span.name(getName(uri));
			addRequestTags(span, url, uri.getHost(), uri.getPath(), request.method());
		}

		for (String header : this.traceKeys.getHttp().getHeaders()) {
			String headerValue = request.header(header);
			if (headerValue != null) {
				span.tag(key(header), headerValue);
			}
		}
	}

	private String key(String key) {
		return this.traceKeys.getHttp().getPrefix() + key.toLowerCase();
	}

	private String getName(URI uri) {
		// The returned name should comply with RFC 882 - Section 3.1.2.
		// i.e Header values must composed of printable ASCII values.
		return SpanNameUtil.shorten(uriScheme(uri) + ":" + uri.getRawPath());
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

	private void addRequestTags(SpanCustomizer span, String url, String host, String path,
			String method) {
		span.tag(URL_KEY, url);
		if (host != null) {
			span.tag(HOST_KEY, host);
		}
		span.tag(PATH_KEY, path);
		span.tag(METHOD_KEY, method);
	}

}
