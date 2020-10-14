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

import io.opentelemetry.baggage.BaggageManager;
import io.opentelemetry.baggage.EntryMetadata;
import io.opentelemetry.trace.Tracer;

import org.springframework.cloud.sleuth.api.Baggage;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

public class OtelBaggage implements Baggage {

	private final Tracer tracer;

	private final ApplicationEventPublisher publisher;

	private final SleuthBaggageProperties sleuthBaggageProperties;

	private final io.opentelemetry.baggage.Baggage delegate;

	private final io.opentelemetry.baggage.BaggageManager manager;

	private final String name;

	private final EntryMetadata entryMetadata;

	public OtelBaggage(Tracer tracer, ApplicationEventPublisher publisher,
			SleuthBaggageProperties sleuthBaggageProperties, io.opentelemetry.baggage.Baggage delegate,
			BaggageManager manager, String name, EntryMetadata entryMetadata) {
		this.tracer = tracer;
		this.publisher = publisher;
		this.sleuthBaggageProperties = sleuthBaggageProperties;
		this.delegate = delegate;
		this.manager = manager;
		this.name = name;
		this.entryMetadata = entryMetadata;
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public String getValue() {
		return this.delegate.getEntryValue(this.name);
	}

	@Override
	public void updateValue(String value) {
		this.manager.baggageBuilder().put(this.name, value, this.entryMetadata);
		if (this.sleuthBaggageProperties.getTagFields().stream().map(String::toLowerCase)
				.anyMatch(s -> s.equals(this.name))) {
			this.tracer.getCurrentSpan().setAttribute(this.name, value);
		}
		this.publisher.publishEvent(new BaggageChanged(this, this.name, value));
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

}
