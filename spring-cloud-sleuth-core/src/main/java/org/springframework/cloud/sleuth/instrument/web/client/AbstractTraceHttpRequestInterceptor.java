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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.util.StringUtils;
/**
 * Abstraction over classes that interact with Http requests. Allows you
 * to enrich the request headers with trace related information.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
abstract class AbstractTraceHttpRequestInterceptor {

	protected final Tracer tracer;
	protected final SpanInjector<HttpRequest> spanInjector;
	protected final TraceKeys traceKeys;

	protected AbstractTraceHttpRequestInterceptor(Tracer tracer,
			SpanInjector<HttpRequest> spanInjector, TraceKeys traceKeys) {
		this.tracer = tracer;
		this.spanInjector = spanInjector;
		this.traceKeys = traceKeys;
	}

	/**
	 * Enriches the request with proper headers and publishes
	 * the client sent event
	 */
	protected void publishStartEvent(HttpRequest request) {
		URI uri = request.getURI();
		String spanName = uriScheme(uri) + ":" + uri.getPath();
		Span newSpan = this.tracer.createSpan(spanName);
		this.spanInjector.inject(newSpan, request);
		addRequestTags(request);
		newSpan.logEvent(Span.CLIENT_SEND);
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

	/**
	 * Adds HTTP tags to the client side span
	 */
	protected void addRequestTags(HttpRequest request) {
		this.tracer.addTag(this.traceKeys.getHttp().getUrl(), request.getURI().toString());
		this.tracer.addTag(this.traceKeys.getHttp().getHost(), request.getURI().getHost());
		this.tracer.addTag(this.traceKeys.getHttp().getPath(), request.getURI().getPath());
		this.tracer.addTag(this.traceKeys.getHttp().getMethod(), request.getMethod().name());
		for (String name : this.traceKeys.getHttp().getHeaders()) {
			HttpHeaders values = request.getHeaders();
			for (Map.Entry<String, List<String>> entry : values.entrySet()) {
				String key = this.traceKeys.getHttp().getPrefix() + name.toLowerCase();
				List<String> list = entry.getValue();
				String value = list.size() == 1 ? list.get(0)
						: StringUtils.collectionToDelimitedString(list, ",", "'", "'");
				this.tracer.addTag(key, value);
			}
		}
	}

	/**
	 * Close the current span and log the client received event
	 */
	public void finish() {
		if (!isTracing()) {
			return;
		}
		currentSpan().logEvent(Span.CLIENT_RECV);
		this.tracer.close(this.currentSpan());
	}

	protected Span currentSpan() {
		return this.tracer.getCurrentSpan();
	}

	protected boolean isTracing() {
		return this.tracer.isTracing();
	}

}
