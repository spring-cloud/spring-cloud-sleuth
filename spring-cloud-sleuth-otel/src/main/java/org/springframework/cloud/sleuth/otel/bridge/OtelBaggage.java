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

import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Context;
import io.opentelemetry.baggage.BaggageManager;
import io.opentelemetry.baggage.BaggageUtils;
import io.opentelemetry.baggage.EntryMetadata;
import io.opentelemetry.trace.Tracer;

import org.springframework.cloud.sleuth.api.Baggage;
import org.springframework.cloud.sleuth.api.TraceContext;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

// TODO: [OTEL] Experimental - doesn't really work
public class OtelBaggage implements Baggage {

	private final Tracer tracer;

	private final ApplicationEventPublisher publisher;

	private final SleuthBaggageProperties sleuthBaggageProperties;

	private final io.opentelemetry.baggage.Baggage delegate;

	private final io.opentelemetry.baggage.BaggageManager manager;

	private final String name;

	private final EntryMetadata entryMetadata;

	private final AtomicReference<Context> context;

	public OtelBaggage(Tracer tracer, ApplicationEventPublisher publisher,
			SleuthBaggageProperties sleuthBaggageProperties, io.opentelemetry.baggage.Baggage delegate,
			BaggageManager manager, AtomicReference<Context> context, String name, EntryMetadata entryMetadata) {
		this.tracer = tracer;
		this.publisher = publisher;
		this.sleuthBaggageProperties = sleuthBaggageProperties;
		this.delegate = delegate;
		this.manager = manager;
		this.name = name;
		this.entryMetadata = entryMetadata;
		this.context = context;
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public String get() {
		return BaggageUtils.getBaggage(this.context.get()).getEntryValue(this.name);
	}

	@Override
	public String get(TraceContext traceContext) {
		// TODO: [OTEL] Discuss this with OTEL
		return null;
	}

	@Override
	public void set(String value) {
		io.opentelemetry.baggage.Baggage baggage = this.manager.baggageBuilder().setParent(this.delegate)
				.put(this.name, value, this.entryMetadata).build();
		this.context.set(BaggageUtils.withBaggage(baggage, this.context.get()));
		if (this.sleuthBaggageProperties.getTagFields().stream().map(String::toLowerCase)
				.anyMatch(s -> s.equals(this.name))) {
			this.tracer.getCurrentSpan().setAttribute(this.name, value);
		}
		this.publisher.publishEvent(new BaggageChanged(this, this.name, value));
	}

	@Override
	public void set(TraceContext traceContext, String value) {
		// TODO: [OTEL] Discuss this with OTEL
	}

	public static class BaggageChanged extends ApplicationEvent {

		public String name;

		public String value;

		public BaggageChanged(OtelBaggage source, String name, String value) {
			super(source);
			this.name = name;
			this.value = value;
		}

		@Override
		public String toString() {
			return "BaggageChanged{" + "name='" + name + '\'' + ", value='" + value + '\'' + '}';
		}

	}

	public static class BaggageScopeEnded extends ApplicationEvent {

		public BaggageScopeEnded(Object source) {
			super(source);
		}

	}

}
