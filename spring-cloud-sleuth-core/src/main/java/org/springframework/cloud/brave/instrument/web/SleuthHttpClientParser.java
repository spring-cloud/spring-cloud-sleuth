/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.brave.instrument.web;

import java.net.URI;

import org.springframework.cloud.brave.TraceKeys;
import org.springframework.cloud.sleuth.util.SpanNameUtil;

import brave.SpanCustomizer;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;

/**
 * An {@link HttpClientParser} that behaves like Sleuth in versions 1.x
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
class SleuthHttpClientParser extends HttpClientParser {

	private final TraceKeys traceKeys;

	public SleuthHttpClientParser(TraceKeys traceKeys) {
		this.traceKeys = traceKeys;
	}

	@Override protected <Req> String spanName(HttpAdapter<Req, ?> adapter,
			Req req) {
		return getName(URI.create(adapter.url(req)));
	}

	@Override public <Req> void request(HttpAdapter<Req, ?> adapter, Req req,
			SpanCustomizer customizer) {
		super.request(adapter, req, customizer);
		String url = adapter.url(req);
		URI uri = URI.create(url);
		addRequestTags(customizer, url, uri.getHost(), uri.getPath(), adapter.method(req));
		this.traceKeys.getHttp().getHeaders()
				.forEach(s -> {
						String headerValue = adapter.requestHeader(req, s);
						if (headerValue != null) {
							customizer.tag(key(s), headerValue);
						}
				});
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

	private void addRequestTags(SpanCustomizer customizer, String url, String host,
			String path, String method) {
		customizer.tag(this.traceKeys.getHttp().getUrl(), url);
		if (host != null) {
			customizer.tag(this.traceKeys.getHttp().getHost(), host);
		}
		customizer.tag(this.traceKeys.getHttp().getPath(), path);
		customizer.tag(this.traceKeys.getHttp().getMethod(), method);
	}
}