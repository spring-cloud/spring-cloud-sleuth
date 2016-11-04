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

package org.springframework.cloud.sleuth.instrument.web;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
	private final Map<String, String> additionalHeaders = new HashMap<>();

	HttpServletRequestTextMap(HttpServletRequest delegate) {
		this.delegate = delegate;
		UrlPathHelper urlPathHelper = new UrlPathHelper();
		this.additionalHeaders.put(ZipkinHttpSpanExtractor.URI_HEADER,
				urlPathHelper.getPathWithinApplication(delegate));
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		Map<String, String> map = new HashMap<>();
		Enumeration<String> headerNames = this.delegate.getHeaderNames();
		while (headerNames != null && headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			map.put(name, this.delegate.getHeader(name));
		}
		map.putAll(this.additionalHeaders);
		return map.entrySet().iterator();
	}

	@Override
	public void put(String key, String value) {
		this.additionalHeaders.put(key, value);
	}
}
