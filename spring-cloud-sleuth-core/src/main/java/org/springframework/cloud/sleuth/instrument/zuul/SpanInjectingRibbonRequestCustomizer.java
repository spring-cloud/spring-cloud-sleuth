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

package org.springframework.cloud.sleuth.instrument.zuul;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.netflix.ribbon.support.RibbonRequestCustomizer;

/**
 * Abstraction over customization of Ribbon Requests. All clients will inject the span
 * into their respective context. The only difference is how those contexts set the headers.
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
abstract class SpanInjectingRibbonRequestCustomizer<T> implements RibbonRequestCustomizer<T> {

	private static final Log log = LogFactory.getLog(SpanInjectingRibbonRequestCustomizer.class);

	private final Tracer tracer;
	HttpClientHandler<T, T> handler;
	TraceContext.Injector<T> injector;

	SpanInjectingRibbonRequestCustomizer(HttpTracing httpTracing) {
		this.tracer = httpTracing.tracing().tracer();
		this.handler = HttpClientHandler
				.create(httpTracing, handlerClientAdapter());
		this.injector = httpTracing.tracing().propagation().injector(setter());
	}

	@Override
	public void customize(T context) {
		Span span = getCurrentSpan();
		if (span == null) {
			this.handler.handleSend(this.injector, context);
			return;
		}
		Span childSpan = this.handler.handleSend(this.injector, context, span);
		try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(childSpan)) {
			if (log.isDebugEnabled()) {
				log.debug("Span in the RibbonRequestCustomizer is" + span);
			}
		} finally {
			childSpan.finish();
		}
	}
	
	protected abstract brave.http.HttpClientAdapter<T, T> handlerClientAdapter();

	protected abstract Propagation.Setter<T, String> setter();

	Span getCurrentSpan() {
		return this.tracer.currentSpan();
	}
}
