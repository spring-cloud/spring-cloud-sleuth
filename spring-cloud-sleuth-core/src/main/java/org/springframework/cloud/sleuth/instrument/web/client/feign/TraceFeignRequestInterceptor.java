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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.Tracer;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * A request interceptor that sets tracing information in the headers.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignRequestInterceptor implements RequestInterceptor {

	private final Tracer tracer;
	private final SpanInjector<RequestTemplate> spanInjector;

	TraceFeignRequestInterceptor(Tracer tracer,
			SpanInjector<RequestTemplate> spanInjector) {
		this.tracer = tracer;
		this.spanInjector = spanInjector;
	}

	@Override
	public void apply(RequestTemplate template) {
		String spanName = getSpanName(template);
		Span span = this.tracer.createSpan(spanName);
		this.spanInjector.inject(span, template);
		span.logEvent(Span.CLIENT_SEND);
	}

	private String getSpanName(RequestTemplate template) {
		URI uri = URI.create(template.url());
		return uriScheme(uri) + ":" + uri.getPath();
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

}
