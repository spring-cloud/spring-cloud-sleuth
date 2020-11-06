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

package org.springframework.cloud.sleuth.brave.propagation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import brave.baggage.BaggageField;
import brave.internal.baggage.BaggageFields;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.brave.bridge.BraveBaggageManager;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test taken from OpenTelemetry.
 */
class W3CBaggagePropagatorTest {

	W3CBaggagePropagator propagator = new W3CBaggagePropagator(new BraveBaggageManager(), sleuthBaggageProperties());

	SleuthBaggageProperties sleuthBaggageProperties() {
		SleuthBaggageProperties properties = new SleuthBaggageProperties();
		properties.setRemoteFields(Arrays.asList("nometa", "meta", "key", "key1", "key2"));
		return properties;
	}

	@Test
	void fields() {
		assertThat(propagator.keys()).containsExactly("baggage");
	}

	@Test
	void extract_noBaggageHeader() {
		TraceContextOrSamplingFlags context = context();
		Map<String, String> carrier = new HashMap<>();

		TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

		assertThat(contextWithBaggage).isSameAs(context);
	}

	@Test
	void extract_emptyBaggageHeader() {
		TraceContextOrSamplingFlags context = context();
		Map<String, String> carrier = new HashMap<>();
		carrier.put("baggage", "");

		TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

		assertThat(contextWithBaggage).isSameAs(context);
	}

	@Test
	void extract_singleEntry() {
		TraceContextOrSamplingFlags context = context();
		Map<String, String> carrier = new HashMap<>();
		carrier.put("baggage", "key=value");

		TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

		Map<String, String> baggageEntries = BaggageField.getAllValues(contextWithBaggage);
		assertThat(baggageEntries).hasSize(1).containsEntry("key", "value");
	}

	@NotNull
	private TraceContextOrSamplingFlags context() {
		return TraceContextOrSamplingFlags
				.create(TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build());
	}

	@NotNull
	private TraceContext.Builder contextBuilder() {
		return TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true);
	}

	@Test
	void extract_multiEntry() {
		TraceContextOrSamplingFlags context = context();
		Map<String, String> carrier = new HashMap<>();
		carrier.put("baggage", "key1=value1,key2=value2");

		TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

		Map<String, String> baggageEntries = BaggageField.getAllValues(contextWithBaggage);
		assertThat(baggageEntries).hasSize(2).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	@Test
	void extract_duplicateKeys() {
		TraceContextOrSamplingFlags context = context();
		Map<String, String> carrier = new HashMap<>();
		carrier.put("baggage", "key=value1,key=value2");

		TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

		Map<String, String> baggageEntries = BaggageField.getAllValues(contextWithBaggage);
		assertThat(baggageEntries).hasSize(1).containsEntry("key", "value2");
	}

	@Test
	void extract_fullComplexities() {
		TraceContextOrSamplingFlags context = context();
		Map<String, String> carrier = new HashMap<>();
		carrier.put("baggage",
				"key1= value1; metadata-key = value; othermetadata, " + "key2 =value2 , key3 =\tvalue3 ; ");

		TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

		Map<String, String> baggageEntries = BaggageField.getAllValues(contextWithBaggage);
		assertThat(baggageEntries).hasSize(3).containsEntry("key1", "value1").containsEntry("key2", "value2")
				.containsEntry("key3", "value3");
	}

	/**
	 * It would be cool if we could replace this with a fuzzer to generate tons of crud
	 * data, to make sure we don't blow up with it.
	 */
	@Test
	@Disabled("We don't support additional data")
	void extract_invalidHeader() {
		TraceContextOrSamplingFlags context = context();
		Map<String, String> carrier = new HashMap<>();
		carrier.put("baggage", "key1= v;alsdf;-asdflkjasdf===asdlfkjadsf ,,a sdf9asdf-alue1; metadata-key = "
				+ "value; othermetadata, key2 =value2 , key3 =\tvalue3 ; ");

		TraceContextOrSamplingFlags contextWithBaggage = propagator.contextWithBaggage(carrier, context, Map::get);

		Map<String, String> baggageEntries = BaggageField.getAllValues(contextWithBaggage);
		assertThat(baggageEntries).isEmpty();
	}

	@Test
	void inject_noBaggage() {
		TraceContextOrSamplingFlags context = context();
		Map<String, String> carrier = new HashMap<>();

		propagator.injector((Propagation.Setter<Map<String, String>, String>) Map::put).inject(context.context(),
				carrier);

		assertThat(carrier).isEmpty();
	}

	@Test
	void inject() {
		TraceContextOrSamplingFlags.Builder builder = context().toBuilder();
		BaggageField nometa = BaggageField.create("nometa");
		BaggageField meta = BaggageField.create("meta");
		builder.addExtra(BaggageFields.newFactory(Arrays.asList(nometa, meta), 10).create());
		TraceContextOrSamplingFlags context = builder.build();
		nometa.updateValue(context, "nometa-value");
		meta.updateValue(context, "meta-value;somemetadata; someother=foo");
		Map<String, String> carrier = new HashMap<>();

		propagator.injector((Propagation.Setter<Map<String, String>, String>) Map::put).inject(context.context(),
				carrier);

		assertThat(carrier).containsExactlyInAnyOrderEntriesOf(
				singletonMap("baggage", "nometa=nometa-value,meta=meta-value;somemetadata; someother=foo"));
	}

}
