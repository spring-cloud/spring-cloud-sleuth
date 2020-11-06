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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.brave.bridge.BraveBaggageManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.cloud.sleuth.brave.propagation.W3CPropagation.TRACE_PARENT;

/**
 * Test taken from OpenTelemetry.
 */
class W3CPropagationTest {

	private static final String TRACE_STATE = "tracestate";

	private static final String TRACE_ID_BASE16 = "ff000000000000000000000000000041";

	private static final String SPAN_ID_BASE16 = "ff00000000000041";

	private static final boolean SAMPLED_TRACE_OPTIONS = true;

	private static final String TRACEPARENT_HEADER_SAMPLED = "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01";

	private static final String TRACEPARENT_HEADER_NOT_SAMPLED = "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-00";

	private static final Propagation.Getter<Map<String, String>, String> getter = Map::get;

	private static final String TRACESTATE_NOT_DEFAULT_ENCODING_WITH_SPACES = "bar=baz   ,    foo=bar";

	private final W3CPropagation w3CPropagation = new W3CPropagation(new BraveBaggageManager(),
			new SleuthBaggageProperties());

	@Test
	void inject_NullCarrierUsage() {
		final Map<String, String> carrier = new LinkedHashMap<>();
		TraceContext traceContext = sampledTraceContext().build();
		w3CPropagation.injector((ignored, key, value) -> carrier.put(key, value)).inject(traceContext, null);
		assertThat(carrier).containsExactly(entry(TRACE_PARENT, TRACEPARENT_HEADER_SAMPLED));
	}

	@NotNull
	private TraceContext.Builder sampledTraceContext() {
		return TraceContext.newBuilder().sampled(SAMPLED_TRACE_OPTIONS)
				.spanId(BigendianEncoding.longFromBase16String("ff00000000000041"))
				.traceIdHigh(BigendianEncoding.longFromBase16String("ff00000000000000"))
				.traceId(BigendianEncoding.longFromBase16String("0000000000000041"));
	}

	@Test
	void inject_SampledContext() {
		final Map<String, String> carrier = new LinkedHashMap<>();
		TraceContext traceContext = sampledTraceContext().build();
		w3CPropagation.injector((ignored, key, value) -> carrier.put(key, value)).inject(traceContext, carrier);
		assertThat(carrier).containsExactly(entry(TRACE_PARENT, TRACEPARENT_HEADER_SAMPLED));
	}

	@Test
	void inject_NotSampledContext() {
		final Map<String, String> carrier = new LinkedHashMap<>();
		TraceContext traceContext = notSampledTraceContext().build();
		w3CPropagation.injector((ignored, key, value) -> carrier.put(key, value)).inject(traceContext, carrier);
		assertThat(carrier).containsExactly(entry(TRACE_PARENT, TRACEPARENT_HEADER_NOT_SAMPLED));
	}

