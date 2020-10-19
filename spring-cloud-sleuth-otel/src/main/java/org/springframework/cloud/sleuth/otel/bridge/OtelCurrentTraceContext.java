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

package org.springframework.cloud.sleuth.otel.bridge;

import java.util.concurrent.LinkedBlockingDeque;

import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.api.CurrentTraceContext;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

public class OtelCurrentTraceContext implements CurrentTraceContext {

	private static final Log log = LogFactory.getLog(OtelCurrentTraceContext.class);

	final Tracer tracer;

	private final ApplicationEventPublisher publisher;

	public OtelCurrentTraceContext(Tracer tracer, ApplicationEventPublisher publisher) {
		this.tracer = tracer;
		this.publisher = publisher;
	}

	@Override
	public TraceContext get() {
		Span currentSpan = this.tracer.getCurrentSpan();
		if (DefaultSpan.getInvalid().equals(currentSpan)) {
			return null;
		}
		return new OtelTraceContext(currentSpan);
	}

	@Override
	public Scope newScope(TraceContext context) {
		OtelTraceContext otelTraceContext = (OtelTraceContext) context;
		SpanContext spanContext = otelTraceContext.delegate;
		Span fromContext = new SpanFromSpanContext(((OtelTraceContext) context).span, spanContext, otelTraceContext);
		this.publisher.publishEvent(new ScopeChanged(this, context));
		return new OtelScope(new OtelSpanInScope(this.tracer.withSpan(fromContext), spanContext), publisher);
	}

	@Override
	public Scope maybeScope(TraceContext context) {
		if (log.isTraceEnabled()) {
			log.trace("Will check if new scope should be created for context [" + context + "]");
		}
		if (context == null || SpanContext.getInvalid().equals(OtelTraceContext.toOtel(context))) {
			if (log.isTraceEnabled()) {
				log.trace("Invalid context - will return noop");
			}
			return new OtelScope.RevertToPrevious(this.publisher, null);
		}
		OtelTraceContext otelTraceContext = (OtelTraceContext) context;
		Span fromContext = new SpanFromSpanContext(otelTraceContext.span, otelTraceContext.delegate, otelTraceContext);
		Span currentSpan = this.tracer.getCurrentSpan();
		if (log.isTraceEnabled()) {
			log.trace("Span from context [" + fromContext + "], current span [" + currentSpan + "]");
		}
		if (traceAndSpanIdsAreEqual(fromContext, currentSpan)) {
			if (log.isTraceEnabled()) {
				log.trace("Same context as the current one - will return noop");
			}
			return new OtelScope.RevertToPrevious(this.publisher, context);
		}
		return newScope(context);
	}

	private boolean traceAndSpanIdsAreEqual(Span fromContext, Span currentSpan) {
		return fromContext.getContext().getTraceIdAsHexString().equals(currentSpan.getContext().getTraceIdAsHexString())
				&& fromContext.getContext().getSpanIdAsHexString()
						.equals(currentSpan.getContext().getSpanIdAsHexString());
	}

	public static class ScopeChanged extends ApplicationEvent {

		public final TraceContext context;

		/**
		 * Create a new {@code ApplicationEvent}.
		 * @param source the object on which the event initially occurred or with which
		 * the event is associated (never {@code null})
		 * @param context
		 */
		public ScopeChanged(Object source, @Nullable TraceContext context) {
			super(source);
			this.context = context;
		}

	}

	public static class ScopeClosed extends ApplicationEvent {

		/**
		 * Create a new {@code ApplicationEvent}.
		 * @param source the object on which the event initially occurred or with which
		 * the event is associated (never {@code null})
		 */
		public ScopeClosed(Object source) {
			super(source);
		}

	}

}

class OtelScope implements CurrentTraceContext.Scope {

	private final OtelSpanInScope delegate;

	private final ApplicationEventPublisher publisher;

	OtelScope(OtelSpanInScope delegate, ApplicationEventPublisher publisher) {
		this.delegate = delegate;
		this.publisher = publisher;
	}

	@Override
	public void close() {
		this.delegate.close();
		this.publisher.publishEvent(new OtelCurrentTraceContext.ScopeClosed(this));
		this.publisher.publishEvent(new OtelBaggageEntry.BaggageScopeEnded(this));
	}

	static class RevertToPrevious implements CurrentTraceContext.Scope {

		private static final Log log = LogFactory.getLog(RevertToPrevious.class);

		private static final LinkedBlockingDeque<TraceContext> CONTEXTS = new LinkedBlockingDeque<>();

		private final ApplicationEventPublisher publisher;

		RevertToPrevious(ApplicationEventPublisher publisher, TraceContext previous) {
			this.publisher = publisher;
			if (previous != null && !previous.equals(CONTEXTS.peekFirst())) {
				CONTEXTS.addFirst(previous);
			}
		}

		@Override
		public void close() {
			publisher.publishEvent(new OtelCurrentTraceContext.ScopeClosed(this));
			TraceContext context = CONTEXTS.pollFirst();
			if (log.isTraceEnabled()) {
				log.trace("Reverting scope to [" + context + "]");
			}
			publisher.publishEvent(new OtelCurrentTraceContext.ScopeChanged(this, context));
		}

	}

}
