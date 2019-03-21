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

package org.springframework.cloud.sleuth.instrument.web;

import java.util.AbstractMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.web.util.UrlPathHelper;

/**
 * A {@link SpanTextMap} abstraction over {@link HttpServletRequest}
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
class HttpServletRequestTextMap implements SpanTextMap {

	private final HttpServletRequest delegate;
	private final UrlPathHelper urlPathHelper;

	HttpServletRequestTextMap(HttpServletRequest delegate) {
		this.delegate = delegate;
		this.urlPathHelper = new UrlPathHelper();
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		final Enumeration<String> headerNames = this.delegate.getHeaderNames();

		return new Iterator<Map.Entry<String, String>>() {

			private boolean useAdditionalHeader = true;

			@Override
			public boolean hasNext() {
				return useAdditionalHeader
						|| (headerNames != null && headerNames.hasMoreElements());
			}

			@Override
			public Map.Entry<String, String> next() {
				if (useAdditionalHeader) {
					useAdditionalHeader = false;
					return new AbstractMap.SimpleImmutableEntry<>(
							ZipkinHttpSpanMapper.URI_HEADER,
							HttpServletRequestTextMap.this.urlPathHelper
									.getPathWithinApplication(
											HttpServletRequestTextMap.this.delegate));
				}

				String name = headerNames.nextElement();
				String value = HttpServletRequestTextMap.this.delegate.getHeader(name);
				return new AbstractMap.SimpleEntry<>(name, value);
			}
		};
	}

	@Override
	public void put(String key, String value) {
		throw new UnsupportedOperationException("change servlet request isn't supported");
	}
}
