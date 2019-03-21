/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.BDDAssertions;
import org.junit.Test;

/**
 * @author Marcin Grzejszczak
 */
public class B3UtilsTest {

	@Test public void should_build_a_b3_string_without_parent_span() {
		Span span = Span.builder()
				.traceId(1L)
				.spanId(2L)
				.exportable(true).build();

		String b3String = B3Utils.toB3String(span);

		BDDAssertions.then(b3String).isEqualTo("0000000000000001-0000000000000002-1");
	}

	@Test public void should_build_a_b3_string_without_parent_span_for_unsampled() {
		Span span = Span.builder()
				.traceId(1L)
				.spanId(2L)
				.exportable(false).build();

		String b3String = B3Utils.toB3String(span);

		BDDAssertions.then(b3String).isEqualTo("0000000000000001-0000000000000002-0");
	}

	@Test public void should_build_a_b3_string_with_parent_span() {
		Span span = Span.builder()
				.traceId(1L)
				.spanId(2L)
				.parent(3L)
				.exportable(true).build();

		String b3String = B3Utils.toB3String(span);

		BDDAssertions.then(b3String).isEqualTo("0000000000000001-0000000000000002-1-0000000000000003");
	}

	@Test public void should_read_trace_id_from_b3_string() {
		Map<String, String> map = new HashMap<>();
		map.put("b3", "0000000000000001-0000000000000002-1-0000000000000003");

		String id = B3Utils.traceId("b3", "fallback", map);

		BDDAssertions.then(id).isEqualTo("0000000000000001");
	}

	@Test public void should_read_trace_id_from_fallback() {
		Map<String, String> map = new HashMap<>();
		map.put("fallback", "0000000000000001");

		String id = B3Utils.traceId("b3", "fallback", map);

		BDDAssertions.then(id).isEqualTo("0000000000000001");
	}

	@Test public void should_read_span_id_from_b3_string() {
		Map<String, String> map = new HashMap<>();
		map.put("b3", "0000000000000001-0000000000000002-1-0000000000000003");

		String id = B3Utils.spanId("b3", "fallback", map);

		BDDAssertions.then(id).isEqualTo("0000000000000002");
	}

	@Test public void should_read_span_id_from_fallback() {
		Map<String, String> map = new HashMap<>();
		map.put("fallback", "0000000000000002");

		String id = B3Utils.spanId("b3", "fallback", map);

		BDDAssertions.then(id).isEqualTo("0000000000000002");
	}

	@Test public void should_read_parent_id_from_b3_string() {
		Map<String, String> map = new HashMap<>();
		map.put("b3", "0000000000000001-0000000000000002-1-0000000000000003");

		String id = B3Utils.parentSpanId("b3", "fallback", map);

		BDDAssertions.then(id).isEqualTo("0000000000000003");
	}

	@Test public void should_read_parent_id_from_fallback() {
		Map<String, String> map = new HashMap<>();
		map.put("fallback", "0000000000000003");

		String id = B3Utils.parentSpanId("b3", "fallback", map);

		BDDAssertions.then(id).isEqualTo("0000000000000003");
	}

	@Test public void should_read_sampled_id_from_b3_string() {
		Map<String, String> map = new HashMap<>();
		map.put("b3", "0000000000000001-0000000000000002-1-0000000000000003");

		B3Utils.Sampled sampled = B3Utils.sampled("b3", "fallbackSampled",
				"fallbackFlags", map);

		BDDAssertions.then(sampled).isEqualTo(B3Utils.Sampled.SAMPLED);
	}

	@Test public void should_read_not_sampled_id_from_b3_string() {
		Map<String, String> map = new HashMap<>();
		map.put("b3", "0000000000000001-0000000000000002-0-0000000000000003");

		B3Utils.Sampled sampled = B3Utils.sampled("b3", "fallbackSampled",
				"fallbackFlags", map);

		BDDAssertions.then(sampled).isEqualTo(B3Utils.Sampled.NOT_SAMPLED);
	}

	@Test public void should_read_debug_sampled_id_from_b3_string() {
		Map<String, String> map = new HashMap<>();
		map.put("b3", "0000000000000001-0000000000000002-d-0000000000000003");

		B3Utils.Sampled sampled = B3Utils.sampled("b3", "fallbackSampled",
				"fallbackFlags", map);

		BDDAssertions.then(sampled).isEqualTo(B3Utils.Sampled.DEBUG);
	}

	@Test public void should_read_sampled_id_from_fallback() {
		Map<String, String> map = new HashMap<>();
		map.put("fallbackSampled", "1");

		B3Utils.Sampled sampled = B3Utils.sampled("b3", "fallbackSampled",
				"fallbackFlags", map);

		BDDAssertions.then(sampled).isEqualTo(B3Utils.Sampled.SAMPLED);
	}

	@Test public void should_read_not_sampled_id_from_fallback() {
		Map<String, String> map = new HashMap<>();
		map.put("fallbackSampled", "0");

		B3Utils.Sampled sampled = B3Utils.sampled("b3", "fallbackSampled",
				"fallbackFlags", map);

		BDDAssertions.then(sampled).isEqualTo(B3Utils.Sampled.NOT_SAMPLED);
	}

	@Test public void should_return_is_sampled_if_sampled() {
		BDDAssertions.then(B3Utils.Sampled.SAMPLED.isSampled()).isTrue();
	}

	@Test public void should_return_is_not_sampled_if_not_sampled() {
		BDDAssertions.then(B3Utils.Sampled.NOT_SAMPLED.isSampled()).isFalse();
	}

	@Test public void should_return_is_sampled_if_debug() {
		BDDAssertions.then(B3Utils.Sampled.DEBUG.isSampled()).isTrue();
	}

	@Test public void should_read_debug_id_from_fallback() {
		Map<String, String> map = new HashMap<>();
		map.put("fallbackFlags", "1");

		B3Utils.Sampled sampled = B3Utils.sampled("b3", "fallbackSampled",
				"fallbackFlags", map);

		BDDAssertions.then(sampled).isEqualTo(B3Utils.Sampled.DEBUG);
	}
}