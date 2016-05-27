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

import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.util.StringUtils;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * A request interceptor that sets tracing information in the headers
 * and retrieves the span from the current {@link FeignRequestContext}.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignRequestInterceptor implements RequestInterceptor {

	private final Tracer tracer;
	private final SpanInjector<RequestTemplate> spanInjector;
	private final FeignRequestContext feignRequestContext = FeignRequestContext.getInstance();
	private final TraceKeys traceKeys;

	TraceFeignRequestInterceptor(Tracer tracer,
								SpanInjector<RequestTemplate> spanInjector,
								TraceKeys traceKeys) {
		this.tracer = tracer;
		this.spanInjector = spanInjector;
		this.traceKeys = traceKeys;
	}

	@Override
	public void apply(RequestTemplate template) {
		String spanName = getSpanName(template);
		Span span = getSpan(spanName);
		this.spanInjector.inject(span, template);
		addRequestTags(template);
		span.logEvent(Span.CLIENT_SEND);
	}

	/**
	 * Adds HTTP tags to the client side span
	 */
	protected void addRequestTags(RequestTemplate requestTemplate) {
		URI uri = URI.create(requestTemplate.url());
		this.tracer.addTag(this.traceKeys.getHttp().getUrl(), uri.toString());
		this.tracer.addTag(this.traceKeys.getHttp().getHost(), uri.getHost());
		this.tracer.addTag(this.traceKeys.getHttp().getPath(), uri.getPath());
		this.tracer.addTag(this.traceKeys.getHttp().getMethod(), requestTemplate.method());
		for (String name : this.traceKeys.getHttp().getHeaders()) {
			Map<String, Collection<String>> values = requestTemplate.headers();
			for (Map.Entry<String, Collection<String>> entry : values.entrySet()) {
				String key = this.traceKeys.getHttp().getPrefix() + name.toLowerCase();
				Collection<String> list = entry.getValue();
				String value = list.size() == 1 ? list.iterator().next()
						: StringUtils.collectionToDelimitedString(list, ",", "'", "'");
				this.tracer.addTag(key, value);
			}
		}
	}

	protected String getSpanName(RequestTemplate template) {
		URI uri = URI.create(template.url());
		return uriScheme(uri) + ":" + uri.getPath();
	}

	/**
	 * Depending on the presence of a Span in context, either starts a new Span
	 * or continues an existing one.
	 */
	protected Span getSpan(String spanName) {
		if (!this.feignRequestContext.hasSpanInProcess()) {
			Span span = this.tracer.createSpan(spanName);
			this.feignRequestContext.putSpan(span, false);
			return span;
		} else {
			if (this.feignRequestContext.wasSpanRetried()) {
				return this.tracer.continueSpan(this.feignRequestContext.getCurrentSpan());
			}
		}
		return this.tracer.createSpan(spanName);
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

}
