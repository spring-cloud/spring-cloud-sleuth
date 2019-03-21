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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.util.StringUtils;

import feign.Request;

/**
 * A {@link SpanTextMap} abstraction over {@link AtomicReference<Request>}
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
class FeignRequestTextMap implements SpanTextMap {

	private final AtomicReference<Request> delegate;

	FeignRequestTextMap(AtomicReference<Request> delegate) {
		this.delegate = delegate;
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		final Iterator<Map.Entry<String, Collection<String>>> iterator = this.delegate.get().headers().entrySet().iterator();
		return new Iterator<Map.Entry<String, String>>() {
			@Override public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override public Map.Entry<String, String> next() {
				Map.Entry<String, Collection<String>> next = iterator.next();
				Collection<String> value = next.getValue();
				return new AbstractMap.SimpleEntry<>(next.getKey(), value.isEmpty() ? "" : value.iterator().next());
			}
		};
	}

	@Override
	public void put(String key, String value) {
		if (!StringUtils.hasText(value)) {
			return;
		}
		String method = this.delegate.get().method();
		String url = this.delegate.get().url();
		Map<String, Collection<String>> headers = new HashMap<>(this.delegate.get().headers());
		byte[] body = this.delegate.get().body();
		Charset charset = this.delegate.get().charset();
		addHeader(key, value, headers);
		this.delegate.set(Request.create(method, url, headers, body, charset));
	}

	private void addHeader(String key, String value,
			Map<String, Collection<String>> headers) {
		if (!headers.containsKey(key)) {
			List<String> list = new ArrayList<>();
			list.add(value);
			headers.put(key, list);
		}
	}
}
