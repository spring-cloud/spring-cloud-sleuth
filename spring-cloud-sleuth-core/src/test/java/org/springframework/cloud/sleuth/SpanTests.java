/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.sleuth;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.assertThat;
import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Marcin Grzejszczak
 * @author Rob Winch
 * @author Spencer Gibb
 */
public class SpanTests {
	Span span = Span.builder().begin(1).end(2).name("http:name").traceId(1L).spanId(2L)
			.remote(true).exportable(true).processId("process").build();

	@Test
	public void should_consider_trace_and_span_id_on_equals_and_hashCode() throws Exception {
		Span span = Span.builder().traceId(1L).spanId(2L).build();
		Span differentSpan = Span.builder().traceId(1L).spanId(3L).build();
		Span withParent =Span.builder().traceId(1L).spanId(2L).parent(3L).build();

		then(span).isEqualTo(withParent);
		then(span).isNotEqualTo(differentSpan);
		then(span.hashCode()).isNotEqualTo(differentSpan.hashCode());
	}

	@Test
	public void should_have_toString_with_identifiers_and_export() throws Exception {
		span = Span.builder().traceId(1L).spanId(2L).parent(3L).name("foo").build();

		then(span).hasToString(
				"[Trace: 0000000000000001, Span: 0000000000000002, Parent: 0000000000000003, exportable:true]");
	}

	@Test
	public void should_have_toString_with_128bit_trace_id() throws Exception {
		span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).parent(4L).build();

