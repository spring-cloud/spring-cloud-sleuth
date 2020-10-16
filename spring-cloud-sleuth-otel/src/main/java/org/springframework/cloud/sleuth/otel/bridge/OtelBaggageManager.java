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
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Context;
import io.opentelemetry.baggage.BaggageUtils;
import io.opentelemetry.baggage.Entry;
import io.opentelemetry.baggage.EntryMetadata;
import io.opentelemetry.trace.Tracer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.api.Baggage;
import org.springframework.cloud.sleuth.api.BaggageManager;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;

public class OtelBaggageManager implements BaggageManager, ApplicationListener<OtelBaggage.BaggageScopeEnded> {

	private static final Log log = LogFactory.getLog(OtelBaggageManager.class);

	private final io.opentelemetry.trace.Tracer tracer;

	private final io.opentelemetry.baggage.BaggageManager delegate;

	private final SleuthBaggageProperties sleuthBaggageProperties;

	private final ApplicationEventPublisher publisher;

	AtomicReference<Context> context = new AtomicReference<>(Context.ROOT);

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
		currentBaggage().getEntries().forEach(entry -> baggage.put(entry.getKey(), entry.getValue()));
		return baggage;
	}

	private io.opentelemetry.baggage.Baggage currentBaggage() {
		return BaggageUtils.getBaggage(this.context.get());
	}

	@Override
	public Baggage getBaggage(String name) {
		io.opentelemetry.baggage.Baggage baggage = currentBaggage();
		Entry entry = entryForName(name, baggage);
		if (entry == null) {
			return null;
		}
		return otelBaggage(name, baggage, entry);
	}

	private Entry entryForName(String name, io.opentelemetry.baggage.Baggage baggage) {
		return baggage.getEntries().stream().filter(e -> e.getKey().toLowerCase().equals(name.toLowerCase()))
				.findFirst().orElse(null);
	}

	private Baggage otelBaggage(String name, io.opentelemetry.baggage.Baggage baggage, Entry entry) {
		return new OtelBaggage(this.tracer, this.publisher, this.sleuthBaggageProperties, baggage, this.delegate,
				this.context, name, entry.getEntryMetadata());
	}

	@Override
	public Baggage createBaggage(String name) {
		return baggageWithValue(name, "");
	}

	private Baggage baggageWithValue(String name, String value) {
		List<String> remoteFieldsFields = this.sleuthBaggageProperties.getRemoteFields();
		boolean remoteField = remoteFieldsFields.stream().map(String::toLowerCase)
				.anyMatch(s -> s.equals(name.toLowerCase()));
		EntryMetadata.EntryTtl entryTtl = EntryMetadata.EntryTtl.NO_PROPAGATION;
		if (remoteField) {
			entryTtl = EntryMetadata.EntryTtl.UNLIMITED_PROPAGATION;
		}
		EntryMetadata entryMetadata = EntryMetadata.create(entryTtl);
		io.opentelemetry.baggage.Baggage baggage = this.delegate.baggageBuilder().put(name, value, entryMetadata)
				.build();
		this.context.set(BaggageUtils.withBaggage(baggage, this.context.get()));
		return new OtelBaggage(this.tracer, this.publisher, this.sleuthBaggageProperties, baggage, this.delegate,
				this.context, name, entryMetadata);
	}

	@Override
	public void onApplicationEvent(OtelBaggage.BaggageScopeEnded event) {
		if (log.isTraceEnabled()) {
			log.trace("Baggage scope ended");
		}
		this.context.set(Context.ROOT);
	}

}