/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
public class SpanNameTest {

	@Test
	public void should_return_span_name_wihout_fragment_when_no_fragment_is_passed() {
		then(new SpanName("component", "address").toString())
				.isEqualTo("component:address");
	}

	@Test
	public void should_return_span_name_together_with_fragment_when_fragment_was_passed() {
		then(new SpanName("component", "address", "fragment").toString())
				.isEqualTo("component:address#fragment");
	}

	@Test
	public void should_return_empty_string_if_there_is_no_span_name() {
		then(SpanName.NO_NAME.toString()).isEmpty();
	}

	@Test
	public void should_build_span_from_valid_string_name() {
		then(SpanName.fromString("http:/a/b/c#async"))
				.hasComponentEqualTo("http")
				.hasAddressEqualTo("/a/b/c")
				.hasFragmentEqualTo("async");
	}

	@Test
	public void should_build_span_from_a_string_with_missing_protocol() {
		then(SpanName.fromString("/a/b/c").toString())
				.isEqualTo("unknown:/a/b/c");
	}

	@Test
	public void should_build_span_from_a_string_name_with_many_separators() {
		then(SpanName.fromString("http:/a/b:/c#async:asd=123#4444"))
				.hasComponentEqualTo("http")
				.hasAddressEqualTo("/a/b:/c")
				.hasFragmentEqualTo("async:asd=123#4444");
	}

	@Test public void should_properly_serialize_object() throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();

		String serializedName = objectMapper
				.writeValueAsString(SpanName.fromString("async:foo#method=bar"));

		then(serializedName).isEqualTo(
				"{\"component\":\"async\",\"address\":\"foo\",\"fragment\":\"method=bar\"}");
	}
}