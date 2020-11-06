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

package org.springframework.cloud.sleuth.otel.propagation;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.EntryMetadata;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.api.BaggageManager;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.context.ApplicationEventPublisher;

class BaggageTextMapPropagator implements TextMapPropagator {

	private static final Log log = LogFactory.getLog(BaggageTextMapPropagator.class);

	private final SleuthBaggageProperties properties;

	private final BaggageManager baggageManager;

	private final ApplicationEventPublisher publisher;

	BaggageTextMapPropagator(SleuthBaggageProperties properties, BaggageManager baggageManager,
			ApplicationEventPublisher publisher) {
		this.properties = properties;
		this.baggageManager = baggageManager;
		this.publisher = publisher;
	}

	@Override
	public List<String> fields() {
		return this.properties.getRemoteFields();
	}

	@Override
	public <C> void inject(Context context, C c, Setter<C> setter) {
		List<Map.Entry<String, String>> baggageEntries = applicableBaggageEntries(c);
		baggageEntries.forEach(e -> setter.set(c, e.getKey(), e.getValue()));
	}

	private <C> List<Map.Entry<String, String>> applicableBaggageEntries(C c) {
		Map<String, String> allBaggage = this.baggageManager.getAllBaggage();
		List<String> lowerCaseKeys = this.properties.getRemoteFields().stream().map(String::toLowerCase)
				.collect(Collectors.toList());
		return allBaggage.entrySet().stream().filter(e -> lowerCaseKeys.contains(e.getKey().toLowerCase()))
				.collect(Collectors.toList());
	}

	@Override
	public <C> Context extract(Context context, C c, Getter<C> getter) {
		Map<String, String> baggageEntries = this.properties.getRemoteFields().stream()
				.map(s -> new AbstractMap.SimpleEntry<>(s, getter.get(c, s))).filter(e -> e.getValue() != null)
				.collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue()));
		Baggage.Builder builder = Baggage.builder().setParent(context);
		// TODO: [OTEL] magic string
		baggageEntries.forEach((key, value) -> builder.put(key, value, EntryMetadata.create("propagation=unlimited")));
		Baggage baggage = builder.build();
		Context withBaggage = context.with(baggage);
		if (log.isDebugEnabled()) {
			log.debug("Will propagate new baggage context for entries " + baggageEntries);
		}
		return withBaggage;
	}

}
