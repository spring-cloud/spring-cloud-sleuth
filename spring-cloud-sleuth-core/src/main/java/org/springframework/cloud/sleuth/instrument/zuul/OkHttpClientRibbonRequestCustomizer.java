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
import okhttp3.Request;

/**
 * Customization of a Ribbon request for OkHttp
 *
 * @author Marcin Grzejszczak
 * @since 1.1.0
 */
class OkHttpClientRibbonRequestCustomizer extends
		SpanInjectingRibbonRequestCustomizer<Request.Builder> {

	static final Propagation.Setter<Request.Builder, String> SETTER =
			new Propagation.Setter<Request.Builder, String>() {
		@Override public void put(Request.Builder carrier, String key, String value) {
			if (carrier.build().header(key) != null) {
				return;
			}
			carrier.addHeader(key, value);
		}

		@Override public String toString() {
			return "RequestBuilder::addHeader";
		}
	};

	OkHttpClientRibbonRequestCustomizer(HttpTracing tracer) {
		super(tracer);
	}

	@Override
	public boolean accepts(Class aClass) {
		return aClass == Request.Builder.class;
	}

	@Override
	protected HttpClientAdapter<Request.Builder, Request.Builder> handlerClientAdapter() {
		return new HttpClientAdapter<Request.Builder, Request.Builder>() {
			@Override public String method(Request.Builder request) {
				return request.build().method();
			}

			@Override public String url(Request.Builder request) {
				return request.build().url().uri().toString();
			}

			@Override public String requestHeader(Request.Builder request, String name) {
				return request.build().header(name);
			}

			@Override public Integer statusCode(Request.Builder response) {
				throw new UnsupportedOperationException("response not supported");
			}
		};
	}

	@Override protected Propagation.Setter<Request.Builder, String> setter() {
		return SETTER;
	}
}