	@Test
	void extract_Nothing() {
		// Context remains untouched.
		assertThat(w3CPropagation.extractor(getter).extract(Collections.emptyMap()))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_SampledContext() {
		Map<String, String> carrier = new LinkedHashMap<>();
		carrier.put(TRACE_PARENT, TRACEPARENT_HEADER_SAMPLED);
		assertThat(w3CPropagation.extractor(getter).extract(carrier).context()).isEqualTo(sharedTraceContext().build());
	}

	@Test
	void extract_NullCarrier() {
		Map<String, String> carrier = new LinkedHashMap<>();
		carrier.put(TRACE_PARENT, TRACEPARENT_HEADER_SAMPLED);
		assertThat(w3CPropagation.extractor((request, key) -> carrier.get(key)).extract(null).context())
				.isEqualTo(sharedTraceContext().build());
	}

	@Test
	void extract_NotSampledContext() {
		Map<String, String> carrier = new LinkedHashMap<>();
		carrier.put(TRACE_PARENT, TRACEPARENT_HEADER_NOT_SAMPLED);
		assertThat(w3CPropagation.extractor(getter).extract(carrier).context())
				.isEqualTo(notSampledTraceContext().shared(true).build());
	}

	@Test
	void extract_NotSampledContext_NextVersion() {
		Map<String, String> carrier = new LinkedHashMap<>();
		carrier.put(TRACE_PARENT, "01-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-00-02");
		assertThat(w3CPropagation.extractor(getter).extract(carrier).context()).isEqualTo(sharedTraceContext().build());
	}

	@Test
	void extract_NotSampledContext_EmptyTraceState() {
		Map<String, String> carrier = new LinkedHashMap<>();
		carrier.put(TRACE_PARENT, TRACEPARENT_HEADER_NOT_SAMPLED);
		carrier.put(TRACE_STATE, "");
		assertThat(w3CPropagation.extractor(getter).extract(carrier).context())
				.isEqualTo(notSampledTraceContext().shared(true).build());
	}

	@NotNull
	private TraceContext.Builder notSampledTraceContext() {
		return sampledTraceContext().sampled(false);
	}

	@Test
	void extract_NotSampledContext_TraceStateWithSpaces() {
		Map<String, String> carrier = new LinkedHashMap<>();
		carrier.put(TRACE_PARENT, TRACEPARENT_HEADER_NOT_SAMPLED);
		carrier.put(TRACE_STATE, TRACESTATE_NOT_DEFAULT_ENCODING_WITH_SPACES);
		assertThat(w3CPropagation.extractor(getter).extract(carrier).context())
				.isEqualTo(sharedTraceContext().sampled(false).build());
	}

	@Test
	void extract_EmptyHeader() {
		Map<String, String> invalidHeaders = new LinkedHashMap<>();
		invalidHeaders.put(TRACE_PARENT, "");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_InvalidTraceId() {
		Map<String, String> invalidHeaders = new LinkedHashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + "abcdefghijklmnopabcdefghijklmnop" + "-" + SPAN_ID_BASE16 + "-01");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_InvalidTraceId_Size() {
		Map<String, String> invalidHeaders = new LinkedHashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "00-" + SPAN_ID_BASE16 + "-01");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_InvalidSpanId() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + "abcdefghijklmnop" + "-01");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_InvalidSpanId_Size() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "00-01");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_InvalidTraceFlags() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-gh");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_InvalidTraceFlags_Size() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-0100");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_InvalidTracestate_EntriesDelimiter() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
		invalidHeaders.put(TRACE_STATE, "foo=bar;test=test");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders).context())
				.isEqualTo(sharedTraceContext().build());
	}

	@NotNull
	private TraceContext.Builder sharedTraceContext() {
		return sampledTraceContext().shared(true);
	}

	@Test
	void extract_InvalidTracestate_KeyValueDelimiter() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
		invalidHeaders.put(TRACE_STATE, "foo=bar,test-test");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders).context())
				.isEqualTo(sharedTraceContext().build());
	}

	@Test
	void extract_InvalidTracestate_OneString() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
		invalidHeaders.put(TRACE_STATE, "test-test");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders).context())
				.isEqualTo(sampledTraceContext().shared(true).build());
	}

	@Test
	void extract_InvalidVersion_ff() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "ff-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-01");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_InvalidTraceparent_extraTrailing() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "00-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-00-01");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders))
				.isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

	@Test
	void extract_ValidTraceparent_nextVersion_extraTrailing() {
		Map<String, String> invalidHeaders = new HashMap<>();
		invalidHeaders.put(TRACE_PARENT, "01-" + TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-00-01");
		assertThat(w3CPropagation.extractor(getter).extract(invalidHeaders).context())
				.isEqualTo(sharedTraceContext().build());
	}

	@Test
	void fieldsList() {
		assertThat(w3CPropagation.keys()).containsExactly(TRACE_PARENT, TRACE_STATE);
	}

	@Test
	void headerNames() {
		assertThat(TRACE_PARENT).isEqualTo("traceparent");
		assertThat(TRACE_STATE).isEqualTo("tracestate");
	}

	@Test
	void extract_emptyCarrier() {
		Map<String, String> emptyHeaders = new HashMap<>();
		assertThat(w3CPropagation.extractor(getter).extract(emptyHeaders)).isSameAs(TraceContextOrSamplingFlags.EMPTY);
	}

}
