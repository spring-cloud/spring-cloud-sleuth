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

import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

/**
 * Utility class to operate on the B3 dash separated string header
 *
 * @author Marcin Grzejszczak
 * @since 1.3.5
 */
public final class B3Utils {

	/**
	 * Converts the span to a B3 String
	 */
	public static String toB3String(Span span) {
		String traceId = span.traceIdString();
		String spanId = Span.idToHex(span.getSpanId());
		boolean sampled = span.isExportable();
		List<Long> parents = span.getParents();
		StringBuilder b3 = new StringBuilder()
				.append(traceId).append("-")
				.append(spanId).append("-")
				.append(sampled ? Span.SPAN_SAMPLED : Span.SPAN_NOT_SAMPLED);
		if (parents != null && !parents.isEmpty()) {
			b3 = b3.append("-").append(Span.idToHex(parents.get(0)));
		}
		return b3.toString();
	}

	/**
	 * Tries to retrieve trace id from b3 header. Falls back to standard header
	 * if there's nothing there in b3 header
	 */
	public static String traceId(String b3HeaderName,
			String fallbackHeaderName, Map<String, String> carrier) {
		String b3 = carrier.get(b3HeaderName);
		if (StringUtils.hasText(b3)) {
			String[] split = b3.split("-");
			if (split.length > 1) {
				return split[0];
			}
		}
		return carrier.get(fallbackHeaderName);
	}

	/**
	 * Tries to retrieve span id from b3 header. Falls back to standard header
	 * if there's nothing there in b3 header
	 */
	public static String spanId(String b3HeaderName,
			String fallbackHeaderName, Map<String, String> carrier) {
		String b3 = carrier.get(b3HeaderName);
		if (StringUtils.hasText(b3)) {
			String[] split = b3.split("-");
			if (split.length > 1) {
				return split[1];
			}
		}
		return carrier.get(fallbackHeaderName);
	}

	/**
	 * Tries to retrieve parent span id from b3 header. Falls back to standard header
	 * if there's nothing there in b3 header
	 */
	public static String parentSpanId(String b3HeaderName,
			String fallbackHeaderName, Map<String, String> carrier) {
		String b3 = carrier.get(b3HeaderName);
		if (StringUtils.hasText(b3)) {
			String[] split = b3.split("-");
			if (split.length == 4) {
				return split[3];
			}
		}
		return carrier.get(fallbackHeaderName);
	}

	/**
	 * Tries to retrieve sample flag from b3 header. Falls back to standard span flag header
	 * and sampled header, if there's nothing there in b3 header
	 */
	public static Sampled sampled(String b3HeaderName,
			String fallbackSampledHeaderName, String fallbackFlagsHeaderName,
			Map<String, String> carrier) {
		String b3 = carrier.get(b3HeaderName);
		if (StringUtils.hasText(b3)) {
			String[] split = b3.split("-");
			if (split.length > 2) {
				return Sampled.from(split[2]);
			}
		}
		String fallbackFlag = carrier.get(fallbackFlagsHeaderName);
		if (Span.SPAN_SAMPLED.equals(fallbackFlag)) {
			return Sampled.DEBUG;
		}
		return Sampled.from(carrier.get(fallbackSampledHeaderName));
	}

	public enum Sampled {
		SAMPLED('1'), NOT_SAMPLED('0'), DEBUG('d');

		final char sampledChar;

		Sampled(char sampledChar) {
			this.sampledChar = sampledChar;
		}

		static Sampled from(String value) {
			if (StringUtils.hasText(value)) {
				switch (value) {
					case "1" : return SAMPLED;
					case "0" : return NOT_SAMPLED;
					case "d" : return DEBUG;
				}
			}
			return null;
		}

		public boolean isSampled() {
			return this == SAMPLED || this == DEBUG;
		}

		@Override public String toString() {
			return String.valueOf(this.sampledChar);
		}
	}

}
