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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.baggage.Entry;
import io.opentelemetry.baggage.EntryMetadata;
import io.opentelemetry.trace.Tracer;

import org.springframework.cloud.sleuth.api.Baggage;
import org.springframework.cloud.sleuth.api.BaggageManager;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.context.ApplicationEventPublisher;

public class OtelBaggageManager implements BaggageManager {

	private final io.opentelemetry.trace.Tracer tracer;

	private final io.opentelemetry.baggage.BaggageManager delegate;

	private final SleuthBaggageProperties sleuthBaggageProperties;

	private final ApplicationEventPublisher publisher;

	public OtelBaggageManager(Tracer tracer, io.opentelemetry.baggage.BaggageManager delegate,
			SleuthBaggageProperties sleuthBaggageProperties, ApplicationEventPublisher publisher) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.sleuthBaggageProperties = sleuthBaggageProperties;
		this.publisher = publisher;
	}

	@Override
	public Map<String, String> getAllBaggage() {
		Map<String, String> baggage = new HashMap<>();
		this.delegate.getCurrentBaggage().getEntries().forEach(entry -> baggage.put(entry.getKey(), entry.getValue()));
		return baggage;
	}

	@Override
	public Baggage getBaggage(String name) {
		io.opentelemetry.baggage.Baggage baggage = this.delegate.getCurrentBaggage();
		Entry entry = baggage.getEntries().stream().filter(e -> e.getKey().toLowerCase().equals(name.toLowerCase()))
				.findFirst().orElse(null);
		if (entry == null) {
			return null;
		}
		return new OtelBaggage(this.tracer, this.publisher, this.sleuthBaggageProperties, baggage, this.delegate, name,
				entry.getEntryMetadata());
	}

	@Override
	public Baggage createBaggage(String name) {
		List<String> remoteFieldsFields = this.sleuthBaggageProperties.getRemoteFields();
		boolean remoteField = remoteFieldsFields.stream().map(String::toLowerCase)
				.anyMatch(s -> s.equals(name.toLowerCase()));
		EntryMetadata.EntryTtl entryTtl = EntryMetadata.EntryTtl.NO_PROPAGATION;
		if (remoteField) {
			entryTtl = EntryMetadata.EntryTtl.UNLIMITED_PROPAGATION;
		}
		EntryMetadata entryMetadata = EntryMetadata.create(entryTtl);
		return new OtelBaggage(this.tracer, this.publisher, this.sleuthBaggageProperties,
				this.delegate.baggageBuilder().put(name, "", entryMetadata).build(), this.delegate, name,
				entryMetadata);
	}

}
