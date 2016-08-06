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

import java.lang.invoke.MethodHandles;
import java.net.URI;

import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;

import feign.RequestInterceptor;
import feign.RequestPostProcessor;
import feign.RequestTemplate;
import feign.RetryableException;

/**
 * A request interceptor that sets tracing information in the headers
 * and retrieves the span from the current {@link FeignRequestContext}.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignRequestInterceptor extends FeignEventPublisher implements RequestInterceptor,
		RequestPostProcessor {

	private static final org.apache.commons.logging.Log log = LogFactory.getLog(
			MethodHandles.lookup().lookupClass());

	private final SpanInjector<RequestTemplate> spanInjector;

	TraceFeignRequestInterceptor(BeanFactory beanFactory,
			SpanInjector<RequestTemplate> spanInjector) {
		super(beanFactory);
		this.spanInjector = spanInjector;
	}

	@Override
	public void apply(RequestTemplate template) {
		String spanName = getSpanName(template);
		Span span = getSpan(spanName, template);
		this.spanInjector.inject(span, template);
		span.logEvent(Span.CLIENT_SEND);
	}

	protected String getSpanName(RequestTemplate template) {
		URI uri = URI.create(template.url());
		return uriScheme(uri) + ":" + uri.getPath();
	}

	/**
	 * Depending on the presence of a Span in context, either starts a new Span
	 * or continues an existing one.
	 */
	protected Span getSpan(String spanName, RequestTemplate template) {
		if (!getTracer().isTracing()) {
			return getTracer().createSpan(spanName);
		} else {
			if (template.request().headers().containsKey("feign.retry")) {
				return getTracer().continueSpan(getTracer().getCurrentSpan());
			}
		}
		return getTracer().createSpan(spanName);
	}

	private String uriScheme(URI uri) {
		return uri.getScheme() == null ? "http" : uri.getScheme();
	}

	@Override
	public void apply(RequestTemplate requestTemplate, RetryableException e) {
		if (!getTracer().isTracing()) {
			log.debug("No span was started so won't do anything new");
			return;
		}
		if (e == null) {
			log.debug("There is no retry to take place so closing the span");
			finish();
		} else {
			requestTemplate.header("feign.retried", "true");
		}
	}
}
