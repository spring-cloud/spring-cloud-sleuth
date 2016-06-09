/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.sleuth;

import java.io.IOException;
import java.util.Collections;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 * @author Rob Winch
 * @author Spencer Gibb
 */
public class SpanTests {

	@Test
	public void should_convert_long_to_hex_string() throws Exception {
		long someLong = 123123L;

		String hexString = Span.idToHex(someLong);

		then(hexString).isEqualTo("1e0f3");
	}

	@Test
	public void should_convert_hex_string_to_long() throws Exception {
		String hexString = "1e0f3";

		long someLong = Span.hexToId(hexString);

		then(someLong).isEqualTo(123123L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void should_throw_exception_when_null_string_is_to_be_converted_to_long() throws Exception {
		Span.hexToId(null);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void getAnnotationsReadOnly() {
		Span span = new Span(1, 2, "http:name", 1L, Collections.<Long>emptyList(), 2L, true,
				true, "process");

		span.tags().put("a", "b");
	}

	@Test(expected = UnsupportedOperationException.class)
	public void getTimelineAnnotationsReadOnly() {
		Span span = new Span(1, 2, "http:name", 1L, Collections.<Long>emptyList(), 2L, true,
				true, "process");

		span.logs().add(new Log(1, "1"));
	}

	@Test public void should_properly_serialize_object() throws JsonProcessingException {
		Span span = new Span(1, 2, "http:name", 1L,
				Collections.<Long>emptyList(), 2L, true, true, "process");
		ObjectMapper objectMapper = new ObjectMapper();

		String serializedName = objectMapper.writeValueAsString(span);

		then(serializedName).isNotEmpty();
	}

	@Test public void should_properly_serialize_logs() throws IOException {
		Span span = new Span(1, 2, "http:name", 1L,
				Collections.<Long>emptyList(), 2L, true, true, "process");
		span.logEvent("cs");

		ObjectMapper objectMapper = new ObjectMapper();

		String serialized = objectMapper.writeValueAsString(span);
		Span deserialized = objectMapper.readValue(serialized, Span.class);

		then(deserialized.logs())
				.isEqualTo(span.logs());
	}

	@Test public void should_properly_serialize_tags() throws IOException {
		Span span = new Span(1, 2, "http:name", 1L,
				Collections.<Long>emptyList(), 2L, true, true, "process");
		span.tag("calculatedTax", "100");

		ObjectMapper objectMapper = new ObjectMapper();

		String serialized = objectMapper.writeValueAsString(span);
		Span deserialized = objectMapper.readValue(serialized, Span.class);

		then(deserialized.tags())
				.isEqualTo(span.tags());
	}
}
