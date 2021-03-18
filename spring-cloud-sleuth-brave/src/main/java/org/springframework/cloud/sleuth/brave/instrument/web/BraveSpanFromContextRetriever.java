/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.web;

import brave.Tracer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import reactor.util.context.Context;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.brave.bridge.BraveSpan;
import org.springframework.cloud.sleuth.instrument.web.SpanFromContextRetriever;

/**
 * Retrieves Brave specific classes from Reactor context.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.2
 */
public class BraveSpanFromContextRetriever implements SpanFromContextRetriever {

	private final CurrentTraceContext currentTraceContext;

	private final Tracer tracer;

	public BraveSpanFromContextRetriever(CurrentTraceContext currentTraceContext, Tracer tracer) {
		this.currentTraceContext = currentTraceContext;
		this.tracer = tracer;
	}

	@Override
	public Span findSpan(Context context) {
		Object braveSpan = context.getOrDefault(brave.Span.class, null);
		if (braveSpan != null) {
			return BraveSpan.fromBrave((brave.Span) braveSpan);
		}
		Object braveContext = context.getOrDefault(TraceContext.class, null);
		if (braveContext != null) {
			TraceContext traceContext = (TraceContext) braveContext;
			try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(traceContext)) {
				return BraveSpan.fromBrave(this.tracer.currentSpan());
			}
		}
		return null;
	}

}
