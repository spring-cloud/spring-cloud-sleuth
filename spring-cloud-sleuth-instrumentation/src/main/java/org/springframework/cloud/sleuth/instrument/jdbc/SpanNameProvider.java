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

package org.springframework.cloud.sleuth.instrument.jdbc;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

class SpanNameProvider {

	private static final String DEFAULT_SPAN_NAME = "query";

	private static final Pattern PATTERN_MATCHING_FIRST_WORD_OF_SQL = compile("^([a-zA-Z]+)[^a-zA-Z]?.*$");

	String getSpanNameFor(String sql) {
		String spanName = DEFAULT_SPAN_NAME;
		if (!Objects.isNull(sql)) {
			spanName = getSpanNameForNonNull(sql);
		}

		return spanName;
	}

	private String getSpanNameForNonNull(String sql) {
		String spanName = DEFAULT_SPAN_NAME;
		Matcher matcher = PATTERN_MATCHING_FIRST_WORD_OF_SQL.matcher(sql);

		if (matcher.matches()) {
			spanName = matcher.group(1).toLowerCase(Locale.ROOT);
		}

		return spanName;
	}

}
