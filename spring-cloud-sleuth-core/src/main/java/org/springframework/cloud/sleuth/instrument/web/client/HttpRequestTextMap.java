/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.http.HttpRequest;
import org.springframework.util.StringUtils;

/**
 * A {@link SpanTextMap} abstraction over {@link HttpRequest}
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
class HttpRequestTextMap implements SpanTextMap {

	private final HttpRequest delegate;

	HttpRequestTextMap(HttpRequest delegate) {
		this.delegate = delegate;
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		final Iterator<Map.Entry<String, List<String>>> iterator = this.delegate.getHeaders()
				.entrySet().iterator();
		return new Iterator<Map.Entry<String, String>>() {
			@Override public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override public Map.Entry<String, String> next() {
				Map.Entry<String, List<String>> next = iterator.next();
				List<String> value = next.getValue();
				return new AbstractMap.SimpleEntry<>(next.getKey(), value.isEmpty() ? "" : value.get(0));
			}
		};
	}

	@Override
	public void put(String key, String value) {
		if (!StringUtils.hasText(value)) {
			return;
		}
		this.delegate.getHeaders().put(key, Collections.singletonList(value));
	}
}
