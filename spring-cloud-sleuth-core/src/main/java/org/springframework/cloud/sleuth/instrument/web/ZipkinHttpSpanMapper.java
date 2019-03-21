/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanTextMap;

/**
 * Mapper util for filter Zipkin compatible carrier values only from {@link SpanTextMap}
 *
 * @author Anton Kislitsyn
 */
class ZipkinHttpSpanMapper {

	static final String HEADER_DELIMITER = "-";
	static final String BAGGAGE_PREFIX = Span.SPAN_BAGGAGE_HEADER_PREFIX
			+ HEADER_DELIMITER;
	static final String URI_HEADER = "X-Span-Uri";

	private static Comparator<String> IGNORE_CASE_COMPARATOR = new Comparator<String>() {
		@Override
		public int compare(String o1, String o2) {
			return o1.toLowerCase().compareTo(o2.toLowerCase());
		}
	};

	/**
	 * Acceptable span fields
	 */
	private static final Set<String> SPAN_FIELDS;

	static {
		TreeSet<String> fields = new TreeSet<>(IGNORE_CASE_COMPARATOR);
		Collections.addAll(fields, Span.B3_NAME,
				Span.SPAN_FLAGS, Span.TRACE_ID_NAME, Span.SPAN_ID_NAME,
				Span.PROCESS_ID_NAME, Span.SPAN_NAME_NAME, Span.PARENT_ID_NAME,
				Span.SAMPLED_NAME, URI_HEADER);
		SPAN_FIELDS = Collections.unmodifiableSet(fields);
	}

	/**
	 * Create new Map of carrier values
	 */
	Map<String, String> convert(SpanTextMap textMap) {
		Map<String, String> carrier = new TreeMap<>(IGNORE_CASE_COMPARATOR);
		for (Map.Entry<String, String> entry : textMap) {
			if (isAcceptable(entry.getKey())) {
				carrier.put(entry.getKey(), entry.getValue());
			}
		}
		return Collections.unmodifiableMap(carrier);
	}

	private boolean isAcceptable(String key) {
		return SPAN_FIELDS.contains(key) || key.startsWith(BAGGAGE_PREFIX);
	}
}