		then(span.toString()).startsWith("[Trace: 00000000000000010000000000000002,");
	}

	@Test
	public void should_consider_128bit_trace_and_span_id_on_equals_and_hashCode() throws Exception {
		Span span = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).build();
		Span differentSpan = Span.builder().traceIdHigh(2L).traceId(2L).spanId(3L).build();
		Span withParent = Span.builder().traceIdHigh(1L).traceId(2L).spanId(3L).parent(4L).build();

		then(span).isEqualTo(withParent);
		then(span).isNotEqualTo(differentSpan);
		then(span.hashCode()).isNotEqualTo(differentSpan.hashCode());
	}

	@Test
	public void should_convert_long_to_16_character_hex_string() throws Exception {
		long someLong = 123123L;

		String hexString = Span.idToHex(someLong);

		then(hexString).isEqualTo("000000000001e0f3");
	}

	@Test
	public void should_convert_hex_string_to_long() throws Exception {
		String hexString = "1e0f3";

		long someLong = Span.hexToId(hexString);

		then(someLong).isEqualTo(123123L);
	}

	@Test
	public void should_convert_lower_64bits_of_hex_string_to_long() throws Exception {
		String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
		String lower64Bits = "48485a3953bb6124";

		long someLong = Span.hexToId(hex128Bits);

		then(someLong).isEqualTo(Span.hexToId(lower64Bits));
	}

	@Test
	public void should_convert_offset_64bits_of_hex_string_to_long() throws Exception {
		String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
		String high64Bits = "463ac35c9f6413ad";

		long someLong = Span.hexToId(hex128Bits, 0);

		then(someLong).isEqualTo(Span.hexToId(high64Bits));
	}

	@Test
	public void should_writeFixedLength64BitTraceId() throws Exception {
		String traceId = span.traceIdString();

		then(traceId).isEqualTo("0000000000000001");
	}

	@Test
	public void should_writeFixedLength128BitTraceId() throws Exception {
		String high128Bits = "463ac35c9f6413ad";
		String low64Bits = "48485a3953bb6124";

		span = Span.builder().traceIdHigh(Span.hexToId(high128Bits)).traceId(Span.hexToId(low64Bits))
				.spanId(1L).name("foo").build();

		String traceId = span.traceIdString();

		then(traceId).isEqualTo(high128Bits + low64Bits);
	}

	@Test(expected = IllegalArgumentException.class)
	public void should_throw_exception_when_null_string_is_to_be_converted_to_long() throws Exception {
		Span.hexToId(null);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void getAnnotationsReadOnly() {
		span.tags().put("a", "b");
	}

	@Test(expected = UnsupportedOperationException.class)
	public void getTimelineAnnotationsReadOnly() {
		span.logs().add(new Log(1, "1"));
	}

	@Test public void should_properly_serialize_object() throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();

		String serializedName = objectMapper.writeValueAsString(span);

		then(serializedName).isNotEmpty();
	}

	@Test public void should_properly_serialize_logs() throws IOException {
		span.logEvent("cs");

		ObjectMapper objectMapper = new ObjectMapper();

		String serialized = objectMapper.writeValueAsString(span);
		Span deserialized = objectMapper.readValue(serialized, Span.class);

		then(deserialized.logs())
				.isEqualTo(span.logs());
	}

	@Test public void can_log_with_generated_timestamp() throws IOException {
		long beforeLog = System.currentTimeMillis();

		span.logEvent("event1");

		assertThat(span.logs().get(0).getTimestamp()).isGreaterThanOrEqualTo(beforeLog);
	}

	@Test public void can_log_with_specified_timestamp() throws IOException {
		span.logEvent(1L, "event1");

		then(span.logs().get(0).getTimestamp()).isEqualTo(1L);
	}

	@Test public void should_properly_serialize_tags() throws IOException {
		span.tag("calculatedTax", "100");

		ObjectMapper objectMapper = new ObjectMapper();

		String serialized = objectMapper.writeValueAsString(span);
		Span deserialized = objectMapper.readValue(serialized, Span.class);

		then(deserialized.tags())
				.isEqualTo(span.tags());
	}

	@Test(expected = IllegalArgumentException.class)
	public void should_throw_exception_when_converting_invalid_hex_value() {
		Span.hexToId("invalid");
	}

	/** When going over a transport like spring-cloud-stream, we must retain the precise duration. */
	@Test public void shouldSerializeDurationMicros() throws IOException {
		Span span = Span.builder().traceId(1L).name("http:parent").build();
		span.stop();

		assertThat(span.getAccumulatedMicros())
				.isGreaterThan(0L); // sanity check

		ObjectMapper objectMapper = new ObjectMapper();

		String serialized = objectMapper.writeValueAsString(span);
		assertThat(serialized)
				.contains("\"durationMicros\"");

		Span deserialized = objectMapper.readValue(serialized, Span.class);

		assertThat(deserialized.getAccumulatedMicros())
				.isEqualTo(span.getAccumulatedMicros());
	}

	// Duration of 0 is confusing to plot and can be misinterpreted as null
	@Test public void getAccumulatedMicros_roundsUpToOneWhenRunning() throws IOException {
		AtomicLong nanoTime = new AtomicLong();

		// starts the span, recording its initial tick as zero
		Span span = new Span(Span.builder().name("http:name").traceId(1L).spanId(2L)) {
			@Override long nanoTime() {
				return nanoTime.get();
			}
		};

		// When only 100 nanoseconds passed
		nanoTime.set(100L);

		// We round so that we don't confuse "not started" with a short span.
		assertThat(span.getAccumulatedMicros()).isEqualTo(1L);
	}

	// Duration of 0 is confusing to plot and can be misinterpreted as null
	@Test public void getAccumulatedMicros_roundsUpToOneWhenStopped() throws IOException {
		AtomicLong nanoTime = new AtomicLong();

		// starts the span, recording its initial tick as zero
		Span span = new Span(Span.builder().name("http:name").traceId(1L).spanId(2L)) {
			@Override long nanoTime() {
				return nanoTime.get();
			}
		};

		// When only 100 nanoseconds passed
		nanoTime.set(100L);
		span.stop();

		// We round so that we don't confuse "not started" with a short span.
		assertThat(span.getAccumulatedMicros()).isEqualTo(1L);
	}

	@Test
	public void should_build_a_span_from_provided_span() throws IOException {
		Span span = builder().build();

		Span builtSpan = Span.builder().from(span).build();

		assertThat(builtSpan).isEqualTo(span);
	}

	@Test
	public void should_build_a_continued_span_from_provided_span() throws IOException {
		Span span = builder().tag("foo", "bar").build();
		Span savedSpan = builder().tag("foo2", "bar2").build();
		Span builtSpan = new Span(span, savedSpan);

		span.tag("foo2", "bar2");

		assertThat(builtSpan).isEqualTo(span);
	}

	@Test
	public void should_convert_a_span_to_builder() throws IOException {
		Span.SpanBuilder spanBuilder = builder();
		Span span = spanBuilder.build();

		Span span2 = span.toBuilder().build();

		assertThat(span).isEqualTo(span2);
	}

	private Span.SpanBuilder builder() {
		return Span.builder().name("http:name").traceId(1L).spanId(2L).parent(3L)
				.begin(1L).end(2L).traceId(3L).exportable(true).parent(4L)
				.baggage("foo", "bar")
				.remote(true).shared(true).tag("tag", "tag").log(new Log(System.currentTimeMillis(), "log"));
	}
}
