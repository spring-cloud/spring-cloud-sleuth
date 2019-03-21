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

package org.springframework.cloud.sleuth.instrument.zuul;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.client.methods.RequestBuilder;
import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.Tracer;

/**
 * Customization of a Ribbon request for Apache HttpClient
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
class ApacheHttpClientRibbonRequestCustomizer extends SpanInjectingRibbonRequestCustomizer<RequestBuilder> {

	ApacheHttpClientRibbonRequestCustomizer(Tracer tracer) {
		super(tracer);
	}

	@Override
	public boolean accepts(Class aClass) {
		return aClass == RequestBuilder.class;
	}

	@Override
	protected SpanTextMap toSpanTextMap(final RequestBuilder context) {
		return new SpanTextMap() {
			@Override public Iterator<Map.Entry<String, String>> iterator() {
				Map<String, String> map = new HashMap<>();
				for (Header header : context.build().getAllHeaders()) {
					map.put(header.getName(), header.getValue());
				}
				return map.entrySet().iterator();
			}

			@Override public void put(String key, String value) {
				context.setHeader(key, value);
			}
		};
	}

}
