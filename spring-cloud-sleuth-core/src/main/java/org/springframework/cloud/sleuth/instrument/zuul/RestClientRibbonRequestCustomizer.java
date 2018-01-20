/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.zuul;

import brave.http.HttpClientAdapter;
import brave.http.HttpTracing;
import brave.propagation.Propagation;

import com.netflix.client.http.HttpRequest;

/**
 * Customization of a Ribbon request for Netflix HttpClient
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
class RestClientRibbonRequestCustomizer extends
		SpanInjectingRibbonRequestCustomizer<HttpRequest.Builder> {

	static final Propagation.Setter<HttpRequest.Builder, String> SETTER =
			new Propagation.Setter<HttpRequest.Builder, String>() {
		@Override public void put(HttpRequest.Builder carrier, String key, String value) {
			if (carrier.build().getHttpHeaders().containsHeader(key)) {
				return;
			}
			carrier.header(key, value);
		}

		@Override public String toString() {
			return "RequestBuilder::addHeader";
		}
	};

	RestClientRibbonRequestCustomizer(HttpTracing tracer) {
		super(tracer);
	}

	@Override
	public boolean accepts(Class aClass) {
		return aClass == HttpRequest.Builder.class;
	}

	@Override
	protected HttpClientAdapter<HttpRequest.Builder, HttpRequest.Builder> handlerClientAdapter() {
		return new HttpClientAdapter<HttpRequest.Builder, HttpRequest.Builder>() {
			@Override public String method(HttpRequest.Builder request) {
				return request.build().getVerb().verb();
			}

			@Override public String url(HttpRequest.Builder request) {
				return request.build().getUri().toString();
			}

			@Override
			public String requestHeader(HttpRequest.Builder request, String name) {
				return request.build().getHttpHeaders().getFirstValue(name);
			}

			@Override public Integer statusCode(HttpRequest.Builder response) {
				throw new UnsupportedOperationException("response not supported");
			}
		};
	}

	@Override protected Propagation.Setter<HttpRequest.Builder, String> setter() {
		return SETTER;
	}
}
