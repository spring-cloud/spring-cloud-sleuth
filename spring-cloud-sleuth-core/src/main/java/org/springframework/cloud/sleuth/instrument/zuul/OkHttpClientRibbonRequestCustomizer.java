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
import java.util.List;
import java.util.Map;

import org.springframework.cloud.sleuth.SpanTextMap;
import org.springframework.cloud.sleuth.Tracer;

import okhttp3.Request;

/**
 * Customization of a Ribbon request for OkHttp
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
class OkHttpClientRibbonRequestCustomizer extends SpanInjectingRibbonRequestCustomizer<Request.Builder> {

	OkHttpClientRibbonRequestCustomizer(Tracer tracer) {
		super(tracer);
	}

	@Override
	public boolean accepts(Class aClass) {
		return aClass == Request.Builder.class;
	}

	@Override
	protected SpanTextMap toSpanTextMap(final Request.Builder context) {
		return new SpanTextMap() {
			@Override public Iterator<Map.Entry<String, String>> iterator() {
				Map<String, String> map = new HashMap<>();
				for (Map.Entry<String, List<String>> entry : context.build().headers().toMultimap().entrySet()) {
					if (!entry.getValue().isEmpty()) {
						map.put(entry.getKey(), entry.getValue().get(0));
					}
				}
				return map.entrySet().iterator();
			}

			@Override public void put(String key, String value) {
				context.header(key, value);
			}
		};
	}
}
