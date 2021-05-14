/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.docs;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * In order to turn on the assertions you need to either turn on the
 * {@code spring.cloud.sleuth.assertions.enabled} system property or
 * {@code SPRING_CLOUD_SLEUTH_ASSERTIONS_ENABLED} environment variable.
 */
final class DocumentedSpanAssertions {

	static boolean SLEUTH_SPAN_ASSERTIONS_ON = Boolean.parseBoolean(System.getProperty(
			"spring.cloud.sleuth.assertions.enabled", System.getenv("SPRING_CLOUD_SLEUTH_ASSERTIONS_ENABLED") != null
					? System.getenv("SPRING_CLOUD_SLEUTH_ASSERTIONS_ENABLED") : "false"));

	private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

	private DocumentedSpanAssertions() {
		throw new IllegalStateException("Can't instantiate utility class");
	}

	static void assertThatKeyIsValid(String key, TagKey[] allowedKeys) {
		if (SLEUTH_SPAN_ASSERTIONS_ON) {
			boolean validTagKey = Arrays.stream(allowedKeys)
					.anyMatch(tagKey -> patternOrValueMatches(key, tagKey.getKey()));
			if (!validTagKey) {
				throw new AssertionError("The key [" + key + "] is invalid. You can use only one matching "
						+ Arrays.stream(allowedKeys).map(TagKey::getKey).collect(Collectors.toList()));
			}
		}
	}

	static void assertThatKeyIsValid(TagKey key, TagKey[] allowedKeys) {
		if (SLEUTH_SPAN_ASSERTIONS_ON) {
			if (Arrays.stream(allowedKeys).noneMatch(tagKey -> tagKey == key)) {
				throw new AssertionError("The key [" + key + "] is invalid. You can use only one matching "
						+ Arrays.stream(allowedKeys).map(TagKey::getKey).collect(Collectors.toList()));
			}
		}
	}

	static void assertThatNameIsValid(String name, String allowedName) {
		if (SLEUTH_SPAN_ASSERTIONS_ON && !patternOrValueMatches(name, allowedName)) {
			throw new AssertionError(
					"The name [" + name + "] is invalid. You can use only one matching [" + allowedName + "]");
		}
	}

	static void assertThatEventIsValid(String eventValue, EventValue[] allowed) {
		if (SLEUTH_SPAN_ASSERTIONS_ON) {
			boolean valid = Arrays.stream(allowed)
					.anyMatch(value -> patternOrValueMatches(eventValue, value.getValue()));
			if (!valid) {
				throw new AssertionError("The event [" + eventValue + "] is invalid. You can use only one matching "
						+ Arrays.stream(allowed).map(EventValue::getValue).collect(Collectors.toList()));
			}
		}
	}

	static void assertThatEventIsValid(EventValue eventValue, EventValue[] allowed) {
		if (SLEUTH_SPAN_ASSERTIONS_ON) {
			boolean valid = Arrays.stream(allowed).noneMatch(value -> value == eventValue);
			if (!valid) {
				throw new AssertionError("The event [" + eventValue + "] is invalid. You can use only one matching "
						+ Arrays.toString(allowed));
			}
		}
	}

	static void assertThatSpanStartedBeforeEnd(AssertingSpan span) {
		if (SLEUTH_SPAN_ASSERTIONS_ON && !span.isStarted()) {
			throw new AssertionError("The span was not started, however you're trying to end it");
		}
	}

	static boolean patternOrValueMatches(String pickedValue, String allowedValue) {
		if (allowedValue.contains("%s")) {
			String stringPattern = allowedValue.replaceAll("%s", ".*?");
			Pattern pattern = PATTERN_CACHE.computeIfAbsent(stringPattern, Pattern::compile);
			return pattern.matcher(pickedValue).matches();
		}
		return allowedValue.equals(pickedValue);
	}

}
