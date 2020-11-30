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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.springframework.cloud.sleuth.BaggageInScope;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * OpenTelemetry implementation of a {@link BaggageInScope}.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
class OtelBaggageInScope implements BaggageInScope {

	private final OtelBaggageManager otelBaggageManager;

	private final CurrentTraceContext currentTraceContext;

	private final ApplicationEventPublisher publisher;

	private final List<String> tagFields;

	private final AtomicReference<Entry> entry = new AtomicReference<>();

	private final AtomicReference<Scope> scope = new AtomicReference<>();

	OtelBaggageInScope(OtelBaggageManager otelBaggageManager, CurrentTraceContext currentTraceContext,
			ApplicationEventPublisher publisher, List<String> tagFields, Entry entry) {
		this.otelBaggageManager = otelBaggageManager;
		this.currentTraceContext = currentTraceContext;
		this.publisher = publisher;
		this.tagFields = tagFields;
		this.entry.set(entry);
	}

	@Override
	public String name() {
		return entry().getKey();
	}

	@Override
	public String get() {
		return this.otelBaggageManager.currentBaggage().getEntryValue(entry().getKey());
	}

	@Override
	public String get(TraceContext traceContext) {
		Entry entry = this.otelBaggageManager.getEntry((OtelTraceContext) traceContext, entry().getKey());
		if (entry == null) {
			return null;
		}
		return entry.getValue();
	}

	@Override
	public BaggageInScope set(String value) {
		return doSet(this.currentTraceContext.context(), value);
	}

	private BaggageInScope doSet(TraceContext context, String value) {
		Context current = Context.current();
		Span currentSpan = Span.current();
		io.opentelemetry.api.baggage.Baggage baggage;
		if (context != null) {
			OtelTraceContext ctx = (OtelTraceContext) context;
			Context storedCtx = ctx.context();
			Baggage fromContext = Baggage.fromContext(storedCtx);
			baggage = fromContext.toBuilder().setParent(current)
					.put(entry().getKey(), value, entry().getEntryMetadata()).build();
			current = baggage.storeInContext(current);
			ctx.updateContext(current);
		}
		else {
			baggage = Baggage.builder().put(entry().getKey(), value, entry().getEntryMetadata()).build();
		}
		Context withBaggage = current.with(baggage);
		this.scope.set(withBaggage.makeCurrent());
		if (this.tagFields.stream().map(String::toLowerCase).anyMatch(s -> s.equals(entry().getKey()))) {
			currentSpan.setAttribute(entry().getKey(), value);
		}
		this.publisher.publishEvent(new BaggageChanged(this, baggage, entry().getKey(), value));
		Entry previous = entry();
		this.entry.set(new Entry(previous.getKey(), value, previous.getEntryMetadata()));
		return this;
	}

	private Entry entry() {
		return this.entry.get();
	}

	@Override
	public BaggageInScope set(TraceContext traceContext, String value) {
		return doSet(traceContext, value);
	}

	@Override
	public BaggageInScope makeCurrent() {
		close();
		Entry entry = entry();
		Scope scope = Baggage.builder().put(entry.getKey(), entry.getValue(), entry.getEntryMetadata()).build()
				.makeCurrent();
		this.scope.set(scope);
		return this;
	}

	@Override
	public void close() {
		Scope scope = this.scope.get();
		if (scope != null) {
			this.scope.set(null);
			scope.close();
		}
	}

	static class BaggageChanged extends ApplicationEvent {

		/**
		 * Baggage with the new entry.
		 */
		Baggage baggage;

		/**
		 * Baggage entry name.
		 */
		String name;

		/**
		 * Baggage entry value.
		 */
		String value;

		BaggageChanged(OtelBaggageInScope source, Baggage baggage, String name, String value) {
			super(source);
			this.baggage = baggage;
			this.name = name;
			this.value = value;
		}

		@Override
		public String toString() {
			return "BaggageChanged{" + "name='" + name + '\'' + ", value='" + value + '\'' + '}';
		}

	}

}
