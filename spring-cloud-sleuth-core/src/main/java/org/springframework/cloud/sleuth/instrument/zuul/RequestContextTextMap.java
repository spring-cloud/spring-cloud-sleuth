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

import java.util.Iterator;
import java.util.Map;

import com.netflix.zuul.context.RequestContext;

import org.springframework.cloud.sleuth.SpanTextMap;

/**
 * A {@link SpanTextMap} abstraction over {@link RequestContext}
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
class RequestContextTextMap implements SpanTextMap {

	private final RequestContext carrier;

	RequestContextTextMap(RequestContext carrier) {
		this.carrier = carrier;
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		return this.carrier.getZuulRequestHeaders().entrySet().iterator();
	}

	@Override
	public void put(String key, String value) {
		this.carrier.getZuulRequestHeaders().put(key, value);
	}
}
